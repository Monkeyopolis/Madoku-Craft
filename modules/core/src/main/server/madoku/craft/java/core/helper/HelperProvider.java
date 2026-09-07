package madoku.craft.java.core.helper;

import net.minecraft.server.MinecraftServer;

/** Provider contract for shared helper services. */
public interface HelperProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerTick(MinecraftServer server) { }
}
