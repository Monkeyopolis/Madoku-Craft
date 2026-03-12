package madoku.craft.rarity;

import net.minecraft.ChatFormatting;

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
}
