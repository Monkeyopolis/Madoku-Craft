package madoku.craft.recipe.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MadokuRecipe {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuRecipe.class);
	private static final String SMITHING_TRIM_PATTERN_FIELD_NAME = "pattern";
	private static final String SHAPELESS_INGREDIENTS_FIELD_NAME = "ingredients";

	private static final String RECIPE_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-recipes";
	private static final String RECIPE_CONFIG_SETTINGS_FILE_NAME = "madoku-recipes";
	private static final String RECIPE_CONFIG_RECIPES_FOLDER_NAME = "madoku-recipes";
	private static final String MADOKU_RECIPE_NAMESPACE = "madoku-craft";
	private static final String EMPTY_SLOT_SYMBOL = "-";
	private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private MadokuRecipe() {
	}

	public static void initialize() {
		// Warm-up defaults so the settings file is always present.
		loadSystemEnabled();
	}

	public static List<RecipeHolder<?>> applyRecipeOverrides(Iterable<RecipeHolder<?>> loadedRecipes) {
		List<RecipeHolder<?>> source = copyRecipes(loadedRecipes);
		if (source.isEmpty()) {
			return source;
		}

		boolean systemEnabled = loadSystemEnabled();
		if (!systemEnabled) {
			return source;
		}
		source = addMadokuDefaultCookingRecipes(source);

		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(RECIPE_CONFIG_ROOT_FOLDER_NAME);
			Path recipesDirectory = rootDirectory.resolve(RECIPE_CONFIG_RECIPES_FOLDER_NAME);

			List<RecipeDescriptor> descriptors = collectDescriptors(source);
			Map<String, RecipeDescriptor> descriptorByRecipeId = new HashMap<>();
			Map<String, JsonObject> defaultsByFileKey = new HashMap<>();
			Set<Path> discoveredFolders = new LinkedHashSet<>();
			Map<Path, Map<String, JsonObject>> defaultsByFolder = new LinkedHashMap<>();
			Map<Path, Set<String>> keysByFolder = new LinkedHashMap<>();
			for (RecipeDescriptor descriptor : descriptors) {
				String normalizedRecipeId = normalizeRecipeId(descriptor.recipeId());
				if (!normalizedRecipeId.isBlank()) {
					descriptorByRecipeId.put(normalizedRecipeId, descriptor);
				}

				Path folder = recipesDirectory.resolve(descriptor.folderPath());
				discoveredFolders.add(folder);
				defaultsByFileKey.putIfAbsent(descriptor.fileKey(), descriptor.defaultsRoot());

				if (!shouldGenerateManagedRecipeFile(descriptor.recipeId(), descriptor.defaultsRoot())) {
					continue;
				}
				defaultsByFolder.computeIfAbsent(folder, ignored -> new LinkedHashMap<>()).put(descriptor.fileKey(), descriptor.defaultsRoot());
				keysByFolder.computeIfAbsent(folder, ignored -> new LinkedHashSet<>()).add(descriptor.fileKey());
			}

			Map<String, JsonObject> normalizedByRecipeId = new HashMap<>();
			for (Path folder : discoveredFolders) {
				Map<String, JsonObject> staticDefaults = defaultsByFolder.getOrDefault(folder, Map.of());
				Set<String> validKeys = keysByFolder.getOrDefault(folder, Set.of());

				Map<String, JsonObject> normalized = DynamicStaticSystem.ensureManagedFolder(
					folder,
					staticDefaults,
					fileKey -> defaultsByFileKey.getOrDefault(fileKey, new JsonObject()),
					(fileKey, sourceRoot) -> isSupportedRecipeConfigFile(fileKey, sourceRoot, validKeys, descriptorByRecipeId),
					MadokuRecipe::copyDynamicEntry
				);

				for (JsonObject normalizedRoot : normalized.values()) {
					String recipeId = normalizeRecipeId(readString(normalizedRoot, MadokuRecipeConfig.FIELD_RECIPE_ID, ""));
					if (!recipeId.isBlank() && descriptorByRecipeId.containsKey(recipeId)) {
						normalizedByRecipeId.put(recipeId, normalizedRoot);
					}
				}
			}

			List<RecipeHolder<?>> resolved = new ArrayList<>(descriptors.size());
			for (RecipeDescriptor descriptor : descriptors) {
				JsonObject recipeRoot = normalizedByRecipeId.getOrDefault(descriptor.recipeId(), descriptor.defaultsRoot());
				if (!readBoolean(recipeRoot, MadokuRecipeConfig.FIELD_ENABLED, true)) {
					continue;
				}

				Recipe<?> overridden = buildRecipeOverride(descriptor, recipeRoot);
				if (overridden == null) {
					resolved.add(descriptor.holder());
					continue;
				}
				resolved.add(new RecipeHolder<>(descriptor.holder().id(), overridden));
			}

			return resolved;
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to apply MadokuRecipe config; using vanilla loaded recipes.", exception);
			return source;
		}
	}

	private static boolean shouldGenerateManagedRecipeFile(String recipeId, JsonObject defaultsRoot) {
		if (!readBoolean(defaultsRoot, MadokuRecipeConfig.FIELD_ENABLED, true)) {
			return true;
		}
		if (recipeId == null || recipeId.isBlank()) {
			return false;
		}
		var identifier = net.minecraft.resources.Identifier.tryParse(recipeId.trim().toLowerCase(Locale.ROOT));
		if (identifier == null) {
			return false;
		}
		return MADOKU_RECIPE_NAMESPACE.equals(identifier.getNamespace());
	}

	private static boolean isSupportedRecipeConfigFile(
		String fileKey,
		JsonObject sourceRoot,
		Set<String> validGeneratedKeys,
		Map<String, RecipeDescriptor> descriptorByRecipeId
	) {
		if (validGeneratedKeys != null && validGeneratedKeys.contains(fileKey)) {
			return true;
		}
		String recipeId = normalizeRecipeId(readString(sourceRoot, MadokuRecipeConfig.FIELD_RECIPE_ID, ""));
		if (recipeId.isBlank() || descriptorByRecipeId == null || !descriptorByRecipeId.containsKey(recipeId)) {
			return false;
		}
		return isUserModifiedRecipeConfig(sourceRoot);
	}

	private static boolean isUserModifiedRecipeConfig(JsonObject sourceRoot) {
		if (sourceRoot == null) {
			return false;
		}
		boolean enabled = readBoolean(sourceRoot, MadokuRecipeConfig.FIELD_ENABLED, true);
		boolean customRecipe = readBoolean(sourceRoot, MadokuRecipeConfig.FIELD_CUSTOM_RECIPE, false);
		return !enabled || customRecipe;
	}

	private static JsonElement copyDynamicEntry(String key, JsonElement sourceValue) {
		if (sourceValue == null || sourceValue.isJsonNull()) {
			return null;
		}
		return sourceValue.deepCopy();
	}

	private static String normalizeRecipeId(String recipeId) {
		if (recipeId == null || recipeId.isBlank()) {
			return "";
		}
		return recipeId.trim().toLowerCase(Locale.ROOT);
	}

	private static List<RecipeHolder<?>> addMadokuDefaultCookingRecipes(List<RecipeHolder<?>> source) {
		if (source == null || source.isEmpty()) {
			return source == null ? List.of() : source;
		}

		Set<Item> smokerInputs = resolveItems(MadokuRecipeDefaults.buildAddedSmokingInputs());
		Set<Item> blastInputs = resolveItems(MadokuRecipeDefaults.buildAddedBlastingInputs());
		List<RecipeHolder<?>> expanded = new ArrayList<>(source);
		Set<String> recipeIds = collectRecipeIds(source);
		List<AbstractCookingRecipe> smeltingRecipes = collectCookingRecipesByType(source, RecipeType.SMELTING);
		if (!smeltingRecipes.isEmpty()) {
			createDerivedCookingRecipes(expanded, recipeIds, smeltingRecipes, smokerInputs, RecipeType.SMOKING);
			createDerivedCookingRecipes(expanded, recipeIds, smeltingRecipes, blastInputs, RecipeType.BLASTING);
		}

		createConfiguredCookingRecipes(expanded, recipeIds, MadokuRecipeDefaults.buildAddedCookingRecipes());
		return expanded;
	}

	private static void createConfiguredCookingRecipes(
		List<RecipeHolder<?>> target,
		Set<String> recipeIds,
		List<MadokuRecipeDefaults.AddedCookingRecipe> defaults
	) {
		if (target == null || recipeIds == null || defaults == null || defaults.isEmpty()) {
			return;
		}

		for (MadokuRecipeDefaults.AddedCookingRecipe configured : defaults) {
			if (configured == null || configured.recipeType() == null) {
				continue;
			}

			Item inputItem = resolveItem(configured.inputItemId());
			Item resultItem = resolveItem(configured.resultItemId());
			if (inputItem == null || resultItem == null) {
				continue;
			}
			if (hasCookingRecipeForItem(target, configured.recipeType(), inputItem)) {
				continue;
			}

			RecipeHolder<?> added = createConfiguredCookingRecipe(configured, inputItem, resultItem, target, recipeIds);
			if (added == null) {
				continue;
			}
			target.add(added);
		}
	}

	private static Set<Item> resolveItems(Set<String> itemIds) {
		if (itemIds == null || itemIds.isEmpty()) {
			return Set.of();
		}
		Set<Item> resolved = new LinkedHashSet<>();
		for (String itemId : itemIds) {
			Item item = resolveItem(itemId);
			if (item != null) {
				resolved.add(item);
			}
		}
		return Set.copyOf(resolved);
	}

	private static Set<String> collectRecipeIds(List<RecipeHolder<?>> source) {
		Set<String> recipeIds = new LinkedHashSet<>();
		if (source == null || source.isEmpty()) {
			return recipeIds;
		}
		for (RecipeHolder<?> holder : source) {
			if (holder == null || holder.id() == null || holder.id().identifier() == null) {
				continue;
			}
			recipeIds.add(holder.id().identifier().toString());
		}
		return recipeIds;
	}

	private static List<AbstractCookingRecipe> collectCookingRecipesByType(List<RecipeHolder<?>> source, RecipeType<?> recipeType) {
		List<AbstractCookingRecipe> resolved = new ArrayList<>();
		if (source == null || source.isEmpty()) {
			return resolved;
		}
		for (RecipeHolder<?> holder : source) {
			if (holder == null || holder.value() == null) {
				continue;
			}
			if (!(holder.value() instanceof AbstractCookingRecipe cookingRecipe)) {
				continue;
			}
			if (recipeType != null && cookingRecipe.getType() != recipeType) {
				continue;
			}
			resolved.add(cookingRecipe);
		}
		return resolved;
	}

	private static void createDerivedCookingRecipes(
		List<RecipeHolder<?>> target,
		Set<String> recipeIds,
		List<AbstractCookingRecipe> smeltingRecipes,
		Set<Item> candidateInputs,
		RecipeType<?> targetType
	) {
		if (target == null || recipeIds == null || smeltingRecipes == null || smeltingRecipes.isEmpty()) {
			return;
		}
		if (candidateInputs == null || candidateInputs.isEmpty() || targetType == null) {
			return;
		}

		for (Item inputItem : candidateInputs) {
			if (inputItem == null || hasCookingRecipeForItem(target, targetType, inputItem)) {
				continue;
			}

			AbstractCookingRecipe source = findSmeltingRecipeForItem(smeltingRecipes, inputItem);
			if (source == null) {
				continue;
			}

			RecipeHolder<?> derived = createDerivedCookingRecipe(source, inputItem, targetType, recipeIds);
			if (derived == null) {
				continue;
			}
			target.add(derived);
		}
	}

	private static boolean hasCookingRecipeForItem(List<RecipeHolder<?>> source, RecipeType<?> recipeType, Item item) {
		if (source == null || source.isEmpty() || recipeType == null || item == null) {
			return false;
		}

		ItemStack stack = new ItemStack(item);
		for (RecipeHolder<?> holder : source) {
			if (holder == null || holder.value() == null) {
				continue;
			}
			if (!(holder.value() instanceof AbstractCookingRecipe cookingRecipe)) {
				continue;
			}
			if (cookingRecipe.getType() != recipeType) {
				continue;
			}
			try {
				if (cookingRecipe.input().test(stack)) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// Skip malformed recipe entries gracefully.
			}
		}
		return false;
	}

	private static AbstractCookingRecipe findSmeltingRecipeForItem(List<AbstractCookingRecipe> smeltingRecipes, Item inputItem) {
		if (smeltingRecipes == null || smeltingRecipes.isEmpty() || inputItem == null) {
			return null;
		}
		ItemStack stack = new ItemStack(inputItem);
		for (AbstractCookingRecipe recipe : smeltingRecipes) {
			if (recipe == null) {
				continue;
			}
			try {
				if (recipe.input().test(stack)) {
					return recipe;
				}
			} catch (RuntimeException ignored) {
				// Skip malformed recipe entries gracefully.
			}
		}
		return null;
	}

	private static RecipeHolder<?> createDerivedCookingRecipe(
		AbstractCookingRecipe source,
		Item inputItem,
		RecipeType<?> targetType,
		Set<String> existingRecipeIds
	) {
		if (source == null || inputItem == null || targetType == null || existingRecipeIds == null) {
			return null;
		}

		ItemStack outputStack = source.assemble(new SingleRecipeInput(new ItemStack(inputItem)));
		if (outputStack == null || outputStack.isEmpty()) {
			return null;
		}

		Ingredient ingredient = Ingredient.of(inputItem);
		ItemStackTemplate result = ItemStackTemplate.fromNonEmptyStack(outputStack);
		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(source.showNotification());
		AbstractCookingRecipe.CookingBookInfo bookInfo = new AbstractCookingRecipe.CookingBookInfo(source.category(), source.group());

		AbstractCookingRecipe created;
		if (targetType == RecipeType.SMOKING) {
			created = new SmokingRecipe(commonInfo, bookInfo, ingredient, result, source.experience(), source.cookingTime());
		} else if (targetType == RecipeType.BLASTING) {
			created = new BlastingRecipe(commonInfo, bookInfo, ingredient, result, source.experience(), source.cookingTime());
		} else {
			return null;
		}

		var inputId = BuiltInRegistries.ITEM.getKey(inputItem);
		var outputItem = outputStack.getItem();
		var outputId = outputItem == null ? null : BuiltInRegistries.ITEM.getKey(outputItem);
		if (inputId == null || outputId == null) {
			return null;
		}
		String targetPathPrefix = targetType == RecipeType.SMOKING ? "smoking" : "blasting";
		String inputToken = recipePathToken(inputId.toString());
		String outputToken = recipePathToken(outputId.toString());
		String recipeId = uniqueDerivedRecipeId(existingRecipeIds, targetPathPrefix, inputToken + "-to-" + outputToken);
		var identifier = net.minecraft.resources.Identifier.tryParse(recipeId);
		if (identifier == null) {
			return null;
		}
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, identifier);
		return new RecipeHolder<>(key, created);
	}

	private static RecipeHolder<?> createConfiguredCookingRecipe(
		MadokuRecipeDefaults.AddedCookingRecipe configured,
		Item inputItem,
		Item resultItem,
		List<RecipeHolder<?>> source,
		Set<String> existingRecipeIds
	) {
		if (configured == null || inputItem == null || resultItem == null || source == null || existingRecipeIds == null) {
			return null;
		}

		RecipeType<?> targetType = configured.recipeType();
		AbstractCookingRecipe template = findAnyCookingRecipeByType(source, targetType);
		if (template == null) {
			return null;
		}

		Ingredient ingredient = Ingredient.of(inputItem);
		ItemStackTemplate result = new ItemStackTemplate(resultItem, 1);
		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(template.showNotification());
		AbstractCookingRecipe.CookingBookInfo bookInfo = new AbstractCookingRecipe.CookingBookInfo(template.category(), template.group());

		AbstractCookingRecipe created;
		if (targetType == RecipeType.SMELTING) {
			created = new SmeltingRecipe(commonInfo, bookInfo, ingredient, result, configured.experience(), Math.max(1, configured.cookingTime()));
		} else if (targetType == RecipeType.BLASTING) {
			created = new BlastingRecipe(commonInfo, bookInfo, ingredient, result, configured.experience(), Math.max(1, configured.cookingTime()));
		} else if (targetType == RecipeType.SMOKING) {
			created = new SmokingRecipe(commonInfo, bookInfo, ingredient, result, configured.experience(), Math.max(1, configured.cookingTime()));
		} else if (targetType == RecipeType.CAMPFIRE_COOKING) {
			created = new CampfireCookingRecipe(commonInfo, bookInfo, ingredient, result, configured.experience(), Math.max(1, configured.cookingTime()));
		} else {
			return null;
		}

		var inputId = BuiltInRegistries.ITEM.getKey(inputItem);
		var resultId = BuiltInRegistries.ITEM.getKey(resultItem);
		if (inputId == null || resultId == null) {
			return null;
		}

		String targetPathPrefix;
		if (targetType == RecipeType.SMELTING) {
			targetPathPrefix = "smelting";
		} else if (targetType == RecipeType.BLASTING) {
			targetPathPrefix = "blasting";
		} else if (targetType == RecipeType.SMOKING) {
			targetPathPrefix = "smoking";
		} else if (targetType == RecipeType.CAMPFIRE_COOKING) {
			targetPathPrefix = "campfire";
		} else {
			targetPathPrefix = "cooking";
		}
		String inputToken = recipePathToken(inputId.toString());
		String resultToken = recipePathToken(resultId.toString());
		String recipeId = uniqueDerivedRecipeId(existingRecipeIds, targetPathPrefix, inputToken + "-to-" + resultToken);
		var identifier = net.minecraft.resources.Identifier.tryParse(recipeId);
		if (identifier == null) {
			return null;
		}

		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, identifier);
		return new RecipeHolder<>(key, created);
	}

	private static String recipePathToken(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return "unknown";
		}

		var identifier = net.minecraft.resources.Identifier.tryParse(itemId.trim().toLowerCase(Locale.ROOT));
		if (identifier == null) {
			return itemId.trim().toLowerCase(Locale.ROOT).replace(':', '-');
		}

		String path = identifier.getPath().replace('_', '-');
		if ("minecraft".equals(identifier.getNamespace())) {
			return path;
		}
		return identifier.getNamespace().replace('_', '-') + "-" + path;
	}

	private static AbstractCookingRecipe findAnyCookingRecipeByType(List<RecipeHolder<?>> source, RecipeType<?> targetType) {
		if (source == null || source.isEmpty() || targetType == null) {
			return null;
		}
		for (RecipeHolder<?> holder : source) {
			if (holder == null || holder.value() == null) {
				continue;
			}
			if (!(holder.value() instanceof AbstractCookingRecipe cookingRecipe)) {
				continue;
			}
			if (cookingRecipe.getType() == targetType) {
				return cookingRecipe;
			}
		}
		return null;
	}

	private static String uniqueDerivedRecipeId(Set<String> existingRecipeIds, String targetPrefix, String inputItemId) {
		String normalizedPrefix = targetPrefix == null ? "derived" : targetPrefix.trim().toLowerCase(Locale.ROOT);
		String normalizedInputId = inputItemId == null ? "unknown" : inputItemId.trim().toLowerCase(Locale.ROOT);
		String normalizedPath = normalizedInputId.replace(':', '/').replaceAll("[^a-z0-9_./-]", "-");
		String base = "madoku-craft:" + normalizedPrefix + "/" + normalizedPath;
		String resolved = base;
		int suffix = 2;
		while (existingRecipeIds.contains(resolved)) {
			resolved = base + "-" + suffix;
			suffix++;
		}
		existingRecipeIds.add(resolved);
		return resolved;
	}

	private static Recipe<?> buildRecipeOverride(RecipeDescriptor descriptor, JsonObject recipeRoot) {
		if (!readCustomRecipeEnabled(recipeRoot)) {
			return null;
		}

		Recipe<?> recipe = descriptor.recipe();
		if (recipe instanceof CraftingRecipe craftingRecipe) {
			return buildCraftingOverride(recipeRoot, descriptor, craftingRecipe);
		}
		if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
			return buildStonecutterOverride(recipeRoot, descriptor, stonecutterRecipe);
		}
		if (recipe instanceof SmithingRecipe smithingRecipe) {
			return buildSmithingOverride(recipeRoot, descriptor, smithingRecipe);
		}

		if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
			return buildCookingOverride(recipeRoot, descriptor, cookingRecipe);
		}

		return null;
	}

	private static boolean readCustomRecipeEnabled(JsonObject recipeRoot) {
		if (recipeRoot == null) {
			return false;
		}
		return readBoolean(recipeRoot, MadokuRecipeConfig.FIELD_CUSTOM_RECIPE, false);
	}

	private static Recipe<?> buildCraftingOverride(JsonObject recipeRoot, RecipeDescriptor descriptor, CraftingRecipe recipe) {
		String requestedShape = readString(
			recipeRoot,
			MadokuRecipeConfig.FIELD_CRAFTING_SHAPE,
			defaultCraftingShape(recipe)
		).trim().toLowerCase(Locale.ROOT);
		if (requestedShape.equals(MadokuRecipeConfig.CRAFTING_SHAPE_SHAPELESS)) {
			return buildShapelessOverride(recipeRoot, descriptor, recipe);
		}
		return buildShapedOverride(recipeRoot, descriptor, recipe);
	}

	private static String defaultCraftingShape(CraftingRecipe recipe) {
		if (recipe instanceof ShapelessRecipe) {
			return MadokuRecipeConfig.CRAFTING_SHAPE_SHAPELESS;
		}
		return MadokuRecipeConfig.CRAFTING_SHAPE_SHAPED;
	}

	private static Recipe<?> buildShapedOverride(JsonObject recipeRoot, RecipeDescriptor descriptor, CraftingRecipe recipe) {
		List<String> patternRows = readPatternRows(recipeRoot.get(MadokuRecipeConfig.FIELD_PATTERN));
		if (patternRows.isEmpty()) {
			return null;
		}

		JsonObject keyRoot = readJsonObject(recipeRoot, MadokuRecipeConfig.FIELD_KEY);
		Map<Character, Ingredient> key = readShapedKey(patternRows, keyRoot);
		if (key == null) {
			return null;
		}

		List<String> javaPatternRows = normalizePatternRows(patternRows, keyRoot);
		if (javaPatternRows.isEmpty()) {
			return null;
		}

		ShapedRecipePattern pattern;
		try {
			pattern = ShapedRecipePattern.of(key, javaPatternRows);
		} catch (RuntimeException exception) {
			return null;
		}

		ItemStackTemplate resultTemplate = buildResultTemplate(recipeRoot, descriptor);
		if (resultTemplate == null) {
			return null;
		}

		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());
		CraftingRecipe.CraftingBookInfo bookInfo = new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group());
		return new ShapedRecipe(commonInfo, bookInfo, pattern, resultTemplate);
	}

	private static Recipe<?> buildShapelessOverride(JsonObject recipeRoot, RecipeDescriptor descriptor, CraftingRecipe recipe) {
		List<Ingredient> ingredients = readIngredientList(recipeRoot.get(MadokuRecipeConfig.FIELD_INGREDIENTS));
		if (ingredients.isEmpty()) {
			for (Ingredient ingredient : readDefaultCraftingIngredients(recipe)) {
				if (ingredient != null && !ingredient.isEmpty()) {
					ingredients.add(ingredient);
				}
			}
		}
		if (ingredients.isEmpty()) {
			return null;
		}

		ItemStackTemplate resultTemplate = buildResultTemplate(recipeRoot, descriptor);
		if (resultTemplate == null) {
			return null;
		}

		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());
		CraftingRecipe.CraftingBookInfo bookInfo = new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group());
		return new ShapelessRecipe(commonInfo, bookInfo, resultTemplate, List.copyOf(ingredients));
	}

	private static List<Ingredient> readDefaultCraftingIngredients(CraftingRecipe recipe) {
		if (recipe == null) {
			return List.of();
		}
		if (recipe instanceof ShapelessRecipe shapelessRecipe) {
			return readShapelessIngredients(shapelessRecipe);
		}
		if (recipe instanceof ShapedRecipe shapedRecipe) {
			return readShapedIngredients(shapedRecipe);
		}
		return List.of();
	}

	private static List<Ingredient> readShapelessIngredients(ShapelessRecipe recipe) {
		if (recipe == null) {
			return List.of();
		}
		try {
			Field ingredientsField = ShapelessRecipe.class.getDeclaredField(SHAPELESS_INGREDIENTS_FIELD_NAME);
			ingredientsField.setAccessible(true);
			Object rawIngredients = ingredientsField.get(recipe);
			if (!(rawIngredients instanceof List<?> list) || list.isEmpty()) {
				return List.of();
			}

			List<Ingredient> resolved = new ArrayList<>();
			for (Object raw : list) {
				if (raw instanceof Ingredient ingredient && !ingredient.isEmpty()) {
					resolved.add(ingredient);
				}
			}
			return List.copyOf(resolved);
		} catch (ReflectiveOperationException exception) {
			LOGGER.warn("Failed to read shapeless recipe ingredients for recipe overrides.", exception);
			return List.of();
		}
	}

	private static List<Ingredient> readShapedIngredients(ShapedRecipe recipe) {
		if (recipe == null) {
			return List.of();
		}
		List<Ingredient> resolved = new ArrayList<>();
		for (Optional<Ingredient> optional : recipe.getIngredients()) {
			if (optional == null || optional.isEmpty()) {
				continue;
			}
			Ingredient ingredient = optional.get();
			if (!ingredient.isEmpty()) {
				resolved.add(ingredient);
			}
		}
		return List.copyOf(resolved);
	}

	private static Recipe<?> buildCookingOverride(JsonObject recipeRoot, RecipeDescriptor descriptor, AbstractCookingRecipe recipe) {
		if (!(recipe instanceof SingleItemRecipe singleItemRecipe)) {
			return null;
		}

		Ingredient input = readIngredient(recipeRoot.get(MadokuRecipeConfig.FIELD_INPUT));
		if (input == null || input.isEmpty()) {
			input = singleItemRecipe.input();
		}
		if (input == null || input.isEmpty()) {
			return null;
		}

		ItemStackTemplate resultTemplate = buildResultTemplate(recipeRoot, descriptor);
		if (resultTemplate == null) {
			return null;
		}

		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());
		AbstractCookingRecipe.CookingBookInfo bookInfo = new AbstractCookingRecipe.CookingBookInfo(recipe.category(), recipe.group());

		if (recipe instanceof SmeltingRecipe) {
			return new SmeltingRecipe(commonInfo, bookInfo, input, resultTemplate, recipe.experience(), recipe.cookingTime());
		}
		if (recipe instanceof BlastingRecipe) {
			return new BlastingRecipe(commonInfo, bookInfo, input, resultTemplate, recipe.experience(), recipe.cookingTime());
		}
		if (recipe instanceof SmokingRecipe) {
			return new SmokingRecipe(commonInfo, bookInfo, input, resultTemplate, recipe.experience(), recipe.cookingTime());
		}
		if (recipe instanceof CampfireCookingRecipe) {
			return new CampfireCookingRecipe(commonInfo, bookInfo, input, resultTemplate, recipe.experience(), recipe.cookingTime());
		}

		return null;
	}

	private static Recipe<?> buildStonecutterOverride(JsonObject recipeRoot, RecipeDescriptor descriptor, StonecutterRecipe recipe) {
		Ingredient input = readIngredient(recipeRoot.get(MadokuRecipeConfig.FIELD_INPUT));
		if (input == null || input.isEmpty()) {
			input = recipe.input();
		}
		if (input == null || input.isEmpty()) {
			return null;
		}

		ItemStackTemplate resultTemplate = buildResultTemplate(recipeRoot, descriptor);
		if (resultTemplate == null) {
			return null;
		}

		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());
		return new StonecutterRecipe(commonInfo, input, resultTemplate);
	}

	private static Recipe<?> buildSmithingOverride(JsonObject recipeRoot, RecipeDescriptor descriptor, SmithingRecipe recipe) {
		Ingredient base = readIngredient(recipeRoot.get(MadokuRecipeConfig.FIELD_BASE));
		if (base == null || base.isEmpty()) {
			base = recipe.baseIngredient();
		}
		if (base == null || base.isEmpty()) {
			return null;
		}

		Optional<Ingredient> template = readOptionalIngredient(recipeRoot.get(MadokuRecipeConfig.FIELD_TEMPLATE));
		if (template.isEmpty()) {
			template = recipe.templateIngredient();
		}

		Optional<Ingredient> addition = readOptionalIngredient(recipeRoot.get(MadokuRecipeConfig.FIELD_ADDITION));
		if (addition.isEmpty()) {
			addition = recipe.additionIngredient();
		}

		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());
		if (recipe instanceof SmithingTransformRecipe) {
			ItemStackTemplate resultTemplate = buildResultTemplate(recipeRoot, descriptor);
			if (resultTemplate == null) {
				return null;
			}
			return new SmithingTransformRecipe(commonInfo, template, base, addition, resultTemplate);
		}
		if (recipe instanceof SmithingTrimRecipe smithingTrimRecipe) {
			if (template.isEmpty() || addition.isEmpty()) {
				return null;
			}
			Object rawPattern = readSmithingTrimPattern(smithingTrimRecipe);
			if (rawPattern == null) {
				return null;
			}
			@SuppressWarnings("unchecked")
			net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimPattern> pattern =
				(net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimPattern>) rawPattern;
			return new SmithingTrimRecipe(commonInfo, template.get(), base, addition.get(), pattern);
		}
		return null;
	}

	private static Object readSmithingTrimPattern(SmithingTrimRecipe recipe) {
		if (recipe == null) {
			return null;
		}
		try {
			Field patternField = SmithingTrimRecipe.class.getDeclaredField(SMITHING_TRIM_PATTERN_FIELD_NAME);
			patternField.setAccessible(true);
			return patternField.get(recipe);
		} catch (ReflectiveOperationException exception) {
			LOGGER.warn("Failed to read smithing trim pattern for recipe overrides.", exception);
			return null;
		}
	}

	private static ItemStackTemplate buildResultTemplate(JsonObject recipeRoot, RecipeDescriptor descriptor) {
		String resultItemId = readString(recipeRoot, MadokuRecipeConfig.FIELD_RESULT_ITEM_ID, descriptor.resultItemId());
		Item item = resolveItem(resultItemId);
		if (item == null) {
			return null;
		}

		int count = readInt(recipeRoot, MadokuRecipeConfig.FIELD_RESULT_COUNT, descriptor.resultCount());
		int normalizedCount = Math.max(1, count);
		return new ItemStackTemplate(item, normalizedCount);
	}

	private static Map<Character, Ingredient> readShapedKey(List<String> patternRows, JsonObject keyRoot) {
		Map<Character, Ingredient> key = new LinkedHashMap<>();
		Set<Character> requiredKeys = collectRequiredPatternKeys(patternRows);
		for (char symbol : requiredKeys) {
			JsonElement entry = keyRoot.get(String.valueOf(symbol));
			Ingredient ingredient = readIngredient(entry);
			if (ingredient == null || ingredient.isEmpty()) {
				return null;
			}
			key.put(symbol, ingredient);
		}
		return key;
	}

	private static Set<Character> collectRequiredPatternKeys(List<String> patternRows) {
		Set<Character> required = new LinkedHashSet<>();
		for (String row : patternRows) {
			if (row == null) {
				continue;
			}
			for (int index = 0; index < row.length(); index++) {
				char symbol = row.charAt(index);
				if (isEmptyPatternSymbol(symbol)) {
					continue;
				}
				required.add(symbol);
			}
		}
		return required;
	}

	private static List<String> normalizePatternRows(List<String> patternRows, JsonObject keyRoot) {
		if (patternRows.isEmpty()) {
			return List.of();
		}

		List<String> normalized = new ArrayList<>(patternRows.size());
		for (String row : patternRows) {
			if (row == null || row.isEmpty()) {
				normalized.add(" ");
				continue;
			}
			StringBuilder builder = new StringBuilder(row.length());
			for (int index = 0; index < row.length(); index++) {
				char symbol = row.charAt(index);
				if (isEmptyPatternSymbol(symbol)) {
					builder.append(' ');
					continue;
				}
				if (!keyRoot.has(String.valueOf(symbol))) {
					builder.append(' ');
					continue;
				}
				builder.append(symbol);
			}
			normalized.add(builder.toString());
		}
		return normalized;
	}

	private static boolean isEmptyPatternSymbol(char symbol) {
		return symbol == ' ' || symbol == EMPTY_SLOT_SYMBOL.charAt(0);
	}

	private static List<String> readPatternRows(JsonElement patternElement) {
		if (!(patternElement instanceof JsonArray patternArray) || patternArray.isEmpty()) {
			return List.of();
		}

		List<String> rows = new ArrayList<>(patternArray.size());
		for (JsonElement row : patternArray) {
			if (!(row instanceof JsonPrimitive primitive) || !primitive.isString()) {
				return List.of();
			}
			rows.add(primitive.getAsString());
		}
		return rows;
	}

	private static Ingredient readIngredient(JsonElement element) {
		List<Item> items = new ArrayList<>();
		collectIngredientItems(element, items);
		if (items.isEmpty()) {
			return null;
		}
		return Ingredient.of(items.toArray(Item[]::new));
	}

	private static List<Ingredient> readIngredientList(JsonElement element) {
		List<Ingredient> ingredients = new ArrayList<>();
		if (element == null || element.isJsonNull()) {
			return ingredients;
		}
		if (element instanceof JsonArray array) {
			for (JsonElement entry : array) {
				Ingredient ingredient = readIngredient(entry);
				if (ingredient != null && !ingredient.isEmpty()) {
					ingredients.add(ingredient);
				}
			}
			return ingredients;
		}

		Ingredient single = readIngredient(element);
		if (single != null && !single.isEmpty()) {
			ingredients.add(single);
		}
		return ingredients;
	}

	private static Optional<Ingredient> readOptionalIngredient(JsonElement element) {
		Ingredient ingredient = readIngredient(element);
		if (ingredient == null || ingredient.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(ingredient);
	}

	private static void collectIngredientItems(JsonElement element, List<Item> into) {
		if (element == null || element.isJsonNull() || into == null) {
			return;
		}

		if (element instanceof JsonPrimitive primitive && primitive.isString()) {
			Item item = resolveItem(primitive.getAsString());
			if (item != null) {
				into.add(item);
			}
			return;
		}

		if (element instanceof JsonArray array) {
			for (JsonElement entry : array) {
				collectIngredientItems(entry, into);
			}
			return;
		}

		if (!(element instanceof JsonObject root)) {
			return;
		}

		JsonElement itemElement = root.get("item");
		if (itemElement instanceof JsonPrimitive itemPrimitive && itemPrimitive.isString()) {
			Item item = resolveItem(itemPrimitive.getAsString());
			if (item != null) {
				into.add(item);
			}
		}
		JsonElement itemsElement = root.get("items");
		if (itemsElement != null) {
			collectIngredientItems(itemsElement, into);
		}
	}

	private static List<RecipeDescriptor> collectDescriptors(List<RecipeHolder<?>> source) {
		List<RecipeDescriptor> descriptors = new ArrayList<>(source.size());
		for (RecipeHolder<?> holder : source) {
			Recipe<?> recipe = holder.value();
			if (recipe == null) {
				continue;
			}

			String recipeId = holder.id().identifier().toString();
			String fileKey = fileKeyFromRecipeId(recipeId);
			RecipeSnapshot snapshot = snapshotRecipe(recipe);
			JsonObject defaults = buildRecipeDefaults(recipeId, snapshot, recipe);
			descriptors.add(new RecipeDescriptor(
				holder,
				recipe,
				recipeId,
				fileKey,
				snapshot.resultItemId(),
				snapshot.resultCount(),
				snapshot.outputCategory() + "/" + snapshot.processCategory(),
				defaults
			));
		}
		return descriptors;
	}

	private static RecipeSnapshot snapshotRecipe(Recipe<?> recipe) {
		String recipeTypeId = "unknown";
		RecipeType<?> recipeType = recipe.getType();
		if (recipeType != null) {
			var typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
			if (typeId != null) {
				recipeTypeId = typeId.toString();
			}
		}

		String processCategory = resolveProcessCategory(recipe, recipeTypeId);
		ItemStack resultStack = resolveResultStack(recipe);
		Item resultItem = resultStack == null || resultStack.isEmpty() ? null : resultStack.getItem();
		String resultItemId = "";
		if (resultItem != null) {
			var resultId = BuiltInRegistries.ITEM.getKey(resultItem);
			if (resultId != null) {
				resultItemId = resultId.toString();
			}
		}

		String outputCategory = resultItem instanceof BlockItem
			? MadokuRecipeConfig.CATEGORY_BLOCK
			: MadokuRecipeConfig.CATEGORY_ITEM;
		int resultCount = resultStack == null || resultStack.isEmpty() ? 1 : Math.max(1, resultStack.getCount());
		return new RecipeSnapshot(recipeTypeId, resultItemId, resultCount, outputCategory, processCategory);
	}

	private static JsonObject buildRecipeDefaults(String recipeId, RecipeSnapshot snapshot, Recipe<?> recipe) {
		boolean defaultEnabled = isDefaultEnabled(recipeId);
		JsonObject root = MadokuRecipeConfig.buildBaseRecipeDefaults(
			recipeId,
			snapshot.recipeTypeId(),
			snapshot.resultItemId(),
			snapshot.resultCount(),
			snapshot.outputCategory(),
			snapshot.processCategory(),
			defaultEnabled
		);

		if (recipe instanceof ShapedRecipe shapedRecipe) {
			root.addProperty(MadokuRecipeConfig.FIELD_CRAFTING_SHAPE, MadokuRecipeConfig.CRAFTING_SHAPE_SHAPED);
			writeShapedDefaults(root, shapedRecipe);
		} else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
			root.addProperty(MadokuRecipeConfig.FIELD_CRAFTING_SHAPE, MadokuRecipeConfig.CRAFTING_SHAPE_SHAPELESS);
			writeShapelessDefaults(root, shapelessRecipe);
		} else if (recipe instanceof SmithingRecipe smithingRecipe) {
			writeSmithingDefaults(root, smithingRecipe);
		} else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
			writeSingleInputDefaults(root, singleItemRecipe);
		}

		return root;
	}

	private static boolean isDefaultEnabled(String recipeId) {
		if (recipeId == null || recipeId.isBlank()) {
			return true;
		}
		String normalized = recipeId.trim().toLowerCase(Locale.ROOT);
		return !normalized.equals("minecraft:bone_meal");
	}

	private static void writeShapedDefaults(JsonObject root, ShapedRecipe recipe) {
		List<Optional<Ingredient>> ingredients = recipe.getIngredients();
		int width = Math.max(1, recipe.getWidth());
		int height = Math.max(1, recipe.getHeight());

		Map<String, Character> symbolBySignature = new LinkedHashMap<>();
		Map<Character, String> firstItemIdBySymbol = new LinkedHashMap<>();
		JsonArray pattern = new JsonArray();
		int symbolIndex = 0;

		for (int row = 0; row < height; row++) {
			StringBuilder rowBuilder = new StringBuilder(width);
			for (int column = 0; column < width; column++) {
				int index = row * width + column;
				Optional<Ingredient> optional = index < ingredients.size() ? ingredients.get(index) : Optional.empty();
				if (optional == null || optional.isEmpty()) {
					rowBuilder.append(EMPTY_SLOT_SYMBOL);
					continue;
				}

				Ingredient ingredient = optional.get();
				String keyItemId = firstItemIdFromIngredient(ingredient);
				if (keyItemId.isBlank()) {
					rowBuilder.append(EMPTY_SLOT_SYMBOL);
					continue;
				}

				Character symbol = symbolBySignature.get(keyItemId);
				if (symbol == null) {
					symbol = symbolForIndex(symbolIndex++);
					symbolBySignature.put(keyItemId, symbol);
					firstItemIdBySymbol.put(symbol, keyItemId);
				}
				rowBuilder.append(symbol);
			}
			pattern.add(rowBuilder.toString());
		}

		JsonObject key = new JsonObject();
		for (Map.Entry<Character, String> entry : firstItemIdBySymbol.entrySet()) {
			key.addProperty(String.valueOf(entry.getKey()), entry.getValue());
		}

		root.add(MadokuRecipeConfig.FIELD_PATTERN, pattern);
		root.add(MadokuRecipeConfig.FIELD_KEY, key);
		writeCraftingIngredientsDefaults(root, readShapedIngredients(recipe));
	}

	private static void writeSingleInputDefaults(JsonObject root, SingleItemRecipe recipe) {
		Ingredient ingredient = recipe.input();
		String itemId = firstItemIdFromIngredient(ingredient);
		if (!itemId.isBlank()) {
			root.addProperty(MadokuRecipeConfig.FIELD_INPUT, itemId);
		}
	}

	private static void writeShapelessDefaults(JsonObject root, ShapelessRecipe recipe) {
		writeCraftingIngredientsDefaults(root, readShapelessIngredients(recipe));
	}

	private static void writeCraftingIngredientsDefaults(JsonObject root, List<Ingredient> ingredientList) {
		JsonArray ingredients = new JsonArray();
		if (ingredientList == null || ingredientList.isEmpty()) {
			return;
		}
		for (Ingredient ingredient : ingredientList) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}
			String itemId = firstItemIdFromIngredient(ingredient);
			if (!itemId.isBlank()) {
				ingredients.add(itemId);
			}
		}
		if (!ingredients.isEmpty()) {
			root.add(MadokuRecipeConfig.FIELD_INGREDIENTS, ingredients);
		}
	}

	private static void writeSmithingDefaults(JsonObject root, SmithingRecipe recipe) {
		if (recipe == null) {
			return;
		}

		recipe.templateIngredient().ifPresent(ingredient -> {
			String itemId = firstItemIdFromIngredient(ingredient);
			if (!itemId.isBlank()) {
				root.addProperty(MadokuRecipeConfig.FIELD_TEMPLATE, itemId);
			}
		});

		Ingredient base = recipe.baseIngredient();
		String baseItemId = firstItemIdFromIngredient(base);
		if (!baseItemId.isBlank()) {
			root.addProperty(MadokuRecipeConfig.FIELD_BASE, baseItemId);
		}

		recipe.additionIngredient().ifPresent(ingredient -> {
			String itemId = firstItemIdFromIngredient(ingredient);
			if (!itemId.isBlank()) {
				root.addProperty(MadokuRecipeConfig.FIELD_ADDITION, itemId);
			}
		});
	}

	private static String firstItemIdFromIngredient(Ingredient ingredient) {
		if (ingredient == null || ingredient.isEmpty()) {
			return "";
		}
		for (Item candidate : BuiltInRegistries.ITEM) {
			if (candidate == null) {
				continue;
			}
			ItemStack stack = new ItemStack(candidate);
			if (!ingredient.test(stack)) {
				continue;
			}
			var candidateId = BuiltInRegistries.ITEM.getKey(candidate);
			if (candidateId != null) {
				return candidateId.toString();
			}
		}
		return "";
	}

	private static ItemStack firstItemStackFromIngredient(Ingredient ingredient) {
		if (ingredient == null || ingredient.isEmpty()) {
			return ItemStack.EMPTY;
		}
		for (Item candidate : BuiltInRegistries.ITEM) {
			if (candidate == null) {
				continue;
			}
			ItemStack stack = new ItemStack(candidate);
			if (ingredient.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static char symbolForIndex(int index) {
		if (index >= 0 && index < SYMBOLS.length()) {
			return SYMBOLS.charAt(index);
		}
		int overflow = Math.max(0, index - SYMBOLS.length());
		return (char) ('a' + (overflow % 26));
	}

	private static ItemStack resolveResultStack(Recipe<?> recipe) {
		try {
			if (recipe instanceof CraftingRecipe craftingRecipe) {
				return craftingRecipe.assemble(CraftingInput.EMPTY);
			}
			if (recipe instanceof SmithingRecipe smithingRecipe) {
				ItemStack template = firstItemStackFromIngredient(smithingRecipe.templateIngredient().orElse(null));
				ItemStack base = firstItemStackFromIngredient(smithingRecipe.baseIngredient());
				ItemStack addition = firstItemStackFromIngredient(smithingRecipe.additionIngredient().orElse(null));
				if (!base.isEmpty()) {
					return smithingRecipe.assemble(new SmithingRecipeInput(template, base, addition));
				}
			}
			if (recipe instanceof SingleItemRecipe singleItemRecipe) {
				return singleItemRecipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
			}
		} catch (RuntimeException ignored) {
			// Keep fallback below.
		}
		return ItemStack.EMPTY;
	}

	private static String resolveProcessCategory(Recipe<?> recipe, String recipeTypeId) {
		if (recipe instanceof CraftingRecipe) {
			return MadokuRecipeConfig.CATEGORY_CRAFTING;
		}
		if (recipe instanceof StonecutterRecipe) {
			return MadokuRecipeConfig.CATEGORY_STONECUTTING;
		}
		if (recipe instanceof SmeltingRecipe) {
			return MadokuRecipeConfig.CATEGORY_SMELTING;
		}
		if (recipe instanceof BlastingRecipe) {
			return MadokuRecipeConfig.CATEGORY_BLASTING;
		}
		if (recipe instanceof SmokingRecipe) {
			return MadokuRecipeConfig.CATEGORY_SMOKING;
		}
		if (recipe instanceof CampfireCookingRecipe) {
			return MadokuRecipeConfig.CATEGORY_CAMPFIRE;
		}
		if (recipeTypeId.contains("smithing")) {
			return MadokuRecipeConfig.CATEGORY_SMITHING;
		}
		return MadokuRecipeConfig.CATEGORY_OTHER;
	}

	private static Item resolveItem(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		var identifier = net.minecraft.resources.Identifier.tryParse(itemId.trim().toLowerCase(Locale.ROOT));
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(identifier);
	}

	private static List<RecipeHolder<?>> copyRecipes(Iterable<RecipeHolder<?>> loadedRecipes) {
		List<RecipeHolder<?>> source = new ArrayList<>();
		if (loadedRecipes == null) {
			return source;
		}
		for (RecipeHolder<?> holder : loadedRecipes) {
			if (holder != null) {
				source.add(holder);
			}
		}
		return source;
	}

	private static boolean loadSystemEnabled() {
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(RECIPE_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, RECIPE_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = JsonStaticSystem.ensureManagedFile(
				settingsFile,
				MadokuRecipeConfig.buildRecipeSystemDefaults()
			);
			return readBoolean(settingsRoot, MadokuRecipeConfig.FIELD_ENABLED, true);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuRecipe settings; disabling recipe overrides.", exception);
			return false;
		}
	}

	private static String fileKeyFromRecipeId(String recipeId) {
		if (recipeId == null || recipeId.isBlank()) {
			return "recipe";
		}
		String normalized = recipeId.trim().toLowerCase(Locale.ROOT);
		StringBuilder key = new StringBuilder(normalized.length() + 8);
		boolean previousDash = false;
		for (int index = 0; index < normalized.length(); index++) {
			char value = normalized.charAt(index);
			if (Character.isLetterOrDigit(value)) {
				key.append(value);
				previousDash = false;
				continue;
			}
			if (!previousDash) {
				key.append('-');
				previousDash = true;
			}
		}

		int start = 0;
		while (start < key.length() && key.charAt(start) == '-') {
			start++;
		}
		int end = key.length();
		while (end > start && key.charAt(end - 1) == '-') {
			end--;
		}
		String collapsed = key.substring(start, end);
		if (collapsed.isBlank()) {
			return "recipe";
		}
		return collapsed;
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
			return fallback;
		}
		return primitive.getAsBoolean();
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			return fallback;
		}
		return primitive.getAsInt();
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
			return fallback;
		}
		return primitive.getAsString();
	}

	private static JsonObject readJsonObject(JsonObject root, String key) {
		if (root == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		if (element instanceof JsonObject object) {
			return object;
		}
		return new JsonObject();
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private record RecipeSnapshot(
		String recipeTypeId,
		String resultItemId,
		int resultCount,
		String outputCategory,
		String processCategory
	) {
	}

	private record RecipeDescriptor(
		RecipeHolder<?> holder,
		Recipe<?> recipe,
		String recipeId,
		String fileKey,
		String resultItemId,
		int resultCount,
		String folderPath,
		JsonObject defaultsRoot
	) {
	}
}
