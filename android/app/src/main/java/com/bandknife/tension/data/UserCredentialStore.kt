package com.bandknife.tension.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** サインイン中の使用者。secret はパスワードそのものではなく PBKDF2 で導出した合言葉。 */
data class UserCredential(
    val name: String,
    val secret: String,
    /** true ならこの端末だけの使用者。ドライブには登録されない。 */
    val localOnly: Boolean = false
)

/**
 * 使用者の合言葉を端末に保存する。毎回パスワードを入力させないための仕組み。
 *
 * 端末が他人の手に渡っても設備マスターを書き換えられないよう、
 * Android のキーストアで暗号化した領域に置く。
 */
class UserCredentialStore(private val context: Context) {
    private val _credential = MutableStateFlow<UserCredential?>(null)
    val credential: StateFlow<UserCredential?> = _credential.asStateFlow()

    val current: UserCredential? get() = _credential.value

    /**
     * 鍵の生成はキーストアへのアクセスを伴い、機種によっては数百ミリ秒かかる。
     * 起動を止めないよう、初回だけ裏で開く。
     */
    @Volatile
    private var prefs: SharedPreferences? = null
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefs = openEncrypted(context)
            _credential.value = load()
            ready.complete(Unit)
        }
    }

    /** 保存できたかを返す。書けなかったのに成功と見せると、再起動で無言で消える。 */
    suspend fun save(credential: UserCredential): Boolean = withContext(Dispatchers.IO) {
        ready.await()
        val store = prefs ?: return@withContext false
        val saved = store.edit()
            .putString(KEY_NAME, credential.name)
            .putString(KEY_SECRET, credential.secret)
            .putBoolean(KEY_LOCAL_ONLY, credential.localOnly)
            .commit()
        if (saved) _credential.value = credential
        saved
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        ready.await()
        prefs?.edit()?.clear()?.commit()
        _credential.value = null
    }

    private fun load(): UserCredential? {
        val store = prefs ?: return null
        val name = store.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val secret = store.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() } ?: return null
        val localOnly = store.getBoolean(KEY_LOCAL_ONLY, false)
        return UserCredential(name, secret, localOnly)
    }

    private companion object {
        const val FILE_NAME = "user_credential"
        const val KEY_NAME = "name"
        const val KEY_SECRET = "secret"
        const val KEY_LOCAL_ONLY = "local_only"

        /**
         * バックアップからの復元などで鍵と暗号文が食い違うと開けなくなる。
         * その場合は保存内容を捨てて作り直し、再ログインで復旧できるようにする。
         */
        fun openEncrypted(context: Context): SharedPreferences? =
            runCatching { create(context) }
                .recoverCatching {
                    context.deleteSharedPreferences(FILE_NAME)
                    create(context)
                }
                .getOrNull()

        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
