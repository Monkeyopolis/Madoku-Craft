package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public final class MadokuMobSpider {
	private static final String SPIDER_VARIANT_DEFAULT_KEY = MobConfigManager.FIELD_DEFAULT_GROUP;
	private static final String SPIDER_VARIANT_TAG_PREFIX = "madoku-craft.spider.variant:";

	private MadokuMobSpider() {
	}

	public static boolean applySpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (spider == null || world == null || difficulty == null || !MadokuMobManager.isEnabled()) {
			return false;
		}
		if (spider.getType() != madoku.craft.entity.MadokuEntityTypes.SPIDER || spawnReason == EntitySpawnReason.JOCKEY) {
			return false;
		}

		String fileKey = MobConfigManager.FILE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}

		JsonObject fileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject spiderRoot = readMobRoot(fileRoot, fileKey);
		boolean overrideSpawnRules = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		if (!overrideSpawnRules) {
			clearSpiderVariantTag(spider);
			return false;
		}
		boolean variantEnabled = isSpiderVariantSystemEnabled(spiderRoot);
		if (spiderRoot.entrySet().isEmpty() || !variantEnabled) {
			clearSpiderVariantTag(spider);
			return false;
		}

		JsonObject defaultGroup = readObject(spiderRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			clearSpiderVariantTag(spider);
			return false;
		}

		String storedVariant = readStoredSpiderVariantKey(spider);
		if (!storedVariant.isBlank()) {
			if (normalizeKey(storedVariant).equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))) {
				return false;
			}
			JsonObject storedVariantRoot = resolveSpiderVariantRootByKey(spiderRoot, storedVariant);
			if (storedVariantRoot.entrySet().isEmpty()) {
				return false;
			}
			JsonObject effectiveStoredVariantRoot = MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, storedVariantRoot);
			return applyConfiguredSpiderVariantOutcome(spider, world, difficulty, spawnReason, effectiveStoredVariantRoot);
		}

		String selectedVariant = selectSpiderVariantKey(spiderRoot, world);
		if (selectedVariant.isBlank()) {
			selectedVariant = SPIDER_VARIANT_DEFAULT_KEY;
		}
		writeSpiderVariantTag(spider, selectedVariant);
		if (normalizeKey(selectedVariant).equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))) {
			return false;
		}
		clearExistingSkeletonPassengers(spider);
		JsonObject variantRoot = resolveSpiderVariantRootByKey(spiderRoot, selectedVariant);
		if (variantRoot.entrySet().isEmpty()) {
			return false;
		}

		JsonObject effectiveVariantRoot = MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, variantRoot);
		return applyConfiguredSpiderVariantOutcome(spider, world, difficulty, spawnReason, effectiveVariantRoot);
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
			EntityType<?> replacementType = MadokuMobManager.resolveConfiguredMobEntityType(alternativeMobRoot);
			if (replacementType != null && replacementType != madoku.craft.entity.MadokuEntityTypes.SPIDER) {
				if (replacementType == madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER) {
					MadokuMobManager.queueCaveSpiderReplacement(spider, spawnReason);
				}
				return true;
			}
		}

		JsonObject jockeyRoot = readObject(spawnRules, MobConfigManager.FIELD_MOB_JOCKEY);
		if (!jockeyRoot.entrySet().isEmpty() && readBoolean(jockeyRoot, MobConfigManager.FIELD_ENABLED, false)) {
			return MadokuMobManager.applyConfiguredMobJockey(spider, world, difficulty, variantRoot, spawnReason, false, false);
		}
		return false;
	}

	private static String selectSpiderVariantKey(JsonObject spiderRoot, ServerLevelAccessor world) {
		return MadokuMobManager.selectWeightedVariantKey(
			spiderRoot,
			world == null ? null : world.getRandom(),
			MobConfigManager.FIELD_DEFAULT_GROUP,
			SPIDER_VARIANT_DEFAULT_KEY,
			MadokuMobSpider::isReservedSpiderGroupKey,
			variantRoot -> readSpawnWeight(variantRoot, 0.0D)
		);
	}

	private static double readSpawnWeight(JsonObject root, double fallback) {
		if (root == null || root.entrySet().isEmpty()) {
			return fallback;
		}
		return MadokuMobManager.readSpawnRuleDoubleForRuntime(root, MobConfigManager.FIELD_SPAWN_WEIGHT, fallback);
	}

	private static Map<String, JsonObject> collectSpiderVariantRoots(JsonObject spiderRoot) {
		return MadokuMobManager.collectVariantRoots(spiderRoot, MadokuMobSpider::isReservedSpiderGroupKey);
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

	public static boolean shouldOverrideSpawnRules(Spider spider) {
		if (spider == null || spider.getType() != madoku.craft.entity.MadokuEntityTypes.SPIDER || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = MobConfigManager.FILE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
	}

	private static String readStoredSpiderVariantKey(Spider spider) {
		if (spider == null) {
			return "";
		}
		for (String tag : spider.entityTags()) {
			if (tag == null || !tag.startsWith(SPIDER_VARIANT_TAG_PREFIX)) {
				continue;
			}
			String normalized = normalizeKey(tag.substring(SPIDER_VARIANT_TAG_PREFIX.length()));
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private static void writeSpiderVariantTag(Spider spider, String variantKey) {
		if (spider == null || variantKey == null || variantKey.isBlank()) {
			return;
		}
		clearSpiderVariantTag(spider);
		spider.addTag(SPIDER_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
	}

	private static void clearSpiderVariantTag(Spider spider) {
		if (spider == null) {
			return;
		}
		String existing = null;
		for (String tag : spider.entityTags()) {
			if (tag != null && tag.startsWith(SPIDER_VARIANT_TAG_PREFIX)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			spider.removeTag(existing);
		}
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
			|| normalizedKey.equals(normalizeKey("spider-spawn-weight"))
			|| normalizedKey.equals(normalizeKey("cave-spider-spawn-weight"))
			|| normalizedKey.equals(normalizeKey("spider-jockey-spawn-weight"));
	}

	private static JsonObject resolveSpiderVariantRootByKey(JsonObject spiderRoot, String variantKey) {
		return MadokuMobManager.resolveVariantRootByKey(
			spiderRoot,
			variantKey,
			MobConfigManager.FIELD_DEFAULT_GROUP,
			MadokuMobSpider::isReservedSpiderGroupKey
		);
	}

	private static void clearExistingSkeletonPassengers(Spider spider) {
		for (Entity passenger : new ArrayList<>(spider.getPassengers())) {
			if (passenger.getType() == madoku.craft.entity.MadokuEntityTypes.SKELETON) {
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

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

}

