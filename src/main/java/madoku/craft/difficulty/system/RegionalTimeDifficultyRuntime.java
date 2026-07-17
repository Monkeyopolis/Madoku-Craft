package madoku.craft.difficulty.system;

import java.util.ArrayList;
import java.util.List;

public record RegionalTimeDifficultyRuntime(
	boolean enabled,
	List<TimeTier> tiers
) {
	public static RegionalTimeDifficultyRuntime defaults(boolean enabled) {
		List<TimeTier> defaults = new ArrayList<>();
		for (RegionalDifficultyConfigManager.TimeTierDefinition definition : RegionalTimeDifficultyConfigManager.defaultTimeTiers()) {
			int maxDay = definition.maxDay() < 0 ? Integer.MAX_VALUE : Math.max(definition.minDay(), definition.maxDay());
			defaults.add(new TimeTier(Math.max(0, definition.minDay()), maxDay, Math.max(0, definition.adjustment())));
		}
		return new RegionalTimeDifficultyRuntime(enabled, List.copyOf(defaults));
	}

	public int resolveAdjustment(long dayCount) {
		if (!enabled) {
			return 0;
		}
		long safeDayCount = Math.max(0L, dayCount);
		for (TimeTier tier : tiers) {
			if (tier.matches(safeDayCount)) {
				return tier.adjustment();
			}
		}
		return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).adjustment();
	}

	public record TimeTier(int minDay, int maxDay, int adjustment) {
		private boolean matches(long dayCount) {
			return dayCount >= minDay && dayCount <= maxDay;
		}
	}
}

