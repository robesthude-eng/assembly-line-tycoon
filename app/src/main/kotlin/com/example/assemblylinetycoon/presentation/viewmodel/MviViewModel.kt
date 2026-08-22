package com.example.assemblylinetycoon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assemblylinetycoon.presentation.state.UiEffect
import com.example.assemblylinetycoon.presentation.state.UiIntent
import com.example.assemblylinetycoon.presentation.state.UiState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Базовый MVI-ViewModel: однонаправленный поток данных.
 *
 *     Intent → handleIntent → setState → StateFlow<State> → Compose
 *                           ↘ sendEffect → SharedFlow<Effect> → разовые действия
 *
 * Состояние наружу отдаётся только как неизменяемый [StateFlow]; UI не имеет
 * никакого способа изменить его напрямую.
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<E> = _effects.asSharedFlow()

    /** Единственная точка входа для действий пользователя. */
    fun onIntent(intent: I) {
        handleIntent(intent)
    }

    protected abstract fun handleIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
