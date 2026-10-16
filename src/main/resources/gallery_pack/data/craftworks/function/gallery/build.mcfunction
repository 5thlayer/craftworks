# Builds each scene in README order, the first where the player stands and each further one 24 blocks east (libworks ADR 0010).
function craftworks:gallery/ask_for_the_item
execute positioned ~24 ~ ~ run function craftworks:gallery/paid_up_front
execute positioned ~48 ~ ~ run function craftworks:gallery/the_inventory
execute positioned ~72 ~ ~ run function craftworks:gallery/crafting_plan
execute positioned ~96 ~ ~ run function craftworks:gallery/cancel
execute positioned ~120 ~ ~ run function craftworks:gallery/nothing_is_lost
execute positioned ~144 ~ ~ run function craftworks:gallery/assembler_1
execute positioned ~168 ~ ~ run function craftworks:gallery/assembler_2
execute positioned ~192 ~ ~ run function craftworks:gallery/assembler_3
execute positioned ~216 ~ ~ run function craftworks:gallery/refiner
