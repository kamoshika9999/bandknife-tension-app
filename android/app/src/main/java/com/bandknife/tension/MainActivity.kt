package com.bandknife.tension

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bandknife.tension.data.AppMode
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.ui.screens.AdvanceFlowScreen
import com.bandknife.tension.ui.screens.DetailMeasureScreen
import com.bandknife.tension.ui.screens.EquipmentScreen
import com.bandknife.tension.ui.screens.HelpScreen
import com.bandknife.tension.ui.screens.HistoryScreen
import com.bandknife.tension.ui.screens.SettingsScreen
import com.bandknife.tension.ui.screens.SimpleFlowScreen
import com.bandknife.tension.ui.theme.BandKnifeTheme
import com.bandknife.tension.viewmodel.AppViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BandKnifeTheme {
                val vm: AppViewModel = viewModel()
                MainScreen(vm)
            }
        }
    }
}

@Composable
fun MainScreen(vm: AppViewModel) {
    val appMode by vm.appMode.collectAsState()
    var showModeDialog by remember { mutableStateOf(false) }
    var detailTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val equipment by vm.equipment.collectAsState()

    LaunchedEffect(Unit) {
        if (equipment.isEmpty()) {
            vm.repository.saveEquipment(
                EquipmentEntity(
                    name = "ペフ用スライサー1号",
                    massPerMeter = 0.314,
                    spanMeters = 0.85,
                    standardTension = 160.0,
                    specLower = 150.0,
                    specUpper = 180.0,
                    widthMm = 50.0,
                    thicknessMm = 0.8
                )
            )
        }
        vm.repository.processUploadQueue()
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("モード選択") },
            text = {
                androidx.compose.foundation.layout.Column {
                    TextButton(onClick = { scope.launch { vm.setAppMode(AppMode.SIMPLE); showModeDialog = false } }) { Text("シンプルモード") }
                    TextButton(onClick = { scope.launch { vm.setAppMode(AppMode.ADVANCE); showModeDialog = false } }) { Text("アドバンスモード") }
                    TextButton(onClick = { scope.launch { vm.setAppMode(AppMode.DETAIL); showModeDialog = false } }) { Text("詳細モード") }
                }
            },
            confirmButton = { TextButton(onClick = { showModeDialog = false }) { Text("閉じる") } }
        )
    }

    when (appMode) {
        AppMode.SIMPLE -> SimpleFlowScreen(vm) { showModeDialog = true }
        AppMode.ADVANCE -> AdvanceFlowScreen(vm) { showModeDialog = true }
        AppMode.DETAIL -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = detailTab == 0, onClick = { detailTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("測定") })
                    NavigationBarItem(selected = detailTab == 1, onClick = { detailTab = 1 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("設備") })
                    NavigationBarItem(selected = detailTab == 2, onClick = { detailTab = 2 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("履歴") })
                    NavigationBarItem(selected = detailTab == 3, onClick = { detailTab = 3 }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("その他") })
                }
            }
        ) { padding ->
            when (detailTab) {
                0 -> DetailMeasureScreen(vm)
                1 -> EquipmentScreen(vm)
                2 -> HistoryScreen(vm)
                3 -> androidx.compose.foundation.layout.Column(Modifier.padding(padding)) {
                    SettingsScreen(vm)
                    HelpScreen()
                    TextButton(onClick = { showModeDialog = true }) { Text("モード切替") }
                }
            }
        }
    }
}
