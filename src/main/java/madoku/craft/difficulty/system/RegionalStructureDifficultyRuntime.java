package madoku.craft.difficulty.system;

import net.minecraft.resources.Identifier;

import java.util.Map;

public record RegionalStructureDifficultyRuntime(
	boolean enabled,
	int defaultUnknownAdjustment,
	Map<Identifier, Integer> adjustments
) {
	public int resolveAdjustment(Identifier structureId) {
		if (!enabled) {
			return 0;
		}
		if (structureId == null) {
			return defaultUnknownAdjustment;
		}
		return adjustments.getOrDefault(structureId, defaultUnknownAdjustment);
	}
}
