package com.example.assemblylinetycoon.domain.catalog

import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.MachineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты целостности баланса. Их задача — ловить не ошибки кода, а ошибки
 * данных: недостижимый предмет, убыточный рецепт, цикл в цепочке.
 * Такие поломки иначе всплывают только через час игры.
 */
class CatalogIntegrityTest {

    @Test // у каждого предмета есть запись в каталоге
    fun everyItemHasCatalogEntry() {
        ItemId.entries.forEach { id ->
            val item = ItemCatalog[id]
            assertTrue("Цена $id должна быть положительной", item.basePrice > 0L)
            assertTrue("Ярус $id не может быть отрицательным", item.tier >= 0)
        }
    }

    @Test // любой предмет либо добывается, либо производится
    fun everyItemIsObtainable() {
        ItemId.entries.forEach { id ->
            assertNotNull("Предмет $id нельзя получить ни одним рецептом", RecipeCatalog.forOutput(id))
        }
    }

    @Test // сырьё производится карьерами и не требует входов
    fun rawItemsComeFromSpawners() {
        ItemCatalog.raw().forEach { item ->
            val recipe = RecipeCatalog.forOutput(item.id)!!
            assertEquals(MachineType.SPAWNER, recipe.machineType)
            assertTrue("Сырьё ${item.id} не должно требовать входов", recipe.inputs.isEmpty())
        }
    }

    @Test // каждый передел добавляет стоимость, иначе цепочка бессмысленна
    fun everyRecipeAddsValue() {
        RecipeCatalog.all().forEach { recipe ->
            val added = RecipeCatalog.valueAdded(recipe)
            assertTrue(
                "Рецепт ${recipe.outputItemId} убыточен: прибавка $added",
                added > 0L,
            )
        }
    }

    @Test // маржа передела не меньше 10%: иначе игроку выгоднее продавать полуфабрикат
    fun everyRecipeKeepsMinimumMargin() {
        RecipeCatalog.all()
            .filter { it.inputs.isNotEmpty() }
            .forEach { recipe ->
                val inputValue = RecipeCatalog.inputValue(recipe)
                val margin = RecipeCatalog.valueAdded(recipe).toDouble() / inputValue
                assertTrue(
                    "Маржа ${recipe.outputItemId} = ${(margin * 100).toInt()}% ниже порога 10%",
                    margin >= 0.10,
                )
            }
    }

    @Test // вход рецепта всегда ниже ярусом, чем выход — цепочка без циклов
    fun recipeGraphIsAcyclic() {
        RecipeCatalog.all().forEach { recipe ->
            val outputTier = ItemCatalog[recipe.outputItemId].tier
            recipe.inputs.keys.forEach { input ->
                assertTrue(
                    "Цикл в цепочке: ${recipe.outputItemId} требует $input того же или выше яруса",
                    ItemCatalog[input].tier < outputTier,
                )
            }
        }
    }

    @Test // длительность такта растёт вместе с глубиной передела
    fun deeperRecipesTakeLonger() {
        val byTier = RecipeCatalog.all().groupBy { ItemCatalog[it.outputItemId].tier }
        val slowestPerTier = byTier.mapValues { (_, list) -> list.maxOf { it.baseDurationMillis } }
        val tiers = slowestPerTier.keys.sorted()
        tiers.zipWithNext().forEach { (low, high) ->
            assertTrue(
                "Ярус $high быстрее яруса $low",
                slowestPerTier.getValue(high) >= slowestPerTier.getValue(low),
            )
        }
    }

    @Test // такты укладываются в заявленный в ТЗ диапазон 2–25 секунд
    fun craftDurationsStayInDesignRange() {
        RecipeCatalog.all().forEach { recipe ->
            assertTrue(
                "Такт ${recipe.outputItemId} = ${recipe.baseDurationMillis} мс вне диапазона 2000..25000",
                recipe.baseDurationMillis in 2_000L..25_000L,
            )
        }
    }

    @Test // машина умеет только те рецепты, которые за ней закреплены
    fun machineRecipeLookupIsConsistent() {
        MachineType.entries.forEach { type ->
            RecipeCatalog.forMachine(type).forEach { recipe ->
                assertEquals(type, recipe.machineType)
            }
        }
        assertTrue("Экспортёр ничего не производит", RecipeCatalog.forMachine(MachineType.EXPORTER).isEmpty())
    }

    @Test // цена постройки растёт с числом уже построенных машин
    fun buildCostGrowsWithOwnedCount() {
        val first = MachineCatalog.buildCost(MachineType.ASSEMBLER, ownedCount = 0)
        val second = MachineCatalog.buildCost(MachineType.ASSEMBLER, ownedCount = 1)
        assertEquals(MachineType.ASSEMBLER.baseCost, first)
        assertTrue("Вторая машина должна быть дороже первой", second > first)
    }

    @Test // первый слот бесплатный, последний стоит 25 миллионов
    fun slotPricesMatchDesign() {
        assertEquals(0L, SlotCatalog.unlockCost(1))
        assertEquals(100L, SlotCatalog.unlockCost(2))
        assertEquals(25_000_000L, SlotCatalog.unlockCost(SlotCatalog.maxSlots))
        assertNull("После последнего слота открывать нечего", SlotCatalog.nextUnlockCost(SlotCatalog.maxSlots))
    }
}
