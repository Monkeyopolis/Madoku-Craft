package madoku.craft.java.compat;

import madoku.craft.java.attributes.MadokuAttributesClient;
import madoku.craft.java.core.season.PlayerClimatePayloadAPIManager;
import madoku.craft.java.core.season.SeasonPayloadAPIManager;
import madoku.craft.java.core.time.TimePayloadAPIManager;
import madoku.craft.java.hud.HudPayloadManager;
import madoku.craft.java.mob.MobPayloadManager;
import madoku.craft.java.core.season.ClientSeasonalPrecipitationState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Routes feature payloads into client-side HUD and seasonal state. */
public final class MadokuCompatClient {
	private static boolean initialized;

	private MadokuCompatClient() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		if (!MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.HUD_ID)) return;

		ClientPlayNetworking.registerGlobalReceiver(TimePayloadAPIManager.TYPE,
			(payload, context) -> HudPayloadManager.setServerTime(payload.day(), payload.hour(), payload.minute()));
		if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)) {
			MadokuAttributesClient.initialize();
			MadokuAttributesClient.addHungerListener(HudPayloadManager::setServerHunger);
		}
		if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.MOBS_ID)) {
			ClientPlayNetworking.registerGlobalReceiver(MobPayloadManager.TYPE,
				(payload, context) -> HudPayloadManager.setServerDifficulty(payload.level()));
		}
		ClientPlayNetworking.registerGlobalReceiver(SeasonPayloadAPIManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				ClientSeasonalPrecipitationState.update(
					payload.season(),
					payload.temperatureOffset(),
					payload.humidityOffset(),
					payload.weatherCondition(),
					payload.seasonDay(),
					payload.seasonLengthDays()
				);
				ClientSeasonalPrecipitationState.refresh(context.client().level);
				HudPayloadManager.setServerSeason(payload.season());
				HudPayloadManager.setServerSeasonProgress(payload.seasonDay(), payload.seasonLengthDays());
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(PlayerClimatePayloadAPIManager.TYPE,
			(payload, context) -> context.client().execute(() ->
				HudPayloadManager.setServerClimate(payload.temperature(), payload.humidity())
			));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HudPayloadManager.reset();
		});
	}
}
