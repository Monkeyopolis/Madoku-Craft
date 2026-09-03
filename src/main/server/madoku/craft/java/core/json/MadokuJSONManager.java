package madoku.craft.java.core.json;

import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;

/** Orchestrates shared JSON services through their public API contract. */
public final class MadokuJSONManager {
	private MadokuJSONManager() {
	}

	public static void initialize() { JSONAPIManager.initialize(); }
	public static void reset() { JSONAPIManager.reset(); }
	public static boolean isInitialized() { return JSONAPIManager.isInitialized(); }
	public static Path getGlobalRootDirectory() { return JSONAPIManager.getGlobalRootDirectory(); }
	public static Path getOrCreateGlobalRootDirectory() { return JSONAPIManager.getOrCreateGlobalRootDirectory(); }
	public static Path getWorldRootDirectory(MinecraftServer server) { return JSONAPIManager.getWorldRootDirectory(server); }
	public static Path getOrCreateGlobalSystemDirectory(String systemName) { return JSONAPIManager.getOrCreateGlobalSystemDirectory(systemName); }
	public static Path getOrCreateWorldSystemDirectory(MinecraftServer server, String systemName) { return JSONAPIManager.getOrCreateWorldSystemDirectory(server, systemName); }
	public static void clearRuntimeState() { JSONAPIManager.clearRuntimeState(); }
}
