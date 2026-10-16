# The Craftworks Gallery: every scene of the Mod's features in README order, each building and clearing a
# plot of its own, 24 blocks wide and 13 deep. The scenes stand in rows of three, read west to east and then
# north, each row 16 blocks north of the one before, so the whole gallery stays near enough to where it is
# run for its machines to keep working (libworks ADR 0010).
function craftworks:gallery/ask_for_the_item
execute positioned ~24 ~ ~ run function craftworks:gallery/paid_up_front
execute positioned ~48 ~ ~ run function craftworks:gallery/the_inventory
execute positioned ~ ~ ~-16 run function craftworks:gallery/crafting_plan
execute positioned ~24 ~ ~-16 run function craftworks:gallery/cancel
execute positioned ~48 ~ ~-16 run function craftworks:gallery/nothing_is_lost
execute positioned ~ ~ ~-32 run function craftworks:gallery/assembler_1
execute positioned ~24 ~ ~-32 run function craftworks:gallery/assembler_2
execute positioned ~48 ~ ~-32 run function craftworks:gallery/assembler_3
execute positioned ~ ~ ~-48 run function craftworks:gallery/refiner
