# A Refiner smelting raw iron, powered by a Creative Energy Source beside it.
function craftworks:gallery/clear

setblock ~8 ~ ~-6 craftworks:refiner[facing=north]{items:{stacks:[{id:"minecraft:raw_iron",count:64}]}} strict
setblock ~9 ~ ~-6 craftworks:creative_energy_source
function craftworks:gallery/sign {scene:"refiner"}
