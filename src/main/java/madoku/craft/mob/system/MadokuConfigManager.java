package madoku.craft.mob.system;

/** Orchestrates configuration managers owned by the mob subsystem. */
public final class MadokuConfigManager {
	private MadokuConfigManager() {
	}

	public static void initialize() {
		MobConfigManager.initialize();
	}

	public static void reset() {
		MobConfigManager.reset();
	}
}
