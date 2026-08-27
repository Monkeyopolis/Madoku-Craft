package madoku.craft.core.rarity;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/** Owns the shared rarity tier identifiers and presentation metadata. */
public final class RarityTierManager {
	private RarityTierManager() {
	}

	public enum Tier {
		COMMON("common", ChatFormatting.WHITE, "*"),
		RARE("rare", ChatFormatting.DARK_GREEN, "**"),
		EPIC("epic", ChatFormatting.BLUE, "***"),
		LEGENDARY("legendary", ChatFormatting.LIGHT_PURPLE, "****"),
		MYTHIC("mythic", ChatFormatting.GOLD, "*****");

		private final String id;
		private final ChatFormatting color;
		private final String inventoryIndicator;

		Tier(String id, ChatFormatting color, String inventoryIndicator) {
			this.id = id;
			this.color = color;
			this.inventoryIndicator = inventoryIndicator;
		}

		public String id() {
			return id;
		}

		public ChatFormatting color() {
			return color;
		}

		public String inventoryIndicator() {
			return inventoryIndicator;
		}
	}

	public static Tier fromString(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (Tier tier : Tier.values()) {
			if (tier.id().equals(normalized)) {
				return tier;
			}
		}
		return null;
	}
}
