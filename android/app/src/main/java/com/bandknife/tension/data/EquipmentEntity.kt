package com.bandknife.tension.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bandknife.tension.domain.BladeMaterialPreset

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ドライブ上の設備マスターと突き合わせる不変の識別子。設備名を変えても追跡できる。 */
    val uuid: String = "",
    /**
     * ドライブの設備マスターに載っていることを確認できた最終時刻（ミリ秒）。
     * null はドライブ未登録を意味し、この設備では測定できない。
     */
    val syncedAt: Long? = null,
    val name: String,
    val massPerMeter: Double,
    val spanMeters: Double,
    val standardTension: Double,
    val specLower: Double,
    val specUpper: Double,
    val useHzMode: Boolean = false,
    val standardHz: Double = 0.0,
    val specHzLower: Double = 0.0,
    val specHzUpper: Double = 0.0,
    val widthMm: Double = 0.0,
    val thicknessMm: Double = 0.0,
    val density: Double = 7850.0,
    /** ヤング率 (GPa)。曲げ剛性 EI の計算に使う。 */
    val youngModulusGpa: Double = 210.0,
    /** 材種プリセット ID（[com.bandknife.tension.domain.BladeMaterialPreset]） */
    val materialId: String = BladeMaterialPreset.CARBON_STEEL.id,
    /** 振動モード次数 n（1=基本モード） */
    val vibrationMode: Int = 1,
    /** true=面内曲げ (t·w³/12)、false=面外曲げ (w·t³/12)。張力測定は面外が感度が高い */
    val edgewiseBending: Boolean = false,
    val deleted: Boolean = false,
    /** 論理削除した日時（ミリ秒）。未削除は null */
    val deletedAt: Long? = null
) {
    /** ドライブの設備マスターに登録済みか。未登録の設備は測定に使えない。 */
    val isSynced: Boolean get() = syncedAt != null

    /**
     * 合否を判定できるだけの規格値が入っているか。
     * 下限も上限も 0 の設備で測ると、何を測っても合格になってしまう。
     */
    val hasUsableSpec: Boolean
        get() = massPerMeter > 0 && spanMeters > 0 &&
            if (useHzMode) specHzLower > 0 && specHzUpper > specHzLower
            else specLower > 0 && specUpper > specLower
}
