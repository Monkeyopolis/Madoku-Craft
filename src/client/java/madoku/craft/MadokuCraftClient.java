package madoku.craft;

import madoku.craft.entity.MadokuEntitiesClient;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.inventory.PlayerEntitiesInventoryClient;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.levels.MadokuLevelsClient;
import madoku.craft.network.HungerHudPayload;
import madoku.craft.network.PetAbilityHudPayload;
import madoku.craft.network.PetSoundStatePayload;
import madoku.craft.network.WorldDifficultyPayload;
import madoku.craft.network.WorldSeasonPayload;
import madoku.craft.network.WorldTimePayload;
import madoku.craft.pet.PetSoundState;
import madoku.craft.network.WorldTimeSync;
import madoku.craft.trade.MerchantEggVariantsClient;
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
		MadokuEntitiesClient.initialize();
		MadokuLevelsClient.initialize();
		PlayerEntitiesInventoryClient.initialize();
		MerchantEggVariantsClient.initialize();
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
		ClientPlayNetworking.registerGlobalReceiver(PetAbilityHudPayload.TYPE, (payload, context) ->
			MadokuHud.setPetAbilityCooldowns(payload.asArray())
		);
		ClientPlayNetworking.registerGlobalReceiver(PetSoundStatePayload.TYPE, (payload, context) ->
			PetSoundState.set(parseUuid(payload.petUuid()), payload.itemId())
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
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
