package app.linkphone.core.util

import kotlinx.coroutines.flow.MutableStateFlow

sealed interface LinkState {
    data object Stopped : LinkState
    data class Connecting(val detail: String) : LinkState
    data class Connected(val pcName: String, val host: String) : LinkState
    data class Error(val message: String) : LinkState
}

object LinkStatus {
    val state = MutableStateFlow<LinkState>(LinkState.Stopped)
    val logs = MutableStateFlow<List<String>>(emptyList())
    val lastError = MutableStateFlow<String?>(null)

    fun log(tag: String, message: String) {
        LogRing.add(tag, message)
        logs.value = LogRing.snapshot()
    }

    fun setState(state: LinkState, error: String? = null) {
        this.state.value = state
        lastError.value = error
    }
}
