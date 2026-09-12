## Overview:

Madoku Craft is an interconnected overhaul mod that modifies vanilla Minecraft.
This mod adds seasons, an ecosystem, pets, and a leveling system.
It also modifies items, attributes, recipes, loot, smithing, enchanting and mobs.
All of these systems can be adjusted in the config files.

## Development layout:

The root project is the Unified aggregate build. The feature directories under
`modules/` remain independent Gradle builds, each using Java 21, official Mojang
mappings, and its own Loom cache.

Unified can be launched from the repository root:

```powershell
.\gradlew.bat --no-daemon --no-parallel runClient
```

Compat consumes the independently published module artifacts. Publish Core
first, then the feature modules, before launching Compat from `modules/compat`:

```powershell
$modules = @('core','ecosystem','attributes','levels','farming','mobs','pets','items','utility','hud')
foreach ($module in $modules) {
    .\gradlew.bat --no-daemon --no-parallel --max-workers=1 -p ("modules/" + $module) publishToMavenLocal
}
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 -p modules/compat runClient
```

The Compat build uses `mavenLocal()` by default. Sibling composite builds are
available for dependency development with `-Puse_composite_modules=true`.

## Main Features:

**Mobs:**

- The mob system modifies mob behaviors and scales their stats based on regional difficulty and world difficulty.
- Regional Difficulty is determined by the biome, structure, and in-game time when a mob spawns.

**Items:**

- The item system modifies item stats and properities based on their category.
- This allows you to modify their stacking limit, damage, mining speed, etc.

**Attributes:**

- The attribute system modifies attributes to rebalance the game.
- This allows you to customize the game's difficulty to your needs.

**Levels:**

- The levels system allows players to allocate points to increase certain stats.
- Press K to open up the Menu.
- This keybind can be customized in the options menu.

**Pets:**

- The pet system allows players to equip pet items in the player's inventory.
- This spawns a tiny mob that helps and follows you without getting in the way.

**Core:**

- The core system adds seasonal changes, and rarity.
- It also modifies loot tables, recipes, enchanting, and smithing.

**Utility:**

- The utility system modifies smelting and in-game music.
- It allows you to configure how fast smelting occurs.
- It also allows you to customize which in-game music plays and how often.

## Disclaimer:

- Make sure to backup your worlds before using this mod.
- This mod severely modifies Minecraft and might cause unintended issues if removed.
