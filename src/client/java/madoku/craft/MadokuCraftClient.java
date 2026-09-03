package madoku.craft;

import madoku.craft.entity.MadokuEntitiesClient;
import madoku.craft.farming.FarmingAPIManager;
import madoku.craft.inventory.PetInventoryClient;
import madoku.craft.items.ItemsCategoriesManager;
import madoku.craft.levels.MadokuLevelsClient;
import madoku.craft.hud.HudAttributesManager;
import madoku.craft.hud.HudPayloadManager;
import madoku.craft.hud.MadokuHudManager;
import madoku.craft.attributes.HungerPayloadManager;
import madoku.craft.core.season.PlayerClimatePayloadAPIManager;
import madoku.craft.core.season.SeasonPayloadAPIManager;
import madoku.craft.core.recipes.RecipesClientSyncAPIManager;
import madoku.craft.core.sync.SyncAPIManager;
import madoku.craft.core.sync.SyncConfigAPIManager;
import madoku.craft.core.sync.SyncPayloadAPIManager;
import madoku.craft.core.time.TimePayloadAPIManager;
import madoku.craft.mob.MobPayloadManager;
import madoku.craft.pet.PetPayloadAPIManager;
import madoku.craft.pet.PetHudManagerClient;
import madoku.craft.pet.PetRendererManager;
import madoku.craft.season.ClientSeasonalPrecipitationState;
import madoku.craft.trade.MerchantEggVariantsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MadokuCraftClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuCraftClient.class);
	private static boolean configuredItemMetadataApplied;

	@Override
	public void onInitializeClient() {
		SyncAPIManager.initializeClient();
		MadokuHudManager.initialize();
		PetHudManagerClient.initialize();
		MadokuEntitiesClient.initialize();
		PetRendererManager.initialize();
		MadokuLevelsClient.initialize();
		PetInventoryClient.initialize();
		MerchantEggVariantsClient.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientSeasonalPrecipitationState.refresh(client.level);
			if (configuredItemMetadataApplied || client.level == null) {
				return;
			}
			configuredItemMetadataApplied = true;
			ItemsCategoriesManager.applyConfiguredItemMetadata();
			FarmingAPIManager.applyCropItemMetadata();
		});
		ClientPlayNetworking.registerGlobalReceiver(TimePayloadAPIManager.TYPE, (payload, context) -> HudPayloadManager.setServerTime(payload.day(), payload.hour(), payload.minute()));
		ClientPlayNetworking.registerGlobalReceiver(HungerPayloadManager.TYPE, (payload, context) ->
			HudPayloadManager.setServerHunger(payload.current(), payload.max())
		);
		ClientPlayNetworking.registerGlobalReceiver(MobPayloadManager.TYPE, (payload, context) ->
			HudPayloadManager.setServerDifficulty(payload.level())
		);
		ClientPlayNetworking.registerGlobalReceiver(SeasonPayloadAPIManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				ClientSeasonalPrecipitationState.update(payload.season(), payload.temperatureOffset(), payload.humidityOffset(), payload.weatherCondition(), payload.seasonDay(), payload.seasonLengthDays());
				ClientSeasonalPrecipitationState.refresh(context.client().level);
				HudPayloadManager.setServerSeason(payload.season());
				HudPayloadManager.setServerSeasonProgress(payload.seasonDay(), payload.seasonLengthDays());
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(PlayerClimatePayloadAPIManager.TYPE, (payload, context) ->
			context.client().execute(() -> HudPayloadManager.setServerClimate(payload.temperature(), payload.humidity()))
		);
		ClientPlayNetworking.registerGlobalReceiver(SyncPayloadAPIManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				try {
					SyncConfigAPIManager.applyClientSnapshot(payload.configId(), payload.snapshot());
					if ("recipes".equals(payload.configId())) {
						RecipesClientSyncAPIManager.refresh();
					}
				} catch (RuntimeException exception) {
					LOGGER.warn("Failed to process synchronized configuration {}.", payload.configId(), exception);
				}
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(PetPayloadAPIManager.PetAbilityHudPayload.TYPE, (payload, context) ->
			PetHudManagerClient.setAbilityCooldowns(payload.asArray())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SyncConfigAPIManager.resetClientSynchronizedState();
			configuredItemMetadataApplied = false;
			ClientSeasonalPrecipitationState.clear();
			HudPayloadManager.reset();
			PetHudManagerClient.reset();
			HudAttributesManager.reset();
		});
	}
}
