package madoku.craft.api.sync;

import madoku.craft.api.season.SeasonPayloadManager;
import madoku.craft.api.time.TimePayloadManager;
import madoku.craft.attributes.hunger.HungerPayloadManager;
import madoku.craft.mob.MobPayloadManager;
import madoku.craft.item.system.ItemProfilePayloadManager;
import madoku.craft.levels.LevelsPayloadManager;
import madoku.craft.pet.PetPayloadManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns global payload registration and transport primitives. */
public final class SyncGlobalManager {
	private static volatile boolean initialized;

	private SyncGlobalManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		registerPayloadTypes();
		initialized = true;
	}

	public static void initializeClient() {
		if (initialized) {
			return;
		}
		registerPayloadTypes();
		initialized = true;
	}

	public static void reset() {
	}

	public static void onServerStarted(MinecraftServer server) {
	}

	public static void onServerStopping(MinecraftServer server) {
	}

	public static boolean canSend(ServerPlayer player, CustomPacketPayload payload) {
		return player != null
			&& payload != null
			&& ServerPlayNetworking.canSend(player, payload.type());
	}

	public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
		if (!canSend(player, payload)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	public static int broadcast(MinecraftServer server, CustomPacketPayload payload) {
		if (server == null || payload == null) {
			return 0;
		}

		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (send(player, payload)) {
				sent++;
			}
		}
		return sent;
	}

	private static void registerPayloadTypes() {
		registerClientbound(HungerPayloadManager.TYPE, HungerPayloadManager.CODEC);
		registerClientbound(ItemProfilePayloadManager.TYPE, ItemProfilePayloadManager.CODEC);
		registerClientbound(LevelsPayloadManager.Payload.TYPE, LevelsPayloadManager.Payload.CODEC);
		registerClientbound(PetPayloadManager.PetAbilityHudPayload.TYPE, PetPayloadManager.PetAbilityHudPayload.CODEC);
		registerClientbound(PetPayloadManager.PetSoundStatePayload.TYPE, PetPayloadManager.PetSoundStatePayload.CODEC);
		registerClientbound(MobPayloadManager.TYPE, MobPayloadManager.CODEC);
		registerClientbound(SeasonPayloadManager.TYPE, SeasonPayloadManager.CODEC);
		registerClientbound(TimePayloadManager.TYPE, TimePayloadManager.CODEC);
		registerServerbound(LevelsPayloadManager.LevelUpPayload.TYPE, LevelsPayloadManager.LevelUpPayload.CODEC);
	}

	private static <T extends CustomPacketPayload> void registerClientbound(
		CustomPacketPayload.Type<T> type,
		StreamCodec<RegistryFriendlyByteBuf, T> codec
	) {
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
	}

	private static <T extends CustomPacketPayload> void registerServerbound(
		CustomPacketPayload.Type<T> type,
		StreamCodec<RegistryFriendlyByteBuf, T> codec
	) {
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
	}

}
