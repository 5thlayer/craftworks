// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

// Craftworks game tests only (#13): copied into the game tests' run, never the client's or a pack's.
// gametest/KubeJSTests names its ids: change them together.

ServerEvents.recipes(event => {
  // A plain crafting recipe, as another mod would ship one: Craftworks converts it as the recipes load.
  event.shapeless('minecraft:emerald', ['minecraft:dirt', 'minecraft:dirt']).id('craftworks:kubejs_gametest/emerald')

  event.recipes.craftworks.assembling('9x minecraft:gold_nugget', ['minecraft:gold_ingot'])
    .fluidIngredients([Fluid.of('minecraft:water', 250)])
    .fluidResults([Fluid.of('minecraft:lava', 50)])
    .handCraftable(false)
    .id('craftworks:kubejs_gametest/fluid')
})
