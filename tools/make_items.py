"""Спрайты предметов: по одному на форму, а не на каждый предмет.

Рисуются белыми с готовой светотенью и тонируются в игре цветом из
`ItemCatalog`. Так четыре файла закрывают все четырнадцать предметов и,
что важнее, новый предмет в каталоге не требует нового ассета — иначе
художник становился бы блокером для баланса.
"""
from PIL import Image, ImageDraw

S = 64
OUT = "app/src/main/res/drawable-nodpi"

LIGHT = (255, 255, 255, 255)
MID = (205, 205, 205, 255)
DARK = (150, 150, 150, 255)
EDGE = (60, 60, 60, 255)


def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def chunk(path):
    """Кусок руды: неровный шестиугольник."""
    img = new(); d = ImageDraw.Draw(img)
    poly = [(32, 6), (54, 20), (52, 46), (30, 58), (10, 44), (12, 18)]
    d.polygon(poly, fill=MID, outline=EDGE)
    d.polygon([(32, 6), (54, 20), (34, 30), (12, 18)], fill=LIGHT)
    d.polygon([(34, 30), (52, 46), (30, 58)], fill=DARK)
    img.save(path)


def ingot(path):
    """Слиток: трапеция с бликом сверху."""
    img = new(); d = ImageDraw.Draw(img)
    d.polygon([(12, 20), (52, 20), (58, 46), (6, 46)], fill=MID, outline=EDGE)
    d.polygon([(12, 20), (52, 20), (54, 28), (10, 28)], fill=LIGHT)
    d.line([(6, 46), (58, 46)], fill=EDGE, width=2)
    img.save(path)


def coil(path):
    """Моток: круг с витками."""
    img = new(); d = ImageDraw.Draw(img)
    d.ellipse([6, 6, 58, 58], fill=MID, outline=EDGE, width=2)
    d.ellipse([14, 14, 50, 50], outline=DARK, width=3)
    d.ellipse([24, 24, 40, 40], fill=DARK, outline=EDGE)
    d.arc([8, 8, 56, 56], start=200, end=320, fill=LIGHT, width=4)
    img.save(path)


def part(path):
    """Деталь: шестерня — узнаётся мгновенно даже в 20 пикселей."""
    img = new(); d = ImageDraw.Draw(img)
    for angle in range(0, 360, 45):
        import math
        a = math.radians(angle)
        cx, cy = 32 + 22 * math.cos(a), 32 + 22 * math.sin(a)
        d.rectangle([cx - 7, cy - 7, cx + 7, cy + 7], fill=MID, outline=EDGE)
    d.ellipse([10, 10, 54, 54], fill=MID, outline=EDGE, width=2)
    d.arc([12, 12, 52, 52], start=190, end=330, fill=LIGHT, width=4)
    d.ellipse([24, 24, 40, 40], fill=(0, 0, 0, 0), outline=EDGE, width=3)
    img.save(path)


if __name__ == "__main__":
    chunk(f"{OUT}/item_chunk.png")
    ingot(f"{OUT}/item_ingot.png")
    coil(f"{OUT}/item_coil.png")
    part(f"{OUT}/item_part.png")
    print("готово")
