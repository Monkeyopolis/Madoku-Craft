## Madoku Craft: Compat

Compat provides optional bridges between Madoku modules and external mods.
This includes the Attributes-to-HUD presentation bridge: when both the HUD
and Attributes modules are loaded, Compat installs the custom health, hunger,
armor, oxygen, and luck bars. Neither HUD nor Core depends on Compat.

Compat also connects Items fuel categories to Utility smelting. This bridge
is active only when Core, Items, and Utility are installed together.
