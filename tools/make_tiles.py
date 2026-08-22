"""Генерация тайлов пола и анимированной ленты.

Рисуем кодом, а не картинкой из модели: тайл обязан стыковаться сам с собой
без шва, а четыре кадра ленты — сдвигаться ровно на четверть шага. Такую
точность генератор изображений не даёт, зато код даёт её бесплатно.
"""
from PIL import Image, ImageDraw

S = 64  # сторона тайла в пикселях
OUT = "app/src/main/res/drawable-nodpi"

STEEL_DARK = (27, 31, 36)
FLOOR_A = (44, 51, 60)
FLOOR_B = (39, 45, 53)
FLOOR_LINE = (55, 64, 75)
BELT_BODY = (72, 79, 89)
BELT_EDGE = (104, 113, 125)
BELT_SLAT = (92, 101, 112)
AMBER = (242, 166, 59)


def floor_tile(path: str) -> None:
    """Пол: клетка с еле заметной насечкой, чтобы сетка читалась без линий."""
    img = Image.new("RGBA", (S, S), FLOOR_A + (255,))
    d = ImageDraw.Draw(img)
    # Диагональная штриховка «рифлёного листа» — только в углу, чтобы
    # повторение тайла не давало заметного узора по всему полю.
    for i in range(0, S, 8):
        d.line([(i, 0), (0, i)], fill=FLOOR_B + (255,), width=1)
    d.rectangle([0, 0, S - 1, S - 1], outline=FLOOR_LINE + (255,), width=1)
    # Заклёпки по углам плиты.
    for cx, cy in ((5, 5), (S - 6, 5), (5, S - 6), (S - 6, S - 6)):
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=FLOOR_LINE + (255,))
    img.save(path)


def belt_frame(path: str, phase: int, frames: int = 4) -> None:
    """Лента, едущая вправо. Остальные три направления — поворот при отрисовке.

    Планки сдвигаются на `phase/frames` шага: прокрутив кадры по кругу,
    получаем бесконечное движение без единого разрыва.
    """
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Полотно тянется от края до края клетки: соседние отрезки обязаны
    # смыкаться без шва, иначе линия конвейера выглядит рваной.
    top = 5
    bottom = S - 6
    d.rectangle([0, top, S - 1, bottom], fill=BELT_BODY + (255,))
    # Борта — сплошные полосы вдоль всей клетки, они и сшивают отрезки.
    d.rectangle([0, top, S - 1, top + 3], fill=BELT_EDGE + (255,))
    d.rectangle([0, bottom - 3, S - 1, bottom], fill=BELT_EDGE + (255,))

    step = 16
    offset = int(step * phase / frames)
    # Планки идут строго через `step` в мировых координатах, поэтому на стыке
    # двух клеток шаг не сбивается.
    x = -step + offset
    while x < S:
        d.rectangle([x, top + 4, x + 2, bottom - 4], fill=BELT_SLAT + (255,))
        cx = x + 8
        if 0 <= cx <= S - 7:
            # Янтарный шеврон показывает направление даже на стоящей ленте.
            d.polygon([(cx, S // 2 - 6), (cx + 6, S // 2), (cx, S // 2 + 6)],
                      fill=AMBER + (255,))
        x += step
    img.save(path)


if __name__ == "__main__":
    import os
    os.makedirs(OUT, exist_ok=True)
    floor_tile(f"{OUT}/tile_floor.png")
    for phase in range(4):
        belt_frame(f"{OUT}/tile_belt_{phase}.png", phase)
    print("готово:", os.listdir(OUT))
