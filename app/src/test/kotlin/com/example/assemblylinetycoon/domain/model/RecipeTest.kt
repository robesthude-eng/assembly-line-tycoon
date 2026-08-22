package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Рецепт: сопоставление входов без учёта порядка, отказы и данные выхода. */
class RecipeTest {

    private val json = Json { encodeDefaults = true }

    private val ironIngot = Recipe(
        outputItemId = "iron_ingot",
        outputAmount = 1,
        inputs = mapOf("iron_ore" to 2, "copper_ore" to 1),
        baseDurationMillis = 4_000L,
        machineType = MachineType.SMELTER,
    )

    @Test // порядок входов в буфере не влияет на результат проверки
    fun inputMatchingIgnoresOrder() {
        val straight = linkedMapOf("iron_ore" to 2, "copper_ore" to 1)
        val reversed = linkedMapOf("copper_ore" to 1, "iron_ore" to 2)

        assertTrue(ironIngot.canCraftFrom(straight))
        assertTrue(ironIngot.canCraftFrom(reversed))
    }

    @Test // порядок в самом рецепте тоже не важен: два рецепта равны
    fun recipesWithDifferentInputOrderAreEqual() {
        val other = ironIngot.copy(inputs = linkedMapOf("copper_ore" to 1, "iron_ore" to 2))
        assertEquals(ironIngot, other)
        assertEquals(ironIngot.hashCode(), other.hashCode())
    }

    @Test // избыток сырья не мешает: проверяется «не меньше», а не «ровно»
    fun surplusInputsAreAccepted() {
        assertTrue(ironIngot.canCraftFrom(mapOf("iron_ore" to 50, "copper_ore" to 7, "silicon" to 3)))
    }

    @Test // нехватки достаточно одной, чтобы такт не начался
    fun insufficientInputsFail() {
        assertFalse(ironIngot.canCraftFrom(mapOf("iron_ore" to 1, "copper_ore" to 1)))
        assertFalse(ironIngot.canCraftFrom(mapOf("iron_ore" to 2)))
        assertFalse(ironIngot.canCraftFrom(emptyMap()))
    }

    @Test // чужие предметы не заменяют требуемые
    fun wrongInputsFail() {
        assertFalse(ironIngot.canCraftFrom(mapOf("silicon" to 100, "gear" to 100)))
    }

    @Test // видно не только «нельзя», но и чего именно не хватает
    fun missingInputsAreReported() {
        val missing = ironIngot.missingInputs(mapOf("iron_ore" to 1))

        assertEquals(mapOf("iron_ore" to 1, "copper_ore" to 1), missing)
        assertTrue(ironIngot.missingInputs(mapOf("iron_ore" to 2, "copper_ore" to 1)).isEmpty())
    }

    @Test // списание убирает ровно необходимое и чистит опустевшие ключи
    fun consumingInputsRemovesExactAmounts() {
        val after = ironIngot.consumeFrom(mapOf("iron_ore" to 5, "copper_ore" to 1, "gear" to 2))

        assertEquals(3, after["iron_ore"])
        assertFalse("Опустевший ключ должен исчезнуть", after.containsKey("copper_ore"))
        assertEquals(2, after["gear"])
    }

    @Test(expected = IllegalArgumentException::class) // списать больше, чем есть, нельзя
    fun consumingWithoutEnoughInputsFails() {
        ironIngot.consumeFrom(mapOf("iron_ore" to 1))
    }

    @Test // данные выхода читаются как заданы
    fun outputDataIsCorrect() {
        assertEquals("iron_ingot", ironIngot.outputItemId)
        assertEquals(1, ironIngot.outputAmount)
        assertEquals(4_000L, ironIngot.baseDurationMillis)
        assertEquals(MachineType.SMELTER, ironIngot.machineType)
        assertTrue(ironIngot.requiresInputs)
    }

    @Test // рецепт карьера не требует входов и крафтится из пустого буфера
    fun spawnerRecipeNeedsNoInputs() {
        val ore = Recipe(
            outputItemId = "iron_ore",
            baseDurationMillis = 2_000L,
            machineType = MachineType.SPAWNER,
        )

        assertFalse(ore.requiresInputs)
        assertTrue(ore.canCraftFrom(emptyMap()))
    }

    @Test // сборка из типизированных идентификаторов даёт те же строковые ключи
    fun typedFactoryProducesStringKeys() {
        val typed = Recipe.of(
            output = ItemId.IRON_INGOT,
            inputs = mapOf(ItemId.IRON_ORE to 2, ItemId.COPPER_ORE to 1),
            baseDurationMillis = 4_000L,
            machineType = MachineType.SMELTER,
        )

        assertEquals(ironIngot, typed)
    }

    @Test // некорректные рецепты отвергаются при создании
    fun invalidRecipesAreRejected() {
        val cases = listOf<Pair<String, () -> Recipe>>(
            "нулевой выход" to {
                Recipe("gear", outputAmount = 0, baseDurationMillis = 1_000L, machineType = MachineType.PRESS)
            },
            "нулевая длительность" to {
                Recipe("gear", baseDurationMillis = 0L, machineType = MachineType.PRESS)
            },
            "нулевое количество входа" to {
                Recipe("gear", inputs = mapOf("iron_ingot" to 0), baseDurationMillis = 1_000L, machineType = MachineType.PRESS)
            },
            "рецепт требует сам себя" to {
                Recipe("gear", inputs = mapOf("gear" to 1), baseDurationMillis = 1_000L, machineType = MachineType.PRESS)
            },
        )
        cases.forEach { (name, create) ->
            try {
                create()
                throw AssertionError("Ожидалась ошибка: $name")
            } catch (expected: IllegalArgumentException) {
                // так и должно быть
            }
        }
    }

    @Test // рецепт переживает JSON: карта входов восстанавливается целиком
    fun recipeSurvivesJsonRoundTrip() {
        val restored = json.decodeFromString(
            Recipe.serializer(),
            json.encodeToString(Recipe.serializer(), ironIngot),
        )

        assertEquals(ironIngot, restored)
        assertEquals(2, restored.inputs["iron_ore"])
    }

    @Test // каталог отдаёт рецепт по ключу предмета и по типу машины
    fun catalogLookupsWork() {
        val gear = RecipeCatalog.forOutput("gear")!!
        assertEquals(MachineType.PRESS, gear.machineType)
        assertTrue(RecipeCatalog.forMachine(MachineType.SPAWNER).all { !it.requiresInputs })
    }
}
