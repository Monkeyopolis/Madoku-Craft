package madoku.craft.levels;


import java.util.EnumMap;
import java.util.List;

public final class MadokuLevelsClientState {
	private static Snapshot snapshot = Snapshot.empty();
	private static int version = 0;

	private MadokuLevelsClientState() {
	}

	public static void applyPayload(LevelsPayloadAPIManager.Payload payload) {
		if (payload == null) {
			return;
		}

		snapshot = new Snapshot(
			payload.username(),
			Math.max(1, payload.level()),
			Math.max(0, payload.currentXp()),
			Math.max(1, payload.requiredXp()),
			Math.max(0, payload.availablePoints()),
			decodeMaxStatLevels(payload.maxStatLevels()),
			payload.useAttributesContainer(),
			visibleStatsForPayload(payload),
			decodeStatLevels(payload.statLevels(), decodeMaxStatLevels(payload.maxStatLevels()))
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

	private static List<LevelStat> visibleStatsForPayload(LevelsPayloadAPIManager.Payload payload) {
		List<LevelStat> decoded = LevelStat.decodeVisibleStats(payload.visibleStats());
		if (!decoded.isEmpty()) {
			return List.copyOf(decoded);
		}
		return payload.useAttributesContainer() ? LevelStat.attributeVisibleStats() : LevelStat.vanillaVisibleStats();
	}

	private static EnumMap<LevelStat, Integer> decodeMaxStatLevels(String encoded) {
		EnumMap<LevelStat, Integer> maxLevels = new EnumMap<>(LevelStat.class);
		for (LevelStat stat : LevelStat.values()) maxLevels.put(stat, stat.maxLevel());
		if (encoded == null || encoded.isBlank()) return maxLevels;
		for (String entry : encoded.split(";")) {
			String[] pair = entry.split("=", 2);
			if (pair.length != 2) continue;
			LevelStat stat = LevelStat.fromId(pair[0]);
			if (stat == null) continue;
			try { maxLevels.put(stat, Math.max(1, Integer.parseInt(pair[1].trim()))); } catch (NumberFormatException ignored) { }
		}
		return maxLevels;
	}

	private static EnumMap<LevelStat, Integer> decodeStatLevels(String encoded, EnumMap<LevelStat, Integer> maxLevels) {
		EnumMap<LevelStat, Integer> levels = LevelStat.createDefaultLevels();
		if (encoded == null || encoded.isBlank()) return levels;
		for (String entry : encoded.split(";")) {
			String[] pair = entry.split("=", 2);
			if (pair.length != 2) continue;
			LevelStat stat = LevelStat.fromId(pair[0]);
			if (stat == null) continue;
			try {
				int maximum = maxLevels.getOrDefault(stat, stat.maxLevel());
				levels.put(stat, Math.max(LevelStat.DEFAULT_LEVEL, Math.min(maximum, Integer.parseInt(pair[1].trim()))));
			} catch (NumberFormatException ignored) { }
		}
		return levels;
	}

	public record Snapshot(
		String username,
		int level,
		int currentXp,
		int requiredXp,
		int availablePoints,
		EnumMap<LevelStat, Integer> maxStatLevels,
		boolean useAttributesContainer,
		List<LevelStat> visibleStats,
		EnumMap<LevelStat, Integer> statLevels
	) {
		private static Snapshot empty() {
			return new Snapshot(
				"",
				1,
				0,
				1,
				0,
				LevelStat.createDefaultLevels(),
				false,
				LevelStat.vanillaVisibleStats(),
				LevelStat.createDefaultLevels()
			);
		}

		public boolean hasData() {
			return username != null && !username.isBlank();
		}

		public int statLevel(LevelStat stat) {
			return statLevels.getOrDefault(stat, LevelStat.DEFAULT_LEVEL);
		}

		public int maxStatLevel(LevelStat stat) {
			return maxStatLevels.getOrDefault(stat, stat == null ? 1 : stat.maxLevel());
		}
	}
}
