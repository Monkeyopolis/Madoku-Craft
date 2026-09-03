package madoku.craft.java.items;

import com.mojang.serialization.DataResult;
import net.minecraft.server.MinecraftServer;

/** Built-in provider backed by the Madoku stack-size implementation. */
public final class MadokuItemsStacksProvider implements ItemsStacksProvider {
	@Override public void initialize() { ItemsStacksManager.initialize(); }
	@Override public void reset() { ItemsStacksManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { ItemsStacksManager.onServerStarted(server); }
	@Override public boolean isEnabled() { return ItemsStacksManager.isEnabled(); }
	@Override public int getStackLimit() { return ItemsStacksManager.getStackLimit(); }
	@Override public int getMaxStackCap() { return ItemsStacksManager.getMaxStackCap(); }
	@Override public int adjustStackLimit(int originalLimit) { return ItemsStacksManager.adjustStackLimit(originalLimit); }
	@Override public boolean shouldExtendCodecRange(int minimum, int maximum) { return ItemsStacksManager.shouldExtendCodecRange(minimum, maximum); }
	@Override public int getCodecUpperBound(int maximum) { return ItemsStacksManager.getCodecUpperBound(maximum); }
	@Override public DataResult<Integer> validateCodecCount(int minimum, int maximum, int value) { return ItemsStacksManager.validateCodecCount(minimum, maximum, value); }
	@Override public String formatCompactStackCount(int count) { return ItemsStacksManager.formatCompactStackCount(count); }
	@Override public void applySynchronizedSettings(boolean enabled, int stackLimit) { ItemsStacksManager.applySynchronizedSettings(enabled, stackLimit); }
	@Override public void resetClientSynchronizedSettings() { ItemsStacksManager.resetClientSynchronizedSettings(); }
}
