package com.ytmusic.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.ytmusic.app.auth.MicroGAccountManager

/**
 * 右上角账号入口（首页/搜索/媒体库共用）。
 * 登录流程（防闪退版）：
 * 1. 已登录 → 点开看账号信息 / 退出
 * 2. 未装 MicroG → 提示先装 MicroG
 * 3. 装了 MicroG → 先弹提示「请先在 MicroG 设置里添加谷歌账号，再回 App 点登录」，
 *    点「去登录」才拉起 MicroG 登录页
 * 4. 登录失败 → 按原因给清楚的提示，不只显示状态码
 */
@Composable
fun AccountIconButton() {
    val context = LocalContext.current

    var account by remember { mutableStateOf(MicroGAccountManager.loadAccount(context)) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showMicroGMissingDialog by remember { mutableStateOf(false) }
    var showLoginHintDialog by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode != Activity.RESULT_OK) {
                loginMessage = "已取消登录"
                return@rememberLauncherForActivityResult
            }
            val data = result.data
            if (data == null) {
                loginMessage = "登录失败：MicroG 没有返回登录结果，请检查 MicroG 设置"
                return@rememberLauncherForActivityResult
            }
            val gmsAccount = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            MicroGAccountManager.saveAccount(context, gmsAccount)
            account = MicroGAccountManager.loadAccount(context)
            loginMessage = "登录成功：${gmsAccount.email ?: ""}"
        } catch (e: ApiException) {
            loginMessage = MicroGAccountManager.describeSignInError(e)
        } catch (e: Exception) {
            // 兜底：MicroG 返回任何意外错误都不闪退，只给提示
            loginMessage = MicroGAccountManager.describeSignInError(e)
        }
    }

    IconButton(onClick = {
        when {
            account != null -> showAccountDialog = true
            !MicroGAccountManager.isMicroGInstalled(context) -> showMicroGMissingDialog = true
            else -> showLoginHintDialog = true
        }
    }) {
        Icon(Icons.Filled.AccountCircle, contentDescription = "账号")
    }

    if (showAccountDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("账号") },
            text = { Text("${account!!.displayName}\n${account!!.email}") },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    MicroGAccountManager.signOut(context)
                    account = null
                    showAccountDialog = false
                }) { Text("退出登录") }
            }
        )
    }

    if (showMicroGMissingDialog) {
        AlertDialog(
            onDismissRequest = { showMicroGMissingDialog = false },
            title = { Text("未检测到 MicroG") },
            text = {
                Text(
                    "请先安装 MicroG（ReVanced MicroG 或 MicroG RE），" +
                        "安装后可登录谷歌账号同步歌单。当前可继续匿名使用，全部功能免费。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showMicroGMissingDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showLoginHintDialog) {
        AlertDialog(
            onDismissRequest = { showLoginHintDialog = false },
            title = { Text("登录谷歌账号") },
            text = {
                Text(
                    "请先在 MicroG 设置里添加谷歌账号，再回 App 点登录。\n\n" +
                        "登录后可同步你的歌单、喜欢的音乐和订阅，全部免费。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLoginHintDialog = false
                    try {
                        signInLauncher.launch(
                            MicroGAccountManager.getSignInIntent(context)
                        )
                    } catch (e: Exception) {
                        // 拉不起登录页（MicroG 版本不支持等）也不闪退
                        loginMessage = "无法打开登录页：${MicroGAccountManager.describeSignInError(e)}"
                    }
                }) { Text("去登录") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginHintDialog = false }) { Text("取消") }
            }
        )
    }

    loginMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { loginMessage = null },
            title = { Text("登录") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { loginMessage = null }) { Text("知道了") }
            }
        )
    }
}
