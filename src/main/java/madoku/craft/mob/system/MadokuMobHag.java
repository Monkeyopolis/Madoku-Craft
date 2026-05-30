package madoku.craft.mob.system;

import net.minecraft.world.entity.Mob;

public final class MadokuMobHag {
	private MadokuMobHag() {
	}

	public static void applySpawnOverrides(Mob hag) {
		MadokuMobManager.applyHagSpawnOverrides(hag);
	}
}

