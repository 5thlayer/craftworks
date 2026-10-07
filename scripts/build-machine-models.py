# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

"""Writes the stand-in block models of the fluid machines: a casing over the footprint and the rings of its Fluid
Connections, and the rings of the Assemblers' (whose casings are written by hand).

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
INSET = 0.02  # an outer face sits this far in, so a ring laid on it doesn't z-fight


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


def casing(width, height, texture_prefix):
    """The casing of a footprint width x width on the ground and height blocks tall: the faces on its outside, one
    block each, so the texture tiles."""
    half = width // 2
    elements = []
    for y in range(height):
        for x in range(-half, half + 1):
            for z in range(-half, half + 1):
                faces = {}
                if y == 0:
                    faces["down"] = _face("#bottom")
                if y == height - 1:
                    faces["up"] = _face("#top")
                if x == -half:
                    faces["west"] = _face("#side")
                if x == half:
                    faces["east"] = _face("#side")
                if z == -half:
                    faces["north"] = _face("#side")
                if z == half:
                    faces["south"] = _face("#side")
                if not faces:
                    continue
                elements.append({
                    "from": [16 * x + (INSET if x == -half else 0), 16 * y, 16 * z + (INSET if z == -half else 0)],
                    "to": [16 * x + 16 - (INSET if x == half else 0), 16 * y + 16, 16 * z + 16 - (INSET if z == half else 0)],
                    "faces": faces,
                })
    textures = {
        "side": "craftworks:block/%s_side" % texture_prefix,
        "top": "craftworks:block/%s_top" % texture_prefix,
        "bottom": "craftworks:block/%s_bottom" % texture_prefix,
    }
    return _group(elements, textures, False, textures["side"])


def rings(sites):
    """The grey ring of each connection: (x, z, side) is the footprint block, in blocks from the origin, and the
    side its face points, 'north' or 'south', as the machine faces north."""
    elements = []
    for x, z, side in sites:
        plane = 16 * z if side == "north" else 16 * z + 16
        elements.append({"from": [16 * x, 0, plane], "to": [16 * x + 16, 16, plane], "faces": {side: _face("#ring")}})
    ring = "craftworks:block/fluid_connection"
    return _group(elements, {"ring": ring}, False, ring)


MODELS_TO_WRITE = {
    # The Assemblers of tiers 2 and 3, 3x3: a connection on each of the three bottom-layer blocks of the two opposite
    # edges, the edge it faces (north) and the other. The casings are hand-written and not made here.
    "assembler_connections": rings([(-1, -1, "north"), (0, -1, "north"), (1, -1, "north"),
                                    (-1, 1, "south"), (0, 1, "south"), (1, 1, "south")]),
    # Factorio's chemical plant: 3x3, two tall; a connection at each end of the two opposite edges.
    "chemical_plant": casing(3, 2, "chemical_plant"),
    "chemical_plant_connections": rings([(-1, -1, "north"), (-1, 1, "south"), (1, -1, "north"), (1, 1, "south")]),
    # Factorio's oil refinery: 5x5, three tall; two connections on the edge it faces (north) and three on the other.
    "oil_refinery": casing(5, 3, "oil_refinery"),
    "oil_refinery_connections": rings([(-1, -2, "north"), (1, -2, "north"), (-2, 2, "south"), (0, 2, "south"), (2, 2, "south")]),
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
