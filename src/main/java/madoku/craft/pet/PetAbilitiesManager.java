package madoku.craft.pet;

import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetConfigManager.PetRule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;


/** Owns passive, reactive, automatic, and cooldown-based pet abilities. */
public final class PetAbilitiesManager {
	private static final Identifier PLAYER_DAMAGE_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_damage_bonus");
	private static final Identifier PLAYER_HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_max_health_bonus");
	private static final Identifier PLAYER_ARMOR_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_armor_bonus");

	private PetAbilitiesManager() {
	}

	public static void initialize() {
	}

	/** Owns the dynamic ability JSON definitions under madoku-abilities. */
	public static final class AbilitiesConfigManager {
		private static final Map<String, JsonObject> definitions = new LinkedHashMap<>();

		private AbilitiesConfigManager() {
		}

		static void reload() {
			try {
				Map<String, JsonObject> defaults = new LinkedHashMap<>();
				String[] abilityTypes = {
					MadokuPetManager.PET_ABILITY_NONE,
					MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW,
					MadokuPetManager.PET_ABILITY_WEB_PROJECTILE,
					MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE,
					MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS,
					MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION,
					MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS,
					MadokuPetManager.PET_ABILITY_ARMOR_BONUS,
					MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK,
					MadokuPetManager.PET_ABILITY_MOB_SCAN,
					MadokuPetManager.PET_ABILITY_BEE_SWARM
				};
				for (String abilityType : abilityTypes) {
					defaults.put(abilityType, PetConfigManager.PetRule.defaultsForItem("minecraft:bat_spawn_egg", abilityType));
				}
				Path abilitiesDirectory = PetConfigManager.petDirectory().resolve(PetConfigManager.ABILITY_FOLDER);
				Map<String, JsonObject> loaded = JSONFormatManager.ensureManagedFolder(
					abilitiesDirectory,
					defaults,
					fileKey -> defaults.getOrDefault(
						PetConfigManager.normalizeFileKey(fileKey),
						JSONFormatManager.object().put("ability", PetConfigManager.normalizeFileKey(fileKey)).build()
					),
					(fileKey, sourceRoot) -> true,
					null
				);
				definitions.clear();
				definitions.putAll(loaded);
			} catch (IOException | RuntimeException exception) {
				definitions.clear();
				PetConfigManager.logFailure("Failed to load Madoku pet ability definitions; using defaults.", exception);
			}
		}

		static Map<String, JsonObject> definitions() {
			return Map.copyOf(definitions);
		}
	}

	static void tickAutomaticAbilities(ServerPlayer player, long gameplayTicks) {
		MadokuPetManager.triggerAutomaticBatMobScan(player, gameplayTicks);
		MadokuPetManager.triggerAutomaticBeeSwarm(player, gameplayTicks);
	}

	static void triggerReactiveAbilities(ServerPlayer player, LivingEntity target) {
		MadokuPetManager.triggerReactivePetAttacks(player, target);
	}

	public static boolean hasAbility(ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		return rule != null && rule.hasAbility();
	}

	public static int cooldownTicks(ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		return rule == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rule.cooldownTicks));
	}

	public static int cooldownTicks(ServerPlayer player, int slot, ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (rule == null) return 0;
		if (!MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(rule.abilityType)) return cooldownTicks(stack);
		PetInventory inventory = MadokuPetManager.petInventory(player);
		int count = MadokuPetManager.countSlotsWithAbility(inventory, MadokuPetManager.PET_ABILITY_MOB_SCAN);
		return (int) Math.min(Integer.MAX_VALUE, MadokuPetManager.effectiveBatScanCooldownTicks(Math.max(1, count), rule));
	}

	public static int cooldownTicks(Player player, int slot, ItemStack stack) {
		return player instanceof ServerPlayer serverPlayer ? cooldownTicks(serverPlayer, slot, stack) : cooldownTicks(stack);
	}

	public static double playerDamageBonus(ServerPlayer player) {
		return sumAbility(player, MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS);
	}

	public static double fallDamageReduction(ServerPlayer player) {
		return Math.min(1.0D, Math.max(0.0D, sumAbility(player, MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION)));
	}

	public static double maxHealthBonus(ServerPlayer player) {
		return Math.max(0.0D, sumAbility(player, MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS));
	}

	public static double armorBonus(ServerPlayer player) {
		return Math.max(0.0D, sumAbility(player, MadokuPetManager.PET_ABILITY_ARMOR_BONUS));
	}

	private static double sumAbility(ServerPlayer player, String abilityType) {
		if (player == null || !PetConfigManager.isEnabled()) return 0.0D;
		PetInventory inventory = MadokuPetManager.petInventory(player);
		if (inventory == null) return 0.0D;
		double total = 0.0D;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
			if (rule != null && abilityType.equals(rule.abilityType)) {
				total += switch (abilityType) {
					case MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS -> rule.playerDamageBonus();
					case MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION -> rule.fallDamageReduction();
					case MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS -> rule.maxHealthBonus();
					case MadokuPetManager.PET_ABILITY_ARMOR_BONUS -> rule.armorBonus();
					default -> 0.0D;
				};
			}
		}
		return total;
	}

	public static float applyFallDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source == null || amount <= 0.0F || !source.is(DamageTypeTags.IS_FALL)) return amount;
		double reduction = fallDamageReduction(player);
		return reduction <= 0.0D ? amount : (float) Math.max(0.0D, amount * (1.0D - reduction));
	}

	public static float applyDamageBlock(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !PetConfigManager.isEnabled()) return amount;
		PetInventory inventory = MadokuPetManager.petInventory(player);
		if (inventory == null) return amount;
		long now = MadokuTimeManager.getGameplayTicks();
		for (int slot = 0; slot < Math.min(MadokuPetManager.SLOT_COUNT, inventory.getContainerSize()); slot++) {
			PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
			if (rule == null || !rule.canBlockIncomingDamage() || !MadokuPetManager.isPetSlotOffCooldown(player, slot, now)) continue;
			MadokuPetManager.setSlotCooldown(player.getUUID(), slot, now + rule.cooldownTicks);
			float blocked = (float) Math.max(0.0D, amount - rule.damageBlockAmount());
			if (blocked < amount) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.0F);
			return blocked;
		}
		return amount;
	}

	public static void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) {
		if (player == null) return;
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) return;
		attribute.removeModifier(PLAYER_HEALTH_MODIFIER);
		double bonus = maxHealthBonus(player);
		if (bonus > 0.0D) attribute.addOrUpdateTransientModifier(new AttributeModifier(PLAYER_HEALTH_MODIFIER, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	public static void applyPlayerArmorAbilityBonus(ServerPlayer player) {
		if (player == null) return;
		AttributeInstance attribute = player.getAttribute(Attributes.ARMOR);
		if (attribute == null) return;
		attribute.removeModifier(PLAYER_ARMOR_MODIFIER);
		double bonus = armorBonus(player);
		if (bonus > 0.0D) attribute.addOrUpdateTransientModifier(new AttributeModifier(PLAYER_ARMOR_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
	}

	public static void applyPlayerDamageAbilityBonus(ServerPlayer player) {
		if (player == null) return;
		AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attribute == null) return;
		attribute.removeModifier(PLAYER_DAMAGE_MODIFIER);
		double bonus = playerDamageBonus(player);
		if (bonus > 0.0D) attribute.addOrUpdateTransientModifier(new AttributeModifier(PLAYER_DAMAGE_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
	}

	public static boolean hasAbility(Entity entity) {
		return entity != null && PetComponentsManager.isManaged(entity);
	}
}
