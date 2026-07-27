package madoku.craft.pet;

import madoku.craft.api.sync.SyncPlayerManager;
import madoku.craft.pet.PetConfigManager.PetRule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Owns pet ability lore and the server-side HUD synchronization boundary. */
public final class PetHudManager {
	private static final Set<UUID> DIRTY_PLAYERS = new HashSet<>();
	private PetHudManager() {
	}

	public static void initialize() {
	}

	static void clear() {
		DIRTY_PLAYERS.clear();
	}

	static void clearPlayer(UUID playerId) {
		if (playerId != null) DIRTY_PLAYERS.remove(playerId);
	}

	static void markAbilityHudDirty(UUID playerId) {
		if (playerId != null) DIRTY_PLAYERS.add(playerId);
	}

	static void flushAbilityHudSyncs(MinecraftServer server) {
		if (server == null || DIRTY_PLAYERS.isEmpty()) return;
		List<UUID> dirtyPlayers = new ArrayList<>(DIRTY_PLAYERS);
		DIRTY_PLAYERS.clear();
		for (UUID playerId : dirtyPlayers) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) sendAbilityCooldowns(player, MadokuPetManager.currentAbilityCooldowns(playerId));
		}
	}

	public static void applyAbilityLore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (rule == null) return;
		stack.remove(DataComponents.LORE);
		List<Component> lines = new ArrayList<>();
		if (PetEntitiesManager.isPetItem(stack)) {
			lines.add(Component.literal("Level: " + PetEntitiesManager.petLevel(stack)).withStyle(ChatFormatting.AQUA));
		}
		if (!rule.abilityDescription().isBlank()) lines.add(Component.literal(rule.abilityDescription()).withStyle(ChatFormatting.GOLD));
		if (!rule.cooldownDescription().isBlank()) lines.add(Component.literal(rule.cooldownDescription()).withStyle(ChatFormatting.GRAY));
		if (!lines.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	public static void applySupportedPetLore(ItemStack stack) {
		if (PetConfigManager.isEnabled() && PetConfigManager.isValidPet(stack)) applyAbilityLore(stack);
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
