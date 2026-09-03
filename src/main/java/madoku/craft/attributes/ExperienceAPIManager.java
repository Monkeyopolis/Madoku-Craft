package madoku.craft.attributes;

import net.minecraft.world.entity.player.Player;

/** Public contract for the experience attribute subsystem. */
public final class ExperienceAPIManager {
	private ExperienceAPIManager() {
	}

	public static void initialize() { MadokuExperienceManager.initialize(); }
	public static boolean isEnabled() { return MadokuExperienceManager.isEnabled(); }
	public static int getMaxLevel() { return MadokuExperienceManager.getMaxLevel(); }
	public static int getXpNeededForNextLevel(Player player) { return MadokuExperienceManager.getXpNeededForNextLevel(player); }
	public static int getXpNeededForLevel(int level) { return MadokuExperienceManager.getXpNeededForLevel(level); }
	public static void applyDeathPenalty(Player player) { MadokuExperienceManager.applyDeathPenalty(player); }
	public static void clampPlayerLevel(Player player) { MadokuExperienceManager.clampPlayerLevel(player); }
}
