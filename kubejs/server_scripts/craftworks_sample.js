// Craftworks with KubeJS (#11): a sample the dev runs load with -PwithKubeJS, and the game tests check
// (gametest/KubeJSTests names its ids, tag and reason: change them together).

ServerEvents.recipes(event => {
  // A new Assembling recipe: ingredients are counted, time is ticks per craft, priority is the route's.
  event.recipes.craftworks.assembling('2x minecraft:diamond', ['minecraft:dirt', '3x minecraft:cobblestone'])
    .time(40)
    .priority(5)
    .id('craftworks:kubejs_sample/diamond')

  // An existing one edited in place: the built-in vanilla pack's stick takes twice as long.
  event.forEachRecipe({ type: 'craftworks:assembling', id: 'minecraft:stick' }, recipe => recipe.set('time', 20))

  // A plain crafting recipe, as another mod would ship one: Craftworks converts it as the recipes load.
  event.shapeless('minecraft:emerald', ['minecraft:dirt', 'minecraft:dirt']).id('craftworks:kubejs_sample/emerald')
})

// Locks the sample diamond for any player tagged craftworks.kubejs_locked, saying why.
// In game: /tag @s add craftworks.kubejs_locked, then ask the Assembler for a diamond.
CraftworksEvents.lock(event => {
  if (String(event.recipe) === 'craftworks:kubejs_sample/diamond' && event.player.entityTags().contains('craftworks.kubejs_locked')) {
    event.lock('Sample: untag yourself to craft this')
  }
})
