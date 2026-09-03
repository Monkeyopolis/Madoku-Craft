package madoku.craft.java.items;

import com.mojang.serialization.DataResult;
import net.minecraft.server.MinecraftServer;

/** Public contract for configurable stack sizes and stack-count codecs. */
public final class ItemsStacksAPIManager {
	private static final ItemsStacksProvider UNAVAILABLE_PROVIDER = new ItemsStacksProvider() { };
	private static volatile ItemsStacksProvider provider = UNAVAILABLE_PROVIDER;

	private ItemsStacksAPIManager() { }
	public static void registerProvider(ItemsStacksProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Items stacks provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static int getStackLimit() { return provider.getStackLimit(); }
	public static int getMaxStackCap() { return provider.getMaxStackCap(); }
	public static int adjustStackLimit(int originalLimit) { return provider.adjustStackLimit(originalLimit); }
	public static boolean shouldExtendCodecRange(int minimum, int maximum) { return provider.shouldExtendCodecRange(minimum, maximum); }
	public static int getCodecUpperBound(int maximum) { return provider.getCodecUpperBound(maximum); }
	public static DataResult<Integer> validateCodecCount(int minimum, int maximum, int value) { return provider.validateCodecCount(minimum, maximum, value); }
	public static String formatCompactStackCount(int count) { return provider.formatCompactStackCount(count); }
	public static void applySynchronizedSettings(boolean enabled, int stackLimit) { provider.applySynchronizedSettings(enabled, stackLimit); }
	public static void resetClientSynchronizedSettings() { provider.resetClientSynchronizedSettings(); }
}
