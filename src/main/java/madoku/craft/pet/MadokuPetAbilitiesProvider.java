package madoku.craft.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

/** Built-in provider backed by the Madoku pet ability implementation. */
public final class MadokuPetAbilitiesProvider implements PetAbilitiesProvider {
	@Override public void initialize() { PetAbilitiesManager.initialize(); }
	@Override public float applyMobScanDamage(LivingEntity entity, float amount) { return PetAbilitiesManager.applyMobScanDamage(entity, amount); }
	@Override public float applyDamageVulnerabilities(LivingEntity entity, float amount) { return PetAbilitiesManager.applyDamageVulnerabilities(entity, amount); }
	@Override public boolean isWebStunned(Entity entity) { return PetAbilitiesManager.isWebStunned(entity); }
	@Override public float scaleWebMovementSpeed(LivingEntity entity, float speed) { return PetAbilitiesManager.scaleWebMovementSpeed(entity, speed); }
	@Override public Vec3 scaleWebMovement(LivingEntity entity, Vec3 movement) { return PetAbilitiesManager.scaleWebMovement(entity, movement); }
	@Override public void handlePlayerLeftClick(ServerPlayer player) { PetAbilitiesManager.handlePlayerLeftClick(player); }
	@Override public boolean hasAbility(ItemStack stack) { return PetAbilitiesManager.hasAbility(stack); }
	@Override public int cooldownTicks(ItemStack stack) { return PetAbilitiesManager.cooldownTicks(stack); }
	@Override public int cooldownTicks(ServerPlayer player, int slot, ItemStack stack) { return PetAbilitiesManager.cooldownTicks(player, slot, stack); }
	@Override public int cooldownTicks(Player player, int slot, ItemStack stack) { return PetAbilitiesManager.cooldownTicks(player, slot, stack); }
	@Override public double playerDamageBonus(ServerPlayer player) { return PetAbilitiesManager.playerDamageBonus(player); }
	@Override public double fallDamageReduction(ServerPlayer player) { return PetAbilitiesManager.fallDamageReduction(player); }
	@Override public double maxHealthBonus(ServerPlayer player) { return PetAbilitiesManager.maxHealthBonus(player); }
	@Override public double armorBonus(ServerPlayer player) { return PetAbilitiesManager.armorBonus(player); }
	@Override public void applyPlayerPassiveAbilityBonuses(ServerPlayer player) { PetAbilitiesManager.applyPlayerPassiveAbilityBonuses(player); }
	@Override public float applyFallDamage(LivingEntity entity, DamageSource source, float amount) { return PetAbilitiesManager.applyFallDamage(entity, source, amount); }
	@Override public float applyDamageBlock(LivingEntity entity, DamageSource source, float amount) { return PetAbilitiesManager.applyDamageBlock(entity, source, amount); }
	@Override public void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) { PetAbilitiesManager.applyPlayerMaxHealthAbilityBonus(player); }
	@Override public void applyPlayerArmorAbilityBonus(ServerPlayer player) { PetAbilitiesManager.applyPlayerArmorAbilityBonus(player); }
	@Override public void applyPlayerArmorToughnessAbilityBonus(ServerPlayer player) { PetAbilitiesManager.applyPlayerArmorToughnessAbilityBonus(player); }
	@Override public void applyPlayerDamageAbilityBonus(ServerPlayer player) { PetAbilitiesManager.applyPlayerDamageAbilityBonus(player); }
	@Override public boolean hasAbility(Entity entity) { return PetAbilitiesManager.hasAbility(entity); }
	@Override public boolean handleManagedChickenEggImpact(Entity projectile, HitResult hitResult) { return PetAbilitiesManager.handleManagedChickenEggImpact(projectile, hitResult); }
}
