package com.example.assemblylinetycoon.presentation.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.example.assemblylinetycoon.R
import com.example.assemblylinetycoon.domain.model.ItemShape
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Загруженные картинки завода.
 *
 * Собираются один раз на экран и передаются в рендерер готовыми: декодировать
 * PNG внутри отрисовки, которая идёт двадцать раз в секунду, нельзя ни при
 * каких обстоятельствах.
 *
 * Спрайты предметов лежат **по формам, а не по предметам**: их четыре на все
 * четырнадцать позиций каталога, а цвет накладывается тонированием из
 * `ItemCatalog`. Иначе каждый новый предмет в балансе требовал бы нового файла,
 * и художник становился бы блокером для правки экономики.
 */
@Immutable
class FactorySprites(
    val floor: ImageBitmap,
    /** Кадры ленты, едущей вправо. Прочие направления — поворот при отрисовке. */
    val beltFrames: List<ImageBitmap>,
    val machines: Map<MachineType, ImageBitmap>,
    val items: Map<ItemShape, ImageBitmap>,
) {
    fun beltFrame(index: Int): ImageBitmap = beltFrames[index.mod(beltFrames.size)]
}

@Composable
fun rememberFactorySprites(): FactorySprites {
    val floor = ImageBitmap.imageResource(R.drawable.tile_floor)
    val belt0 = ImageBitmap.imageResource(R.drawable.tile_belt_0)
    val belt1 = ImageBitmap.imageResource(R.drawable.tile_belt_1)
    val belt2 = ImageBitmap.imageResource(R.drawable.tile_belt_2)
    val belt3 = ImageBitmap.imageResource(R.drawable.tile_belt_3)

    val spawner = ImageBitmap.imageResource(R.drawable.machine_spawner)
    val smelter = ImageBitmap.imageResource(R.drawable.machine_smelter)
    val press = ImageBitmap.imageResource(R.drawable.machine_press)
    val wireDrawer = ImageBitmap.imageResource(R.drawable.machine_wire_drawer)
    val assembler = ImageBitmap.imageResource(R.drawable.machine_assembler)
    val qualityGate = ImageBitmap.imageResource(R.drawable.machine_quality_gate)
    val exporter = ImageBitmap.imageResource(R.drawable.machine_exporter)

    val chunk = ImageBitmap.imageResource(R.drawable.item_chunk)
    val ingot = ImageBitmap.imageResource(R.drawable.item_ingot)
    val coil = ImageBitmap.imageResource(R.drawable.item_coil)
    val part = ImageBitmap.imageResource(R.drawable.item_part)

    return remember(floor, belt0, spawner, chunk) {
        FactorySprites(
            floor = floor,
            beltFrames = listOf(belt0, belt1, belt2, belt3),
            machines = mapOf(
                MachineType.SPAWNER to spawner,
                MachineType.SMELTER to smelter,
                MachineType.PRESS to press,
                MachineType.WIRE_DRAWER to wireDrawer,
                MachineType.ASSEMBLER to assembler,
                MachineType.QUALITY_GATE to qualityGate,
                MachineType.EXPORTER to exporter,
            ),
            items = mapOf(
                // Форма из каталога решает, какой картинкой рисовать предмет:
                // руда — куском, переплав — слитком, провод — мотком,
                // готовые изделия — деталью.
                ItemShape.HEXAGON to chunk,
                ItemShape.SQUARE to ingot,
                ItemShape.CIRCLE to coil,
                ItemShape.TRIANGLE to part,
            ),
        )
    }
}
