package madoku.craft.network;

import madoku.craft.item.system.MadokuItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ItemProfileSync {
	private static boolean initialized = false;

	private ItemProfileSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		PayloadTypeRegistry.clientboundPlay().register(ItemProfileSyncPayload.TYPE, ItemProfileSyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!ServerPlayNetworking.canSend(handler, ItemProfileSyncPayload.TYPE)) {
				return;
			}
			sender.sendPacket(new ItemProfileSyncPayload(MadokuItem.createClientSyncSnapshot()));
		});
		initialized = true;
	}
}
