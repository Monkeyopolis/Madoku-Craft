package madoku.craft.java.core.enchant;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Provider contract for the Madoku enchantment subsystem. */
public interface EnchantProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerTick(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default boolean isEnabled() { return false; }
	default void resetClientSynchronizedState() { }
	default void applyClientSynchronizedSnapshot(String snapshot) { }
	default void copyEnchantments(ItemStack source, ItemStack target) { }
	default void mergeEnchantments(ItemStack primary, ItemStack duplicate, ItemStack target) { }
}
