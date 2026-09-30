// Craftworks game tests only (#13): copied into the game tests' run, never the client's or a pack's.
// gametest/KubeJSTests names its id: change them together.

ServerEvents.recipes(event => {
  // A plain crafting recipe, as another mod would ship one: Craftworks converts it as the recipes load.
  event.shapeless('minecraft:emerald', ['minecraft:dirt', 'minecraft:dirt']).id('craftworks:kubejs_gametest/emerald')
})
