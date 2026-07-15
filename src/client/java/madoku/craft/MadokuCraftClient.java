package madoku.craft;

import madoku.craft.entity.MadokuEntitiesClient;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.inventory.PlayerEntitiesInventoryClient;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.levels.MadokuLevelsClient;
import madoku.craft.api.season.SeasonPayloadManager;
import madoku.craft.api.time.TimePayloadManager;
import madoku.craft.attributes.hunger.HungerPayloadManager;
import madoku.craft.difficulty.system.DifficultyPayloadManager;
import madoku.craft.item.system.ItemProfilePayloadManager;
import madoku.craft.pet.PetAbilityHudPayloadManager;
import madoku.craft.pet.PetSoundStatePayloadManager;
import madoku.craft.pet.PetSoundState;
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
		MadokuHud.initialize();
		MadokuEntitiesClient.initialize();
		MadokuLevelsClient.initialize();
		PlayerEntitiesInventoryClient.initialize();
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
		ClientPlayNetworking.registerGlobalReceiver(TimePayloadManager.TYPE, (payload, context) -> MadokuHud.setServerTime(payload.day(), payload.hour(), payload.minute()));
		ClientPlayNetworking.registerGlobalReceiver(HungerPayloadManager.TYPE, (payload, context) ->
			MadokuHud.setServerHunger(payload.current(), payload.pending(), payload.max())
		);
		ClientPlayNetworking.registerGlobalReceiver(DifficultyPayloadManager.TYPE, (payload, context) ->
			MadokuHud.setServerDifficulty(payload.level())
		);
		ClientPlayNetworking.registerGlobalReceiver(SeasonPayloadManager.TYPE, (payload, context) ->
			context.client().execute(() -> {
				ClientSeasonalPrecipitationState.update(payload.season(), payload.temperatureOffset(), payload.humidityOffset());
				ClientSeasonalPrecipitationState.refresh(context.client().level);
				MadokuHud.setServerSeason(payload.season());
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(ItemProfilePayloadManager.TYPE, (payload, context) ->
			context.client().execute(() -> MadokuItem.applySynchronizedProfiles(payload.snapshot()))
		);
		ClientPlayNetworking.registerGlobalReceiver(PetAbilityHudPayloadManager.TYPE, (payload, context) ->
			MadokuHud.setPetAbilityCooldowns(payload.asArray())
		);
		ClientPlayNetworking.registerGlobalReceiver(PetSoundStatePayloadManager.TYPE, (payload, context) ->
			PetSoundState.set(parseUuid(payload.petUuid()), payload.itemId())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientSeasonalPrecipitationState.clear();
			MadokuHud.clearServerTime();
			MadokuHud.clearServerHunger();
			MadokuHud.clearServerDifficulty();
			MadokuHud.clearServerSeason();
			MadokuHud.clearOxygenHudState();
			MadokuHud.clearPetAbilityHudState();
			PetSoundState.clear();
		});
	}

	private static java.util.UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return java.util.UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
