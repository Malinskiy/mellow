package dev.mellow.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.data.repository.UserRepositoryImpl
import dev.mellow.sync.LibrarySyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthState { CHECKING, LOGGED_IN, LOGGED_OUT }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepositoryImpl,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.CHECKING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _serverId = MutableStateFlow<String?>(null)
    val serverId: StateFlow<String?> = _serverId.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = userRepository.restoreSession()
            if (restored) {
                val server = userRepository.getActiveServer()
                _serverId.value = server?.id
                _serverUrl.value = server?.url
                _authState.value = AuthState.LOGGED_IN
                server?.id?.let { triggerSync(it) }
            } else {
                _authState.value = AuthState.LOGGED_OUT
            }
        }
    }

    fun onLoggedIn(serverId: String) {
        viewModelScope.launch {
            val server = userRepository.getActiveServer()
            _serverId.value = serverId
            _serverUrl.value = server?.url
            _authState.value = AuthState.LOGGED_IN
            triggerSync(serverId)
        }
    }

    private fun triggerSync(serverId: String) {
        val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to serverId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
