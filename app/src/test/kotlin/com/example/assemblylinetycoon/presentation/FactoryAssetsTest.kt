package com.example.assemblylinetycoon.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.assemblylinetycoon.domain.model.ItemShape
import com.example.assemblylinetycoon.domain.model.MachineType
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Проверка наличия графики.
 *
 * Смысл теста не в картинках, а в связке «каталог — ассеты». Новый тип машины
 * или новая форма предмета добавляются в домене, и без этой проверки они
 * молча появились бы на поле безликим прямоугольником: рендерер честно
 * рисует запасной вариант вместо падения, поэтому пропажу заметил бы только
 * игрок.
 */
@RunWith(RobolectricTestRunner::class)
class FactoryAssetsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun drawableId(name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)

    @Test // у каждого типа машины есть свой спрайт
    fun everyMachineTypeHasSprite() {
        MachineType.entries.forEach { type ->
            val name = "machine_" + type.name.lowercase()
            assertNotEquals("Нет ассета $name для $type", 0, drawableId(name))
        }
    }

    @Test // у каждой формы предмета есть спрайт под тонировку
    fun everyItemShapeHasSprite() {
        val sprites = mapOf(
            ItemShape.HEXAGON to "item_chunk",
            ItemShape.SQUARE to "item_ingot",
            ItemShape.CIRCLE to "item_coil",
            ItemShape.TRIANGLE to "item_part",
        )

        // Карта обязана покрывать перечисление целиком: добавили форму —
        // добавьте картинку, иначе предмет поедет по ленте пустым кружком.
        assertNotEquals(0, ItemShape.entries.size)
        ItemShape.entries.forEach { shape ->
            val name = requireNotNull(sprites[shape]) { "Для формы $shape не задан спрайт" }
            assertNotEquals("Нет ассета $name", 0, drawableId(name))
        }
    }

    @Test // кадров анимации ленты ровно столько, сколько ждёт рендерер
    fun beltAnimationHasAllFrames() {
        repeat(4) { frame ->
            assertNotEquals("Нет кадра tile_belt_$frame", 0, drawableId("tile_belt_$frame"))
        }
        assertNotEquals("Нет тайла пола", 0, drawableId("tile_floor"))
    }
}
