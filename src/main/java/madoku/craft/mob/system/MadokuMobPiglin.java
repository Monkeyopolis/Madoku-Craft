package madoku.craft.mob.system;

import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobPiglin {
	private MadokuMobPiglin() {
	}

	public static void applySpawnOverrides(Piglin piglin, ServerLevelAccessor world) {
		MadokuMobManager.applyPiglinSpawnOverrides(piglin, world);
	}
}

