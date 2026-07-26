package madoku.craft.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Owns configured passive, reactive, automatic, and cooldown-based pet abilities. */
public final class PetAbilitiesManager {
	private PetAbilitiesManager() {
	}

	public static void initialize() {
	}

	public static boolean hasAbility(ItemStack stack) {
		return MadokuPetManager.hasAbility(stack);
	}

	public static int cooldownTicks(ItemStack stack) {
		return MadokuPetManager.abilityCooldownTicks(stack);
	}

	public static int cooldownTicks(ServerPlayer player, int slot, ItemStack stack) {
		return MadokuPetManager.abilityCooldownTicks(player, slot, stack);
	}

	public static int cooldownTicks(Player player, int slot, ItemStack stack) {
		return MadokuPetManager.abilityCooldownTicks(player, slot, stack);
	}

	public static double playerDamageBonus(ServerPlayer player) {
		return MadokuPetManager.playerDamageAbilityBonus(player);
	}

	public static double fallDamageReduction(ServerPlayer player) {
		return MadokuPetManager.playerFallDamageAbilityReduction(player);
	}

	public static double maxHealthBonus(ServerPlayer player) {
		return MadokuPetManager.playerMaxHealthAbilityBonus(player);
	}

	public static double armorBonus(ServerPlayer player) {
		return MadokuPetManager.playerArmorAbilityBonus(player);
	}

	public static void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) {
		MadokuPetManager.applyPlayerMaxHealthAbilityBonus(player);
	}

	public static void applyPlayerArmorAbilityBonus(ServerPlayer player) {
		MadokuPetManager.applyPlayerArmorAbilityBonus(player);
	}

	public static void applyPlayerDamageAbilityBonus(ServerPlayer player) {
		MadokuPetManager.applyPlayerDamageAbilityBonus(player);
	}

	public static float applyFallDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return MadokuPetManager.applyFallDamageAbilityReduction(entity, source, amount);
	}

	public static float applyDamageBlock(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return MadokuPetManager.applyIncomingDamageBlockAbility(entity, source, amount);
	}

	public static boolean hasAbility(Entity entity) {
		return entity != null && MadokuPetManager.isManagedPet(entity);
	}
}
