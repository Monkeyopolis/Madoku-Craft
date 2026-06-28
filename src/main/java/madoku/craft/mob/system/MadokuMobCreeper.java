package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.LivingEntity;

public final class MadokuMobCreeper {
	private MadokuMobCreeper() {
	}

	public static void applySpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
		MadokuMobManager.applyCreeperSpawnOverrides(creeper, world, difficulty);
	}

	public static boolean shouldOverrideSpawnRules(Creeper creeper) {
		if (creeper == null || !MadokuMobManager.isEnabled()) {
			return false;
		}
		if (!MadokuMobManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_CREEPER)) {
			return false;
		}
		JsonObject root = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_CREEPER);
		return readBoolean(root, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof Creeper creeper) || creeper.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		if (!MadokuMobManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_CREEPER)) {
			return false;
		}
		JsonObject root = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_CREEPER);
		return MadokuMobManager.applyCreeperRuntimeStats(creeper, root);
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		if (!(entity instanceof Creeper creeper) || creeper.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		return MadokuMobManager.applyCreeperLoadedEntityDifficultyOverrides(creeper);
	}

	public static void applyExplosionOverride(
		Creeper creeper,
		ServerLevel level,
		Entity source,
		double x,
		double y,
		double z,
		float vanillaPower,
		Level.ExplosionInteraction vanillaInteraction
	) {
		MadokuMobManager.applyCreeperExplosionOverride(creeper, level, source, x, y, z, vanillaPower, vanillaInteraction);
	}

	public static float resolveGriefExplosionRadius(ServerExplosion explosion, float fallbackRadius) {
		return MadokuMobManager.resolveCreeperGriefExplosionRadius(explosion, fallbackRadius);
	}

	public static float resolveFixedPlayerExplosionDamage(Creeper creeper, float fallbackExplosionRadius) {
		return MadokuMobManager.resolveFixedPlayerExplosionDamage(creeper, fallbackExplosionRadius);
	}

	public static boolean shouldUseMobExplodeBehavior(Creeper creeper) {
		return MadokuMobManager.shouldUseCreeperMobExplodeBehavior(creeper);
	}

	public static boolean shouldUseMobExplodeBehavior(LivingEntity entity) {
		return entity instanceof Creeper creeper && shouldUseMobExplodeBehavior(creeper);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}
}

