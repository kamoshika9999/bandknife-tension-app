package com.bandknife.tension.domain

import java.util.concurrent.TimeUnit

enum class GateStatus {
    /** 期限内。測定できる。 */
    VALID,

    /** 期限が近い。測定はできるが確認を促す。 */
    EXPIRING,

    /** 期限切れ、または一度も確認できていない。測定を止める。 */
    BLOCKED
}

/**
 * ドライブの設備マスターとの接続確認の鮮度。
 *
 * 設備の規格値が古いまま合否を出すと、不良を良品と判定してしまう。
 * そのため一定期間ドライブを確認できていない端末では測定させない。
 */
data class MeasurementGate(
    val lastSyncAt: Long,
    val now: Long
) {
    /** 未確認のまま経過したミリ秒。一度も確認できていなければ期限切れとして扱う。 */
    private val elapsedMillis: Long =
        if (lastSyncAt <= 0L) Long.MAX_VALUE else (now - lastSyncAt).coerceAtLeast(0L)

    val neverSynced: Boolean = lastSyncAt <= 0L

    /** 測定できなくなるまでの残り日数。切り上げのため「あと1日」は24時間未満を含む。 */
    val remainingDays: Int =
        if (neverSynced) 0
        else ceilDays((VALID_DURATION_MILLIS - elapsedMillis).coerceAtLeast(0L))

    /** 最後に確認できてからの経過日数。 */
    val elapsedDays: Int = if (neverSynced) 0 else (elapsedMillis / DAY_MILLIS).toInt()

    val status: GateStatus = when {
        elapsedMillis >= VALID_DURATION_MILLIS -> GateStatus.BLOCKED
        elapsedMillis >= WARN_AFTER_MILLIS -> GateStatus.EXPIRING
        else -> GateStatus.VALID
    }

    val canMeasure: Boolean get() = status != GateStatus.BLOCKED

    private fun ceilDays(millis: Long): Int =
        ((millis + DAY_MILLIS - 1) / DAY_MILLIS).toInt()

    companion object {
        /** 接続確認が有効な期間。 */
        val VALID_DAYS: Long = 7

        /** 残りがこの日数を切ったら警告を出す。 */
        val WARN_WITHIN_DAYS: Long = 2

        private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)
        private val VALID_DURATION_MILLIS = TimeUnit.DAYS.toMillis(VALID_DAYS)
        private val WARN_AFTER_MILLIS = TimeUnit.DAYS.toMillis(VALID_DAYS - WARN_WITHIN_DAYS)
    }
}
