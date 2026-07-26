package madoku.craft.pet;

import madoku.craft.api.sync.SyncPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Owns pet ability lore and the server-side HUD synchronization boundary. */
public final class PetHudManager {
	private PetHudManager() {
	}

	public static void initialize() {
	}

	public static void applyAbilityLore(ItemStack stack) {
		MadokuPetManager.applyAbilityLore(stack);
	}

	public static void applySupportedPetLore(ItemStack stack) {
		MadokuPetManager.applySupportedSpawnEggLore(stack);
	}

	static void sendAbilityCooldowns(ServerPlayer player, int[] remainingTicks) {
		if (player != null) {
			SyncPlayerManager.send(player, PetPayloadManager.PetAbilityHudPayload.fromArray(remainingTicks));
		}
	}

	static void sendSoundState(ServerPlayer player, String petUuid, String itemId) {
		if (player != null) {
			SyncPlayerManager.send(player, new PetPayloadManager.PetSoundStatePayload(petUuid, itemId == null ? "" : itemId));
		}
	}
}
