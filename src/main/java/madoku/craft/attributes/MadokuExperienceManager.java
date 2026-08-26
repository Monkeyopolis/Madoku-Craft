package madoku.craft.attributes;

import net.minecraft.world.entity.player.Player;

/** Replaces vanilla's player experience curve with Madoku's configured curve. */
public final class MadokuExperienceManager {
	private static volatile ExperienceConfigManager.Settings settings = ExperienceConfigManager.Settings.defaults();

	private MadokuExperienceManager() { }

	public static void initialize() {
		settings = ExperienceConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static int getMaxLevel() {
		return Math.max(0, settings.levels.maxLevel);
	}

	public static int getXpNeededForNextLevel(Player player) {
		return player == null ? getXpNeededForLevel(0) : getXpNeededForLevel(player.experienceLevel);
	}

	public static int getXpNeededForLevel(int level) {
		return settings.levels.xpRequired;
	}

	public static void applyDeathPenalty(Player player) {
		if (!isEnabled() || player == null || settings.levels.deathPenalty <= 0.0d) {
			return;
		}

		double currentExperience = getCurrentExperience(player);
		if (currentExperience <= 0.0d) {
			return;
		}

		setCurrentExperience(player, currentExperience * (1.0d - settings.levels.deathPenalty));
	}

	private static double getCurrentExperience(Player player) {
		int level = Math.max(0, Math.min(getMaxLevel(), player.experienceLevel));
		double experience = 0.0d;
		for (int currentLevel = 0; currentLevel < level; currentLevel++) {
			experience += getXpNeededForLevel(currentLevel);
		}
		return experience + Math.max(0.0f, Math.min(1.0f, player.experienceProgress))
			* getXpNeededForLevel(level);
	}

	private static void setCurrentExperience(Player player, double experience) {
		int level = 0;
		double remaining = Math.max(0.0d, experience);
		int maximum = getMaxLevel();
		while (level < maximum) {
			int required = getXpNeededForLevel(level);
			if (remaining < required) {
				break;
			}
			remaining -= required;
			level++;
		}

		player.experienceLevel = level;
		player.experienceProgress = (float) Math.max(0.0d,
			Math.min(1.0d, remaining / getXpNeededForLevel(level)));
		player.totalExperience = Math.max(0,
			(int) Math.min(Integer.MAX_VALUE, Math.round(experience)));
	}

	public static void clampPlayerLevel(Player player) {
		if (!isEnabled() || player == null) {
			return;
		}
		int maximum = getMaxLevel();
		if (player.experienceLevel > maximum) {
			player.experienceLevel = maximum;
			player.experienceProgress = 0.0f;
		}
	}
}
