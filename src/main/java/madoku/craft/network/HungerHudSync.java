package madoku.craft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class HungerHudSync {
	private static boolean initialized = false;

	private HungerHudSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		PayloadTypeRegistry.clientboundPlay().register(HungerHudPayload.TYPE, HungerHudPayload.CODEC);
		initialized = true;
	}

	public static boolean send(ServerPlayer player, int current, int pending, int max) {
		if (player == null || max <= 0 || !ServerPlayNetworking.canSend(player, HungerHudPayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new HungerHudPayload(Math.max(0, current), Math.max(0, pending), Math.max(1, max)));
		return true;
	}
}

