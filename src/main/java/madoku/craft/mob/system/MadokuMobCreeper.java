package madoku.craft.mob.system;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobCreeper {
	private MadokuMobCreeper() {
	}

	public static void applySpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
		MadokuMobManager.applyCreeperSpawnOverrides(creeper, world, difficulty);
	}
}

