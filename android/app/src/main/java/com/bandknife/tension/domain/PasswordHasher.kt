package com.bandknife.tension.domain

import java.security.spec.KeySpec
import java.text.Normalizer
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * パスワードから、ドライブへ送る合言葉（secret）を作る。
 *
 * パスワードそのものは端末からも外へ出さない。名前を塩に混ぜているため、
 * 同じパスワードでも使用者が違えば別の secret になる。
 */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_PREFIX = "bandknife-tension:v1:"

    /**
     * 照合に使う名前。drive-upload.gs の nameKey と同じ規則にすること。
     * ここがずれると、大文字違いの同名で同じ合言葉が通ってしまう。
     */
    fun nameKey(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    /** 実行に数百ミリ秒かかるため、必ずバックグラウンドで呼ぶこと。 */
    fun derive(name: String, password: String): String {
        val salt = (SALT_PREFIX + nameKey(name)).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec as KeySpec).encoded.toHex()
        } finally {
            // ヒープにパスワードの写しを残さない
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte) }
}

/** パスワードとして受け付ける条件。現場で覚えられる範囲に留める。 */
object PasswordPolicy {
    const val MIN_LENGTH = 6
    const val MAX_NAME_LENGTH = 20

    fun nameError(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "名前を入力してください"
            trimmed.length > MAX_NAME_LENGTH -> "名前は ${MAX_NAME_LENGTH} 文字以内にしてください"
            else -> null
        }
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "パスワードを入力してください"
        password.length < MIN_LENGTH -> "パスワードは ${MIN_LENGTH} 文字以上にしてください"
        else -> null
    }

    fun confirmError(password: String, confirm: String): String? =
        if (password != confirm) "パスワードが一致しません" else null
}
