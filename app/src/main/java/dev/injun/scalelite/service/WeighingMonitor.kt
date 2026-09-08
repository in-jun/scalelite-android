package dev.injun.scalelite.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live progress of the current weigh-in, shared between [WeighingService] and the UI. */
sealed interface WeighingState {
    data object Idle : WeighingState
    data class Connecting(val address: String) : WeighingState
    data class Reading(val address: String, val grams: Int, val progress: Int, val required: Int) : WeighingState
    data class Recorded(val address: String, val grams: Int, val syncedToHealthConnect: Boolean) : WeighingState
    data class Failed(val address: String, val reason: String) : WeighingState
}

@Singleton
class WeighingMonitor @Inject constructor() {
    private val _state = MutableStateFlow<WeighingState>(WeighingState.Idle)
    val state: StateFlow<WeighingState> = _state.asStateFlow()

    fun update(state: WeighingState) {
        _state.value = state
    }
}
