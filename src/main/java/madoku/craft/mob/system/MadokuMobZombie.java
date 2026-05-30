package madoku.craft.mob.system;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobZombie {
	private MadokuMobZombie() {
	}

	public static void applySpawnOverrides(
		Zombie zombie,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		MadokuMobManager.applyZombieSpawnOverrides(zombie, world, difficulty, spawnReason);
	}
}

