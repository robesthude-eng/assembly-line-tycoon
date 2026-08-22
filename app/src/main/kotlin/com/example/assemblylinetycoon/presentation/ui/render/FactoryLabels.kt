package com.example.assemblylinetycoon.presentation.ui.render

import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Названия игровых сущностей для интерфейса.
 *
 * Живут в presentation, а не в домене: домен не должен знать язык игрока.
 * Обычный Kotlin без Compose — значит, подписи проверяются юнит-тестом.
 *
 * Строки пока лежат в коде, а не в `strings.xml`: локализации нет, а держать
 * подписи рядом с рендерером удобнее. Перенос в ресурсы — механическая правка,
 * когда появится вторая локаль.
 */
object FactoryLabels {

    /** Полное название машины для диалога. */
    fun machineName(type: MachineType): String = when (type) {
        MachineType.SPAWNER -> "Карьер"
        MachineType.SMELTER -> "Плавильня"
        MachineType.PRESS -> "Пресс"
        MachineType.WIRE_DRAWER -> "Волочильный стан"
        MachineType.ASSEMBLER -> "Сборщик"
        MachineType.QUALITY_GATE -> "Контроль качества"
        MachineType.EXPORTER -> "Экспортёр"
    }

    /**
     * Двухбуквенный значок на клетке.
     *
     * Клетка на телефоне — примерно 30 dp: полное название туда не влезет, а
     * иконок у проекта пока нет. Две буквы читаются и не требуют ассетов.
     */
    fun machineGlyph(type: MachineType): String = when (type) {
        MachineType.SPAWNER -> "КР"
        MachineType.SMELTER -> "ПЛ"
        MachineType.PRESS -> "ПР"
        MachineType.WIRE_DRAWER -> "ВС"
        MachineType.ASSEMBLER -> "СБ"
        MachineType.QUALITY_GATE -> "КК"
        MachineType.EXPORTER -> "ЭК"
    }

    /** Куда толкает лента. */
    fun direction(direction: Direction): String = when (direction) {
        Direction.UP -> "Вверх"
        Direction.DOWN -> "Вниз"
        Direction.LEFT -> "Влево"
        Direction.RIGHT -> "Вправо"
    }

    /** Стрелка направления: короче слова и читается на кнопке. */
    fun directionArrow(direction: Direction): String = when (direction) {
        Direction.UP -> "↑"
        Direction.DOWN -> "↓"
        Direction.LEFT -> "←"
        Direction.RIGHT -> "→"
    }

    /** Что машина делает прямо сейчас. */
    fun status(status: MachineStatus): String = when (status) {
        MachineStatus.IDLE -> "Ожидает сырьё"
        MachineStatus.CRAFTING -> "Работает"
        MachineStatus.OUTPUT_EJECT -> "Выгружает"
    }
}
