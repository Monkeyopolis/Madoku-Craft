package madoku.craft.java.levels;

import net.minecraft.server.level.ServerPlayer;

/** Optional bridge for Attributes and Pets integrations used by Levels. */
public interface LevelsFeatureAdapter {
	default boolean useAttributesContainer() { return false; }
	default boolean isHungerEnabled() { return false; }
	default boolean isLuckEnabled() { return false; }
	default void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) { }
	default void applyPlayerDamageAbilityBonus(ServerPlayer player) { }
	default void applyPlayerArmorAbilityBonus(ServerPlayer player) { }
	default void handleMaximumHungerChanged(ServerPlayer player) { }
	default void restoreJoinHealth(ServerPlayer player) { }
}
