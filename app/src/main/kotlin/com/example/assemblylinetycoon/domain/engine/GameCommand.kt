package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.model.AdPlacement

/**
 * Команды, меняющие состояние симуляции.
 *
 * Отличие от presentation-интентов: интент — это «пользователь нажал»,
 * команда — «симуляция должна сделать». Экран транслирует первое во второе,
 * а движок не знает о существовании UI.
 */
sealed interface GameCommand {

    /** Служебный тик симуляции. */
    data class Tick(val deltaMillis: Long) : GameCommand

    /** Начисление офлайн-дохода после расчёта на старте. */
    data class ApplyOfflineEarnings(val coins: Long) : GameCommand

    /** Награда за просмотренный ролик. */
    data class ApplyAdReward(val placement: AdPlacement) : GameCommand

    /** Сброс прогресса. */
    data object ResetProgress : GameCommand
}
