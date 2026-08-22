package com.example.assemblylinetycoon.data.local.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.assemblylinetycoon.data.local.datastore.model.SavedGameState
import com.example.assemblylinetycoon.data.mapper.GameStateMapper
import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Сериализатор файла сохранения.
 *
 * Работает с [SavedGameState] — моделью слоя данных, а не с доменным
 * снапшотом: формат файла обязан быть устойчивее, чем классы симуляции.
 *
 * Формат — JSON через kotlinx.serialization. Proto DataStore без .proto-схемы
 * выигрыша не даёт, а JSON проще мигрировать и читать глазами при разборе
 * жалобы игрока на потерянный прогресс.
 *
 * Повреждённый файл не роняет игру: DataStore получает [CorruptionException]
 * и подставляет новую игру. Иначе часть игроков получила бы краш-луп на
 * старте без единого способа выбраться.
 */
class GameStateSerializer(
    private val json: Json = SerializationConfig.json,
) : Serializer<SavedGameState> {

    override val defaultValue: SavedGameState = GameStateMapper.toData(GameState.NEW_GAME)

    override suspend fun readFrom(input: InputStream): SavedGameState =
        try {
            json.decodeFromString(
                SavedGameState.serializer(),
                input.readBytes().decodeToString(),
            )
        } catch (e: SerializationException) {
            throw CorruptionException("Не удалось прочитать сохранение", e)
        }

    override suspend fun writeTo(t: SavedGameState, output: OutputStream) {
        output.write(
            json.encodeToString(SavedGameState.serializer(), t).encodeToByteArray(),
        )
    }
}
