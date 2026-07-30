package madoku.craft;

import madoku.craft.entity.MadokuEntitiesClient;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.inventory.PetInventoryClient;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.levels.MadokuLevelsClient;
import madoku.craft.hud.HudAttributesManager;
import madoku.craft.hud.HudPayloadManager;
import madoku.craft.hud.MadokuHudManager;
import madoku.craft.api.season.SeasonPayloadManager;
import madoku.craft.api.time.TimePayloadManager;
import madoku.craft.attributes.hunger.HungerPayloadManager;
import madoku.craft.mob.MobPayloadManager;
import madoku.craft.item.system.ItemProfilePayloadManager;
import madoku.craft.pet.PetPayloadManager;
import madoku.craft.pet.PetHudManagerClient;
import madoku.craft.pet.PetRendererManager;
import madoku.craft.api.sync.MadokuSyncManager;
import madoku.craft.season.ClientSeasonalPrecipitationState;
import madoku.craft.trade.MerchantEggVariantsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MadokuCraftClient implements ClientModInitializer {
	private static boolean configuredItemMetadataApplied;

	@Override
	public void onInitializeClient() {
		MadokuSyncManager.initializeClient();
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
			MadokuItem.applyConfiguredItemMetadata();
			MadokuFarming.applyCropItemMetadata();
		});
		ClientPlayNetworking.registerGlobalReceiver(TimePayloadManager.TYPE, (payload, context) -> HudPayloadManager.setServerTime(payload.day(), payload.hour(), payload.minute()));
		ClientPlayNetworking.registerGlobalReceiver(HungerPayloadManager.TYPE, (payload, context) ->
			HudPayloadManager.setServerHunger(payload.current(), payload.pending(), payload.max())
		);
		ClientPlayNetworking.registerGlobalReceiver(MobPayloadManager.TYPE, (payload, context) ->
			HudPayloadManager.setServerDifficulty(payload.level())
		);
		ClientPlayNetworking.registerGlobalReceiver(SeasonPayloadManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				ClientSeasonalPrecipitationState.update(payload.season(), payload.temperatureOffset(), payload.humidityOffset(), payload.weatherCondition(), payload.seasonDay(), payload.seasonLengthDays());
				ClientSeasonalPrecipitationState.refresh(context.client().level);
				HudPayloadManager.setServerSeason(payload.season());
				HudPayloadManager.setServerSeasonProgress(payload.seasonDay(), payload.seasonLengthDays());
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(ItemProfilePayloadManager.TYPE, (payload, context) ->
			context.client().execute(() -> MadokuItem.applySynchronizedProfiles(payload.snapshot()))
		);
		ClientPlayNetworking.registerGlobalReceiver(PetPayloadManager.PetAbilityHudPayload.TYPE, (payload, context) ->
			PetHudManagerClient.setAbilityCooldowns(payload.asArray())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientSeasonalPrecipitationState.clear();
			HudPayloadManager.reset();
			PetHudManagerClient.reset();
			HudAttributesManager.reset();
		});
	}
}
