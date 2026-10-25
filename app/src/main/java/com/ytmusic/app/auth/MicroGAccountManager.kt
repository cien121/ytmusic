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
import com.google.android.gms.common.api.ApiException
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

    /** MicroG 的全部已知包名：只查第一个会漏掉 MicroG RE，必须全查 */
    val MICROG_PACKAGES = listOf(
        "com.google.android.gms",
        "com.mgoogle.android.gms",
        "app.revanced.microg"
    )

    /** 是否装了 MicroG（任一包名命中即算装） */
    fun isMicroGInstalled(context: Context): Boolean =
        getInstalledMicroGPackage(context) != null

    /** 返回命中的 MicroG 包名，未装返回 null */
    fun getInstalledMicroGPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in MICROG_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // 继续查下一个包名
            }
        }
        return null
    }

    /**
     * 登录失败原因翻译：不只给状态码，分清三种情况。
     * ApiException 状态码：12501=用户取消，12500=无可用账号，
     * 10=DEVELOPER_ERROR（MicroG 版本不支持），8=内部错误。
     */
    fun describeSignInError(e: Exception): String {
        if (e is ApiException) {
            return when (e.statusCode) {
                12501 -> "已取消登录"
                12500 -> "MicroG 里没有可用的谷歌账号：请先打开 MicroG 设置添加谷歌账号，再回 App 点登录"
                10 -> "MicroG 版本不支持登录：请更新到最新版 MicroG RE 再试"
                8 -> "MicroG 登录接口异常：请检查 MicroG 是否正常运行，或重装 MicroG 再试"
                17 -> "MicroG 服务未连接：请打开 MicroG 设置检查服务状态"
                else -> "登录失败（错误码 ${e.statusCode}）：请检查 MicroG 设置，或更新 MicroG 再试"
            }
        }
        return "登录失败：${e.message ?: "未知错误"}，请检查 MicroG 设置"
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
     * 第二步用：拿 YouTube API 的 OAuth token（歌单、喜欢的音乐、订阅同步）。
     * 走 GoogleAuthUtil + MicroG，scope 是 YouTube 只读；账号用已保存的邮箱重建，
     * 不依赖登录当时的 GoogleSignInAccount 对象。
     */
    suspend fun getYouTubeAuthToken(context: Context, email: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val gmsAccount = android.accounts.Account(email, "com.google")
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
