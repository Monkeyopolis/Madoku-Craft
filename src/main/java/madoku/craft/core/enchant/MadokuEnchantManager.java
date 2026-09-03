package madoku.craft.core.enchant;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the enchantment subsystem through its public API contract. */
public final class MadokuEnchantManager {
	private MadokuEnchantManager() {
	}

	public static void initialize() { EnchantAPIManager.initialize(); }
	public static void reset() { EnchantAPIManager.reset(); }
	public static void onServerTick(MinecraftServer server) { EnchantAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) { EnchantAPIManager.onServerStarted(server); }
}
