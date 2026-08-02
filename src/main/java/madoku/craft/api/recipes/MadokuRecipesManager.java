package madoku.craft.api.recipes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MadokuRecipesManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuRecipesManager.class);
	private static final String MADOKU_RECIPE_NAMESPACE = "madoku-craft";
	private static volatile boolean initialized;

	private MadokuRecipesManager() {
	}

	public static void initialize() {
		RecipesConfigManager.initialize();
		RecipesFormatManager.initialize();
		RecipesBlockManager.initialize();
		RecipesItemManager.initialize();
		loadSystemEnabled();
		initialized = true;
	}

	public static void reset() {
		RecipesItemManager.reset();
		RecipesBlockManager.reset();
		RecipesFormatManager.reset();
		RecipesConfigManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

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
			Path rootDirectory = RecipesConfigManager.getRootDirectory();
			Path recipesDirectory = rootDirectory;

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

				Map<String, JsonObject> normalized = JSONFormatManager.ensureManagedFolder(
					folder,
					staticDefaults,
					fileKey -> defaultsByFileKey.getOrDefault(fileKey, new JsonObject()),
					(fileKey, sourceRoot) -> isSupportedRecipeConfigFile(fileKey, sourceRoot, validKeys, descriptorByRecipeId),
					MadokuRecipesManager::copyDynamicEntry
				);

				for (JsonObject normalizedRoot : normalized.values()) {
					JsonObject flatRoot = RecipesConfigManager.flattenRecipeConfig(normalizedRoot);
					String recipeId = normalizeRecipeId(readString(flatRoot, RecipesConfigManager.FIELD_RECIPE_ID, ""));
					if (!recipeId.isBlank() && descriptorByRecipeId.containsKey(recipeId)) {
						normalizedByRecipeId.put(recipeId, flatRoot);
					}
				}
			}

			List<RecipeHolder<?>> resolved = new ArrayList<>(descriptors.size());
			for (RecipeDescriptor descriptor : descriptors) {
				JsonObject recipeRoot = normalizedByRecipeId.getOrDefault(
					descriptor.recipeId(),
					RecipesConfigManager.flattenRecipeConfig(descriptor.defaultsRoot())
				);
				if (!readBoolean(recipeRoot, RecipesConfigManager.FIELD_ENABLED, true)) {
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
			LOGGER.error("Failed to apply MadokuRecipes config; using vanilla loaded recipes.", exception);
			return source;
		}
	}

	private static boolean shouldGenerateManagedRecipeFile(String recipeId, JsonObject defaultsRoot) {
		if (!readBoolean(defaultsRoot, RecipesConfigManager.FIELD_ENABLED, true)) {
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
		JsonObject flatRoot = RecipesConfigManager.flattenRecipeConfig(sourceRoot);
		String recipeId = normalizeRecipeId(readString(flatRoot, RecipesConfigManager.FIELD_RECIPE_ID, ""));
		if (recipeId.isBlank() || descriptorByRecipeId == null || !descriptorByRecipeId.containsKey(recipeId)) {
			return false;
		}
		return isUserModifiedRecipeConfig(flatRoot);
	}

	private static boolean isUserModifiedRecipeConfig(JsonObject sourceRoot) {
		if (sourceRoot == null) {
			return false;
		}
		boolean enabled = readBoolean(sourceRoot, RecipesConfigManager.FIELD_ENABLED, true);
		boolean customRecipe = readBoolean(sourceRoot, RecipesConfigManager.FIELD_CUSTOM_RECIPE, false);
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

		Set<Item> smokerInputs = resolveItems(ConfigSmokingManager.buildAddedSmokingInputs());
		Set<Item> blastInputs = resolveItems(ConfigBlastingManager.buildAddedBlastingInputs());
		List<RecipeHolder<?>> expanded = new ArrayList<>(source);
		Set<String> recipeIds = collectRecipeIds(source);
		List<AbstractCookingRecipe> smeltingRecipes = collectCookingRecipesByType(source, RecipeType.SMELTING);
		if (!smeltingRecipes.isEmpty()) {
			createDerivedCookingRecipes(expanded, recipeIds, smeltingRecipes, smokerInputs, RecipeType.SMOKING);
			createDerivedCookingRecipes(expanded, recipeIds, smeltingRecipes, blastInputs, RecipeType.BLASTING);
		}

		ConfigSmeltingManager.AddedCookingRecipe smeltingDefault = ConfigSmeltingManager.buildAddedRecipe();
		createConfiguredCookingRecipe(expanded, recipeIds, smeltingDefault.inputItemId(), smeltingDefault.resultItemId(),
			RecipeType.SMELTING, smeltingDefault.experience(), smeltingDefault.cookingTime());
		ConfigBlastingManager.AddedCookingRecipe blastingDefault = ConfigBlastingManager.buildAddedRecipe();
		createConfiguredCookingRecipe(expanded, recipeIds, blastingDefault.inputItemId(), blastingDefault.resultItemId(),
			RecipeType.BLASTING, blastingDefault.experience(), blastingDefault.cookingTime());
		return expanded;
	}

	private static void createConfiguredCookingRecipe(
		List<RecipeHolder<?>> target,
		Set<String> recipeIds,
		String inputItemId,
		String resultItemId,
		RecipeType<?> recipeType,
		float experience,
		int cookingTime
	) {
		if (target == null || recipeIds == null || recipeType == null) return;
		Item inputItem = resolveItem(inputItemId);
		Item resultItem = resolveItem(resultItemId);
		if (inputItem == null || resultItem == null || hasCookingRecipeForItem(target, recipeType, inputItem)) return;
		RecipeHolder<?> added = createConfiguredCookingRecipe(inputItemId, resultItemId, recipeType, experience, cookingTime,
			inputItem, resultItem, target, recipeIds);
		if (added != null) target.add(added);
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
		String inputItemId,
		String resultItemId,
		RecipeType<?> targetType,
		float experience,
		int cookingTime,
		Item inputItem,
		Item resultItem,
		List<RecipeHolder<?>> source,
		Set<String> existingRecipeIds
	) {
		if (inputItem == null || resultItem == null || source == null || existingRecipeIds == null) {
			return null;
		}
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
			created = new SmeltingRecipe(commonInfo, bookInfo, ingredient, result, experience, Math.max(1, cookingTime));
		} else if (targetType == RecipeType.BLASTING) {
			created = new BlastingRecipe(commonInfo, bookInfo, ingredient, result, experience, Math.max(1, cookingTime));
		} else if (targetType == RecipeType.SMOKING) {
			created = new SmokingRecipe(commonInfo, bookInfo, ingredient, result, experience, Math.max(1, cookingTime));
		} else if (targetType == RecipeType.CAMPFIRE_COOKING) {
			created = new CampfireCookingRecipe(commonInfo, bookInfo, ingredient, result, experience, Math.max(1, cookingTime));
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

		var identifier = net.minecraft.resources.Identifier.tryParse(
			MadokuJSONManager.normalizeRegistryIdentifierForLookup(itemId)
		);
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
		return RecipesFormatManager.buildOverride(
			recipeRoot,
			descriptor.recipe(),
			descriptor.resultItemId(),
			descriptor.resultCount()
		);
	}

	private static boolean readCustomRecipeEnabled(JsonObject recipeRoot) {
		if (recipeRoot == null) {
			return false;
		}
		return readBoolean(recipeRoot, RecipesConfigManager.FIELD_CUSTOM_RECIPE, false);
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
				RecipesConfigManager.folderForOutputCategory(snapshot.outputCategory()) + "/" + snapshot.processCategory(),
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

		String outputCategory = RecipesBlockManager.isBlockResult(resultStack)
			? RecipesConfigManager.CATEGORY_BLOCK
			: RecipesConfigManager.CATEGORY_ITEM;
		int resultCount = resultStack == null || resultStack.isEmpty() ? 1 : Math.max(1, resultStack.getCount());
		return new RecipeSnapshot(recipeTypeId, resultItemId, resultCount, outputCategory, processCategory);
	}

	private static JsonObject buildRecipeDefaults(String recipeId, RecipeSnapshot snapshot, Recipe<?> recipe) {
		boolean defaultEnabled = isDefaultEnabled(recipeId);
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object().putAll(RecipesConfigManager.buildBaseRecipeDefaults(
			recipeId,
			snapshot.recipeTypeId(),
			snapshot.resultItemId(),
			snapshot.resultCount(),
			snapshot.outputCategory(),
			snapshot.processCategory(),
			defaultEnabled
		));

		RecipesFormatManager.writeDefaults(root, recipe);

		return RecipesConfigManager.nestRecipeDefaults(root.build(), snapshot.processCategory());
	}

	private static boolean isDefaultEnabled(String recipeId) {
		if (recipeId == null || recipeId.isBlank()) {
			return true;
		}
		String normalized = recipeId.trim().toLowerCase(Locale.ROOT);
		return !normalized.equals("minecraft:bone_meal");
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
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_CRAFTING);
		}
		if (recipe instanceof StonecutterRecipe) {
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_STONECUTTING);
		}
		if (recipe instanceof SmeltingRecipe) {
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_SMELTING);
		}
		if (recipe instanceof BlastingRecipe) {
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_BLASTING);
		}
		if (recipe instanceof SmokingRecipe) {
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_SMOKING);
		}
		if (recipe instanceof CampfireCookingRecipe) {
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_CAMPFIRE);
		}
		if (recipeTypeId.contains("smithing")) {
			return RecipesFormatManager.normalizeCategory(RecipesConfigManager.CATEGORY_SMITHING);
		}
		return RecipesConfigManager.CATEGORY_OTHER;
	}

	private static Item resolveItem(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		var identifier = net.minecraft.resources.Identifier.tryParse(
			MadokuJSONManager.normalizeRegistryIdentifierForLookup(itemId)
		);
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
			Path rootDirectory = RecipesConfigManager.getRootDirectory();
			Path settingsFile = resolveJsonFile(rootDirectory, RecipesConfigManager.SETTINGS_FILE_NAME);
			JsonObject settingsRoot = JSONFormatManager.ensureManagedFile(
				settingsFile,
				RecipesConfigManager.buildRecipeSystemDefaults()
			);
			return readBoolean(settingsRoot, RecipesConfigManager.FIELD_ENABLED, true);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuRecipes settings; disabling recipe overrides.", exception);
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

