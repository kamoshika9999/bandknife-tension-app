package com.bandknife.tension.data

import android.content.Context
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.upload.DriveUploader
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    private val equipmentDao = db.equipmentDao()
    private val recordDao = db.recordDao()
    private val uploadQueueDao = db.uploadQueueDao()
    private val prefs = PreferencesManager(context)
    private val uploader = DriveUploader(context, uploadQueueDao)

    val equipment: Flow<List<EquipmentEntity>> = equipmentDao.observeAll()
    val deletedEquipment: Flow<List<EquipmentEntity>> = equipmentDao.observeDeleted()
    val records: Flow<List<MeasurementRecordEntity>> = recordDao.observeAll()
    val preferences = prefs

    suspend fun getEquipment(id: Long) = equipmentDao.getById(id)
    suspend fun saveEquipment(entity: EquipmentEntity) = equipmentDao.insert(entity)
    suspend fun updateEquipment(entity: EquipmentEntity) = equipmentDao.update(entity)
    suspend fun deleteEquipment(entity: EquipmentEntity) = equipmentDao.softDelete(entity.id)
    suspend fun restoreEquipment(entity: EquipmentEntity) = equipmentDao.restore(entity.id)

    suspend fun saveRecord(record: MeasurementRecordEntity): Long {
        val id = recordDao.insert(record)
        val saved = record.copy(id = id)
        uploader.enqueueIfEnabled(saved)
        return id
    }

    suspend fun updateRecord(record: MeasurementRecordEntity) = recordDao.update(record)

    suspend fun deleteRecord(record: MeasurementRecordEntity) {
        if (!record.isLocked) recordDao.delete(record)
    }

    suspend fun deleteRecordsInRange(from: Long, to: Long) = recordDao.deleteInRange(from, to)

    fun recordsForEquipment(equipmentId: Long): Flow<List<MeasurementRecordEntity>> =
        recordDao.observeByEquipment(equipmentId)

    suspend fun exportBackup(): String {
        val json = JSONObject()
        val eqArray = JSONArray()
        equipmentDao.getAll().forEach { eq ->
            eqArray.put(JSONObject().apply {
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
            })
        }
        json.put("equipment", eqArray)
        return json.toString(2)
    }

    suspend fun processUploadQueue() = uploader.processQueue()

    suspend fun testUpload() = uploader.testUpload()

    suspend fun pendingUploadCount() = uploadQueueDao.count()

    fun evaluate(equipment: EquipmentEntity, frequencyHz: Double, tensionN: Double): Boolean {
        return if (equipment.useHzMode) {
            frequencyHz in equipment.specHzLower..equipment.specHzUpper
        } else {
            TensionCalculator.isWithinSpec(tensionN, equipment.specLower, equipment.specUpper)
        }
    }

    fun tensionFor(equipment: EquipmentEntity, frequencyHz: Double): Double {
        return TensionCalculator.tensionNewtons(equipment.massPerMeter, equipment.spanMeters, frequencyHz)
    }
}
