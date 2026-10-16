# A lectern at a stand, and the sign that says how to make this happen with the Personal Assembler, which every player has.
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:air

setblock ~13 ~ ~-2 minecraft:lectern[facing=south]
setblock ~13 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"craftworks.gallery.nothing_is_lost"},{translate:"craftworks.gallery.nothing_is_lost.1"},{translate:"craftworks.gallery.nothing_is_lost.2"},{translate:"craftworks.gallery.nothing_is_lost.3"}]}}
