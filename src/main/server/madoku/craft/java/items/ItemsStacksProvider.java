package madoku.craft.java.items;

import com.mojang.serialization.DataResult;
import net.minecraft.server.MinecraftServer;

/** Provider contract for configurable stack sizes. */
public interface ItemsStacksProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default boolean isEnabled() { return false; }
	default int getStackLimit() { return 1; }
	default int getMaxStackCap() { return 99; }
	default int adjustStackLimit(int originalLimit) { return originalLimit; }
	default boolean shouldExtendCodecRange(int minimum, int maximum) { return false; }
	default int getCodecUpperBound(int maximum) { return maximum; }
	default DataResult<Integer> validateCodecCount(int minimum, int maximum, int value) { return DataResult.success(value); }
	default String formatCompactStackCount(int count) { return Integer.toString(count); }
	default void applySynchronizedSettings(boolean enabled, int stackLimit) { }
	default void resetClientSynchronizedSettings() { }
}
