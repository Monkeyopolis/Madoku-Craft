package madoku.craft.difficulty.system;

import net.minecraft.resources.Identifier;

import java.util.Map;

public record RegionalBiomeDifficultyRuntime(
	boolean enabled,
	int defaultUnknownAdjustment,
	Map<Identifier, Integer> adjustments
) {
	public int resolveAdjustment(Identifier biomeId) {
		if (!enabled) {
			return 0;
		}
		if (biomeId == null) {
			return defaultUnknownAdjustment;
		}
		return adjustments.getOrDefault(biomeId, defaultUnknownAdjustment);
	}
}
