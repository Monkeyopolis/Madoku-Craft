package madoku.craft.recipe.system;

import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MadokuRecipeDefaults {
	private MadokuRecipeDefaults() {
	}

	public static Set<String> buildAddedSmokingInputs() {
		Set<String> defaults = new LinkedHashSet<>();
		defaults.add("minecraft:cactus");
		defaults.add("minecraft:sea_pickle");
		defaults.add("minecraft:chorus_fruit");
		defaults.add("minecraft:oak_log");
		defaults.add("minecraft:spruce_log");
		defaults.add("minecraft:birch_log");
		defaults.add("minecraft:jungle_log");
		defaults.add("minecraft:acacia_log");
		defaults.add("minecraft:cherry_log");
		defaults.add("minecraft:dark_oak_log");
		defaults.add("minecraft:mangrove_log");
		defaults.add("minecraft:pale_oak_log");
		defaults.add("minecraft:stripped_oak_log");
		defaults.add("minecraft:stripped_spruce_log");
		defaults.add("minecraft:stripped_birch_log");
		defaults.add("minecraft:stripped_jungle_log");
		defaults.add("minecraft:stripped_acacia_log");
		defaults.add("minecraft:stripped_cherry_log");
		defaults.add("minecraft:stripped_dark_oak_log");
		defaults.add("minecraft:stripped_mangrove_log");
		defaults.add("minecraft:stripped_pale_oak_log");
		defaults.add("minecraft:crimson_stem");
		defaults.add("minecraft:warped_stem");
		defaults.add("minecraft:stripped_crimson_stem");
		defaults.add("minecraft:stripped_warped_stem");
		defaults.add("minecraft:oak_wood");
		defaults.add("minecraft:spruce_wood");
		defaults.add("minecraft:birch_wood");
		defaults.add("minecraft:jungle_wood");
		defaults.add("minecraft:acacia_wood");
		defaults.add("minecraft:cherry_wood");
		defaults.add("minecraft:pale_oak_wood");
		defaults.add("minecraft:dark_oak_wood");
		defaults.add("minecraft:mangrove_wood");
		defaults.add("minecraft:stripped_oak_wood");
		defaults.add("minecraft:stripped_spruce_wood");
		defaults.add("minecraft:stripped_birch_wood");
		defaults.add("minecraft:stripped_jungle_wood");
		defaults.add("minecraft:stripped_acacia_wood");
		defaults.add("minecraft:stripped_cherry_wood");
		defaults.add("minecraft:stripped_pale_oak_wood");
		defaults.add("minecraft:stripped_dark_oak_wood");
		defaults.add("minecraft:stripped_mangrove_wood");
		defaults.add("minecraft:crimson_hyphae");
		defaults.add("minecraft:warped_hyphae");
		defaults.add("minecraft:stripped_crimson_hyphae");
		defaults.add("minecraft:stripped_warped_hyphae");
		return Set.copyOf(defaults);
	}

	public static Set<String> buildAddedBlastingInputs() {
		Set<String> defaults = new LinkedHashSet<>();
		defaults.add("minecraft:netherrack");
		defaults.add("minecraft:clay_ball");
		defaults.add("minecraft:wet_sponge");
		defaults.add("minecraft:sand");
		defaults.add("minecraft:red_sand");
		defaults.add("minecraft:clay");
		defaults.add("minecraft:quartz_block");
		defaults.add("minecraft:basalt");
		defaults.add("minecraft:nether_bricks");
		defaults.add("minecraft:polished_blackstone_bricks");
		defaults.add("minecraft:red_sandstone");
		defaults.add("minecraft:sandstone");
		defaults.add("minecraft:deepslate_tiles");
		defaults.add("minecraft:cobbled_deepslate");
		defaults.add("minecraft:deepslate_bricks");
		defaults.add("minecraft:stone_bricks");
		defaults.add("minecraft:stone");
		defaults.add("minecraft:cobblestone");
		defaults.add("minecraft:terracotta");
		defaults.add("minecraft:white_terracotta");
		defaults.add("minecraft:orange_terracotta");
		defaults.add("minecraft:magenta_terracotta");
		defaults.add("minecraft:light_blue_terracotta");
		defaults.add("minecraft:yellow_terracotta");
		defaults.add("minecraft:lime_terracotta");
		defaults.add("minecraft:pink_terracotta");
		defaults.add("minecraft:gray_terracotta");
		defaults.add("minecraft:light_gray_terracotta");
		defaults.add("minecraft:cyan_terracotta");
		defaults.add("minecraft:purple_terracotta");
		defaults.add("minecraft:blue_terracotta");
		defaults.add("minecraft:brown_terracotta");
		defaults.add("minecraft:green_terracotta");
		defaults.add("minecraft:red_terracotta");
		defaults.add("minecraft:black_terracotta");
		return Set.copyOf(defaults);
	}

	public static List<AddedCookingRecipe> buildAddedCookingRecipes() {
		List<AddedCookingRecipe> defaults = new ArrayList<>();
		defaults.add(new AddedCookingRecipe(
			"minecraft:soul_sand",
			"minecraft:tinted_glass",
			RecipeType.SMELTING,
			0.1F,
			200
		));
		defaults.add(new AddedCookingRecipe(
			"minecraft:soul_sand",
			"minecraft:tinted_glass",
			RecipeType.BLASTING,
			0.1F,
			100
		));
		return List.copyOf(defaults);
	}

	public record AddedCookingRecipe(
		String inputItemId,
		String resultItemId,
		RecipeType<?> recipeType,
		float experience,
		int cookingTime
	) {
	}
}
