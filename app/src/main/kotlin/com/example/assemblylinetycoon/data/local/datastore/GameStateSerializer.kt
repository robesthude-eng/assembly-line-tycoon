package com.example.assemblylinetycoon.data.local.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Сериализатор снапшота игры для типизированного DataStore.
 *
 * Формат — JSON через kotlinx.serialization (Proto DataStore без .proto-схемы
 * не даёт выигрыша, а JSON проще мигрировать и читать при отладке).
 *
 * Повреждённый файл не роняет игру: возвращается состояние новой игры,
 * иначе краш-луп на старте у части игроков гарантирован.
 */
class GameStateSerializer(
    private val json: Json = SerializationConfig.json,
) : Serializer<GameState> {

    override val defaultValue: GameState = GameState.EMPTY

    override suspend fun readFrom(input: InputStream): GameState =
        try {
            json.decodeFromString(
                GameState.serializer(),
                input.readBytes().decodeToString(),
            )
        } catch (e: SerializationException) {
            throw CorruptionException("Не удалось прочитать сохранение", e)
        }

    override suspend fun writeTo(t: GameState, output: OutputStream) {
        output.write(
            json.encodeToString(GameState.serializer(), t).encodeToByteArray(),
        )
    }
}
