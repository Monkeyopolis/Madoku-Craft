package madoku.craft.mob.system;

import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobZombifiedPiglin {
	private MadokuMobZombifiedPiglin() {
	}

	public static void applySpawnOverrides(Zombie zombifiedPiglin, ServerLevelAccessor world) {
		MadokuMobManager.applyZombifiedPiglinSpawnOverrides(zombifiedPiglin, world);
	}
}

