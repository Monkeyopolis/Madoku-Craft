package madoku.craft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class PetAbilityHudSync {
	private static boolean initialized = false;

	private PetAbilityHudSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		PayloadTypeRegistry.clientboundPlay().register(PetAbilityHudPayload.TYPE, PetAbilityHudPayload.CODEC);
		initialized = true;
	}

	public static boolean send(ServerPlayer player, int[] remainingTicks) {
		if (player == null || !ServerPlayNetworking.canSend(player, PetAbilityHudPayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, PetAbilityHudPayload.fromArray(remainingTicks));
		return true;
	}
}
