package madoku.craft.java.core.sync;

import madoku.craft.java.core.recipes.RecipesClientSyncAPIManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-side receiver for Core configuration synchronization. */
public final class MadokuSyncClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuSyncClient.class);
	private static boolean initialized;

	private MadokuSyncClient() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;

		ClientPlayNetworking.registerGlobalReceiver(SyncPayloadAPIManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				try {
					SyncConfigAPIManager.applyClientSnapshot(payload.configId(), payload.snapshot());
					if ("recipes".equals(payload.configId())) {
						RecipesClientSyncAPIManager.refresh();
					}
				} catch (RuntimeException exception) {
					LOGGER.warn("Failed to process synchronized configuration {}.", payload.configId(), exception);
				}
			})
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
			SyncConfigAPIManager.resetClientSynchronizedState()
		);
	}
}
