# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

"""Writes the Assemblers' casings, `assembler_1` to `assembler_3`, and the rings of their Fluid Connections,
`assembler_connections`. The casings' sprites are Malcolm Riley's, unmodified (NOTICE).

A machine's origin draws the whole footprint, which is wider and taller than one block. Minecraft refuses an
element whose 'from' or 'to' is below -16 or above 32 (CuboidModelElement), a block and a half either way from the
one block the model is placed on, so a footprint more than three blocks wide or tall can't be one list of
elements. Such a model is a NeoForge composite (`neoforge:composite`): children that each stay inside those
bounds, each placed by a `transform` translation of whole blocks, which NeoForge applies when it bakes, after the
bounds were checked and before the blockstate's rotation. A footprint that fits is written as one plain model.

Coordinates are in sixteenths of a block, with the origin block at 0..16 and the machine facing north: north is
negative z, and a connection's `right` is positive x.

Run it from anywhere: python3 scripts/build-machine-models.py
"""

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "craftworks", "models", "block")

LOW, HIGH = -16, 32  # the bounds Minecraft allows an element's from and to


def _face(texture):
    return {"uv": [0, 0, 16, 16], "texture": texture}


def _shift(lo, hi):
    """The whole blocks to translate by, so that lo..hi, less that, is inside the bounds."""
    for blocks in (0, -1, 1, -2, 2):
        if lo - 16 * blocks >= LOW and hi - 16 * blocks <= HIGH:
            return blocks
    raise ValueError("no translation brings %s..%s inside %s..%s" % (lo, hi, LOW, HIGH))


def _number(value):
    value = round(value, 4)
    return int(value) if value == int(value) else value


def _group(elements, textures, ambient_occlusion, particle):
    """The model of these elements ({'from', 'to', 'faces'}, in the machine's own frame): plain if they fit as
    they are, else a composite of children translated into bounds."""
    children = {}
    for element in elements:
        shift = tuple(_shift(element["from"][axis], element["to"][axis]) for axis in range(3))
        placed = {
            "from": [_number(element["from"][axis] - 16 * shift[axis]) for axis in range(3)],
            "to": [_number(element["to"][axis] - 16 * shift[axis]) for axis in range(3)],
            "faces": element["faces"],
        }
        children.setdefault(shift, []).append(placed)
    top = {"ambientocclusion": ambient_occlusion, "textures": dict(textures, particle=particle)}
    if list(children) == [(0, 0, 0)]:
        top["elements"] = children[(0, 0, 0)]
        return top
    top["loader"] = "neoforge:composite"
    top["children"] = {}
    for shift in sorted(children):
        top["children"]["part_%d_%d_%d" % shift] = {
            "textures": dict(textures),
            "transform": {"translation": list(shift)},
            "elements": children[shift],
        }
    return top


def rings(sites):
    """The grey ring of each connection: (x, z, side) is the footprint block, in blocks from the origin, and the
    side its face points, as the machine faces north."""
    elements = []
    for x, z, side in sites:
        if side in ("north", "south"):
            plane = 16 * z if side == "north" else 16 * z + 16
            box = ([16 * x, 0, plane], [16 * x + 16, 16, plane])
        else:
            plane = 16 * x if side == "west" else 16 * x + 16
            box = ([plane, 0, 16 * z], [plane, 16, 16 * z + 16])
        elements.append({"from": box[0], "to": box[1], "faces": {side: _face("#ring")}})
    ring = "craftworks:block/fluid_connection"
    return _group(elements, {"ring": ring}, False, ring)


def casing(tier):
    """An Assembler's 3x2x3 casing, one element per footprint block with only its outward faces: plating, braced
    at the top's corners and vented at its sides; the tier's band round the upper row and across the top; and the
    crafting grid at the upper centre of the front (north), at eye height; the blockstate turns it to each facing. The lower row is left plain plating,
    for the Fluid Connections' rings."""
    elements = []
    for x in (-1, 0, 1):
        for y in (0, 1):
            for z in (-1, 0, 1):
                faces = {}
                if y == 0:
                    faces["down"] = _face("#plating")
                else:
                    corner = x != 0 and z != 0
                    faces["up"] = _face("#brace" if corner else "#band_vertical" if x == 0 else "#vent")
                side = "#band" if y == 1 else "#inset"
                for outward, name in ((x == -1, "west"), (x == 1, "east"), (z == -1, "north"), (z == 1, "south")):
                    if outward:
                        faces[name] = _face(side)
                if (x, y, z) == (0, 1, -1):
                    faces["north"] = _face("#front")
                elements.append({"from": [16 * x, 16 * y, 16 * z], "to": [16 * x + 16, 16 * y + 16, 16 * z + 16],
                                 "faces": faces})
    plating, assembler = "craftworks:block/plating/", "craftworks:block/assembler/"
    textures = {
        "plating": plating + "plain",
        "inset": plating + "inset",
        "brace": plating + "brace",
        "vent": plating + "vent",
        "front": assembler + "front",
        "band": assembler + "band_%d" % tier,
        "band_vertical": assembler + "band_%d_vertical" % tier,
    }
    return _group(elements, textures, False, textures["band"])


MODELS_TO_WRITE = {
    "assembler_1": casing(1),
    "assembler_2": casing(2),
    "assembler_3": casing(3),
    # Every Assembler tier, 3x3, spaced so no two pipes beside them touch: the two ends of the edge it faces
    # (north) and of the other, and the centre of each side. The layout is symmetric under a half turn, so the
    # blockstate's two rotations cover the four facings.
    "assembler_connections": rings([(-1, -1, "north"), (1, -1, "north"),
                                    (-1, 1, "south"), (1, 1, "south"),
                                    (-1, 0, "west"), (1, 0, "east")]),
}


def main():
    for name, model in MODELS_TO_WRITE.items():
        path = os.path.join(MODELS, name + ".json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(model, handle, indent=2)
            handle.write("\n")
        print("wrote " + os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
