# A tier 2 Assembler holding minecraft:stick, fed by hand and powered by a Creative Energy Source beside it.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:air

setblock ~8 ~ ~-6 craftworks:assembler_2[facing=north]{held_recipe:"minecraft:stick",items:{stacks:[{id:"minecraft:oak_planks",count:64}]}} strict
setblock ~7 ~0 ~-7 craftworks:assembler_2_part[facing=north,part=1] strict
setblock ~8 ~0 ~-7 craftworks:assembler_2_part[facing=north,part=2] strict
setblock ~9 ~0 ~-7 craftworks:assembler_2_part[facing=north,part=3] strict
setblock ~7 ~0 ~-6 craftworks:assembler_2_part[facing=north,part=4] strict
setblock ~9 ~0 ~-6 craftworks:assembler_2_part[facing=north,part=5] strict
setblock ~7 ~0 ~-5 craftworks:assembler_2_part[facing=north,part=6] strict
setblock ~8 ~0 ~-5 craftworks:assembler_2_part[facing=north,part=7] strict
setblock ~9 ~0 ~-5 craftworks:assembler_2_part[facing=north,part=8] strict
setblock ~7 ~1 ~-7 craftworks:assembler_2_part[facing=north,part=9] strict
setblock ~8 ~1 ~-7 craftworks:assembler_2_part[facing=north,part=10] strict
setblock ~9 ~1 ~-7 craftworks:assembler_2_part[facing=north,part=11] strict
setblock ~7 ~1 ~-6 craftworks:assembler_2_part[facing=north,part=12] strict
setblock ~8 ~1 ~-6 craftworks:assembler_2_part[facing=north,part=13] strict
setblock ~9 ~1 ~-6 craftworks:assembler_2_part[facing=north,part=14] strict
setblock ~7 ~1 ~-5 craftworks:assembler_2_part[facing=north,part=15] strict
setblock ~8 ~1 ~-5 craftworks:assembler_2_part[facing=north,part=16] strict
setblock ~9 ~1 ~-5 craftworks:assembler_2_part[facing=north,part=17] strict
setblock ~10 ~ ~-6 craftworks:creative_energy_source
setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"craftworks.gallery.assembler_2"},{translate:"craftworks.gallery.assembler_2.1"},{translate:"craftworks.gallery.assembler_2.2"},{translate:"craftworks.gallery.assembler_2.3"}]}}
