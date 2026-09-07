package madoku.craft.java.core.enchant;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Public contract for the Madoku enchantment subsystem. */
public final class EnchantAPIManager {
	public static final String ENCHANT_FOLDER_NAME = "madoku-craft-core/madoku-enchants";
	public static final String ENCHANTMENTS_FOLDER_NAME = "madoku-enchantments";
	private static final EnchantProvider UNAVAILABLE_PROVIDER = new EnchantProvider() { };
	private static volatile EnchantProvider provider = UNAVAILABLE_PROVIDER;

	private EnchantAPIManager() { }

	public static void registerProvider(EnchantProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Enchant provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerTick(MinecraftServer server) { provider.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static void resetClientSynchronizedState() { provider.resetClientSynchronizedState(); }
	public static void applyClientSynchronizedSnapshot(String snapshot) { provider.applyClientSynchronizedSnapshot(snapshot); }
	public static void copyEnchantments(ItemStack source, ItemStack target) { provider.copyEnchantments(source, target); }
	public static void mergeEnchantments(ItemStack primary, ItemStack duplicate, ItemStack target) { provider.mergeEnchantments(primary, duplicate, target); }
}
