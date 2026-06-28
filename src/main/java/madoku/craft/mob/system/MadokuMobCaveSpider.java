package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobCaveSpider {
	private MadokuMobCaveSpider() {
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
		if (spider.getType() != madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER) {
			return;
		}
		String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}
		JsonObject fileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject caveSpiderRoot = readMobRoot(fileRoot, fileKey);
		if (caveSpiderRoot.entrySet().isEmpty()) {
			return;
		}
		boolean overrideStats = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		if (overrideStats) {
			MadokuMobManager.applyUniversalBaseStatsForRuntime(spider, caveSpiderRoot);
		}
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof Spider spider) || spider.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject fileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject caveSpiderRoot = readMobRoot(fileRoot, fileKey);
		boolean overrideStats = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		return overrideStats && MadokuMobManager.applyUniversalBaseStatsForRuntime(spider, caveSpiderRoot);
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		if (!(entity instanceof Spider spider) || spider.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject fileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject caveSpiderRoot = readMobRoot(fileRoot, fileKey);
		boolean overrideStats = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		return overrideStats && MadokuMobManager.applyUniversalDifficultyStatsForRuntime(spider, caveSpiderRoot);
	}

	static boolean isCustomMobDropsEnabled(LivingEntity entity) {
		if (!(entity instanceof Spider spider) || spider.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject resolved = resolveActiveCaveSpiderRoot(spider);
		return readBoolean(resolved, MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
	}

	static String resolveMobDropsConfigReference(LivingEntity entity) {
		if (!(entity instanceof Spider spider) || !MadokuMobManager.isEnabled()) {
			return "";
		}
		String fileKey = MobConfigManager.FILE_CAVE_SPIDER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return "";
		}
		JsonObject resolved = resolveActiveCaveSpiderRoot(spider);
		JsonObject statsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_STATS);
		return readString(statsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
	}

	private static JsonObject resolveActiveCaveSpiderRoot(Spider spider) {
		if (spider == null) {
			return new JsonObject();
		}
		JsonObject fileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_CAVE_SPIDER);
		return readMobRoot(fileRoot, MobConfigManager.FILE_CAVE_SPIDER);
	}

	private static JsonObject readMobRoot(JsonObject fileRoot, String fileKey) {
		if (fileRoot == null || fileKey == null || fileKey.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = fileRoot.get(fileKey);
		JsonObject mobRoot = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		return readObject(mobRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
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
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		return element.getAsString();
	}
}

