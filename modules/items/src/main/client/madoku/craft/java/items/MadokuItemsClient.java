package madoku.craft.java.items;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Client bootstrap for Items-owned metadata application. */
public final class MadokuItemsClient {
	private static boolean initialized;
	private static boolean metadataApplied;

	private MadokuItemsClient() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (metadataApplied || client.level == null) return;
			metadataApplied = true;
			ItemsCategoriesAPIManager.applyConfiguredItemMetadata();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> metadataApplied = false);
	}
}
