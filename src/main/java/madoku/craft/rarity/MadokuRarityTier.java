package madoku.craft.rarity;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum MadokuRarityTier {
	COMMON(ChatFormatting.WHITE, "*"),
	RARE(ChatFormatting.BLUE, "**"),
	EPIC(ChatFormatting.LIGHT_PURPLE, "***"),
	MYTHIC(ChatFormatting.GOLD, "****");

	private final ChatFormatting color;
	private final String inventoryIndicator;

	MadokuRarityTier(ChatFormatting color, String inventoryIndicator) {
		this.color = color;
		this.inventoryIndicator = inventoryIndicator;
	}

	public ChatFormatting color() {
		return color;
	}

	public String inventoryIndicator() {
		return inventoryIndicator;
	}

	public static MadokuRarityTier fromString(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		try {
			return valueOf(normalized);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
