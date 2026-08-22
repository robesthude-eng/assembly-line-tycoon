package com.example.assemblylinetycoon.data.local.datastore.model

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели файла сохранения.
 *
 * Отдельные от доменных сознательно. Доменные классы меняются вслед за
 * геймплеем — переименовали поле, разбили класс надвое, вынесли enum, — и
 * каждое такое изменение молча ломало бы сохранения игроков, если бы формат
 * файла определялся ими напрямую. Здесь же поле переименовать нельзя: имя
 * зафиксировано в [SerialName] и является частью контракта с уже
 * установленными копиями игры.
 *
 * Второе следствие: домен не знает про kotlinx.serialization вовсе. Слой
 * симуляции не должен зависеть даже от библиотеки сериализации — иначе
 * «данные» неизбежно начнут просачиваться в правила игры.
 *
 * Правила формата:
 *  * все поля со значениями по умолчанию — старое сохранение обязано читаться
 *    новой сборкой;
 *  * деньги только `Long`, ни `Float`, ни `Double` (GDD);
 *  * короткие имена ключей: файл пишется каждые десять секунд.
 */
@Serializable
data class SavedGameState(
    @SerialName("v") val schemaVersion: Int = GameConstants.SAVE_SCHEMA_VERSION,

    // ── игрок ───────────────────────────────────────────────────────────────
    @SerialName("coins") val coins: Long = 0L,
    @SerialName("stats") val stats: SavedStats = SavedStats(),

    // ── завод ───────────────────────────────────────────────────────────────
    @SerialName("w") val gridWidth: Int = GameConstants.GRID_WIDTH,
    @SerialName("h") val gridHeight: Int = GameConstants.GRID_HEIGHT,
    @SerialName("cells") val cells: List<SavedCell> = emptyList(),
    @SerialName("machines") val machines: List<SavedMachine> = emptyList(),
    @SerialName("nextId") val nextMachineId: Int = 1,

    // ── предметы в пути ─────────────────────────────────────────────────────
    @SerialName("items") val items: List<SavedItem> = emptyList(),

    // ── прогресс ────────────────────────────────────────────────────────────
    @SerialName("tech") val unlockedTechMask: Long = 0L,
    @SerialName("slots") val unlockedSlots: Int = 1,
    @SerialName("rate") val baselineProductionRate: Double = 0.0,
    @SerialName("init") val isInitialized: Boolean = false,
    @SerialName("savedAt") val lastSavedAtMillis: Long = 0L,
    @SerialName("tickAt") val lastTickAtMillis: Long = 0L,

    // ── усиления ────────────────────────────────────────────────────────────
    @SerialName("overdriveUntil") val overdriveUntilMillis: Long = 0L,
)

/**
 * Ячейка поля.
 *
 * Тип и направление хранятся строками, а не порядковыми номерами enum:
 * перестановка констант в коде не должна превращать конвейеры игрока
 * в экспортёры. Неизвестное значение при чтении трактуется как пустая клетка.
 */
@Serializable
data class SavedCell(
    @SerialName("t") val type: String = "EMPTY",
    @SerialName("d") val direction: String = "RIGHT",
    @SerialName("m") val machineId: Int? = null,
)

@Serializable
data class SavedMachine(
    @SerialName("id") val id: Int = 0,
    @SerialName("t") val type: String = "SPAWNER",
    @SerialName("x") val x: Int = 0,
    @SerialName("y") val y: Int = 0,
    @SerialName("f") val facing: String = "RIGHT",
    @SerialName("lvl") val level: Int = 0,
    @SerialName("out") val recipeOutputId: String? = null,
    @SerialName("st") val status: String = "IDLE",
    @SerialName("el") val elapsedMillis: Long = 0L,
    @SerialName("in") val inputBuffer: Map<String, Int> = emptyMap(),
    @SerialName("ob") val outputBuffer: Map<String, Int> = emptyMap(),
)

/** Предмет, едущий по ленте: откуда, куда и какая доля пути пройдена. */
@Serializable
data class SavedItem(
    @SerialName("i") val itemId: String = "",
    @SerialName("n") val amount: Int = 1,
    @SerialName("fx") val fromX: Int = 0,
    @SerialName("fy") val fromY: Int = 0,
    @SerialName("tx") val toX: Int = 0,
    @SerialName("ty") val toY: Int = 0,
    @SerialName("p") val progress: Float = 0f,
)

@Serializable
data class SavedStats(
    @SerialName("produced") val itemsProduced: Long = 0L,
    @SerialName("exported") val itemsExported: Long = 0L,
    @SerialName("earned") val coinsEarned: Long = 0L,
    @SerialName("simulated") val simulatedMillis: Long = 0L,
)
