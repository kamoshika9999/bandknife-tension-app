package com.bandknife.tension.viewmodel

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bandknife.tension.audio.AudioAnalyzer
import com.bandknife.tension.audio.NoiseCalibrationSnapshot
import com.bandknife.tension.data.AppMode
import com.bandknife.tension.data.EquipmentEntity
import com.bandknife.tension.data.MeasurementRecordEntity
import com.bandknife.tension.data.Repository
import com.bandknife.tension.data.UserActionReport
import com.bandknife.tension.data.UserCredential
import com.bandknife.tension.domain.GateStatus
import com.bandknife.tension.domain.MeasurementGate
import com.bandknife.tension.domain.MeasurementStats
import com.bandknife.tension.domain.RecordsCsvExporter
import com.bandknife.tension.domain.StatisticsEngine
import com.bandknife.tension.domain.TapQuality
import com.bandknife.tension.domain.TensionCalculator
import com.bandknife.tension.util.WaveformArchive
import com.bandknife.tension.upload.HistoryOutcome
import com.bandknife.tension.upload.EquipmentRevision
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import org.json.JSONArray
import java.util.Locale

enum class MeasureMode { SINGLE, CONTINUOUS }
enum class SimpleStep { SELECT, MEASURE, RESULT }

/** 規格に対して張力をどちら向きに調整すべきか。 */
enum class AdjustDirection { UP, DOWN, NONE }

data class AdjustmentHint(
    val direction: AdjustDirection,
    val headline: String,
    val action: String
)

data class ContinuousState(
    val targetCount: Int = 5,
    val currentCount: Int = 0,
    val values: List<Double> = emptyList(),
    val frequencies: List<Double> = emptyList(),
    val stats: MeasurementStats? = null,
    val lastTapMessage: String = "",
    val lastTapQuality: TapQuality? = null,
    /** 品質不良で棄却した打撃の累積数。同じ失敗が続くときの救済表示に使う。 */
    val rejectedCounts: Map<TapQuality, Int> = emptyMap(),
    val savedRecordId: Long? = null
) {
    /** 直近に最も多く発生した棄却理由と、その連続回数。 */
    val dominantRejection: Pair<TapQuality, Int>?
        get() = rejectedCounts.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.toPair()
}

/** 同期操作の結果。失敗は色でも区別できるようにする。 */
data class SyncMessage(val text: String, val isError: Boolean)

/** 設備マスターの更新履歴の読み込み状態。 */
data class HistoryUiState(
    val revisions: List<EquipmentRevision> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false
)

/** 設備マスターの鮮度と、それに基づく測定可否。 */
data class SyncUiState(
    val gate: MeasurementGate,
    val syncing: Boolean = false,
    val pushing: Boolean = false,
    val lastSyncText: String = "未確認",
    val revision: Int = 0
) {
    val status: GateStatus get() = gate.status
    val canMeasure: Boolean get() = gate.canMeasure
    val busy: Boolean get() = syncing || pushing
}

/** ドライブへの登録で設備が消えるときに、実行前に見せる内容。 */
data class RemovalConfirmation(val names: List<String>)

data class MeasureUiState(
    val frequencyHz: Double = 0.0,
    val tensionN: Double = 0.0,
    val passed: Boolean = false,
    val micName: String = "",
    val spectrum: FloatArray = FloatArray(0),
    val tapMessage: String = "",
    val tapQuality: TapQuality? = null,
    val amplitude: Double = 0.0,
    val isMeasuring: Boolean = false,
    val isArming: Boolean = false,
    val armingSecondsLeft: Int = 0,
    val noiseWarning: String? = null,
    val signalQuality: Double = 0.0,
    val stackedTapCount: Int = 0
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repository = (app as com.bandknife.tension.TensionApp).repository
    private val audio = AudioAnalyzer(app)
    private val vibrator = app.getSystemService(VibratorManager::class.java)?.defaultVibrator

    val equipment = repository.equipment.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val deletedEquipment = repository.deletedEquipment.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records = repository.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val appMode = repository.preferences.appMode.stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.SIMPLE)
    val useKgf = repository.preferences.useKgf.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val continuousCount = repository.preferences.continuousCount.stateIn(viewModelScope, SharingStarted.Eagerly, 5)
    val localMode = repository.preferences.localMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _selectedEquipment = MutableStateFlow<EquipmentEntity?>(null)
    val selectedEquipment: StateFlow<EquipmentEntity?> = _selectedEquipment.asStateFlow()

    private val _measureState = MutableStateFlow(MeasureUiState())
    val measureState: StateFlow<MeasureUiState> = _measureState.asStateFlow()

    private val _measureMode = MutableStateFlow(MeasureMode.CONTINUOUS)
    val measureMode: StateFlow<MeasureMode> = _measureMode.asStateFlow()

    private val _continuous = MutableStateFlow(ContinuousState())
    val continuous: StateFlow<ContinuousState> = _continuous.asStateFlow()

    private val _simpleStep = MutableStateFlow(SimpleStep.SELECT)
    val simpleStep: StateFlow<SimpleStep> = _simpleStep.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _pendingUploads = MutableStateFlow(0)
    val pendingUploads: StateFlow<Int> = _pendingUploads.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    private val _pushing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<SyncMessage?>(null)
    val syncMessage: StateFlow<SyncMessage?> = _syncMessage.asStateFlow()

    private val _removalConfirmation = MutableStateFlow<RemovalConfirmation?>(null)
    val removalConfirmation: StateFlow<RemovalConfirmation?> = _removalConfirmation.asStateFlow()

    /** 期限切れの瞬間に画面へ反映させるための時計。 */
    private val _now = MutableStateFlow(System.currentTimeMillis())

    val syncState: StateFlow<SyncUiState> = combine(
        repository.preferences.equipmentSyncedAt,
        repository.preferences.equipmentRevision,
        _syncing,
        _pushing,
        _now
    ) { values ->
        val syncedAt = values[0] as Long
        val revision = values[1] as Int
        val now = values[4] as Long
        SyncUiState(
            gate = MeasurementGate(lastSyncAt = syncedAt, now = now),
            syncing = values[2] as Boolean,
            pushing = values[3] as Boolean,
            lastSyncText = if (syncedAt <= 0L) "未確認" else SYNC_TIME_FORMAT.format(syncedAt),
            revision = revision
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SyncUiState(MeasurementGate(0L, System.currentTimeMillis()))
    )

    /** サインイン中の使用者。設備の追加・変更の可否を決める。 */
    val currentUser: StateFlow<UserCredential?> = repository.credential

    /**
     * 初回起動なら使用者の設定画面から始める。
     * 読み終えるまでは null。どちらの画面も出さず、切り替わって見えるのを防ぐ。
     */
    val firstRun: StateFlow<Boolean?> = repository.preferences.isFirstRun
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeFirstRun() {
        viewModelScope.launch { repository.preferences.setFirstRunDone() }
    }

    private val _accountBusy = MutableStateFlow(false)
    val accountBusy: StateFlow<Boolean> = _accountBusy.asStateFlow()

    private val _historyState = MutableStateFlow(HistoryUiState())
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    private var audioJob: Job? = null
    private var startJob: Job? = null
    private var syncJob: Job? = null
    private var pushJob: Job? = null
    private var accountJob: Job? = null
    private var historyJob: Job? = null
    private var lastHandledTapId = 0L
    private var waveformSessionDir: File? = null
    private var sessionNoiseWaveformPath: String? = null
    private val sessionTapWaveformPaths = mutableListOf<String>()

    init {
        viewModelScope.launch {
            repository.ensureDefaultEquipmentIfNeeded()
            if (!repository.isLocalMode()) {
                repository.processUploadQueue()
                refreshPendingUploads()
                syncEquipment(showResultOnSuccess = false)
            }
        }
        viewModelScope.launch {
            if (repository.isLocalMode()) return@launch
            // 資格情報の読み込みは裏で走るため、確定してから 1 度だけ確かめる
            repository.credential.filterNotNull().first()
            verifyStoredCredential()
        }
        viewModelScope.launch {
            repository.preferences.builtinMicSpecialMode.collect { enabled ->
                applyBuiltinMicSpecialMode(enabled)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(CLOCK_TICK_MILLIS)
                _now.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch {
            combine(audio.result, audio.isRecording) { result, recording ->
                result to recording
            }.collect { (result, recording) ->
                val eq = _selectedEquipment.value ?: return@collect
                val tension = repository.tensionFor(eq, result.frequencyHz)
                val passed = if (result.frequencyHz > 0) repository.evaluate(eq, result.frequencyHz, tension) else false
                _measureState.value = _measureState.value.copy(
                    frequencyHz = result.frequencyHz,
                    tensionN = tension,
                    passed = passed,
                    micName = result.micName,
                    spectrum = result.spectrum,
                    tapMessage = result.tapAnalysis?.message ?: "",
                    tapQuality = result.tapAnalysis?.quality,
                    amplitude = result.amplitude,
                    isMeasuring = recording,
                    signalQuality = result.signalQuality,
                    stackedTapCount = result.stackedTapCount
                )
                // 打撃が終わってから平均スペクトルで周波数を確定する
                if (recording && result.tapFinalizedId > 0 &&
                    result.tapFinalizedId != lastHandledTapId &&
                    result.finalizedTapAnalysis != null &&
                    result.finalizedTapFrequencyHz > 0
                ) {
                    lastHandledTapId = result.tapFinalizedId
                    saveTapWaveform(result.tapFinalizedId, sessionTapWaveformPaths.size + 1)
                    val tapTension = repository.tensionFor(eq, result.finalizedTapFrequencyHz)
                    handleTap(
                        result.finalizedTapFrequencyHz,
                        tapTension,
                        result.finalizedTapAnalysis.quality,
                        result.finalizedTapAnalysis.message,
                        result.tapFinalizedId
                    )
                }
            }
        }
    }

    fun selectEquipment(eq: EquipmentEntity) {
        _selectedEquipment.value = eq
        _saveMessage.value = null
    }

    fun setMeasureMode(mode: MeasureMode) {
        _measureMode.value = mode
        resetContinuous()
    }

    fun setAppMode(mode: AppMode) {
        viewModelScope.launch { repository.preferences.setAppMode(mode) }
    }

    fun setSimpleStep(step: SimpleStep) {
        _simpleStep.value = step
    }

    // ===== 設備マスターの同期 =====

    /** ドライブの設備マスターを取り込み、接続確認の期限を更新する。 */
    fun syncEquipmentNow() = syncEquipment(showResultOnSuccess = true)

    private fun syncEquipment(showResultOnSuccess: Boolean) {
        if (localMode.value) return
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _syncing.value = true
            val report = try {
                repository.pullEquipmentFromDrive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                _syncing.value = false
            }
            _now.value = System.currentTimeMillis()
            when {
                report == null ->
                    _syncMessage.value = SyncMessage("設備マスターを確認できませんでした", isError = true)
                !report.success -> _syncMessage.value = SyncMessage(report.message, isError = true)
                showResultOnSuccess ->
                    _syncMessage.value = SyncMessage(report.message, isError = false)
            }
        }
    }

    /**
     * 端末の設備一覧をドライブへ登録する。設備マスターを整える管理者向けの操作。
     * 設備が消える内容だった場合は実行せず、消える設備名を返して確認を求める。
     */
    fun registerEquipmentToDrive(confirmRemoval: Boolean = false) {
        if (localMode.value) {
            _syncMessage.value = SyncMessage("ローカルモード中はドライブへ登録できません", isError = true)
            return
        }
        if (pushJob?.isActive == true) return
        pushJob = viewModelScope.launch {
            _pushing.value = true
            _removalConfirmation.value = null
            val report = try {
                repository.pushEquipmentToDrive(confirmRemoval)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                _pushing.value = false
            }
            _now.value = System.currentTimeMillis()
            when {
                report == null ->
                    _syncMessage.value = SyncMessage("ドライブへ登録できませんでした", isError = true)
                report.needsRemovalConfirmation -> {
                    _removalConfirmation.value = RemovalConfirmation(report.removedNames)
                    _syncMessage.value = null
                }
                report.success ->
                    _syncMessage.value = SyncMessage("ドライブに登録しました。${report.message}", isError = false)
                else -> _syncMessage.value = SyncMessage(report.message, isError = true)
            }
        }
    }

    fun dismissRemovalConfirmation() {
        _removalConfirmation.value = null
    }

    /** 設備マスターの更新履歴を読み込む。誰がいつ何を変えたかの確認に使う。 */
    fun loadEquipmentHistory() {
        if (localMode.value) {
            _historyState.value = HistoryUiState(
                loading = false,
                error = "ローカルモード中は更新履歴を取得できません",
                loaded = true
            )
            return
        }
        if (historyJob?.isActive == true) return
        historyJob = viewModelScope.launch {
            _historyState.value = _historyState.value.copy(loading = true, error = null)
            val outcome = try {
                repository.pullEquipmentHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HistoryOutcome.Failure("更新履歴を取得できませんでした")
            }
            _historyState.value = when (outcome) {
                is HistoryOutcome.Failure ->
                    HistoryUiState(loading = false, error = outcome.message, loaded = true)
                is HistoryOutcome.Success ->
                    HistoryUiState(revisions = outcome.revisions, loading = false, loaded = true)
            }
        }
    }

    // ===== 使用者 =====

    fun registerUser(
        name: String,
        password: String,
        registrationKey: String,
        onDone: (UserActionReport) -> Unit
    ) = runAccountAction(onDone) { repository.registerUser(name, password, registrationKey) }

    fun signIn(name: String, password: String, onDone: (UserActionReport) -> Unit) =
        runAccountAction(onDone) { repository.signIn(name, password) }

    fun updateUser(
        currentPassword: String,
        newName: String?,
        newPassword: String?,
        onDone: (UserActionReport) -> Unit
    ) = runAccountAction(onDone) { repository.updateUser(currentPassword, newName, newPassword) }

    fun signOut(onDone: (UserActionReport) -> Unit) =
        runAccountAction(onDone) {
            repository.signOut()
            UserActionReport(true, "使用者を外しました")
        }

    /**
     * 保存済みの資格情報がドライブ側と食い違っていないか確かめる。
     * 別の端末で名前やパスワードを変えた場合、この端末は変更前のまま残ってしまう。
     */
    private fun verifyStoredCredential() {
        viewModelScope.launch {
            val report = try {
                repository.verifyStoredCredential()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // 通信できなかっただけの失敗で締め出さない。食い違いが確定したときだけ知らせる。
            if (report != null && report.staleCredential) {
                _syncMessage.value = SyncMessage(report.message, isError = true)
            }
        }
    }

    private fun runAccountAction(
        onDone: (UserActionReport) -> Unit,
        action: suspend () -> UserActionReport
    ) {
        if (accountJob?.isActive == true) return
        accountJob = viewModelScope.launch {
            _accountBusy.value = true
            val report = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UserActionReport(false, "処理できませんでした（${e.message ?: "原因不明"}）")
            } finally {
                _accountBusy.value = false
            }
            onDone(report)
        }
    }

    /** 設備を追加・変更できない理由。できるときは null。 */
    fun equipmentEditBlockReason(): String? {
        if (currentUser.value == null) {
            return if (localMode.value) {
                "設備の変更にはこの端末の使用者を設定してください。「その他」のローカルモード欄から設定できます"
            } else {
                "設備の変更には使用者の登録が必要です。「その他」の使用者欄で名前とパスワードを設定してください"
            }
        }
        return null
    }

    /** 期限が近い、または切れている端末では画面を開くたびに確認をやり直す。 */
    fun ensureFreshSync() {
        if (localMode.value) return
        if (syncState.value.status == GateStatus.VALID) return
        syncEquipment(showResultOnSuccess = false)
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    /** 測定を始められない理由。始められるときは null。 */
    fun measureBlockReason(equipment: EquipmentEntity?): String? {
        if (!localMode.value) {
            val gate = syncState.value.gate
            when {
                !gate.canMeasure && gate.neverSynced ->
                    return "ドライブの設備マスターをまだ確認できていないため測定できません。「今すぐ確認」を押してください"
                !gate.canMeasure ->
                    return "ドライブとの接続確認が ${gate.elapsedDays} 日途切れているため測定できません。" +
                        "電波の届く場所で「今すぐ確認」を押すか、ローカルモードに切り替えてください"
            }
        }
        return when {
            !audio.hasUsableMicrophone() ->
                "USBマイクが接続されていません。USB-C マイクを接続するか、" +
                    "設定の特別モードで内蔵マイクを有効にしてください"
            equipment == null -> "先に設備を選択してください"
            !localMode.value && !equipment.isSynced ->
                "「${equipment.name}」はドライブの設備マスターに登録されていないため測定できません。" +
                    "管理者に登録を依頼してください"
            !equipment.hasUsableSpec ->
                "「${equipment.name}」の規格値が設定されていないため合否を判定できません。" +
                    "管理者に規格の設定を依頼してください"
            else -> null
        }
    }

    fun registerLocalUser(name: String, password: String, onDone: (UserActionReport) -> Unit) =
        runAccountAction(onDone) { repository.registerLocalUser(name, password) }

    /**
     * ローカルモードの ON/OFF。
     * 有効化には保存済みパスワードの確認が必要。使用者が未設定のときは先に端末専用使用者を作る。
     */
    fun setLocalMode(enabled: Boolean, password: String, onDone: (UserActionReport) -> Unit) {
        if (accountJob?.isActive == true) return
        accountJob = viewModelScope.launch {
            _accountBusy.value = true
            val report = try {
                val user = currentUser.value
                if (user == null) {
                    UserActionReport(false, "先に使用者を設定してください")
                } else if (!repository.verifyPassword(password)) {
                    UserActionReport(false, "パスワードが違います")
                } else {
                    repository.setLocalMode(enabled)
                    if (enabled) {
                        UserActionReport(
                            true,
                            "ローカルモードに切り替えました。測定記録はこの端末だけに保存され、ドライブへは送りません"
                        )
                    } else {
                        if (user.localOnly) {
                            repository.signOut()
                        }
                        syncEquipment(showResultOnSuccess = false)
                        refreshPendingUploads()
                        UserActionReport(
                            true,
                            if (user.localOnly) {
                                "ドライブ連携モードに戻しました。端末専用の使用者は解除されました。" +
                                    "設備の変更にはクラウド使用者の登録が必要です。" +
                                    "ローカルモード中の記録は引き続き端末のみに残ります"
                            } else {
                                "ドライブ連携モードに戻しました。ローカルモード中の記録は引き続き端末のみに残ります"
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UserActionReport(false, "切り替えできませんでした（${e.message ?: "原因不明"}）")
            } finally {
                _accountBusy.value = false
            }
            onDone(report)
        }
    }

    private val _micPermissionGranted = MutableStateFlow(true)
    val micPermissionGranted: StateFlow<Boolean> = _micPermissionGranted.asStateFlow()

    fun setMicPermissionGranted(granted: Boolean) {
        _micPermissionGranted.value = granted
    }

    suspend fun buildRecordsCsv(): String = RecordsCsvExporter.export(records.value)

    fun startMeasuring() {
        if (!_micPermissionGranted.value) {
            _saveMessage.value = "マイクの使用が許可されていません。画面上部の案内から設定を開き、許可してください"
            return
        }
        measureBlockReason(_selectedEquipment.value)?.let {
            _saveMessage.value = it
            return
        }
        _saveMessage.value = null
        startJob?.cancel()
        startJob = viewModelScope.launch {
            lastHandledTapId = 0
            // カウンタは較正前に空にしておく。準備中の打撃で一度上がってから
            // 0 に戻ると、作業者はアプリが記録を取りこぼしたと受け取ってしまう。
            _continuous.value = ContinuousState(targetCount = repository.preferences.continuousCount.first())
            val sensitivity = repository.preferences.sensitivity.first()
            val micDeviceId = repository.preferences.micDeviceId.first()
            val resolvedMicId = audio.resolveInputDeviceId(micDeviceId.takeIf { it >= 0 })
            if (resolvedMicId == null) {
                _saveMessage.value = "使用できるマイクがありません。USBマイクを接続するか、特別モードを有効にしてください"
                return@launch
            }
            audio.setSensitivity(sensitivity)
            audio.setPreferredDevice(resolvedMicId)
            audio.resetTapAccumulation()
            waveformSessionDir = WaveformArchive.createSessionDir(getApplication())
            sessionNoiseWaveformPath = null
            sessionTapWaveformPaths.clear()
            audio.beginNoiseCalibration()
            if (!audio.start()) {
                _saveMessage.value = "USBマイクを開始できませんでした。接続を確認してください"
                return@launch
            }

            for (sec in ARMING_SECONDS downTo 1) {
                _measureState.value = _measureState.value.copy(isArming = true, armingSecondsLeft = sec)
                delay(1000)
            }

            val noiseSnapshot = audio.finalizeNoiseCalibration()
            saveNoiseWaveforms(noiseSnapshot)
            val noiseDb = noiseSnapshot.levelDb
            val warning = if (noiseDb > NOISY_ENVIRONMENT_DB) {
                "周囲が騒がしいため測定精度が落ちる可能性があります"
            } else null

            _measureState.value = _measureState.value.copy(
                isArming = false,
                armingSecondsLeft = 0,
                frequencyHz = 0.0,
                tensionN = 0.0,
                passed = false,
                tapMessage = "",
                spectrum = FloatArray(0),
                amplitude = 0.0,
                noiseWarning = warning,
                signalQuality = 0.0,
                stackedTapCount = 0
            )
        }
    }

    /** 測定を打ち切り、設備選択まで戻す。 */
    fun cancelMeasuring() {
        stopMeasuring()
        resetContinuous()
        _saveMessage.value = null
        _simpleStep.value = SimpleStep.SELECT
    }

    /** 結果を保存せずに捨て、同じ設備でもう一度測り直す。 */
    fun discardAndRemeasure() {
        stopMeasuring()
        _saveMessage.value = null
        _simpleStep.value = SimpleStep.MEASURE
        startMeasuring()
    }

    /** 打撃が検出されないときの救済。感度を一段上げて測定をやり直す。 */
    fun raiseSensitivityAndRestart() {
        viewModelScope.launch {
            val current = repository.preferences.sensitivity.first()
            val raised = (current * 0.5).coerceAtLeast(MIN_SENSITIVITY)
            repository.preferences.setSensitivity(raised)
            discardAndRemeasure()
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    fun stopMeasuring() {
        startJob?.cancel()
        startJob = null
        _measureState.value = _measureState.value.copy(isArming = false, armingSecondsLeft = 0)
        audio.stop()
    }

    private fun handleTap(freq: Double, tension: Double, quality: TapQuality, message: String, tapId: Long) {
        // 較正中の打撃はノイズスペクトルを汚染するため、カウントも積分もしない。
        if (_measureState.value.isArming) return
        if (quality != TapQuality.GOOD) {
            val current = _continuous.value
            _continuous.value = current.copy(
                lastTapMessage = message,
                lastTapQuality = quality,
                rejectedCounts = current.rejectedCounts +
                    (quality to (current.rejectedCounts[quality] ?: 0) + 1)
            )
            vibrateRejected()
            return
        }
        audio.markTapForStacking(tapId)
        when (_measureMode.value) {
            MeasureMode.SINGLE -> {
                _continuous.value = ContinuousState(
                    targetCount = 1,
                    currentCount = 1,
                    values = listOf(tension),
                    frequencies = listOf(freq),
                    stats = StatisticsEngine.analyze(listOf(tension), false),
                    lastTapQuality = TapQuality.GOOD
                )
                vibrateComplete()
            }
            MeasureMode.CONTINUOUS -> {
                val current = _continuous.value
                if (current.currentCount >= current.targetCount) return
                val newValues = current.values + tension
                val newFreqs = current.frequencies + freq
                val count = current.currentCount + 1
                val stats = if (count >= current.targetCount) StatisticsEngine.analyze(newValues) else null
                _continuous.value = current.copy(
                    currentCount = count,
                    values = newValues,
                    frequencies = newFreqs,
                    stats = stats,
                    lastTapQuality = TapQuality.GOOD,
                    // 成功したら棄却の累積をリセットし、直った救済表示を残さない
                    rejectedCounts = emptyMap(),
                    lastTapMessage = "適正な打撃 ($count/${current.targetCount})"
                )
                if (stats != null) vibrateComplete() else vibrateGoodTap()
            }
        }
    }

    fun resetContinuous() {
        viewModelScope.launch {
            val count = repository.preferences.continuousCount.first()
            _continuous.value = ContinuousState(targetCount = count)
        }
    }

    fun setContinuousTarget(count: Int) {
        viewModelScope.launch {
            repository.preferences.setContinuousCount(count)
            _continuous.value = _continuous.value.copy(targetCount = count)
        }
    }

    suspend fun saveCurrentRecord(comment: String = ""): Long? {
        val eq = _selectedEquipment.value ?: run {
            _saveMessage.value = "設備が選択されていません"
            return null
        }
        val cont = _continuous.value
        cont.savedRecordId?.let { return it }
        val stats = cont.stats ?: run {
            _saveMessage.value = "記録できる測定結果がありません"
            return null
        }
        val record = MeasurementRecordEntity(
            equipmentId = eq.id,
            equipmentName = eq.name,
            frequencyHz = cont.frequencies.average(),
            tensionN = stats.mean,
            passed = repository.evaluate(eq, cont.frequencies.average(), stats.mean),
            sampleCount = stats.values.size,
            stdDev = stats.stdDev,
            ci95Lower = stats.ci95Lower,
            ci95Upper = stats.ci95Upper,
            comment = comment,
            rawValuesJson = JSONArray(cont.values).toString(),
            noiseWaveformPath = sessionNoiseWaveformPath.orEmpty(),
            tapWaveformsJson = JSONArray(sessionTapWaveformPaths).toString()
        )
        val id = repository.saveRecord(record)
        _continuous.value = cont.copy(savedRecordId = id)
        _saveMessage.value = if (localMode.value) {
            "履歴に記録しました（この端末のみ）"
        } else {
            "履歴に記録しました"
        }
        return id
    }

    /** 張力の表示単位。設定の kgf 表示に追従する。 */
    fun tensionUnit(): String = if (useKgf.value) "kgf" else "N"

    private fun tensionNumber(n: Double): String =
        if (useKgf.value) String.format(Locale.JAPAN, "%.1f", TensionCalculator.newtonsToKgf(n))
        else String.format(Locale.JAPAN, "%.0f", n)

    fun formatTension(n: Double): String = "${tensionNumber(n)} ${tensionUnit()}"

    /** 表示単位に変換した数値。グラフの軸など、単位を別に添える場所で使う。 */
    fun toDisplayTension(n: Double): Double =
        if (useKgf.value) TensionCalculator.newtonsToKgf(n) else n

    /** 個別値の一覧。単位は末尾に 1 度だけ付ける。 */
    fun formatTensionValues(values: List<Double>): String =
        values.joinToString("、") { tensionNumber(it) } + " ${tensionUnit()}"

    /** 「150〜180 N」のように範囲を表示する。単位は 1 度だけ付ける。 */
    fun formatTensionRange(lower: Double, upper: Double): String =
        "${tensionNumber(lower)}〜${tensionNumber(upper)} ${tensionUnit()}"

    /** 「+12 N」のように符号付きで差分を表示する。 */
    fun formatTensionDelta(delta: Double): String {
        val sign = if (delta >= 0) "+" else "-"
        return "$sign${tensionNumber(kotlin.math.abs(delta))} ${tensionUnit()}"
    }

    private fun formatHz(hz: Double): String = String.format(Locale.JAPAN, "%.1f Hz", hz)

    /**
     * 合否判定に使っている量を主表示にする。
     * Hz 管理の設備で N を大きく出すと、判定の根拠と表示が食い違って読み解けなくなる。
     */
    fun formatPrimaryValue(equipment: EquipmentEntity?, frequencyHz: Double, tensionN: Double): String =
        if (equipment?.useHzMode == true) formatHz(frequencyHz) else formatTension(tensionN)

    /** 主表示の裏付けとして併記する参考値。Hz 管理なら張力、張力管理なら周波数。 */
    fun formatSecondaryValue(equipment: EquipmentEntity?, frequencyHz: Double, tensionN: Double): String =
        if (equipment?.useHzMode == true) "参考 ${formatTension(tensionN)}"
        else "周波数 ${formatHz(frequencyHz)}"

    /** 「規格 150〜180 N」の帯。 */
    fun formatSpecRange(equipment: EquipmentEntity): String =
        if (equipment.useHzMode) {
            String.format(Locale.JAPAN, "%.1f〜%.1f Hz", equipment.specHzLower, equipment.specHzUpper)
        } else {
            formatTensionRange(equipment.specLower, equipment.specUpper)
        }

    fun formatStandard(equipment: EquipmentEntity): String =
        if (equipment.useHzMode) formatHz(equipment.standardHz) else formatTension(equipment.standardTension)

    /**
     * 規格に対して張力を上げるべきか下げるべきかと、不足量を返す。
     * 「要調整」とだけ出しても現場では次の一手が決まらない。
     */
    fun adjustmentHint(equipment: EquipmentEntity, frequencyHz: Double, tensionN: Double): AdjustmentHint {
        val hz = equipment.useHzMode
        val value = if (hz) frequencyHz else tensionN
        val lower = if (hz) equipment.specHzLower else equipment.specLower
        val upper = if (hz) equipment.specHzUpper else equipment.specUpper
        fun amount(diff: Double) =
            if (hz) String.format(Locale.JAPAN, "%.1f Hz", diff) else "${tensionNumber(diff)} ${tensionUnit()}"

        return when {
            value < lower -> AdjustmentHint(
                direction = AdjustDirection.UP,
                headline = "規格下限まで ${amount(lower - value)} 不足",
                action = "張力を上げてください"
            )
            value > upper -> AdjustmentHint(
                direction = AdjustDirection.DOWN,
                headline = "規格上限を ${amount(value - upper)} 超過",
                action = "張力を下げてください"
            )
            else -> AdjustmentHint(
                direction = AdjustDirection.NONE,
                headline = "規格 ${formatSpecRange(equipment)} の範囲内です",
                action = "このまま使用できます"
            )
        }
    }

    fun updateRecordComment(record: MeasurementRecordEntity, comment: String) {
        if (record.isLocked) return
        viewModelScope.launch {
            repository.updateRecord(record.copy(comment = comment))
        }
    }

    fun toggleRecordLock(record: MeasurementRecordEntity) {
        viewModelScope.launch {
            repository.updateRecord(record.copy(isLocked = !record.isLocked))
        }
    }

    fun deleteRecord(record: MeasurementRecordEntity) {
        viewModelScope.launch { repository.deleteRecord(record) }
    }

    fun deleteEquipment(entity: EquipmentEntity) {
        viewModelScope.launch {
            repository.deleteEquipment(entity)
            if (_selectedEquipment.value?.id == entity.id) {
                _selectedEquipment.value = null
            }
        }
    }

    fun restoreEquipment(entity: EquipmentEntity) {
        viewModelScope.launch { repository.restoreEquipment(entity) }
    }

    fun permanentlyDeleteEquipment(entity: EquipmentEntity) {
        viewModelScope.launch {
            repository.permanentlyDeleteEquipment(entity)
            if (_selectedEquipment.value?.id == entity.id) {
                _selectedEquipment.value = null
            }
        }
    }

    fun deleteRecordsInRange(from: Long, to: Long) {
        viewModelScope.launch { repository.deleteRecordsInRange(from, to) }
    }

    // 測定中は端末を刃に添えていて画面を見られないため、
    // 成功・失敗・完了を触覚パターンで区別できるようにしている。
    private fun vibrateGoodTap() = vibrate(longArrayOf(0, 80))

    private fun vibrateRejected() = vibrate(longArrayOf(0, 40, 80, 40))

    private fun vibrateComplete() = vibrate(longArrayOf(0, 120, 80, 300))

    private fun vibrate(pattern: LongArray) {
        viewModelScope.launch {
            if (!repository.preferences.vibrationEnabled.first()) return@launch
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    fun saveRecord() {
        viewModelScope.launch { saveCurrentRecord() }
    }

    fun saveAndResetToSelect() {
        viewModelScope.launch {
            saveCurrentRecord()
            refreshPendingUploads()
            resetContinuous()
            setSimpleStep(SimpleStep.SELECT)
        }
    }

    fun refreshPendingUploads() {
        viewModelScope.launch {
            _pendingUploads.value = runCatching { repository.pendingUploadCount() }.getOrDefault(0)
        }
    }

    /** 同じ設備の直近の記録。前回からの変化を結果画面と設備一覧で示すのに使う。 */
    fun lastRecordFor(equipmentId: Long): MeasurementRecordEntity? =
        records.value.filter { it.equipmentId == equipmentId }.maxByOrNull { it.timestamp }

    /** マニュアル用スクリーンショット生成向け。測定中に規定回数分の打撃をシミュレートする。 */
    fun injectManualScreenshotTaps() {
        val eq = _selectedEquipment.value ?: return
        val targetCount = _continuous.value.targetCount.coerceAtLeast(1)
        // 標準張力になる周波数を逆算し、±1.5% 程度の揺らぎを付けて規格内に収める
        val baseFreq = TensionCalculator.frequencyFromEquipment(eq, eq.standardTension)
        val jitters = listOf(-0.012, -0.005, 0.0, 0.006, 0.011, -0.008, 0.003, 0.014, -0.003, 0.009)
        repeat(targetCount) { index ->
            val freq = baseFreq * (1.0 + jitters[index % jitters.size])
            val tension = repository.tensionFor(eq, freq)
            handleTap(freq, tension, TapQuality.GOOD, "適正な打撃", System.nanoTime() + index)
        }
    }

    fun getInputDevices() = audio.listInputDevices()

    fun hasUsableMicrophone() = audio.hasUsableMicrophone()

    fun applyBuiltinMicSpecialMode(enabled: Boolean) {
        audio.setAllowBuiltinMic(enabled)
    }

    fun setBuiltinMicSpecialMode(enabled: Boolean) {
        applyBuiltinMicSpecialMode(enabled)
        viewModelScope.launch {
            repository.preferences.setBuiltinMicSpecialMode(enabled)
        }
    }

    private fun saveNoiseWaveforms(snapshot: NoiseCalibrationSnapshot) {
        val dir = waveformSessionDir ?: return
        val app = getApplication<Application>()
        snapshot.waveform?.takeIf { it.isNotEmpty() }?.let { samples ->
            val wav = File(dir, "noise.wav")
            WaveformArchive.writeWav(wav, samples, AudioAnalyzer.SAMPLE_RATE)
            sessionNoiseWaveformPath = WaveformArchive.relativePath(app, wav)
        }
        snapshot.spectrum?.let { spec ->
            val (minBin, _) = audio.analysisBins()
            WaveformArchive.writeSpectrumCsv(
                File(dir, "noise_spectrum.csv"),
                spec,
                minBin,
                AudioAnalyzer.SAMPLE_RATE,
                AudioAnalyzer.BUFFER_SIZE
            )
        }
    }

    private fun saveTapWaveform(tapId: Long, tapIndex: Int) {
        val dir = waveformSessionDir ?: return
        val app = getApplication<Application>()
        audio.snapshotTapWaveform(tapId)?.takeIf { it.isNotEmpty() }?.let { samples ->
            val wav = File(dir, "tap_%02d.wav".format(Locale.US, tapIndex))
            WaveformArchive.writeWav(wav, samples, AudioAnalyzer.SAMPLE_RATE)
            sessionTapWaveformPaths.add(WaveformArchive.relativePath(app, wav))
        }
        audio.snapshotTapSpectrum(tapId)?.let { spec ->
            val (minBin, _) = audio.analysisBins()
            WaveformArchive.writeSpectrumCsv(
                File(dir, "tap_%02d_spectrum.csv".format(Locale.US, tapIndex)),
                spec,
                minBin,
                AudioAnalyzer.SAMPLE_RATE,
                AudioAnalyzer.BUFFER_SIZE
            )
        }
    }

    override fun onCleared() {
        audio.stop()
        super.onCleared()
    }

    private companion object {
        const val ARMING_SECONDS = 5
        const val NOISY_ENVIRONMENT_DB = -20.0
        const val MIN_SENSITIVITY = 0.02

        /** 期限切れを画面に反映する間隔。分単位で足りる。 */
        const val CLOCK_TICK_MILLIS = 60_000L

        val SYNC_TIME_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.JAPAN)
    }
}
