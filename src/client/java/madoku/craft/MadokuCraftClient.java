package madoku.craft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import madoku.craft.network.HungerHudPayload;
import madoku.craft.network.WorldDifficultyPayload;
import madoku.craft.network.WorldTimePayload;
import madoku.craft.network.WorldTimeSync;

public class MadokuCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		WorldTimeSync.initialize();
		MadokuHud.initialize();
		ClientPlayNetworking.registerGlobalReceiver(WorldTimePayload.TYPE, (payload, context) -> MadokuHud.setServerTime(payload.day(), payload.hour(), payload.minute()));
			ClientPlayNetworking.registerGlobalReceiver(HungerHudPayload.TYPE, (payload, context) ->
				MadokuHud.setServerHunger(payload.current(), payload.pending(), payload.max())
			);
			ClientPlayNetworking.registerGlobalReceiver(WorldDifficultyPayload.TYPE, (payload, context) ->
				MadokuHud.setServerDifficulty(payload.level())
			);
			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
				MadokuHud.clearServerTime();
				MadokuHud.clearServerHunger();
				MadokuHud.clearServerDifficulty();
				MadokuHud.clearOxygenHudState();
			});
		}
}
