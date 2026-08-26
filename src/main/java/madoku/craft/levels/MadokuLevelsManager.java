package madoku.craft.levels;

import madoku.craft.MadokuCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Orchestrates the Madoku Levels runtime and its managed subsystems. */
public final class MadokuLevelsManager {
	private static volatile boolean initialized;

	private MadokuLevelsManager() { }

	public static void initialize() {
		if (initialized) return;
		LevelsConfigManager.initialize();
		LevelsAttributesManager.initialize();
		LevelsPlayerManager.initialize();
		LevelsPayloadManager.initialize();
		initialized = true;
	}

	public static void reset() {
		LevelsPayloadManager.reset();
		LevelsPlayerManager.reset();
		LevelsAttributesManager.reset();
		LevelsConfigManager.reset();
	}

	public static boolean isInitialized() { return initialized; }
	public static boolean isEnabled() { return LevelsConfigManager.isEnabled(); }

	public static void loadPersistedData(MinecraftServer server) { LevelsPlayerManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { LevelsPlayerManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { LevelsPlayerManager.savePersistedData(server); }
	public static void flushDirtySyncs(MinecraftServer server) { LevelsPlayerManager.flushDirtySyncs(server); }
	public static void addXp(ServerPlayer player, int xpAmount) { LevelsPlayerManager.addXp(player, xpAmount); }
	public static int getPlayerHungerBonusPoints(ServerPlayer player) { return LevelsPlayerManager.getPlayerHungerBonusPoints(player); }

	static void handleLevelUpRequest(ServerPlayer player, String statId) {
		LevelsPlayerManager.upgradeStat(player, statId);
	}

	/** Shared level-stat group owned by the Madoku Levels main system. */
	public enum LevelStat {
		HEALTH("health", "Health", "health", 0xFF6B6B, 1.0d),
		HUNGER("hunger", "Hunger", "hunger", 0xF9C74F, 2.0d),
		STRENGTH("strength", "Strength", "strength", 0xF4A261, 0.2d),
		ARMOR("armor", "Armor", "defense", 0x4D96FF, 0.4d),
		LUCK("luck", "Luck", "luck", 0x6A994E, 2.0d),
		MOVEMENT_SPEED("movement-speed", "Speed", "speed", 0x43AA8B, 0.001d);

		public static final int DEFAULT_LEVEL = 0;

		private final String id;
		private final String label;
		private final String iconTextureName;
		private final int accentColor;
		private final double defaultIncrement;

		LevelStat(String id, String label, String iconTextureName, int accentColor, double defaultIncrement) {
			this.id = id;
			this.label = label;
			this.iconTextureName = iconTextureName;
			this.accentColor = accentColor;
			this.defaultIncrement = defaultIncrement;
		}

		public String id() { return id; }
		public String label() { return label; }
		public int accentColor() { return accentColor; }
		public double defaultIncrement() { return defaultIncrement; }
		public Identifier iconTexture() {
			return Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/" + iconTextureName + ".png");
		}

		public int maxLevel() { return LevelsConfigManager.stat(this).maxLevel(); }

		public int clampLevel(int level) {
			return Math.max(DEFAULT_LEVEL, Math.min(maxLevel(), level));
		}

		public String formattedValue(int level) {
			return "+" + BigDecimal.valueOf(LevelsAttributesManager.valueAtLevel(null, this, level))
				.stripTrailingZeros().toPlainString();
		}

		public static LevelStat fromId(String id) {
			if (id == null || id.isBlank()) return null;
			for (LevelStat stat : values()) if (stat.id.equalsIgnoreCase(id.trim())) return stat;
			return null;
		}

		public static EnumMap<LevelStat, Integer> createDefaultLevels() {
			EnumMap<LevelStat, Integer> levels = new EnumMap<>(LevelStat.class);
			for (LevelStat stat : values()) levels.put(stat, DEFAULT_LEVEL);
			return levels;
		}

		public static List<LevelStat> vanillaVisibleStats() {
			return List.of(HEALTH, STRENGTH, ARMOR, MOVEMENT_SPEED);
		}

		public static List<LevelStat> attributeVisibleStats() {
			return List.of(HEALTH, HUNGER, STRENGTH, ARMOR, LUCK, MOVEMENT_SPEED);
		}

		public static List<LevelStat> visibleStats() {
			if (!MadokuLevelsManager.useAttributesContainer()) return vanillaVisibleStats();
			List<LevelStat> visible = new ArrayList<>(attributeVisibleStats());
			if (!madoku.craft.attributes.MadokuHungerManager.isEnabled()) visible.remove(HUNGER);
			if (!madoku.craft.attributes.MadokuLuckManager.isEnabled()) visible.remove(LUCK);
			return List.copyOf(visible);
		}

		public static String encodeVisibleStats(Iterable<LevelStat> stats) {
			StringBuilder builder = new StringBuilder();
			if (stats != null) for (LevelStat stat : stats) {
				if (stat == null) continue;
				if (builder.length() > 0) builder.append(';');
				builder.append(stat.id);
			}
			return builder.toString();
		}

		public static List<LevelStat> decodeVisibleStats(String encodedStats) {
			List<LevelStat> decoded = new ArrayList<>();
			if (encodedStats == null || encodedStats.isBlank()) return decoded;
			for (String entry : encodedStats.split(";")) {
				LevelStat stat = fromId(entry);
				if (stat != null && !decoded.contains(stat)) decoded.add(stat);
			}
			return decoded;
		}

		public static String encodeLevels(Map<LevelStat, Integer> levels) {
			StringBuilder builder = new StringBuilder();
			for (LevelStat stat : values()) {
				if (builder.length() > 0) builder.append(';');
				builder.append(stat.id).append('=').append(stat.clampLevel(
					levels == null ? DEFAULT_LEVEL : levels.getOrDefault(stat, DEFAULT_LEVEL)));
			}
			return builder.toString();
		}

		public static EnumMap<LevelStat, Integer> decodeLevels(String encodedLevels) {
			EnumMap<LevelStat, Integer> decoded = createDefaultLevels();
			if (encodedLevels == null || encodedLevels.isBlank()) return decoded;
			for (String entry : encodedLevels.split(";")) {
				String[] pair = entry.split("=", 2);
				if (pair.length != 2) continue;
				LevelStat stat = fromId(pair[0]);
				if (stat == null) continue;
				try { decoded.put(stat, stat.clampLevel(Integer.parseInt(pair[1].trim()))); }
				catch (NumberFormatException ignored) { }
			}
			return decoded;
		}
	}

	public static boolean useAttributesContainer() {
		return madoku.craft.attributes.MadokuAttributesManager.isEnabled()
			&& (madoku.craft.attributes.MadokuHungerManager.isEnabled()
				|| madoku.craft.attributes.MadokuLuckManager.isEnabled());
	}
}
