package madoku.craft.api.sync;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.season.SeasonPayloadManager;
import madoku.craft.api.time.TimePayloadManager;
import madoku.craft.attributes.hunger.HungerPayloadManager;
import madoku.craft.difficulty.system.DifficultyPayloadManager;
import madoku.craft.item.system.ItemProfilePayloadManager;
import madoku.craft.levels.LevelsPayloadManager;
import madoku.craft.pet.PetAbilityHudPayloadManager;
import madoku.craft.pet.PetSoundStatePayloadManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns global payload registration and transport primitives. */
public final class SyncGlobalManager {
	private static final String DEBUG_MAIN_SYSTEM = "api";
	private static final String DEBUG_SUB_SYSTEM = "sync-manager";
	private static final String DEBUG_GROUP = "sync-global-manager";
	private static volatile boolean initialized;

	private SyncGlobalManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		registerPayloadTypes();
		initialized = true;
		emitDebug("initialize", builder -> builder
			.field("clientbound-payloads", 8)
			.field("serverbound-payloads", 1));
	}

	public static void initializeClient() {
		if (initialized) {
			return;
		}
		registerPayloadTypes();
		initialized = true;
	}

	public static void reset() {
		emitDebug("reset", builder -> builder.field("initialized", initialized));
	}

	public static void onServerStarted(MinecraftServer server) {
		emitDebug("server-started", builder -> builder
			.field("players", server == null ? 0 : server.getPlayerList().getPlayers().size()));
	}

	public static void onServerStopping(MinecraftServer server) {
		emitDebug("server-stopping", builder -> builder
			.field("players", server == null ? 0 : server.getPlayerList().getPlayers().size()));
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
		registerClientbound(PetAbilityHudPayloadManager.TYPE, PetAbilityHudPayloadManager.CODEC);
		registerClientbound(PetSoundStatePayloadManager.TYPE, PetSoundStatePayloadManager.CODEC);
		registerClientbound(DifficultyPayloadManager.TYPE, DifficultyPayloadManager.CODEC);
		registerClientbound(SeasonPayloadManager.TYPE, SeasonPayloadManager.CODEC);
		registerClientbound(TimePayloadManager.TYPE, TimePayloadManager.CODEC);
		registerServerbound(LevelsPayloadManager.LevelUpPayload.TYPE, LevelsPayloadManager.LevelUpPayload.CODEC);
	}

	private static <T extends CustomPacketPayload> void registerClientbound(
		CustomPacketPayload.Type<T> type,
		StreamCodec<RegistryFriendlyByteBuf, T> codec
	) {
		PayloadTypeRegistry.playS2C().register(type, codec);
	}

	private static <T extends CustomPacketPayload> void registerServerbound(
		CustomPacketPayload.Type<T> type,
		StreamCodec<RegistryFriendlyByteBuf, T> codec
	) {
		PayloadTypeRegistry.playC2S().register(type, codec);
	}

	private static void emitDebug(String entry, java.util.function.Consumer<MadokuDebugManager.EventBuilder> customizer) {
		if (!MadokuDebugManager.shouldEmit(DEBUG_MAIN_SYSTEM, DEBUG_SUB_SYSTEM, DEBUG_GROUP, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"sync.global",
			DEBUG_MAIN_SYSTEM,
			DEBUG_SUB_SYSTEM,
			DEBUG_GROUP,
			entry
		).side(MadokuDebugManager.Side.SERVER).tick(MadokuTimeManager.getGameplayTicks()).subject(entry);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}
}
