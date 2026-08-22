package com.example.assemblylinetycoon.data.local.datastore

import kotlinx.serialization.json.Json

/**
 * Единая конфигурация kotlinx.serialization для сохранений.
 *
 * `ignoreUnknownKeys` и `encodeDefaults` обязательны: сохранение старой версии
 * должно читаться новой сборкой, а новые поля — записываться со значениями
 * по умолчанию. Без этого обновление игры ломает прогресс игроков.
 */
object SerializationConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        prettyPrint = false
    }
}
