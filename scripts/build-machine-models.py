# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

"""Writes the rings of the Assemblers' Fluid Connections, `assembler_connections` (their casings are written by hand).

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


MODELS_TO_WRITE = {
    # Every Assembler tier, 3x3, spaced so no two pipes beside them touch: the two ends of the edge it faces
    # (north) and of the other, and the centre of each side. The layout is symmetric under a half turn, so the
    # blockstate's two rotations cover the four facings. The casings are hand-written and not made here.
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
