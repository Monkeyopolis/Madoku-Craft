package madoku.craft.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

/** Public contract for Madoku pet abilities and their gameplay effects. */
public final class PetAbilitiesAPIManager {
	private static final PetAbilitiesProvider UNAVAILABLE_PROVIDER = new PetAbilitiesProvider() { };
	private static volatile PetAbilitiesProvider provider = UNAVAILABLE_PROVIDER;
	private PetAbilitiesAPIManager() { }
	public static void registerProvider(PetAbilitiesProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet abilities provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static float applyMobScanDamage(LivingEntity entity, float amount) { return provider.applyMobScanDamage(entity, amount); }
	public static float applyDamageVulnerabilities(LivingEntity entity, float amount) { return provider.applyDamageVulnerabilities(entity, amount); }
	public static boolean isWebStunned(Entity entity) { return provider.isWebStunned(entity); }
	public static float scaleWebMovementSpeed(LivingEntity entity, float speed) { return provider.scaleWebMovementSpeed(entity, speed); }
	public static Vec3 scaleWebMovement(LivingEntity entity, Vec3 movement) { return provider.scaleWebMovement(entity, movement); }
	public static void handlePlayerLeftClick(ServerPlayer player) { provider.handlePlayerLeftClick(player); }
	public static boolean hasAbility(ItemStack stack) { return provider.hasAbility(stack); }
	public static int cooldownTicks(ItemStack stack) { return provider.cooldownTicks(stack); }
	public static int cooldownTicks(ServerPlayer player, int slot, ItemStack stack) { return provider.cooldownTicks(player, slot, stack); }
	public static int cooldownTicks(Player player, int slot, ItemStack stack) { return provider.cooldownTicks(player, slot, stack); }
	public static double playerDamageBonus(ServerPlayer player) { return provider.playerDamageBonus(player); }
	public static double fallDamageReduction(ServerPlayer player) { return provider.fallDamageReduction(player); }
	public static double maxHealthBonus(ServerPlayer player) { return provider.maxHealthBonus(player); }
	public static double armorBonus(ServerPlayer player) { return provider.armorBonus(player); }
	public static void applyPlayerPassiveAbilityBonuses(ServerPlayer player) { provider.applyPlayerPassiveAbilityBonuses(player); }
	public static float applyFallDamage(LivingEntity entity, DamageSource source, float amount) { return provider.applyFallDamage(entity, source, amount); }
	public static float applyDamageBlock(LivingEntity entity, DamageSource source, float amount) { return provider.applyDamageBlock(entity, source, amount); }
	public static void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) { provider.applyPlayerMaxHealthAbilityBonus(player); }
	public static void applyPlayerArmorAbilityBonus(ServerPlayer player) { provider.applyPlayerArmorAbilityBonus(player); }
	public static void applyPlayerArmorToughnessAbilityBonus(ServerPlayer player) { provider.applyPlayerArmorToughnessAbilityBonus(player); }
	public static void applyPlayerDamageAbilityBonus(ServerPlayer player) { provider.applyPlayerDamageAbilityBonus(player); }
	public static boolean hasAbility(Entity entity) { return provider.hasAbility(entity); }
	public static boolean handleManagedChickenEggImpact(Entity projectile, HitResult hitResult) { return provider.handleManagedChickenEggImpact(projectile, hitResult); }
}
