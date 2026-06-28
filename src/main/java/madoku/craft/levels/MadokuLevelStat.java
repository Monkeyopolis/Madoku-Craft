package madoku.craft.levels;

import madoku.craft.armor.MadokuArmorManager;
import madoku.craft.health.MadokuHealthManager;
import madoku.craft.hunger.MadokuHungerManager;
import madoku.craft.luck.MadokuLuckManager;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum MadokuLevelStat {
	HEALTH("health", "Health", "health", 0xFF6B6B),
	PLAYER_DAMAGE("player_damage", "Strength", "strength", 0xF4A261),
	PLAYER_ARMOR("player_armor", "Defense", "defense", 0x4D96FF),
	PLAYER_LUCK("player_luck", "Luck", "luck", 0x6A994E),
	PLAYER_HUNGER("player_hunger", "Hunger", "hunger", 0xF9C74F),
	PLAYER_MOVEMENT_SPEED("player_movement_speed", "Speed", "speed", 0x43AA8B);

	public static final int DEFAULT_STAT_LEVEL = 0;

	private final String id;
	private final String label;
	private final String iconTextureName;
	private final int accentColor;

	MadokuLevelStat(String id, String label, String iconTextureName, int accentColor) {
		this.id = id;
		this.label = label;
		this.iconTextureName = iconTextureName;
		this.accentColor = accentColor;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}

	public static int maxStatLevel() {
		return MadokuLevels.maxStatLevel();
	}

	public Identifier iconTexture() {
		return Identifier.fromNamespaceAndPath("madoku-craft", "textures/icons/" + iconTextureName + ".png");
	}

	public int accentColor() {
		return accentColor;
	}

	public double valueAtLevel(int level) {
		int normalizedLevel = clampLevel(level);
		return switch (this) {
			case HEALTH -> normalizedLevel * (MadokuHealthManager.isEnabled() ? MadokuLevels.healthPerLevel() : 0.0d);
			case PLAYER_DAMAGE -> normalizedLevel * MadokuLevels.playerDamagePerLevel();
			case PLAYER_ARMOR -> normalizedLevel * (
				MadokuArmorManager.isEnabled() ? MadokuLevels.playerArmorPerLevelAttributes() : MadokuLevels.playerArmorPerLevelVanilla()
			);
			case PLAYER_LUCK -> normalizedLevel * (MadokuLuckManager.isEnabled() ? MadokuLevels.playerLuckPerLevel() : 0.0d);
			case PLAYER_HUNGER -> normalizedLevel * (MadokuHungerManager.isEnabled() ? MadokuLevels.playerHungerPerLevel() : 0.0d);
			case PLAYER_MOVEMENT_SPEED -> normalizedLevel * MadokuLevels.playerMovementSpeedPerLevel();
		};
	}

	public String formattedValue(int level) {
		if (this == PLAYER_LUCK) {
			return "+" + BigDecimal.valueOf(valueAtLevel(level) * 100.0d).stripTrailingZeros().toPlainString() + "%";
		}
		return "+" + BigDecimal.valueOf(valueAtLevel(level)).stripTrailingZeros().toPlainString();
	}

	public static int clampLevel(int level) {
		return Math.max(0, Math.min(maxStatLevel(), level));
	}

	public static MadokuLevelStat fromId(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}

		for (MadokuLevelStat stat : values()) {
			if (stat.id.equalsIgnoreCase(id.trim())) {
				return stat;
			}
		}

		return null;
	}

	public static EnumMap<MadokuLevelStat, Integer> createDefaultLevels() {
		EnumMap<MadokuLevelStat, Integer> levels = new EnumMap<>(MadokuLevelStat.class);
		for (MadokuLevelStat stat : values()) {
			levels.put(stat, DEFAULT_STAT_LEVEL);
		}
		return levels;
	}

	public static List<MadokuLevelStat> vanillaVisibleStats() {
		return List.of(HEALTH, PLAYER_DAMAGE, PLAYER_ARMOR, PLAYER_MOVEMENT_SPEED);
	}

	public static List<MadokuLevelStat> attributeVisibleStats() {
		return List.of(HEALTH, PLAYER_DAMAGE, PLAYER_ARMOR, PLAYER_LUCK, PLAYER_HUNGER, PLAYER_MOVEMENT_SPEED);
	}

	public static List<MadokuLevelStat> attributeVisibleStatsWithoutHunger() {
		return List.of(HEALTH, PLAYER_DAMAGE, PLAYER_ARMOR, PLAYER_LUCK, PLAYER_MOVEMENT_SPEED);
	}

	public static List<MadokuLevelStat> attributeVisibleStatsWithoutLuck() {
		return List.of(HEALTH, PLAYER_DAMAGE, PLAYER_ARMOR, PLAYER_HUNGER, PLAYER_MOVEMENT_SPEED);
	}

	public static String encodeVisibleStats(Iterable<MadokuLevelStat> stats) {
		StringBuilder builder = new StringBuilder();
		if (stats == null) {
			return "";
		}

		for (MadokuLevelStat stat : stats) {
			if (stat == null) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(';');
			}
			builder.append(stat.id);
		}
		return builder.toString();
	}

	public static List<MadokuLevelStat> decodeVisibleStats(String encodedStats) {
		List<MadokuLevelStat> decoded = new ArrayList<>();
		if (encodedStats == null || encodedStats.isBlank()) {
			return decoded;
		}

		String[] entries = encodedStats.split(";");
		for (String entry : entries) {
			MadokuLevelStat stat = fromId(entry);
			if (stat != null && !decoded.contains(stat)) {
				decoded.add(stat);
			}
		}
		return decoded;
	}

	public static String encodeLevels(Map<MadokuLevelStat, Integer> levels) {
		StringBuilder builder = new StringBuilder();
		for (MadokuLevelStat stat : values()) {
			if (builder.length() > 0) {
				builder.append(';');
			}
			int level = clampLevel(levels == null ? DEFAULT_STAT_LEVEL : levels.getOrDefault(stat, DEFAULT_STAT_LEVEL));
			builder.append(stat.id).append('=').append(level);
		}
		return builder.toString();
	}

	public static EnumMap<MadokuLevelStat, Integer> decodeLevels(String encodedLevels) {
		EnumMap<MadokuLevelStat, Integer> decoded = createDefaultLevels();
		if (encodedLevels == null || encodedLevels.isBlank()) {
			return decoded;
		}

		String[] entries = encodedLevels.split(";");
		for (String entry : entries) {
			String[] pair = entry.split("=", 2);
			if (pair.length != 2) {
				continue;
			}

			MadokuLevelStat stat = fromId(pair[0]);
			if (stat == null) {
				continue;
			}

			try {
				decoded.put(stat, clampLevel(Integer.parseInt(pair[1].trim())));
			} catch (NumberFormatException ignored) {
				decoded.put(stat, DEFAULT_STAT_LEVEL);
			}
		}

		return decoded;
	}
}

