package com.ytmusic.app.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 谷歌账号登录（MicroG 方案，ReVanced 同款路子）：
 * App 调标准 Google Sign-In API；手机上装了 MicroG（ReVanced MicroG / MicroG RE）
 * 后由 MicroG 响应登录，返回谷歌账号。不要 WebView、不要 OAuth 网页流程。
 * 未登录时全部功能仍可用（NewPipeExtractor 公开数据），登录只是增强。
 */
object MicroGAccountManager {

    private const val PREFS_NAME = "ytmusic_account"
    private const val KEY_SIGNED_IN = "signed_in"
    private const val KEY_EMAIL = "email"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_ACCOUNT_ID = "account_id"

    data class SignedInAccount(
        val email: String,
        val displayName: String,
        val accountId: String
    )

    /** 是否装了 MicroG（包名就是 com.google.android.gms，MicroG 冒充 GMS 提供接口） */
    fun isMicroGInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(context: Context): Intent =
        getSignInClient(context).signInIntent

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveAccount(context: Context, account: GoogleSignInAccount) {
        prefs(context).edit()
            .putBoolean(KEY_SIGNED_IN, true)
            .putString(KEY_EMAIL, account.email ?: "")
            .putString(KEY_DISPLAY_NAME, account.displayName ?: "")
            .putString(KEY_ACCOUNT_ID, account.id ?: "")
            .apply()
    }

    fun loadAccount(context: Context): SignedInAccount? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_SIGNED_IN, false)) return null
        val email = p.getString(KEY_EMAIL, "") ?: ""
        if (email.isEmpty()) return null
        return SignedInAccount(
            email = email,
            displayName = p.getString(KEY_DISPLAY_NAME, "") ?: "",
            accountId = p.getString(KEY_ACCOUNT_ID, "") ?: ""
        )
    }

    fun signOut(context: Context) {
        try {
            getSignInClient(context).signOut()
        } catch (e: Exception) {
            // MicroG 不在时 signOut 也会抛，忽略，只清本地
        }
        prefs(context).edit().clear().apply()
    }

    /**
     * 第二步用：拿 YouTube API 的 OAuth token（歌单、喜欢的音乐等）。
     * 走 GoogleAuthUtil + MicroG，scope 是 YouTube 只读；MVP 先备好不调用。
     */
    suspend fun getYouTubeAuthToken(
        context: Context,
        account: GoogleSignInAccount
    ): String? = withContext(Dispatchers.IO) {
        try {
            val gmsAccount = account.account ?: return@withContext null
            GoogleAuthUtil.getToken(
                context,
                gmsAccount,
                "oauth2:https://www.googleapis.com/auth/youtube.readonly"
            )
        } catch (e: Exception) {
            null
        }
    }
}
