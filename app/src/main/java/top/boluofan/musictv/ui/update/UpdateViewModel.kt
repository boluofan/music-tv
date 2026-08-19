package top.boluofan.musictv.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.model.UpdateInfo
import top.boluofan.musictv.data.repository.DownloadState
import top.boluofan.musictv.data.repository.UpdateCheckResult
import top.boluofan.musictv.data.repository.UpdateRepository
import top.boluofan.musictv.data.storage.PreferencesDataStore
import java.io.File
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class CheckFailed(val message: String) : UpdateUiState
    data class UpdateAvailable(val info: UpdateInfo, val fromAutoCheck: Boolean) : UpdateUiState
    data class Downloading(
        val info: UpdateInfo,
        val bytesRead: Long,
        val totalBytes: Long,
        val mirrorLabel: String
    ) : UpdateUiState

    data class DownloadFailed(val info: UpdateInfo, val message: String) : UpdateUiState
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val preferencesDataStore: PreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun autoCheckOnLaunch() {
        if (repository.autoCheckDone) return
        repository.autoCheckDone = true
        viewModelScope.launch {
            // 等首屏加载/焦点安置完成再静默检查
            delay(3000)
            val result = repository.checkUpdate()
            if (result is UpdateCheckResult.UpdateAvailable) {
                val ignored = preferencesDataStore.ignoredVersionCode.first()
                if (result.info.versionCode > ignored && _uiState.value == UpdateUiState.Idle) {
                    _uiState.value = UpdateUiState.UpdateAvailable(result.info, fromAutoCheck = true)
                }
            }
        }
    }

    fun manualCheck() {
        checkJob?.cancel()
        _uiState.value = UpdateUiState.Checking
        checkJob = viewModelScope.launch {
            _uiState.value = when (val result = repository.checkUpdate()) {
                is UpdateCheckResult.UpdateAvailable ->
                    UpdateUiState.UpdateAvailable(result.info, fromAutoCheck = false)
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Failed -> UpdateUiState.CheckFailed(result.message)
            }
        }
    }

    fun startDownload() {
        val info = when (val s = _uiState.value) {
            is UpdateUiState.UpdateAvailable -> s.info
            is UpdateUiState.DownloadFailed -> s.info
            else -> return
        }
        downloadJob?.cancel()
        _uiState.value = UpdateUiState.Downloading(info, 0L, -1L, "")
        downloadJob = viewModelScope.launch {
            repository.downloadApk(info).collect { state ->
                _uiState.value = when (state) {
                    is DownloadState.Downloading ->
                        UpdateUiState.Downloading(info, state.bytesRead, state.totalBytes, state.mirrorLabel)
                    is DownloadState.Success -> UpdateUiState.ReadyToInstall(info, state.apkFile)
                    is DownloadState.Failed -> UpdateUiState.DownloadFailed(info, state.message)
                }
            }
        }
    }

    fun ignoreVersion() {
        val s = _uiState.value as? UpdateUiState.UpdateAvailable ?: return
        viewModelScope.launch { preferencesDataStore.setIgnoredVersionCode(s.info.versionCode) }
        _uiState.value = UpdateUiState.Idle
    }

    fun dismiss() {
        checkJob?.cancel()
        checkJob = null
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = UpdateUiState.Idle
    }
}
