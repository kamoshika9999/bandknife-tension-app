package com.bandknife.tension.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bandknife.tension.data.UserActionReport
import com.bandknife.tension.domain.PasswordPolicy
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private val HISTORY_TIME_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)

/** 通知を出したまま残さない。読み終わる程度の時間で消す。 */
private const val NOTICE_DURATION_MILLIS = 6_000L

/**
 * 初回起動で使用者を決める画面。
 *
 * 設備の規格値を誰が変えたのかを残すために名前が要る。
 * 測定するだけの端末まで足止めしないよう、後回しにする道も用意する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupScreen(vm: AppViewModel, onSkip: () -> Unit) {
    // 手袋での入力をやり直させないため、プロセス再生成でも名前と選択は残す
    var signInExisting by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var registrationKey by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val busy by vm.accountBusy.collectAsState()

    // 初回設定を途中で抜ける手段は「あとで設定する」だけにする
    BackHandler(enabled = true) {}

    fun submit() {
        if (busy) return
        val validation = PasswordPolicy.nameError(name)
            ?: PasswordPolicy.passwordError(password)
            ?: if (signInExisting) null else PasswordPolicy.confirmError(password, confirm)
        if (validation != null) {
            error = validation
            return
        }
        error = null
        val onDone: (UserActionReport) -> Unit = { report ->
            if (report.success) success = report.message else error = report.message
        }
        if (signInExisting) vm.signIn(name, password, onDone)
        else vm.registerUser(name, password, registrationKey, onDone)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.SpaceXl)
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Dimens.SpaceSm))
        Text(
            "使用者を設定してください",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "設備の規格値を変更した人を記録するため、名前とパスワードを登録します。" +
                "パスワードはこの端末に保存されるので、次回からは入力不要です。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceSm)
        )

        Spacer(Modifier.height(Dimens.SpaceLg))
        AccountModeSelector(
            signInExisting = signInExisting,
            onSelect = { signInExisting = it; error = null }
        )

        Spacer(Modifier.height(Dimens.SpaceMd))
        error?.let { FormError(it) }
        success?.let { FormSuccess(it) }

        Spacer(Modifier.height(Dimens.SpaceSm))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("名前") },
            supportingText = { Text("測定履歴や設備の更新記録に残ります") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Dimens.SpaceSm))
        PasswordField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "パスワード",
            supportingText = "${PasswordPolicy.MIN_LENGTH} 文字以上",
            enabled = !busy,
            imeAction = if (signInExisting) ImeAction.Done else ImeAction.Next,
            onImeAction = { if (signInExisting) submit() }
        )
        if (!signInExisting) {
            Spacer(Modifier.height(Dimens.SpaceSm))
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it; error = null },
                label = "パスワード（確認）",
                supportingText = "同じパスワードをもう一度入力してください",
                enabled = !busy,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(Dimens.SpaceSm))
            OutlinedTextField(
                value = registrationKey,
                onValueChange = { registrationKey = it; error = null },
                label = { Text("登録キー（任意）") },
                supportingText = { Text("管理者から渡されている場合だけ入力してください") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(Dimens.SpaceLg))
        Button(
            onClick = ::submit,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.ButtonHeight)
        ) {
            if (busy) {
                InlineSpinner(MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(Dimens.SpaceSm))
                Text("確認中")
            } else {
                Text(if (signInExisting) "この名前で続ける" else "登録する")
            }
        }
        // 「登録する」の誤タップでスキップしないよう間を空ける
        Spacer(Modifier.height(Dimens.SpaceXl))
        Text(
            "電波が届かない場所ではクラウドへの登録はできません。" +
                "「あとで設定する」を選び、詳細モードの「その他」→ ローカルモードから端末だけで使えます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = onSkip,
            enabled = !busy,
            modifier = Modifier
                .padding(top = Dimens.SpaceXs)
                .heightIn(min = Dimens.MinTouch)
        ) { Text("あとで設定する（測定のみ行う）") }
    }
}

/** 新規登録と既存ログインの切り替え。並びは画面をまたいで固定する。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountModeSelector(signInExisting: Boolean, onSelect: (Boolean) -> Unit) {
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !signInExisting,
                onClick = { onSelect(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("はじめて登録") }
            SegmentedButton(
                selected = signInExisting,
                onClick = { onSelect(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("登録済み") }
        }
        Text(
            if (signInExisting) {
                "他の端末で登録した名前とパスワードを入力してください。"
            } else {
                "まだ登録していない名前を入力してください。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXs)
        )
    }
}

/** 設定画面の使用者セクション。今誰として操作しているのかを常に見えるようにする。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserAccountSection(vm: AppViewModel, modifier: Modifier = Modifier) {
    val user by vm.currentUser.collectAsState()
    var showSignIn by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_DURATION_MILLIS)
            notice = null
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "使用者",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "設備の追加・変更はここで登録した人だけが行えます。誰が変更したかはドライブに残ります。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXs)
        )

        val current = user
        if (current == null) {
            AccountRequiredCard(onSetUp = { showSignIn = true })
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(Dimens.SpaceSm))
                Text(current.name, style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceSm)
            ) {
                Button(
                    onClick = { showEdit = true },
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) { Text("名前・パスワードを変更") }
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.heightIn(min = Dimens.MinTouch)
                ) { Text("この端末から外す") }
            }
        }

        notice?.let { FormSuccess(it) }
    }

    if (showSignIn) {
        SignInDialog(
            vm = vm,
            onDismiss = { showSignIn = false },
            onSuccess = { message -> showSignIn = false; notice = message }
        )
    }
    if (showEdit) {
        AccountEditDialog(
            vm = vm,
            currentName = user?.name.orEmpty(),
            onDismiss = { showEdit = false },
            onSuccess = { message -> showEdit = false; notice = message }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("この端末から使用者を外しますか？") },
            text = {
                Text(
                    "設備の追加・変更ができなくなります。測定は続けられます。" +
                        "同じ名前とパスワードで、いつでも設定し直せます。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    vm.signOut { report -> notice = report.message }
                }) { Text("外す") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("やめる") }
            }
        )
    }
}

/**
 * 使用者が未設定であることを知らせるカード。
 * 測定は続けられるので、失敗ではなく警告として扱う。
 */
@Composable
private fun AccountRequiredCard(onSetUp: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpaceSm)
    ) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Text("使用者が未設定です", style = MaterialTheme.typography.titleSmall)
            Text(
                "測定はできますが、設備の追加・変更はできません。名前とパスワードを設定してください。",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onSetUp,
                modifier = Modifier
                    .padding(top = Dimens.SpaceSm)
                    .heightIn(min = Dimens.MinTouch)
            ) { Text("使用者を設定する") }
        }
    }
}

/** 設備を変更しようとしたのに使用者が未設定だったときに、その場で設定できるようにする。 */
@Composable
fun SignInDialog(vm: AppViewModel, onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
    var signInExisting by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var registrationKey by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by vm.accountBusy.collectAsState()
    val localMode by vm.localMode.collectAsState()

    fun submit() {
        if (busy) return
        val validation = PasswordPolicy.nameError(name)
            ?: PasswordPolicy.passwordError(password)
            ?: if (signInExisting && !localMode) null else PasswordPolicy.confirmError(password, confirm)
        if (validation != null) {
            error = validation
            return
        }
        error = null
        val onDone: (UserActionReport) -> Unit = { report ->
            if (report.success) onSuccess(report.message) else error = report.message
        }
        when {
            localMode -> vm.registerLocalUser(name, password, onDone)
            signInExisting -> vm.signIn(name, password, onDone)
            else -> vm.registerUser(name, password, registrationKey, onDone)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (localMode) "この端末の使用者" else "使用者の設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (localMode) {
                        "設備を追加・変更するには、この端末専用の使用者を設定してください。" +
                            "ドライブには登録されません。"
                    } else {
                        "設備を追加・変更するには使用者の登録が必要です。" +
                            "一度設定すれば、次回から入力は不要です。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 確定ボタンはスクロール領域の外にあるため、結果は上に出す
                error?.let { FormError(it) }
                Spacer(Modifier.height(Dimens.SpaceMd))
                if (!localMode) {
                AccountModeSelector(
                    signInExisting = signInExisting,
                    onSelect = { signInExisting = it; error = null }
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名前") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "パスワード",
                    supportingText = "${PasswordPolicy.MIN_LENGTH} 文字以上",
                    enabled = !busy,
                    imeAction = if (signInExisting && !localMode) ImeAction.Done else ImeAction.Next,
                    onImeAction = { if (signInExisting && !localMode) submit() }
                )
                if (localMode || !signInExisting) {
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    PasswordField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        label = "パスワード（確認）",
                        enabled = !busy,
                        imeAction = if (localMode) ImeAction.Done else ImeAction.Next,
                        onImeAction = { if (localMode) submit() }
                    )
                }
                if (!signInExisting && !localMode) {
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    OutlinedTextField(
                        value = registrationKey,
                        onValueChange = { registrationKey = it; error = null },
                        label = { Text("登録キー（任意）") },
                        supportingText = { Text("管理者から渡されている場合だけ入力してください") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { DialogActionButton(busy, "確認中", "設定する", ::submit) },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("やめる") }
        }
    )
}

@Composable
private fun AccountEditDialog(
    vm: AppViewModel,
    currentName: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf(currentName) }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by vm.accountBusy.collectAsState()

    fun submit() {
        if (busy) return
        val trimmedName = newName.trim()
        val nameChanged = trimmedName != currentName
        val passwordChanged = newPassword.isNotEmpty()
        val validation = PasswordPolicy.passwordError(currentPassword)
            ?: (if (nameChanged) PasswordPolicy.nameError(trimmedName) else null)
            ?: (if (passwordChanged) {
                PasswordPolicy.passwordError(newPassword)
                    ?: PasswordPolicy.confirmError(newPassword, confirm)
            } else null)
            ?: (if (!nameChanged && !passwordChanged) "変更する内容がありません" else null)
        if (validation != null) {
            error = validation
            return
        }
        error = null
        vm.updateUser(
            currentPassword = currentPassword,
            newName = if (nameChanged) trimmedName else null,
            newPassword = newPassword.ifEmpty { null }
        ) { report ->
            if (report.success) onSuccess(report.message) else error = report.message
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("名前・パスワードの変更") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                error?.let { FormError(it) }
                PasswordField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error = null },
                    label = "現在のパスワード",
                    supportingText = "本人確認のため必要です",
                    enabled = !busy,
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                HorizontalDivider()
                Spacer(Modifier.height(Dimens.SpaceMd))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; error = null },
                    label = { Text("名前") },
                    supportingText = {
                        Text("変えると、他の端末では名前とパスワードの設定し直しが必要になります")
                    },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = "新しいパスワード",
                    supportingText = "変えない場合は空のままにしてください",
                    enabled = !busy,
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = "新しいパスワード（確認）",
                    enabled = !busy,
                    imeAction = ImeAction.Done,
                    onImeAction = ::submit
                )
            }
        },
        confirmButton = { DialogActionButton(busy, "変更中", "変更する", ::submit) },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("やめる") }
        }
    )
}

/** 設備マスターの世代。誰がいつ何を変えたかを遡れるようにする。 */
@Composable
fun EquipmentHistorySection(vm: AppViewModel, modifier: Modifier = Modifier) {
    val history by vm.historyState.collectAsState()

    Column(modifier.fillMaxWidth()) {
        Text(
            "設備マスターの更新履歴",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "設備の設定を誰がいつ変更したかの記録です。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXs)
        )
        val loadedOnce = history.loaded && history.error == null
        OutlinedButton(
            onClick = { vm.loadEquipmentHistory() },
            enabled = !history.loading,
            modifier = Modifier
                .padding(top = Dimens.SpaceSm)
                .heightIn(min = Dimens.MinTouch)
        ) {
            if (history.loading) {
                InlineSpinner(MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimens.SpaceSm))
                Text("読み込み中")
            } else {
                Text(
                    when {
                        history.error != null -> "もう一度読み込む"
                        loadedOnce -> "履歴を更新"
                        else -> "履歴を読み込む"
                    }
                )
            }
        }
        history.error?.let { FormError(it) }
        if (loadedOnce && history.revisions.isEmpty()) {
            Text(
                "まだ更新履歴がありません",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            )
        }
        history.revisions.forEach { revision ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceSm)
            ) {
                Column(Modifier.padding(Dimens.CardPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${revision.revision} 版", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            HISTORY_TIME_FORMAT.format(revision.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${revision.updatedBy}（設備 ${revision.equipmentCount} 件）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        revision.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun DialogActionButton(
    busy: Boolean,
    busyLabel: String,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        enabled = !busy,
        onClick = onClick,
        modifier = Modifier.heightIn(min = Dimens.MinTouch)
    ) {
        if (busy) {
            InlineSpinner(MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Dimens.SpaceSm))
            Text(busyLabel)
        } else {
            Text(label)
        }
    }
}

@Composable
private fun InlineSpinner(color: androidx.compose.ui.graphics.Color) {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = color
    )
}

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier.semantics {
                    role = Role.Switch
                    stateDescription = if (visible) "表示中" else "非表示"
                }
            ) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "パスワードを隠す" else "パスワードを表示する"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun FormError(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(top = Dimens.SpaceSm)
            .semantics { liveRegion = LiveRegionMode.Assertive }
    )
}

@Composable
internal fun FormSuccess(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = Dimens.SpaceSm)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}
