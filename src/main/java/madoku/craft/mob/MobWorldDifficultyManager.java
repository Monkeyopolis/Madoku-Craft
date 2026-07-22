package madoku.craft.mob;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

/** Runtime owner for world-difficulty attribute scaling. */
public final class MobWorldDifficultyManager {
	private MobWorldDifficultyManager() {
	}

	public static void initialize() {
		WorldDifficultyConfigManager.initialize();
	}

	public static void onServerStarted(MinecraftServer server) {
	}

	public static void onServerStopped() {
		WorldDifficultyConfigManager.reset();
	}

	public static boolean isEnabled() {
		return WorldDifficultyConfigManager.isEnabled();
	}

	public static double resolveAddition(String attribute, double baseValue, Difficulty difficulty, boolean hardcore) {
		int level = switch (difficulty == null ? Difficulty.NORMAL : difficulty) {
			case PEACEFUL -> 0;
			case EASY -> 1;
			case NORMAL -> 2;
			case HARD -> hardcore ? 4 : 3;
		};
		return WorldDifficultyConfigManager.resolveAddition(attribute, baseValue, level);
	}
}
