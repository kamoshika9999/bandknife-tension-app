package com.bandknife.tension

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bandknife.tension.data.AppMode
import com.bandknife.tension.ui.components.AppModeSelector
import com.bandknife.tension.ui.screens.AccountSetupScreen
import com.bandknife.tension.ui.screens.AdvanceFlowScreen
import com.bandknife.tension.ui.screens.DetailMeasureScreen
import com.bandknife.tension.ui.screens.EquipmentScreen
import com.bandknife.tension.ui.screens.HelpScreen
import com.bandknife.tension.ui.screens.HistoryScreen
import com.bandknife.tension.ui.screens.ManualButton
import com.bandknife.tension.ui.screens.SettingsScreen
import com.bandknife.tension.ui.screens.SimpleFlowScreen
import com.bandknife.tension.ui.theme.BandKnifeTheme
import com.bandknife.tension.ui.theme.Dimens
import com.bandknife.tension.viewmodel.AppViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var micPermissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micPermissionGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshMicPermission()
        if (!micPermissionGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BandKnifeTheme {
                val vm: AppViewModel = viewModel()
                val activity = LocalContext.current as MainActivity
                DisposableEffect(vm) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            // adb (settings put global) でのみ有効化できるマニュアル撮影用フック
                            val enabled = Settings.Global.getInt(
                                activity.contentResolver, MANUAL_MODE_SETTING, 0
                            ) == 1
                            if (enabled) vm.injectManualScreenshotTaps()
                        }
                    }
                    val filter = IntentFilter(MANUAL_TAP_BROADCAST)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        activity.registerReceiver(receiver, filter, RECEIVER_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        activity.registerReceiver(receiver, filter)
                    }
                    onDispose { activity.unregisterReceiver(receiver) }
                }
                MainScreen(
                    vm = vm,
                    micPermissionGranted = micPermissionGranted,
                    onOpenAppSettings = ::openAppSettings
                )
                LaunchedEffect(micPermissionGranted) {
                    vm.setMicPermissionGranted(micPermissionGranted)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMicPermission()
    }

    private fun refreshMicPermission() {
        micPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    companion object {
        const val MANUAL_TAP_BROADCAST = "com.bandknife.tension.INJECT_MANUAL_TAPS"
        const val MANUAL_MODE_SETTING = "bandknife_manual_mode"
    }
}

@Composable
fun MainScreen(
    vm: AppViewModel,
    micPermissionGranted: Boolean = true,
    onOpenAppSettings: () -> Unit = {}
) {
    val appMode by vm.appMode.collectAsState()
    var detailTab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val firstRun by vm.firstRun.collectAsState()
    val currentUser by vm.currentUser.collectAsState()

    // 初回かどうかが決まるまでは何も出さない。画面が入れ替わって見えるのを防ぐ。
    if (firstRun == null) return
    // 設備を変えた人を残すため、まず名前を決めてもらう。
    if (firstRun == true && currentUser == null) {
        AccountSetupScreen(vm, onSkip = { vm.completeFirstRun() })
        return
    }
    LaunchedEffect(currentUser) {
        if (currentUser != null && firstRun == true) vm.completeFirstRun()
    }

    Column {
        if (!micPermissionGranted) {
            MicPermissionBanner(onOpenAppSettings)
        }
        when (appMode) {
            AppMode.SIMPLE -> SimpleFlowScreen(vm)
            AppMode.ADVANCE -> AdvanceFlowScreen(vm)
            AppMode.DETAIL -> Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = detailTab == 0,
                            onClick = { detailTab = 0 },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("測定") }
                        )
                        NavigationBarItem(
                            selected = detailTab == 1,
                            onClick = { detailTab = 1 },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("設備") }
                        )
                        NavigationBarItem(
                            selected = detailTab == 2,
                            onClick = { detailTab = 2 },
                            icon = { Icon(Icons.Default.History, null) },
                            label = { Text("履歴") }
                        )
                        NavigationBarItem(
                            selected = detailTab == 3,
                            onClick = { detailTab = 3 },
                            icon = { Icon(Icons.Default.Info, null) },
                            label = { Text("その他") }
                        )
                    }
                }
            ) { padding ->
                when (detailTab) {
                    0 -> DetailMeasureScreen(vm, Modifier.padding(padding))
                    1 -> EquipmentScreen(vm, modifier = Modifier.padding(padding))
                    2 -> HistoryScreen(vm, modifier = Modifier.padding(padding))
                    // 設定とヘルプは 1 つのスクロール領域にまとめる。
                    // 子側で fillMaxSize すると後続がスクロール範囲外に押し出されて到達できなくなる。
                    3 -> Column(
                        Modifier
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AppModeSelector(
                            current = appMode,
                            onSelect = { mode -> scope.launch { vm.setAppMode(mode) } },
                            modifier = Modifier.padding(
                                horizontal = Dimens.SpaceLg,
                                vertical = Dimens.SpaceSm
                            )
                        )
                        ManualButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.SpaceLg)
                        )
                        SettingsScreen(vm)
                        HelpScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun MicPermissionBanner(onOpenAppSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(Dimens.SpaceLg)) {
            Icon(Icons.Default.MicOff, contentDescription = null)
            Text("マイクが使用できません", style = MaterialTheme.typography.titleMedium)
            Text(
                "マイクの使用が許可されていないため測定できません。" +
                    "「設定を開く」を押し、「権限」→「マイク」を許可してください。",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onOpenAppSettings,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            ) { Text("設定を開く") }
        }
    }
}
