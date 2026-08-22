package com.example.assemblylinetycoon.presentation.ui.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.assemblylinetycoon.domain.catalog.ItemCatalog
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.presentation.ui.theme.ConveyorAmber
import com.example.assemblylinetycoon.presentation.ui.theme.ConveyorAmberDark
import com.example.assemblylinetycoon.presentation.ui.theme.CopperAccent
import com.example.assemblylinetycoon.presentation.ui.theme.SignalGreen
import com.example.assemblylinetycoon.presentation.ui.theme.SignalRed
import com.example.assemblylinetycoon.presentation.ui.theme.SteelSurface
import com.example.assemblylinetycoon.presentation.ui.theme.SteelSurfaceVariant

/**
 * Цвета холста завода.
 *
 * Все Color-объекты создаются **один раз** при загрузке класса. Рендерер
 * вызывается 20 раз в секунду; парсить `#RRGGBB` из каталога предметов на
 * каждом кадре означало бы гонять сборщик мусора впустую.
 */
@Immutable
object FactoryPalette {

    val background: Color = SteelSurface
    val gridLine: Color = SteelSurfaceVariant
    val emptyCell: Color = SteelSurface
    val belt: Color = SteelSurfaceVariant
    val beltArrow: Color = ConveyorAmberDark
    val selection: Color = ConveyorAmber
    val machineBody: Color = CopperAccent
    val machineText: Color = Color(0xFF14181D)
    val progressTrack: Color = Color(0x33000000)
    val progressFill: Color = SignalGreen
    val idleMarker: Color = SignalRed

    /** Цвет корпуса по роли машины: источник, передел, сбыт. */
    fun machine(type: MachineType): Color = when {
        type.isSource -> SignalGreen
        type.isSink -> ConveyorAmber
        else -> CopperAccent
    }

    /**
     * Цвет предмета из каталога. Значения разобраны заранее и лежат в карте,
     * поиск по ключу — единственное, что происходит во время отрисовки.
     */
    private val itemColors: Map<String, Color> = ItemCatalog.all().associate { item ->
        // Подсказка по цвету необязательна: у предмета без неё будет
        // нейтральный медный оттенок, а не падение рендерера.
        item.id to (item.visual?.colorHex?.let(::parseHex) ?: CopperAccent)
    }

    fun item(itemId: String): Color = itemColors[itemId] ?: CopperAccent

    private fun parseHex(hex: String): Color {
        val value = hex.removePrefix("#")
        val rgb = value.toLongOrNull(radix = 16) ?: return CopperAccent
        return Color(0xFF000000L or rgb)
    }
}
