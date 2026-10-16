# A Refiner smelting raw iron, powered by a Creative Energy Source beside it.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:air

setblock ~8 ~ ~-6 craftworks:refiner[facing=north]{items:{stacks:[{id:"minecraft:raw_iron",count:64}]}} strict
setblock ~9 ~ ~-6 craftworks:creative_energy_source
setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"craftworks.gallery.refiner"},{translate:"craftworks.gallery.refiner.1"},{translate:"craftworks.gallery.refiner.2"},{translate:"craftworks.gallery.refiner.3"}]}}
