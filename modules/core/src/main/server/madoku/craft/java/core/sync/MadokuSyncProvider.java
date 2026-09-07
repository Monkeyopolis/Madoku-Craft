package madoku.craft.java.core.sync;

import net.minecraft.server.MinecraftServer;

/** Built-in provider backed by Madoku synchronization services. */
public final class MadokuSyncProvider implements SyncProvider {
	@Override public void initialize() { MadokuSyncManager.initialize(); }
	@Override public void initializeClient() { MadokuSyncManager.initializeClient(); }
	@Override public void reset() { MadokuSyncManager.reset(); }
	@Override public boolean isInitialized() { return MadokuSyncManager.isInitialized(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuSyncManager.onServerStarted(server); }
	@Override public void onServerStopping(MinecraftServer server) { MadokuSyncManager.onServerStopping(server); }
	@Override public boolean shouldRunWorldSync(MinecraftServer server) { return MadokuSyncManager.shouldRunWorldSync(server); }
}
