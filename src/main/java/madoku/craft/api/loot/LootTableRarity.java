package madoku.craft.api.loot;

import java.util.Locale;

public enum LootTableRarity {
	COMMON("common"),
	RARE("rare"),
	EPIC("epic"),
	LEGENDARY("legendary"),
	MYTHIC("mythic");

	private final String id;

	LootTableRarity(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static LootTableRarity fromString(String value) {
		if (value == null || value.isBlank()) {
			return COMMON;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (LootTableRarity rarity : values()) {
			if (rarity.id.equals(normalized)) {
				return rarity;
			}
		}
		return COMMON;
	}
}


