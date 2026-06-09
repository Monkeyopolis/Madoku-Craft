package madoku.craft.mob.system;

import com.google.gson.JsonObject;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobStray {
	private MadokuMobStray() {
	}

	public static void applySpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		MadokuMobSkeleton.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		return MadokuMobSkeleton.applyLoadedEntityOverrides(entity);
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		return MadokuMobSkeleton.applyLoadedEntityDifficultyOverrides(entity);
	}

	public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
		return MadokuMobSkeleton.shouldOverrideSpawnRules(skeleton);
	}

	public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
		return MadokuMobSkeleton.resolveRuntimeRoot(skeleton);
	}

	public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
		return MadokuMobSkeleton.isBowAttackEnabled(skeleton);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		MadokuMobSkeleton.ensureBowEquipped(skeleton);
	}

	public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		return MadokuMobSkeleton.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
	}

	public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
		return MadokuMobSkeleton.resolveBowAttackIntervalTicks(skeleton);
	}

	public static int resolveBowChargeUpTicks(AbstractSkeleton attacker) {
		return MadokuMobSkeleton.resolveBowChargeUpTicks(attacker);
	}

	public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
		MadokuMobSkeleton.tickRangedSkeletonRuntime(skeleton);
	}

	public static void onEntityCleanup(net.minecraft.world.entity.Entity entity) {
		MadokuMobSkeleton.onEntityCleanup(entity);
	}

	public static void resetRuntimeState() {
		MadokuMobSkeleton.resetRuntimeState();
	}
}
