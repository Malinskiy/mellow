package dev.mellow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mellow.core.data.repository.UserRepository
import dev.mellow.core.model.Server
import dev.mellow.core.network.NetworkPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val server: Server? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val networkPreferences: NetworkPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val trustSelfSigned: StateFlow<Boolean> = networkPreferences.trustSelfSigned
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTrustSelfSigned(enabled: Boolean) {
        viewModelScope.launch {
            networkPreferences.setTrustSelfSigned(enabled)
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val server = userRepository.authenticate(serverUrl, username, password)
                _uiState.value = _uiState.value.copy(isLoading = false, server = server)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Authentication failed")
            }
        }
    }
}
