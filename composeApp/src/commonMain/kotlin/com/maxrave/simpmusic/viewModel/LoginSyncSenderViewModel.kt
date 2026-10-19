package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.model.loginsync.DesktopInfo
import com.maxrave.domain.data.model.loginsync.LoginSyncException
import com.maxrave.domain.data.model.loginsync.LoginSyncService
import com.maxrave.domain.data.model.loginsync.SettingsCategory
import com.maxrave.domain.data.model.loginsync.SyncDirection
import com.maxrave.domain.repository.LoginSyncSenderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Android: scans the Desktop's QR, asks the user to trust it, exchanges the chosen sign-ins and settings. */
class LoginSyncSenderViewModel(
    private val repository: LoginSyncSenderRepository,
) : ViewModel() {
    sealed interface Step {
        data object Loading : Step

        data object NothingSignedIn : Step

        /** [blocked]: denied with "don't ask again", so only the system settings can grant it now. */
        data class NeedsCamera(
            val blocked: Boolean,
        ) : Step

        data object NoCamera : Step

        data object Scanning : Step

        data object Connecting : Step

        data class Trust(
            val desktop: DesktopInfo,
        ) : Step

        data class Choose(
            val desktop: DesktopInfo,
        ) : Step

        data object Sending : Step

        data class Done(
            val desktop: DesktopInfo,
            val services: List<LoginSyncService>,
            val settings: List<SettingsCategory> = emptyList(),
            val direction: SyncDirection = SyncDirection.PUSH,
        ) : Step

        data class Failed(
            val reason: LoginSyncException.Reason,
            val detail: String?,
        ) : Step
    }

    data class UiState(
        val step: Step = Step.Loading,
        val signedIn: List<LoginSyncService> = emptyList(),
        val selected: Set<LoginSyncService> = emptySet(),
        val selectedSettings: Set<SettingsCategory> = SettingsCategory.entries.toSet(),
        val direction: SyncDirection = SyncDirection.PUSH,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private fun step(step: Step) = _uiState.update { it.copy(step = step) }

    /** The sheet opened. [hasCamera]: the camera permission is already granted. */
    fun open(hasCamera: Boolean) {
        _uiState.value = UiState()
        viewModelScope.launch {
            val signedIn = repository.signedInServices()
            _uiState.value =
                UiState(
                    step = if (hasCamera) Step.Scanning else Step.NeedsCamera(blocked = false),
                    signedIn = signedIn,
                    selected = signedIn.toSet(),
                    selectedSettings = SettingsCategory.entries.toSet(),
                    direction = SyncDirection.PUSH,
                )
        }
    }

    fun onCameraPermission(
        granted: Boolean,
        blocked: Boolean,
    ) = step(if (granted) Step.Scanning else Step.NeedsCamera(blocked))

    fun onCameraUnavailable() = step(Step.NoCamera)

    fun onScanned(text: String) {
        // Any other QR is ignored and the camera keeps looking.
        if (_uiState.value.step != Step.Scanning || !repository.isInvite(text)) return
        step(Step.Connecting)
        viewModelScope.launch {
            step(
                try {
                    val desktop = repository.connect(text)
                    Step.Trust(desktop)
                } catch (e: LoginSyncException) {
                    Step.Failed(e.reason, e.detail)
                },
            )
        }
    }

    fun trust() {
        val trust = _uiState.value.step as? Step.Trust ?: return
        step(Step.Choose(trust.desktop))
    }

    fun setDirection(direction: SyncDirection) {
        val step = _uiState.value.step as? Step.Choose
        val desktopLogins = step?.desktop?.availableLogins?.toSet().orEmpty()
        val defaultServices = if (direction == SyncDirection.PUSH) _uiState.value.signedIn.toSet() else desktopLogins
        _uiState.update {
            it.copy(
                direction = direction,
                selected = defaultServices,
            )
        }
    }

    fun toggle(service: LoginSyncService) =
        _uiState.update { it.copy(selected = if (service in it.selected) it.selected - service else it.selected + service) }

    fun toggleSetting(category: SettingsCategory) =
        _uiState.update {
            it.copy(
                selectedSettings =
                    if (category in it.selectedSettings) {
                        it.selectedSettings - category
                    } else {
                        it.selectedSettings + category
                    },
            )
        }

    fun send() {
        val choose = _uiState.value.step as? Step.Choose ?: return
        val direction = _uiState.value.direction
        val services = _uiState.value.selected
        val settings = _uiState.value.selectedSettings
        if (services.isEmpty() && settings.isEmpty()) return
        step(Step.Sending)
        viewModelScope.launch {
            step(
                try {
                    val result = repository.sync(direction, services, settings)
                    Step.Done(choose.desktop, result.first, result.second, direction)
                } catch (e: LoginSyncException) {
                    Step.Failed(e.reason, e.detail)
                },
            )
        }
    }

    fun retry(hasCamera: Boolean) {
        repository.disconnect()
        step(if (hasCamera) Step.Scanning else Step.NeedsCamera(blocked = false))
    }

    /** The sheet is gone. A computer still waiting on this phone is put back on its QR. */
    fun close() = repository.disconnect()

    override fun onCleared() = close()
}
