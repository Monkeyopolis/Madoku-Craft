package madoku.craft.loot.system;

import java.util.Locale;

public enum MadokuLootRarity {
	COMMON("common"),
	RARE("rare"),
	EPIC("epic"),
	MYTHIC("mythic");

	private final String id;

	MadokuLootRarity(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static MadokuLootRarity fromString(String value) {
		if (value == null || value.isBlank()) {
			return COMMON;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (MadokuLootRarity rarity : values()) {
			if (rarity.id.equals(normalized)) {
				return rarity;
			}
		}
		return COMMON;
	}
}

