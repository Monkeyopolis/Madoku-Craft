package madoku.craft.mob.system;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobSkeleton {
	private MadokuMobSkeleton() {
	}

	public static void applySpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		MadokuMobManager.applySkeletonSpawnOverrides(skeleton, world, difficulty, spawnReason);
	}
}

