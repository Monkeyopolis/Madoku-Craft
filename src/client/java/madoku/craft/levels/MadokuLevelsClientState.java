package madoku.craft.levels;

import madoku.craft.network.MadokuLevelsPayload;

import java.util.EnumMap;
import java.util.List;

public final class MadokuLevelsClientState {
	private static Snapshot snapshot = Snapshot.empty();
	private static int version = 0;

	private MadokuLevelsClientState() {
	}

	public static void applyPayload(MadokuLevelsPayload payload) {
		if (payload == null) {
			return;
		}

		snapshot = new Snapshot(
			payload.username(),
			Math.max(1, payload.level()),
			Math.max(0, payload.currentXp()),
			Math.max(1, payload.requiredXp()),
			Math.max(0, payload.availablePoints()),
			Math.max(1, payload.maxStatLevel()),
			payload.useAttributesContainer(),
			visibleStatsForPayload(payload),
			MadokuLevelStat.decodeLevels(payload.statLevels())
		);
		version++;
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static int version() {
		return version;
	}

	public static void clear() {
		snapshot = Snapshot.empty();
		version++;
	}

	private static List<MadokuLevelStat> visibleStatsForPayload(MadokuLevelsPayload payload) {
		List<MadokuLevelStat> decoded = MadokuLevelStat.decodeVisibleStats(payload.visibleStats());
		if (!decoded.isEmpty()) {
			return List.copyOf(decoded);
		}
		return payload.useAttributesContainer()
			? MadokuLevelStat.attributeVisibleStats()
			: MadokuLevelStat.vanillaVisibleStats();
	}

	public record Snapshot(
		String username,
		int level,
		int currentXp,
		int requiredXp,
		int availablePoints,
		int maxStatLevel,
		boolean useAttributesContainer,
		List<MadokuLevelStat> visibleStats,
		EnumMap<MadokuLevelStat, Integer> statLevels
	) {
		private static Snapshot empty() {
			return new Snapshot(
				"",
				1,
				0,
				1,
				0,
				MadokuLevelStat.maxStatLevel(),
				false,
				MadokuLevelStat.vanillaVisibleStats(),
				MadokuLevelStat.createDefaultLevels()
			);
		}

		public boolean hasData() {
			return username != null && !username.isBlank();
		}

		public int statLevel(MadokuLevelStat stat) {
			return statLevels.getOrDefault(stat, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		}
	}
}
