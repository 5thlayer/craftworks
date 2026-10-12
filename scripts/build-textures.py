#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Builds sprites from Malcolm Riley's unused textures in data/art/ (NOTICE).

The Refiner's front, cold and lit, is the smelter's front let into the Assemblers' plating: the brick
showing round the smelter's rim, in its top two rows and the outer two columns of its upper half, is painted
over with the plain plating, so the iron firebox and grate sit in a plated casing like the Assemblers'.
Which pixels are brick is read from the cold front: the lit one's glow warms its iron to a brick's red.
Every frame of the lit smelter's animation is built, and its animation copied beside the sprite.

Run from the repository root; it needs Pillow.
"""

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "data" / "art"
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "craftworks" / "textures"

# The smelter's rim, where its brick shows: rows down to RIM_TOP, and the outer RIM_SIDE columns on each
# side down to RIM_UPPER, the bottom of the firebox; below it the grate's iron reaches the edge.
RIM_TOP = 1
RIM_SIDE = 2
RIM_UPPER = 7
# How much redder than blue a pixel is to be the smelter's brick, against the grey of its cold iron.
BRICK_RED = 18


def frames(name):
    image = Image.open(ART / f"{name}.png").convert("RGBA")
    # An animated sprite's frames stack downwards.
    size = image.width
    return [image.crop((0, y, size, y + size)) for y in range(0, image.height, size)]


def stack(images):
    strip = Image.new("RGBA", (16, 16 * len(images)))
    for i, image in enumerate(images):
        strip.paste(image, (0, 16 * i))
    return strip


def save(image, path):
    image.save(path)
    print(f"wrote {path.relative_to(ROOT)}")


def on_rim(x, y):
    return y <= RIM_TOP or (y <= RIM_UPPER and (x < RIM_SIDE or x >= 16 - RIM_SIDE))


def brick(pixel):
    r, g, b, _ = pixel
    return r > g and r - b >= BRICK_RED


def brick_on_rim():
    cold = frames("device_smelter_front")[0].load()
    return [(x, y) for y in range(16) for x in range(16) if on_rim(x, y) and brick(cold[x, y])]


def refiner_front(smelter, face):
    plating = frames("space-plating-plain")[0].load()
    rim = brick_on_rim()
    built = []
    for frame in frames(smelter):
        pixels = frame.load()
        for x, y in rim:
            pixels[x, y] = plating[x, y]
        built.append(frame)
    path = TEXTURES / "block" / "refiner" / f"{face}.png"
    save(stack(built), path)
    animation = ART / f"{smelter}.png.mcmeta"
    if animation.exists():
        shutil.copyfile(animation, f"{path}.mcmeta")


def main():
    refiner_front("device_smelter_front", "front_off")
    refiner_front("device_smelter_front_lit", "front_on")


if __name__ == "__main__":
    main()
