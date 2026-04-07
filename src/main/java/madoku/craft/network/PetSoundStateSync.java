package madoku.craft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class PetSoundStateSync {
	private static boolean initialized = false;

	private PetSoundStateSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		PayloadTypeRegistry.clientboundPlay().register(PetSoundStatePayload.TYPE, PetSoundStatePayload.CODEC);
		initialized = true;
	}

	public static boolean send(ServerPlayer player, UUID petId, String itemId) {
		if (player == null || petId == null || !ServerPlayNetworking.canSend(player, PetSoundStatePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new PetSoundStatePayload(petId.toString(), itemId == null ? "" : itemId));
		return true;
	}
}
