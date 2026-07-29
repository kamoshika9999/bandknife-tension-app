package com.bandknife.tension.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.bandknife.tension.domain.PasswordPolicy
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.viewmodel.AppViewModel

/**
 * ローカルモードの切り替えと、端末専用使用者の設定。
 * ドライブが長期間使えないときの逃がし道だが、規格の一元管理は外れるため慎重に切り替える。
 */
@Composable
fun LocalModeSection(vm: AppViewModel, modifier: Modifier = Modifier) {
    val localMode by vm.localMode.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val accountBusy by vm.accountBusy.collectAsState()
    var showSwitchDialog by remember { mutableStateOf(false) }
    var showLocalUserDialog by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeError by remember { mutableStateOf(false) }

    if (showSwitchDialog) {
        LocalModeSwitchDialog(
            enabling = !localMode,
            busy = accountBusy,
            hasUser = currentUser != null,
            onDismiss = { showSwitchDialog = false },
            onConfirm = { password ->
                vm.setLocalMode(!localMode, password) { report ->
                    notice = report.message
                    noticeError = !report.success
                    if (report.success) showSwitchDialog = false
                }
            },
            onRequestLocalUser = {
                showSwitchDialog = false
                showLocalUserDialog = true
            }
        )
    }
    if (showLocalUserDialog) {
        LocalUserSetupDialog(
            busy = accountBusy,
            onDismiss = { showLocalUserDialog = false },
            onSubmit = { name, password ->
                vm.registerLocalUser(name, password) { report ->
                    notice = report.message
                    noticeError = !report.success
                    if (report.success) showLocalUserDialog = false
                }
            }
        )
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "ローカルモード",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            if (localMode) {
                "ドライブ連携なしで測定します。記録はこの端末だけに残り、ドライブへは送りません。" +
                    "設備の規格は端末内の設定を使います。"
            } else {
                "ドライブ連携やアップロードが長期間使えないときに、端末だけで測定を続けられます。" +
                    "切り替えにはパスワードが必要です。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXs)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceSm),
            colors = CardDefaults.cardColors(
                containerColor = if (localMode) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                Modifier.padding(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (localMode) "ローカルモード中" else "ドライブ連携モード",
                        style = MaterialTheme.typography.titleSmall
                    )
                    currentUser?.let { user ->
                        val suffix = if (user.localOnly) "（この端末のみ）" else ""
                        Text(
                            "使用者: ${user.name}$suffix",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } ?: Text(
                        "使用者が未設定です",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        notice?.let { message ->
            if (noticeError) FormError(message) else FormSuccess(message)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Button(
                onClick = { showSwitchDialog = true },
                enabled = !accountBusy,
                modifier = Modifier.heightIn(min = Dimens.MinTouch)
            ) {
                Text(if (localMode) "ドライブ連携に戻す" else "ローカルモードに切り替え")
            }
            if (currentUser == null || currentUser?.localOnly == true) {
                OutlinedButton(
                    onClick = { showLocalUserDialog = true },
                    enabled = !accountBusy,
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) {
                    Text(if (currentUser == null) "端末の使用者を設定" else "使用者を変更")
                }
            }
        }
    }
}

@Composable
private fun LocalModeSwitchDialog(
    enabling: Boolean,
    busy: Boolean,
    hasUser: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onRequestLocalUser: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(if (enabling) "ローカルモードに切り替え" else "ドライブ連携に戻す")
        },
        text = {
            Column {
                Text(
                    if (enabling) {
                        "ドライブへの接続確認とアップロードを止め、この端末だけで測定します。\n\n" +
                            "・ローカルモード中に保存した記録は、あとでドライブ連携に戻しても送信されません\n" +
                            "・設備の規格は端末内の設定が使われます\n" +
                            "・設備マスターの共有はできません"
                    } else {
                        "ドライブ連携を再開します。\n\n" +
                            "・ローカルモード中の記録は端末のみのまま残ります\n" +
                            "・起動時に設備マスターを取り込みます"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!hasUser) {
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    Text(
                        "先に端末の使用者を設定してください。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    PasswordField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = "パスワード",
                        supportingText = "設定済みの使用者のパスワードを入力してください",
                        enabled = !busy,
                        onImeAction = {
                            validatePassword(password)?.let { error = it }
                                ?: onConfirm(password)
                        }
                    )
                    error?.let { FormError(it) }
                }
            }
        },
        confirmButton = {
            if (!hasUser) {
                TextButton(onClick = onRequestLocalUser) { Text("使用者を設定") }
            } else {
                DialogActionButton(
                    busy = busy,
                    busyLabel = "処理中…",
                    label = if (enabling) "切り替える" else "戻す",
                    onClick = {
                        validatePassword(password)?.let { error = it }
                            ?: onConfirm(password)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") }
        }
    )
}

@Composable
private fun LocalUserSetupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        PasswordPolicy.nameError(name)?.let { error = it; return }
        PasswordPolicy.passwordError(password)?.let { error = it; return }
        PasswordPolicy.confirmError(password, confirm)?.let { error = it; return }
        onSubmit(name.trim(), password)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("この端末の使用者") },
        text = {
            Column {
                Text(
                    "ドライブに登録せず、この端末だけで設備を変更するための名前です。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = Dimens.SpaceSm)
                )
                error?.let {
                    FormError(it)
                    Spacer(Modifier.height(Dimens.SpaceSm))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名前") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "パスワード",
                    enabled = !busy
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = "パスワード（確認）",
                    enabled = !busy,
                    onImeAction = { submit() }
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                busy = busy,
                busyLabel = "処理中…",
                label = "設定する",
                onClick = { submit() }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") }
        }
    )
}

private fun validatePassword(password: String): String? = PasswordPolicy.passwordError(password)
