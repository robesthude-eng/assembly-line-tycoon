package com.example.assemblylinetycoon.domain.repository

import com.example.assemblylinetycoon.domain.model.Recipe
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Доступ к рецептам.
 *
 * Интерфейс объявлен в домене, а реализация живёт в data: домен диктует, что
 * ему нужно, и не знает, откуда это придёт — из кода, из файла или с сервера.
 * Сейчас источник один, `RecipeCatalog`, но подмена его в тестах или загрузка
 * баланса с сервера не потребуют правок в use case'ах.
 *
 * Методы синхронные: справочник целиком лежит в памяти, обращаться к нему
 * будет игровой цикл на каждом такте, и приостановка там неуместна.
 */
interface RecipeRepository {

    /** Все рецепты игры. */
    fun getAll(): List<Recipe>

    /** Рецепт, производящий предмет; null, если такого нет. */
    fun findByOutput(outputItemId: String): Recipe?

    /** Рецепты, доступные на машине данного типа. */
    fun findByMachine(machineType: MachineType): List<Recipe>
}
