package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
public final class MadokuMobSpider {
	private static final String SPIDER_VARIANT_DEFAULT_KEY = MobConfigManager.FIELD_DEFAULT_GROUP;

	private MadokuMobSpider() {
	}

	public static void applySpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (spider == null || world == null || difficulty == null || !MadokuMobManager.isEnabled()) {
			return;
		}
		if (spider.getType() != EntityType.SPIDER || spawnReason == EntitySpawnReason.JOCKEY) {
			return;
		}

		String fileKey = MobConfigManager.FILE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}

		JsonObject fileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject spiderRoot = readMobRoot(fileRoot, fileKey);
		boolean variantEnabled = isSpiderVariantSystemEnabled(spiderRoot);
		if (spiderRoot.entrySet().isEmpty() || !variantEnabled) {
			return;
		}

		clearExistingSkeletonPassengers(spider);

		String selectedVariant = selectSpiderVariantKey(spiderRoot, world);
		if (selectedVariant.isBlank() || normalizeKey(selectedVariant).equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))) {
			return;
		}

		JsonObject defaultGroup = readObject(spiderRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		JsonObject variantRoot = resolveSpiderVariantRootByKey(spiderRoot, selectedVariant);
		if (variantRoot.entrySet().isEmpty()) {
			return;
		}

		JsonObject effectiveVariantRoot = resolveEffectiveSpiderVariantGroup(defaultGroup, variantRoot);
		applyConfiguredSpiderVariantOutcome(spider, world, difficulty, spawnReason, effectiveVariantRoot);
	}

	private static boolean applyConfiguredSpiderVariantOutcome(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		JsonObject variantRoot
	) {
		if (variantRoot == null) {
			return false;
		}

		JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
		JsonObject alternativeMobRoot = readObject(spawnRules, MobConfigManager.FIELD_SPAWN_ALTERNATIVE_MOB);
		if (!alternativeMobRoot.entrySet().isEmpty() && readBoolean(alternativeMobRoot, MobConfigManager.FIELD_ENABLED, false)) {
			EntityType<?> replacementType = resolveConfiguredMobEntityType(alternativeMobRoot);
			if (replacementType != null && replacementType != EntityType.SPIDER) {
				if (replacementType == EntityType.CAVE_SPIDER) {
					MadokuMobManager.queueCaveSpiderReplacement(spider, spawnReason);
				}
				return true;
			}
		}

		JsonObject jockeyRoot = readObject(spawnRules, MobConfigManager.FIELD_MOB_JOCKEY);
		if (!jockeyRoot.entrySet().isEmpty() && readBoolean(jockeyRoot, MobConfigManager.FIELD_ENABLED, false)) {
			EntityType<?> riderType = resolveConfiguredMobEntityType(jockeyRoot);
			if (riderType != null) {
				MadokuMobManager.queueSpiderJockeyReplacement(spider, spawnReason);
				return true;
			}
		}
		return false;
	}

	private static String selectSpiderVariantKey(JsonObject spiderRoot, ServerLevelAccessor world) {
		if (spiderRoot == null || world == null) {
			return SPIDER_VARIANT_DEFAULT_KEY;
		}
		JsonObject defaultGroup = readObject(spiderRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		double defaultWeight = Math.max(0.0D, readSpawnWeight(defaultGroup, 80.0D));
		List<SpiderVariantWeight> weightedVariants = new ArrayList<>();
		double total = defaultWeight;
		for (Map.Entry<String, JsonObject> entry : collectSpiderVariantRoots(spiderRoot).entrySet()) {
			double weight = Math.max(0.0D, readSpawnWeight(entry.getValue(), 0.0D));
			if (weight <= 0.0D) {
				continue;
			}
			total += weight;
			weightedVariants.add(new SpiderVariantWeight(entry.getKey(), weight));
		}
		if (total <= 0.0D) {
			return SPIDER_VARIANT_DEFAULT_KEY;
		}
		double roll = world.getRandom().nextDouble() * total;
		if (roll < defaultWeight) {
			return SPIDER_VARIANT_DEFAULT_KEY;
		}
		double cursor = defaultWeight;
		for (SpiderVariantWeight variant : weightedVariants) {
			cursor += variant.weight();
			if (roll < cursor) {
				return variant.key();
			}
		}
		return SPIDER_VARIANT_DEFAULT_KEY;
	}

	private static double readSpawnWeight(JsonObject root, double fallback) {
		if (root == null || root.entrySet().isEmpty()) {
			return fallback;
		}
		return MadokuMobManager.readSpawnRuleDoubleForRuntime(root, MobConfigManager.FIELD_SPAWN_WEIGHT, fallback);
	}

	private static Map<String, JsonObject> collectSpiderVariantRoots(JsonObject spiderRoot) {
		Map<String, JsonObject> variants = new java.util.LinkedHashMap<>();
		if (spiderRoot == null) {
			return variants;
		}
		for (Map.Entry<String, JsonElement> entry : spiderRoot.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			String key = normalizeKey(entry.getKey());
			if (isReservedSpiderGroupKey(key)) {
				continue;
			}
			variants.putIfAbsent(key, entry.getValue().getAsJsonObject());
		}
		return variants;
	}

	private static boolean isSpiderVariantSystemEnabled(JsonObject spiderRoot) {
		if (spiderRoot == null || spiderRoot.entrySet().isEmpty()) {
			return false;
		}
		if (readBoolean(spiderRoot, MobConfigManager.FIELD_MOB_VARIANT, false)) {
			return true;
		}
		if (!readObject(spiderRoot, MobConfigManager.FIELD_DEFAULT_GROUP).entrySet().isEmpty()) {
			return true;
		}
		return !collectSpiderVariantRoots(spiderRoot).isEmpty();
	}

	private static boolean isReservedSpiderGroupKey(String normalizedKey) {
		if (normalizedKey == null || normalizedKey.isBlank()) {
			return true;
		}
		return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_STATS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_VARIANT))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALE))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_STATS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_SPAWN_RULES))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_BEHAVIOR))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_GOALS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_SHARED_COMPONENTS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_SPIDER_SPAWN_WEIGHT))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CAVE_SPIDER_SPAWN_WEIGHT))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT));
	}

	private static JsonObject resolveSpiderVariantRootByKey(JsonObject spiderRoot, String variantKey) {
		if (spiderRoot == null || variantKey == null || variantKey.isBlank()) {
			return new JsonObject();
		}
		if (normalizeKey(variantKey).equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))) {
			return readObject(spiderRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		}
		JsonObject variant = collectSpiderVariantRoots(spiderRoot).get(normalizeKey(variantKey));
		return variant == null ? new JsonObject() : variant;
	}

	private static JsonObject resolveEffectiveSpiderVariantGroup(JsonObject defaultGroup, JsonObject variantGroup) {
		if (variantGroup == null || variantGroup.entrySet().isEmpty()) {
			return defaultGroup == null ? new JsonObject() : defaultGroup;
		}
		boolean sharedComponents = readBoolean(variantGroup, MobConfigManager.FIELD_SHARED_COMPONENTS, false);
		if (!sharedComponents) {
			return variantGroup;
		}
		JsonObject overlay = variantGroup.deepCopy();
		overlay.remove(MobConfigManager.FIELD_SHARED_COMPONENTS);
		return mergeJsonWithOverride(defaultGroup, overlay);
	}

	private static JsonObject mergeJsonWithOverride(JsonObject base, JsonObject override) {
		JsonObject merged = base == null ? new JsonObject() : base.deepCopy();
		if (override == null) {
			return merged;
		}
		deepMergeOverride(merged, override);
		return merged;
	}

	private static void deepMergeOverride(JsonObject target, JsonObject override) {
		if (target == null || override == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value != null && value.isJsonObject() && target.has(key) && target.get(key).isJsonObject()) {
				deepMergeOverride(target.getAsJsonObject(key), value.getAsJsonObject());
				continue;
			}
			target.add(key, value == null ? JsonNull.INSTANCE : value.deepCopy());
		}
	}

	private static void clearExistingSkeletonPassengers(Spider spider) {
		for (Entity passenger : new ArrayList<>(spider.getPassengers())) {
			if (passenger.getType() == EntityType.SKELETON) {
				passenger.stopRiding();
				passenger.discard();
			}
		}
	}

	private static JsonObject readMobRoot(JsonObject fileRoot, String fileKey) {
		if (fileRoot == null || fileKey == null || fileKey.isBlank()) {
			return new JsonObject();
		}
		JsonObject mainRoot = readObject(fileRoot, "main");
		if (!mainRoot.entrySet().isEmpty()) {
			return mainRoot;
		}
		JsonElement element = fileRoot.get(fileKey);
		if (element != null && element.isJsonObject()) {
			return element.getAsJsonObject();
		}
		return fileRoot;
	}

	private static EntityType<?> resolveConfiguredMobEntityType(JsonObject mobRoot) {
		if (mobRoot == null || mobRoot.entrySet().isEmpty()) {
			return null;
		}
		JsonElement mobElement = mobRoot.get(MobConfigManager.FIELD_MOB);
		if (mobElement == null || mobElement.isJsonNull()) {
			return null;
		}

		String mobId = "";
		if (mobElement.isJsonPrimitive() && mobElement.getAsJsonPrimitive().isString()) {
			mobId = mobElement.getAsString();
		} else if (mobElement.isJsonObject()) {
			JsonObject byAge = mobElement.getAsJsonObject();
			mobId = readString(byAge, MobConfigManager.FIELD_ADULT_GROUP, "");
			if (mobId.isBlank()) {
				mobId = readString(byAge, MobConfigManager.FIELD_BABY_GROUP, "");
			}
		}
		return resolveEntityTypeById(mobId);
	}

	private static EntityType<?> resolveEntityTypeById(String entityTypeId) {
		if (entityTypeId == null || entityTypeId.isBlank()) {
			return null;
		}
		Identifier id = Identifier.tryParse(entityTypeId.trim());
		if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getValue(id);
	}

	private static JsonObject readObject(JsonObject parent, String key) {
		if (parent == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : fallback;
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private record SpiderVariantWeight(String key, double weight) {}
}
