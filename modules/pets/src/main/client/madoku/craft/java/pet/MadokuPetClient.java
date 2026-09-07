package madoku.craft.java.pet;

import madoku.craft.java.inventory.PetInventoryClient;
import madoku.craft.java.pet.entity.MadokuEntitiesClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import madoku.craft.java.pet.trade.MerchantEggVariantsClient;

/** Owns all client initialization for the Pets module. */
public final class MadokuPetClient {
	private MadokuPetClient() {
	}

	public static void initialize() {
		PetHudManagerClient.initialize();
		MadokuEntitiesClient.initialize();
		PetRendererManager.initialize();
		PetInventoryClient.initialize();
		MerchantEggVariantsClient.initialize();
		ClientPlayNetworking.registerGlobalReceiver(PetPayloadAPIManager.PetAbilityHudPayload.TYPE,
			(payload, context) -> PetHudManagerClient.setAbilityCooldowns(payload.asArray()));
	}
}
