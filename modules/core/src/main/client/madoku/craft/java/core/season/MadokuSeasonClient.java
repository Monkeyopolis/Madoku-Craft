package madoku.craft.java.core.season;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Client bootstrap for Core-owned seasonal climate and weather state. */
public final class MadokuSeasonClient {
	private static boolean initialized;

	private MadokuSeasonClient() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(client ->
			ClientSeasonalPrecipitationState.refresh(client.level)
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
			ClientSeasonalPrecipitationState.clear()
		);
	}
}
