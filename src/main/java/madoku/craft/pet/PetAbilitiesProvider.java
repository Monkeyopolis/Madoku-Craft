package madoku.craft.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

/** Provider contract for Madoku pet abilities. */
public interface PetAbilitiesProvider {
	default void initialize() { }
	default float applyMobScanDamage(LivingEntity entity, float amount) { return amount; }
	default float applyDamageVulnerabilities(LivingEntity entity, float amount) { return amount; }
	default boolean isWebStunned(Entity entity) { return false; }
	default float scaleWebMovementSpeed(LivingEntity entity, float speed) { return speed; }
	default Vec3 scaleWebMovement(LivingEntity entity, Vec3 movement) { return movement; }
	default void handlePlayerLeftClick(ServerPlayer player) { }
	default boolean hasAbility(ItemStack stack) { return false; }
	default int cooldownTicks(ItemStack stack) { return 0; }
	default int cooldownTicks(ServerPlayer player, int slot, ItemStack stack) { return cooldownTicks(stack); }
	default int cooldownTicks(Player player, int slot, ItemStack stack) { return cooldownTicks(stack); }
	default double playerDamageBonus(ServerPlayer player) { return 0.0D; }
	default double fallDamageReduction(ServerPlayer player) { return 0.0D; }
	default double maxHealthBonus(ServerPlayer player) { return 0.0D; }
	default double armorBonus(ServerPlayer player) { return 0.0D; }
	default void applyPlayerPassiveAbilityBonuses(ServerPlayer player) { }
	default float applyFallDamage(LivingEntity entity, DamageSource source, float amount) { return amount; }
	default float applyDamageBlock(LivingEntity entity, DamageSource source, float amount) { return amount; }
	default void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) { }
	default void applyPlayerArmorAbilityBonus(ServerPlayer player) { }
	default void applyPlayerArmorToughnessAbilityBonus(ServerPlayer player) { }
	default void applyPlayerDamageAbilityBonus(ServerPlayer player) { }
	default boolean hasAbility(Entity entity) { return false; }
	default boolean handleManagedChickenEggImpact(Entity projectile, HitResult hitResult) { return false; }
}
