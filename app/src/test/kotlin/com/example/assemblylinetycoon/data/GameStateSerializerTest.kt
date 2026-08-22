package com.example.assemblylinetycoon.data

import androidx.datastore.core.CorruptionException
import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.data.local.datastore.GameStateSerializer
import com.example.assemblylinetycoon.data.local.datastore.model.SavedGameState
import com.example.assemblylinetycoon.data.mapper.GameStateMapper
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.domain.engine.FactoryBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Тесты формата сохранения.
 *
 * Сохранение — единственное, что игрок не может восстановить: потерянный
 * прогресс не вернуть ни патчем, ни поддержкой. Поэтому здесь проверяется
 * не «код не падает», а совместимость: старый файл читается новой сборкой,
 * повреждённый не роняет игру, построенный завод переживает круг записи.
 */
class GameStateSerializerTest {

    private val serializer = GameStateSerializer()

    /** Полный круг: домен → файл → домен, как это происходит на устройстве. */
    private suspend fun roundTrip(state: GameState): GameState {
        val out = ByteArrayOutputStream()
        serializer.writeTo(GameStateMapper.toData(state), out)
        return GameStateMapper.toDomain(serializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    private suspend fun read(json: String): SavedGameState =
        serializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))

    @Test // построенный завод переживает запись и чтение без потерь
    fun factorySurvivesRoundTrip() = runTest {
        var state = GameState.NEW_GAME.copy(coins = 5_000L)
        state = FactoryBuilder.place(state, GridPosition(1, 1), MachineType.SPAWNER)
        state = FactoryBuilder.placeBelt(state, GridPosition(2, 1), Direction.RIGHT)
        state = FactoryBuilder.place(state, GridPosition(3, 1), MachineType.EXPORTER)
        state = state.copy(
            movingItems = listOf(
                MovingItem("iron_ore", 1, GridPosition(2, 1), GridPosition(3, 1), 0.42f),
            ),
        )

        val restored = roundTrip(state)

        assertEquals(state, restored)
        assertEquals(MachineType.SPAWNER, restored.machineAt(GridPosition(1, 1))?.type)
        assertEquals(Direction.RIGHT, restored.grid[GridPosition(2, 1)]?.direction)
        assertEquals(0.42f, restored.movingItems.single().progress, 0.0001f)
    }

    @Test // отметка времени сохраняется: без неё офлайн-доход не посчитать
    fun timestampSurvivesRoundTrip() = runTest {
        val state = GameState.NEW_GAME.copy(lastSavedAtMillis = 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, roundTrip(state).lastSavedAtMillis)
    }

    @Test // сохранение прошлой версии читается: обновление игры не стирает прогресс
    fun savesWithoutNewFieldsAreStillReadable() = runTest {
        // Файл, записанный сборкой, где ещё не было половины полей.
        val legacy = """{"v":1,"coins":777,"savedAt":123}"""

        val restored = GameStateMapper.toDomain(read(legacy))

        assertEquals(777L, restored.coins)
        assertEquals(123L, restored.lastSavedAtMillis)
        // Недостающие поля берутся по умолчанию, а не роняют чтение.
        assertTrue(restored.machines.isEmpty())
    }

    @Test // неизвестные поля из будущей версии игнорируются, а не ломают чтение
    fun unknownFieldsAreIgnored() = runTest {
        val future = """{"v":1,"coins":10,"prestigeLevel":4,"guild":"нет"}"""

        assertEquals(10L, read(future).coins)
    }

    @Test // битый файл даёт понятную ошибку, а не падение в случайном месте
    fun corruptedSaveRaisesCorruptionException() = runTest {
        val garbage = ByteArrayInputStream("не json вовсе".encodeToByteArray())

        try {
            serializer.readFrom(garbage)
            throw AssertionError("Ожидалось CorruptionException")
        } catch (expected: CorruptionException) {
            assertTrue(expected.message!!.isNotBlank())
        }
    }

    @Test // после повреждения игрок начинает новую игру, а не смотрит на краш-луп
    fun defaultValueIsPlayableNewGame() {
        assertEquals(GameStateMapper.toData(GameState.NEW_GAME), serializer.defaultValue)
        assertEquals(GameConstants.STARTING_COINS, serializer.defaultValue.coins)
    }

    @Test // версия схемы записывается: без неё будущие миграции не к чему привязать
    fun schemaVersionIsPersisted() = runTest {
        assertEquals(GameConstants.SAVE_SCHEMA_VERSION, roundTrip(GameState.NEW_GAME).schemaVersion)
    }
}
