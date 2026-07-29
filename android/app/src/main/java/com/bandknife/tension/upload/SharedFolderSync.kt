package com.bandknife.tension.upload

import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.domain.GasRecordsFormat
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties

/**
 * GAS がドライブへ書き込むのと同一の records.csv / equipment.json を
 * 工場 LAN の共有フォルダへ置く。到達できないときは黙ってスキップする。
 */
class SharedFolderSync {

    suspend fun isAccessible(): Boolean = withContext(Dispatchers.IO) {
        runCatching { smbFile(DATA_SMB_URL).exists() }.getOrDefault(false)
    }

    /** 送信済みの測定記録を共有フォルダへ追記（重複は無視）。 */
    suspend fun mirrorRecord(record: MeasurementRecordEntity) {
        if (record.localOnly) return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = smbFile(RECORDS_CSV)
                val existing = if (file.exists()) file.readText() else ""
                if (GasRecordsFormat.containsTimestamp(existing, record.timestamp)) return@runCatching
                val row = GasRecordsFormat.toCsvRow(record)
                if (existing.isEmpty()) {
                    file.writeText(GasRecordsFormat.header() + row)
                } else {
                    file.appendText(row)
                }
            }
        }
    }

    /** 設備マスターを共有フォルダへ書き込む（リビジョンが新しいときだけ）。 */
    suspend fun mirrorEquipment(master: EquipmentMaster) {
        if (master.revision <= 0 && master.equipment.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = smbFile(EQUIPMENT_JSON)
                if (file.exists()) {
                    val current = parseEquipmentRevision(file.readText())
                    if (current >= master.revision) return@runCatching
                }
                file.writeText(equipmentToJson(master))
            }
        }
    }

    /**
     * 起動時の過不足修正。送信済み記録と設備マスターを共有フォルダへ反映する。
     * @param pendingTimestamps アップロードキューに残っているタイムスタンプ（未送信）
     */
    suspend fun startupSync(
        uploadedRecords: List<MeasurementRecordEntity>,
        equipmentMaster: EquipmentMaster?,
        pendingTimestamps: Set<Long>
    ) {
        if (!isAccessible()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val toMirror = uploadedRecords.filter { !it.localOnly && it.timestamp !in pendingTimestamps }
                if (toMirror.isNotEmpty()) reconcileRecords(toMirror)
                equipmentMaster?.let { mirrorEquipment(it) }
            }
        }
    }

    private fun reconcileRecords(records: List<MeasurementRecordEntity>) {
        val file = smbFile(RECORDS_CSV)
        val shareBody = if (file.exists()) file.readText() else ""
        val localBody = buildString {
            append(GasRecordsFormat.header())
            records.forEach { append(GasRecordsFormat.toCsvRow(it)) }
        }
        val merged = GasRecordsFormat.mergeBodies(shareBody, localBody.toString())
        file.writeText(merged)
    }

    private fun smbContext(): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.resolveOrder", "DNS")
            setProperty("jcifs.smb.client.responseTimeout", "5000")
        }
        val config = PropertyConfiguration(props)
        return BaseContext(config).withCredentials(NtlmPasswordAuthenticator("", "", ""))
    }

    private fun smbFile(relativePath: String): SmbFile {
        val url = if (relativePath.endsWith("/")) {
            DATA_SMB_URL
        } else {
            "$DATA_SMB_URL$relativePath"
        }
        return SmbFile(url, smbContext())
    }

    private fun SmbFile.readText(): String = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun SmbFile.writeText(text: String) {
        ensureParent(this)
        outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun SmbFile.appendText(text: String) {
        ensureParent(this)
        val out = ByteArrayOutputStream()
        if (exists()) inputStream.use { it.copyTo(out) }
        out.write(text.toByteArray(Charsets.UTF_8))
        outputStream.use { it.write(out.toByteArray()) }
    }

    private fun ensureParent(file: SmbFile) {
        val parent = file.parent ?: return
        SmbFile(parent, smbContext()).mkdirs()
    }

    private fun InputStream.readBytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        copyTo(buffer)
        return buffer.toByteArray()
    }

    private fun equipmentToJson(master: EquipmentMaster): String {
        val equipment = JSONArray()
        master.equipment.forEach { eq ->
            equipment.put(equipmentEntityToJson(eq))
        }
        return JSONObject()
            .put("revision", master.revision)
            .put("updatedAt", master.updatedAt)
            .put("updatedBy", master.updatedBy)
            .put("equipment", equipment)
            .toString(2)
    }

    private fun equipmentEntityToJson(eq: EquipmentEntity) = JSONObject().apply {
        put("uuid", eq.uuid)
        put("name", eq.name)
        put("massPerMeter", eq.massPerMeter)
        put("spanMeters", eq.spanMeters)
        put("standardTension", eq.standardTension)
        put("specLower", eq.specLower)
        put("specUpper", eq.specUpper)
        put("useHzMode", eq.useHzMode)
        put("standardHz", eq.standardHz)
        put("specHzLower", eq.specHzLower)
        put("specHzUpper", eq.specHzUpper)
        put("widthMm", eq.widthMm)
        put("thicknessMm", eq.thicknessMm)
        put("density", eq.density)
        put("youngModulusGpa", eq.youngModulusGpa)
        put("materialId", eq.materialId)
        put("vibrationMode", eq.vibrationMode)
        put("edgewiseBending", eq.edgewiseBending)
    }

    private fun parseEquipmentRevision(json: String): Int =
        runCatching { JSONObject(json).optInt("revision") }.getOrDefault(0)

    private companion object {
        const val SMB_HOST = "192.168.0.101"
        const val SMB_SHARE = "共有フォルダ"
        const val DATA_RELATIVE =
            "湖南工場/湖南共有/002  加工G/●バンドナイフ張力測定/DATA"
        const val DATA_SMB_URL = "smb://$SMB_HOST/$SMB_SHARE/$DATA_RELATIVE/"
        const val RECORDS_CSV = "records.csv"
        const val EQUIPMENT_JSON = "equipment.json"
    }
}
