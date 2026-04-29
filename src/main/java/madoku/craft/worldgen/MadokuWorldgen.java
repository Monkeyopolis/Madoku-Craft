package madoku.craft.worldgen;

import madoku.craft.MadokuCraft;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class MadokuWorldgen {
	private static final ResourceKey<PlacedFeature> NETHER_IRON_ORE_PLACED_FEATURE = ResourceKey.create(
		Registries.PLACED_FEATURE,
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "ore_nether_iron")
	);
	private static final ResourceKey<PlacedFeature> NETHER_IRON_ORE_DELTAS_PLACED_FEATURE = ResourceKey.create(
		Registries.PLACED_FEATURE,
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "ore_nether_iron_deltas")
	);

	private MadokuWorldgen() {
	}

	public static void initialize() {
		BiomeModifications.addFeature(
			BiomeSelectors.foundInTheNether().and(BiomeSelectors.excludeByKey(Biomes.BASALT_DELTAS)),
			GenerationStep.Decoration.UNDERGROUND_ORES,
			NETHER_IRON_ORE_PLACED_FEATURE
		);
		BiomeModifications.addFeature(
			BiomeSelectors.includeByKey(Biomes.BASALT_DELTAS),
			GenerationStep.Decoration.UNDERGROUND_ORES,
			NETHER_IRON_ORE_DELTAS_PLACED_FEATURE
		);
	}
}
