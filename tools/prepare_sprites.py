"""Подготовка сгенерированных спрайтов машин к использованию в игре.

Модель отдаёт большую картинку на сплошном пурпурном фоне. Здесь фон
вырезается по цветовому ключу, спрайт обрезается по содержимому, дополняется
до квадрата и уменьшается до 128 пикселей — размера, в котором клетка поля
рисуется на телефоне. Все шаги детерминированные: пересобрать ассеты можно
в любой момент одной командой.
"""
from PIL import Image
import os

SRC = "/home/user/assets_raw"
OUT = "app/src/main/res/drawable-nodpi"
SIZE = 128
# Порог по «пурпурности»: фон #FF00FF, у спрайта таких пикселей нет.
KEY_TOLERANCE = 90

NAMES = {
    "spawner": "machine_spawner",
    "smelter": "machine_smelter",
    "press": "machine_press",
    "wire_drawer": "machine_wire_drawer",
    "assembler": "machine_assembler",
    "quality_gate": "machine_quality_gate",
    "exporter": "machine_exporter",
}


def is_background(r: int, g: int, b: int) -> bool:
    return r > 255 - KEY_TOLERANCE and b > 255 - KEY_TOLERANCE and g < KEY_TOLERANCE


def prepare(src_path: str, dst_path: str) -> None:
    img = Image.open(src_path).convert("RGBA")
    pixels = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = pixels[x, y]
            if is_background(r, g, b):
                pixels[x, y] = (0, 0, 0, 0)

    bbox = img.getbbox()
    if bbox is None:
        raise SystemExit("Пустой спрайт: " + src_path)
    sprite = img.crop(bbox)

    # Квадрат с полем в 4 %: иначе спрайт упрётся в границы клетки.
    side = int(max(sprite.width, sprite.height) * 1.08)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(sprite, ((side - sprite.width) // 2, (side - sprite.height) // 2))

    canvas.resize((SIZE, SIZE), Image.LANCZOS).save(dst_path)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for raw, name in NAMES.items():
        prepare(f"{SRC}/{raw}.png", f"{OUT}/{name}.png")
        print("готов", name)
