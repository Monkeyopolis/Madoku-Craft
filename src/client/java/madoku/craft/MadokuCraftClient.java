package madoku.craft;

import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.levels.MadokuLevelsClient;
import madoku.craft.network.HungerHudPayload;
import madoku.craft.network.WorldDifficultyPayload;
import madoku.craft.network.WorldSeasonPayload;
import madoku.craft.network.WorldTimePayload;
import madoku.craft.network.WorldTimeSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MadokuCraftClient implements ClientModInitializer {
	private static boolean configuredItemMetadataApplied;

	@Override
	public void onInitializeClient() {
		WorldTimeSync.initialize();
		MadokuHud.initialize();
		MadokuLevelsClient.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (configuredItemMetadataApplied || client.level == null) {
				return;
			}
			configuredItemMetadataApplied = true;
			MadokuItem.applyConfiguredItemMetadata();
			MadokuFarming.applyCropItemMetadata();
		});
		ClientPlayNetworking.registerGlobalReceiver(WorldTimePayload.TYPE, (payload, context) -> MadokuHud.setServerTime(payload.day(), payload.hour(), payload.minute()));
		ClientPlayNetworking.registerGlobalReceiver(HungerHudPayload.TYPE, (payload, context) ->
			MadokuHud.setServerHunger(payload.current(), payload.pending(), payload.max())
		);
		ClientPlayNetworking.registerGlobalReceiver(WorldDifficultyPayload.TYPE, (payload, context) ->
			MadokuHud.setServerDifficulty(payload.level())
		);
		ClientPlayNetworking.registerGlobalReceiver(WorldSeasonPayload.TYPE, (payload, context) ->
			MadokuHud.setServerSeason(payload.season())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MadokuHud.clearServerTime();
			MadokuHud.clearServerHunger();
			MadokuHud.clearServerDifficulty();
			MadokuHud.clearServerSeason();
			MadokuHud.clearOxygenHudState();
		});
	}
}
