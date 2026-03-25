package madoku.craft;

import madoku.craft.clock.MadokuTicks;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.network.HungerHudPayload;
import madoku.craft.network.WorldDifficultyPayload;
import madoku.craft.network.WorldSeasonPayload;
import madoku.craft.network.WorldTimePayload;
import madoku.craft.network.WorldTimeSync;
import madoku.craft.season.MadokuSeason;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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
			ClientPlayNetworking.registerGlobalReceiver(WorldSeasonPayload.TYPE, (payload, context) ->
				{
					MadokuHud.setServerSeason(payload.season());
					MadokuSeason.setSyncedClientSeason(payload.season());
					if (MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.sync_receive")) {
						MadokuDebug.event("season.sync_receive", MadokuDebug.Domain.SEASON)
							.side(MadokuDebug.Side.CLIENT)
							.tick(MadokuTicks.getGameplayTicks())
							.subject("world_season")
							.field("season", payload.season())
							.log();
					}
				}
			);
			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
				MadokuHud.clearServerTime();
				MadokuHud.clearServerHunger();
				MadokuHud.clearServerDifficulty();
				MadokuHud.clearServerSeason();
				MadokuSeason.clearSyncedClientSeason();
				MadokuHud.clearOxygenHudState();
			});
		}
	}
