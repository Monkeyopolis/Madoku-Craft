package madoku.craft.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.entity.Hag;
import madoku.craft.mob.MobEntityManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import java.util.*;

import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetConfigManager.PetAbilityRule;
import madoku.craft.pet.PetConfigManager.PetRule;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.level.ClipContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/** Owns passive, reactive, automatic, and cooldown-based pet abilities. */
public final class PetAbilitiesManager {
	private static final Identifier PLAYER_DAMAGE_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_damage_bonus");
	private static final Identifier PLAYER_HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_max_health_bonus");
	private static final Identifier PLAYER_ARMOR_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_armor_bonus");
	private static final Identifier PLAYER_ARMOR_TOUGHNESS_MODIFIER = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_armor_toughness_bonus");

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PetAbilitiesManager.class);
	private static final int SLOT_COUNT = PetEntitiesManager.SLOT_COUNT;
	static final String TASK_TYPE_PET_ATTACK = "pet_attack";
	private static final String PET_ABILITY_RANGED_HOMING_ARROW = MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW;
	private static final String PET_ABILITY_WEB_PROJECTILE = MadokuPetManager.PET_ABILITY_WEB_PROJECTILE;
	private static final String PET_ABILITY_EXPLOSIVE_PROJECTILE = MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE;
	private static final String PET_ABILITY_EGG_PROJECTILE = MadokuPetManager.PET_ABILITY_EGG_PROJECTILE;
	private static final String PET_ABILITY_PLAYER_DAMAGE_BONUS = MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS;
	private static final String PET_ABILITY_FALL_DAMAGE_REDUCTION = MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION;
	private static final String PET_ABILITY_MAX_HEALTH_BONUS = MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS;
	private static final String PET_ABILITY_ARMOR_BONUS = MadokuPetManager.PET_ABILITY_ARMOR_BONUS;
	private static final String PET_ABILITY_DAMAGE_BLOCK = MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK;
	private static final String PET_ABILITY_HEALTH_REGENERATION = MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION;
	private static final String PET_ABILITY_MOB_SCAN = MadokuPetManager.PET_ABILITY_MOB_SCAN;
	private static final String PET_ABILITY_BEE_SWARM = MadokuPetManager.PET_ABILITY_BEE_SWARM;
	private static final String PLAYER_SCHEDULER_KEY = "player_entities";
	private static final int WEB_PROJECTILE_LIFETIME_TICKS = 20;
	private static final int WEB_PROJECTILE_BASE_RICOCHETS = 3;
	private static final double WEB_PROJECTILE_RICOCHET_RADIUS = 4.0D;
	private static final double WEB_PROJECTILE_RICOCHETS_PER_LEVEL = 0.25D;
	private static final int WEB_PROJECTILE_DUPLICATE_DAMAGE = 1;
	private static final int WEB_PROJECTILE_DUPLICATE_STUN_TICKS = 10;
	private static final long WEB_PROJECTILE_COOLDOWN_TICKS = 30L * 20L;
	private static final double HEALTH_REGEN_BASE_PERCENTAGE = 0.05D;
	private static final long HEALTH_REGEN_BASE_DURATION_TICKS = 3L * 20L;
	private static final long HEALTH_REGEN_DUPLICATE_DURATION_TICKS = 20L;
	private static final long HEALTH_REGEN_TICK_INTERVAL_TICKS = 20L;
	private static final double WEB_PROJECTILE_HIT_DISTANCE = 0.75D;
	private static final double WEB_PROJECTILE_MIN_SPEED = 0.20D;
	private static final int EXPLOSIVE_PROJECTILE_LIFETIME_TICKS = 20;
	private static final double EXPLOSIVE_PROJECTILE_HIT_DISTANCE = 1.0D;
	private static final double EXPLOSIVE_PROJECTILE_MIN_SPEED = 0.5D;
	private static final double LEFT_CLICK_TARGET_RANGE = 32.0D;
	private static final long CHICKEN_EGG_COOLDOWN_TICKS = 15L * 20L;
	private static final int CHICKEN_EGG_BASE_PROJECTILE_COUNT = 3;
	private static final long CHICKEN_EGG_PROJECTILE_DELAY_TICKS = 4L;
	private static final int CHICKEN_EGG_LIFETIME_TICKS = 100;
	private static final double CHICKEN_FALL_BASE_REDUCTION = 0.20D;
	private static final double CHICKEN_FALL_EXTRA_REDUCTION = 0.10D;
	private static final double CHICKEN_FALL_LEVEL_REDUCTION = 0.025D;
	private static final int BAT_SCAN_BASE_RADIUS_BLOCKS = 24;
	private static final int BAT_SCAN_VERTICAL_RADIUS_PER_EXTRA_BAT = 4;
	private static final long BAT_SCAN_BASE_GLOWING_DURATION_TICKS = 60L * 20L;
	private static final long BAT_SCAN_GLOWING_DURATION_PER_LEVEL_TICKS = 2L * 20L + 10L;
	private static final long BAT_SCAN_GLOWING_DURATION_PER_EXTRA_BAT_TICKS = 5L * 20L;
	private static final float BAT_SCAN_BASE_VULNERABILITY = 0.15F;
	private static final float BAT_SCAN_VULNERABILITY_PER_LEVEL = 0.025F;
	private static final float BAT_SCAN_VULNERABILITY_PER_EXTRA_BAT = 0.05F;
	private static final String MOB_SCAN_VULNERABILITY_TAG = "madoku-craft.mob-scan-vulnerability";
	private static final long BAT_SCAN_COOLDOWN_REDUCTION_PER_EXTRA_BAT = 5L * 20L;
	private static final long BAT_SCAN_COOLDOWN_REDUCTION_PER_LEVEL = 2L * 20L + 10L;
	private static final double BEE_SWARM_SCAN_RADIUS = 20.0D;
	private static final double BEE_SWARM_SCAN_VERTICAL_RADIUS = 10.0D;
	private static final int BEE_SWARM_MIN_TARGET_SCAN_INTERVAL_TICKS = 4;
	private static final int BEE_SWARM_MAX_TARGET_SCAN_INTERVAL_TICKS = 20;
	private static final int BEE_SWARM_MAX_TARGET_CANDIDATES = 4;
	private static final String BEE_TARGET_SCAN_SCHEDULER_OWNER_ID = "madoku-pets-bee-target-scan";
	private static final long BEE_SWARM_MAX_TARGET_DURATION_TICKS = 15L * 20L;
	private static final long BEE_SWARM_DAMAGE_INTERVAL_TICKS = 20L;
	private static final float BEE_SWARM_DEFAULT_DAMAGE_PER_SECOND = 1.6F;
	private static final double BEE_SWARM_ORBIT_RADIUS_BASE = 0.70D;
	private static final double BEE_SWARM_ORBIT_RADIUS_VARIANCE = 0.30D;
	private static final double BEE_SWARM_ORBIT_VERTICAL_VARIANCE = 0.30D;
	private static final double BEE_SWARM_MAX_MOVE_PER_TICK = 0.38D;
	private static final String FIELD_SLOT = "slot";
	private static final String FIELD_ABILITY_ID = "ability-id";
	private static final String FIELD_TARGET_UUID = "target-uuid";
	private static final String FIELD_SPAWN_X = "spawn-x";
	private static final String FIELD_SPAWN_Y = "spawn-y";
	private static final String FIELD_SPAWN_Z = "spawn-z";
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Map<UUID, Map<Integer, Map<String, Long>>> PLAYER_ABILITY_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_BEE_TARGET_SCAN_TICK = new HashMap<>();
	private static final Map<UUID, WebProjectileState> ACTIVE_WEB_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, WebControlState> ACTIVE_WEB_CONTROLS = new ConcurrentHashMap<>();
	private static final Map<UUID, HealthRegenerationState> ACTIVE_HEALTH_REGENERATIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, ExplosiveProjectileState> ACTIVE_EXPLOSIVE_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, ChickenEggProjectileState> ACTIVE_CHICKEN_EGG_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, ChickenEggVolleyState> ACTIVE_CHICKEN_EGG_VOLLEYS = new ConcurrentHashMap<>();
	private static final Map<String, BeeSwarmState> ACTIVE_BEE_SWARMS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> MOB_SCAN_VULNERABILITY_BY_ENTITY = new ConcurrentHashMap<>();
	private static final Map<UUID, ExplosiveVulnerabilityState> EXPLOSIVE_VULNERABILITY_BY_ENTITY = new ConcurrentHashMap<>();

	static void reset() {
		PLAYER_SCHEDULER_IDS.clear();
		PLAYER_ABILITY_COOLDOWNS.clear();
		NEXT_BEE_TARGET_SCAN_TICK.clear();
		MadokuSchedulerManager.clearAdaptiveDelayState(BEE_TARGET_SCAN_SCHEDULER_OWNER_ID);
		ACTIVE_WEB_PROJECTILES.clear();
		ACTIVE_WEB_CONTROLS.clear();
		ACTIVE_HEALTH_REGENERATIONS.clear();
		ACTIVE_EXPLOSIVE_PROJECTILES.clear();
		ACTIVE_CHICKEN_EGG_PROJECTILES.clear();
		ACTIVE_CHICKEN_EGG_VOLLEYS.clear();
		ACTIVE_BEE_SWARMS.clear();
		MOB_SCAN_VULNERABILITY_BY_ENTITY.clear();
		EXPLOSIVE_VULNERABILITY_BY_ENTITY.clear();
	}

	static void tickWebControls(MinecraftServer server) {
		if (server == null || ACTIVE_WEB_CONTROLS.isEmpty()) {
			return;
		}
		long now = MadokuTimeManager.getGameplayTicks();
		EXPLOSIVE_VULNERABILITY_BY_ENTITY.entrySet().removeIf(entry ->
			entry.getValue() == null || now >= entry.getValue().expiresAtTick
		);
		for (Map.Entry<UUID, WebControlState> entry : ACTIVE_WEB_CONTROLS.entrySet()) {
			WebControlState state = entry.getValue();
			if (state == null) {
				ACTIVE_WEB_CONTROLS.remove(entry.getKey());
				continue;
			}
			if (now >= state.slowUntilTick) {
				boolean liveEntityLoaded = false;
				for (ServerLevel level : server.getAllLevels()) {
					if (level.getEntity(entry.getKey()) instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
						liveEntityLoaded = true;
						break;
					}
				}
				if (!liveEntityLoaded) {
					ACTIVE_WEB_CONTROLS.remove(entry.getKey(), state);
				}
			}
		}
	}

	static void tickHealthRegeneration(MinecraftServer server) {
		if (server == null || ACTIVE_HEALTH_REGENERATIONS.isEmpty()) {
			return;
		}
		long now = MadokuTimeManager.getGameplayTicks();
		for (Map.Entry<UUID, HealthRegenerationState> entry : ACTIVE_HEALTH_REGENERATIONS.entrySet()) {
			HealthRegenerationState state = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (state == null || player == null || !player.isAlive() || now >= state.untilTick) {
				ACTIVE_HEALTH_REGENERATIONS.remove(entry.getKey(), state);
				continue;
			}

			long nextHealTick = state.nextHealTick;
			while (nextHealTick < state.untilTick && nextHealTick <= now) {
				applyHealthRegenerationHeal(player, state.healPercentage);
				nextHealTick += HEALTH_REGEN_TICK_INTERVAL_TICKS;
			}
			if (nextHealTick != state.nextHealTick) {
				ACTIVE_HEALTH_REGENERATIONS.put(
					entry.getKey(),
					new HealthRegenerationState(nextHealTick, state.untilTick, state.healPercentage)
				);
			}
		}
	}

	public static float applyMobScanDamage(LivingEntity entity, float amount) {
		if (entity == null || amount <= 0.0F) {
			return amount;
		}
		if (!entity.entityTags().contains(MOB_SCAN_VULNERABILITY_TAG)) {
			MOB_SCAN_VULNERABILITY_BY_ENTITY.remove(entity.getUUID());
			return amount;
		}
		Float vulnerability = MOB_SCAN_VULNERABILITY_BY_ENTITY.get(entity.getUUID());
		if (vulnerability == null) {
			entity.removeTag(MOB_SCAN_VULNERABILITY_TAG);
			return amount;
		}
		if (!entity.hasEffect(MobEffects.GLOWING)) {
			entity.removeTag(MOB_SCAN_VULNERABILITY_TAG);
			MOB_SCAN_VULNERABILITY_BY_ENTITY.remove(entity.getUUID());
			return amount;
		}
		return amount * (1.0F + vulnerability);
	}

	public static float applyDamageVulnerabilities(LivingEntity entity, float amount) {
		return applyExplosiveVulnerabilityDamage(entity, applyMobScanDamage(entity, amount));
	}

	private static float applyExplosiveVulnerabilityDamage(LivingEntity entity, float amount) {
		if (entity == null || amount <= 0.0F) {
			return amount;
		}
		ExplosiveVulnerabilityState state = EXPLOSIVE_VULNERABILITY_BY_ENTITY.get(entity.getUUID());
		long now = MadokuTimeManager.getGameplayTicks();
		if (state == null || now >= state.expiresAtTick) {
			if (state != null) {
				EXPLOSIVE_VULNERABILITY_BY_ENTITY.remove(entity.getUUID(), state);
			}
			return amount;
		}
		return amount * (1.0F + state.vulnerability);
	}

	private static void addExplosiveVulnerability(LivingEntity entity, float vulnerability, int durationTicks) {
		if (entity == null || vulnerability <= 0.0F || durationTicks <= 0) {
			return;
		}
		long now = MadokuTimeManager.getGameplayTicks();
		ExplosiveVulnerabilityState existing = EXPLOSIVE_VULNERABILITY_BY_ENTITY.get(entity.getUUID());
		if (existing != null && now >= existing.expiresAtTick) {
			existing = null;
		}
		float appliedVulnerability = existing == null ? vulnerability : vulnerability * 0.5F;
		int appliedDurationTicks = existing == null ? durationTicks : halfDurationTicks(durationTicks);
		float totalVulnerability = appliedVulnerability + (existing == null ? 0.0F : existing.vulnerability);
		long expiresAt = (existing == null ? now : Math.max(now, existing.expiresAtTick)) + appliedDurationTicks;
		EXPLOSIVE_VULNERABILITY_BY_ENTITY.put(
			entity.getUUID(),
			new ExplosiveVulnerabilityState(totalVulnerability, expiresAt)
		);
	}

	public static boolean isWebStunned(Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity) || entity instanceof Player) {
			return false;
		}
		WebControlState state = ACTIVE_WEB_CONTROLS.get(livingEntity.getUUID());
		return state != null && MadokuTimeManager.getGameplayTicks() < state.stunUntilTick;
	}

	public static float scaleWebMovementSpeed(LivingEntity entity, float speed) {
		if (entity == null || speed <= 0.0F) {
			return speed;
		}
		WebControlState state = ACTIVE_WEB_CONTROLS.get(entity.getUUID());
		if (state == null) {
			return speed;
		}
		long now = MadokuTimeManager.getGameplayTicks();
		if (now < state.stunUntilTick) {
			return 0.0F;
		}
		if (now >= state.slowUntilTick) {
			return speed;
		}
		return (float) (speed * Math.max(0.0D, 1.0D - state.slowPercentage));
	}

	public static Vec3 scaleWebMovement(LivingEntity entity, Vec3 movement) {
		if (entity == null || movement == null) {
			return movement;
		}
		WebControlState state = ACTIVE_WEB_CONTROLS.get(entity.getUUID());
		if (state == null) {
			return movement;
		}
		long now = MadokuTimeManager.getGameplayTicks();
		if (now < state.stunUntilTick || now >= state.slowUntilTick) {
			return movement;
		}
		double multiplier = Math.max(0.0D, 1.0D - state.slowPercentage);
		return movement.scale(multiplier);
	}

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
					MadokuPetManager.PET_ABILITY_EGG_PROJECTILE,
					MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS,
					MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION,
					MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS,
					MadokuPetManager.PET_ABILITY_ARMOR_BONUS,
					MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK,
					MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION,
					MadokuPetManager.PET_ABILITY_MOB_SCAN,
					MadokuPetManager.PET_ABILITY_BEE_SWARM
				};
				for (String abilityType : abilityTypes) {
					defaults.put(PetConfigManager.abilityConfigId(abilityType), PetConfigManager.PetRule.defaultsForAbility(abilityType));
				}
				Path abilitiesDirectory = PetConfigManager.petDirectory().resolve(PetConfigManager.ABILITY_FOLDER);
				Map<String, JsonObject> loaded = JSONFormatManager.ensureManagedFolder(
					abilitiesDirectory,
					defaults,
					fileKey -> defaults.getOrDefault(
						PetConfigManager.abilityConfigId(fileKey),
						PetConfigManager.PetRule.defaultsForAbility(PetConfigManager.normalizeAbilityId(fileKey))
					),
					(fileKey, sourceRoot) -> true,
					null
				);
				definitions.clear();
				for (Map.Entry<String, JsonObject> entry : loaded.entrySet()) {
					JsonObject abilityGroup = PetConfigManager.objectField(entry.getValue(), "ability-id");
					String abilityId = PetConfigManager.normalizeAbilityId(PetConfigManager.getString(abilityGroup, "id", entry.getKey()));
					definitions.put(abilityId, entry.getValue());
				}
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
		triggerAutomaticBatMobScan(player, gameplayTicks);
		triggerAutomaticBeeSwarm(player, gameplayTicks);
	}

	static void triggerReactiveAbilities(ServerPlayer player, LivingEntity target) {
		triggerReactivePetAttacks(player, target);
	}

	/** Handles a server-side main-hand left-click for multi-ability pets. */
	public static void handlePlayerLeftClick(ServerPlayer player) {
		if (player == null || !player.isAlive()) {
			return;
		}
		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}

		long now = MadokuTimeManager.getGameplayTicks();
		int chickenCount = 0;
		double levelBonus = 0.0D;
		float damage = 0.0F;
		float radius = 0.0F;
		boolean hasReadyTargetedPet = false;
		int[] chickenSlots = new int[SLOT_COUNT];
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule baseRule = PetConfigManager.resolvePetRule(PetEntitiesManager.petId(stack));
			PetRule rule = baseRule == null ? null : baseRule.atLevel(PetEntitiesManager.petLevel(stack));
			if (rule != null && rule.enabled) {
				for (PetAbilityRule ability : rule.reactiveAbilities()) {
					if (isLeftClickProjectileAbility(ability) && isAbilityOffCooldown(player, slot, ability.abilityType, now)) {
						hasReadyTargetedPet = true;
						break;
					}
				}
			}
			PetAbilityRule eggAbility = rule == null ? null : rule.ability(PET_ABILITY_EGG_PROJECTILE);
			if (rule == null || !rule.enabled || !"minecraft:chicken".equals(rule.petId) || eggAbility == null
				|| !isAbilityOffCooldown(player, slot, eggAbility.abilityType, now)) {
				continue;
			}
			chickenSlots[chickenCount++] = slot;
			int level = PetEntitiesManager.petLevel(stack);
			levelBonus += Math.max(0, level - 1) * 0.5D;
			damage = Math.max(damage, eggAbility.attackDamage);
			radius = Math.max(radius, eggAbility.explosionRadius);
		}
		if (chickenCount <= 0 && !hasReadyTargetedPet) {
			return;
		}
		LivingEntity target = hasReadyTargetedPet ? resolveLeftClickTarget(player) : null;
		if (target == null && isLeftClickTargetingBlock(player)) {
			return;
		}
		if (chickenCount <= 0 || ACTIVE_CHICKEN_EGG_VOLLEYS.containsKey(player.getUUID())) {
		} else {
			Vec3 targetPosition = player.getEyePosition().add(player.getLookAngle().scale(32.0D));
			int wholeLevelBonus = (int) Math.floor(levelBonus);
			double fractionalLevelBonus = levelBonus - wholeLevelBonus;
			int stochasticLevelBonus = wholeLevelBonus
				+ (fractionalLevelBonus > 0.0D && player.getRandom().nextDouble() < fractionalLevelBonus ? 1 : 0);
			int projectileCount = CHICKEN_EGG_BASE_PROJECTILE_COUNT
				+ Math.max(0, chickenCount - 1)
				+ stochasticLevelBonus;
			damage += Math.max(0, chickenCount - 1);
			damage = Math.max(0.0F, damage);
			radius = Math.max(0.5F, radius);
			if (damage > 0.0F && radius > 0.0F) {
				ACTIVE_CHICKEN_EGG_VOLLEYS.put(
					player.getUUID(),
					new ChickenEggVolleyState(
						player.getUUID(),
						player.level().dimension().toString(),
						targetPosition,
						projectileCount,
						now,
						damage,
						radius
					)
				);
				for (int index = 0; index < chickenCount; index++) {
					setAbilityCooldown(player.getUUID(), chickenSlots[index], PET_ABILITY_EGG_PROJECTILE, now + CHICKEN_EGG_COOLDOWN_TICKS);
				}
			}
		}

		triggerLeftClickPetAttacks(player, target);
	}

	static void handleAfterDamage(
		LivingEntity entity,
		net.minecraft.world.damagesource.DamageSource source,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked
	) {
		if (!PetConfigManager.settings().enabled || damageTaken <= 0.0F) {
			return;
		}
		if (entity instanceof ServerPlayer playerVictim) {
			triggerHealthRegeneration(playerVictim);
		}
		if (blocked) {
			return;
		}
		if (entity != null && source != null && source.getEntity() instanceof ServerPlayer playerAttacker) {
			triggerReactiveAbilities(playerAttacker, entity);
		}
		if (entity instanceof ServerPlayer playerVictim) {
			LivingEntity attackerTarget = resolveDamageSourceLivingEntity(source);
			if (attackerTarget != null) {
				triggerReactiveAbilities(playerVictim, attackerTarget);
			}
		}
	}

	private static void triggerHealthRegeneration(ServerPlayer player) {
		if (player == null || !player.isAlive() || !PetConfigManager.isEnabled()) {
			return;
		}
		long now = MadokuTimeManager.getGameplayTicks();
		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}
		int abilityCount = 0;
		int[] healthSlots = new int[SLOT_COUNT];
		int healthSlotCount = 0;
		long sharedCooldownTicks = 0L;
		double healPercentage = HEALTH_REGEN_BASE_PERCENTAGE;
		long durationTicks = HEALTH_REGEN_BASE_DURATION_TICKS;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null || !rule.enabled) {
				continue;
			}
			for (PetAbilityRule ability : rule.abilities) {
				if (!PET_ABILITY_HEALTH_REGENERATION.equals(ability.abilityType)) {
					continue;
				}
				abilityCount++;
				if (healthSlotCount == 0 || healthSlots[healthSlotCount - 1] != slot) {
					healthSlots[healthSlotCount++] = slot;
				}
				sharedCooldownTicks = Math.max(sharedCooldownTicks, ability.cooldownTicks);
				healPercentage += Math.max(0.0D, ability.healthRegenerationAmount - HEALTH_REGEN_BASE_PERCENTAGE);
				durationTicks += Math.max(0L, ability.effectDurationTicks - HEALTH_REGEN_BASE_DURATION_TICKS);
			}
		}
		if (abilityCount <= 0) {
			return;
		}
		long sharedCooldownTick = synchronizeSharedAbilityCooldown(
			player.getUUID(),
			PET_ABILITY_HEALTH_REGENERATION,
			healthSlots,
			healthSlotCount,
			now
		);
		if (now < sharedCooldownTick) {
			return;
		}
		healPercentage += (abilityCount - 1) * HEALTH_REGEN_BASE_PERCENTAGE;
		durationTicks += (long) (abilityCount - 1) * HEALTH_REGEN_DUPLICATE_DURATION_TICKS;
		if (healPercentage <= 0.0D || durationTicks <= 0L) {
			return;
		}
		if (sharedCooldownTicks > 0L) {
			setSharedAbilityCooldown(
				player.getUUID(),
				PET_ABILITY_HEALTH_REGENERATION,
				healthSlots,
				healthSlotCount,
				now + sharedCooldownTicks
			);
		}

		applyHealthRegenerationHeal(player, healPercentage);
		ACTIVE_HEALTH_REGENERATIONS.put(
			player.getUUID(),
			new HealthRegenerationState(
				now + HEALTH_REGEN_TICK_INTERVAL_TICKS,
				now + durationTicks,
				healPercentage
			)
		);
	}

	private static void applyHealthRegenerationHeal(ServerPlayer player, double healPercentage) {
		if (player == null || !player.isAlive() || healPercentage <= 0.0D) {
			return;
		}
		player.heal((float) (player.getMaxHealth() * healPercentage));
	}

	private static LivingEntity resolveDamageSourceLivingEntity(net.minecraft.world.damagesource.DamageSource source) {
		if (source == null) {
			return null;
		}
		if (source.getEntity() instanceof LivingEntity livingSource) {
			return livingSource;
		}
		if (source.getDirectEntity() instanceof LivingEntity directSource) {
			return directSource;
		}
		return null;
	}

	public static boolean hasAbility(ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		return rule != null && rule.hasAbility();
	}

	public static int cooldownTicks(ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (rule == null) return 0;
		long cooldown = rule.minimumCooldownTicks();
		if (rule.hasAbility(PET_ABILITY_MOB_SCAN)) {
			int level = PetEntitiesManager.petLevel(stack);
			cooldown -= Math.max(0, level - 1) * BAT_SCAN_COOLDOWN_REDUCTION_PER_LEVEL;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, cooldown));
	}

	public static int cooldownTicks(ServerPlayer player, int slot, ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (rule == null) return 0;
		if (!rule.hasAbility(PET_ABILITY_MOB_SCAN)) return cooldownTicks(stack);
		PetInventory inventory = petInventory(player);
		int[] batSlots = new int[SLOT_COUNT];
		int count = collectSlotsWithAbility(inventory, PET_ABILITY_MOB_SCAN, batSlots, null);
		return (int) Math.min(Integer.MAX_VALUE, effectiveBatScanCooldownTicks(inventory, batSlots, Math.max(1, count), rule));
	}

	public static int cooldownTicks(Player player, int slot, ItemStack stack) {
		return player instanceof ServerPlayer serverPlayer ? cooldownTicks(serverPlayer, slot, stack) : cooldownTicks(stack);
	}

	public static double playerDamageBonus(ServerPlayer player) {
		return sumAbility(player, PET_ABILITY_PLAYER_DAMAGE_BONUS);
	}

	public static double fallDamageReduction(ServerPlayer player) {
		return Math.min(1.0D, Math.max(0.0D, sumAbility(player, PET_ABILITY_FALL_DAMAGE_REDUCTION)));
	}

	public static double maxHealthBonus(ServerPlayer player) {
		return Math.max(0.0D, sumAbility(player, PET_ABILITY_MAX_HEALTH_BONUS));
	}

	public static double armorBonus(ServerPlayer player) {
		return Math.max(0.0D, sumAbility(player, PET_ABILITY_ARMOR_BONUS));
	}

	private static double sumAbility(ServerPlayer player, String abilityType) {
		if (player == null || !PetConfigManager.isEnabled()) return 0.0D;
		PetInventory inventory = petInventory(player);
		if (inventory == null) return 0.0D;
		double total = 0.0D;
		int chickenCount = 0;
		int chickenLevelBonus = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
			if (rule != null && rule.hasAbility(abilityType)) {
				if (PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) && "minecraft:chicken".equals(rule.petId)) {
					chickenCount++;
					chickenLevelBonus += Math.max(0, PetEntitiesManager.petLevel(inventory.getItem(slot)) - 1);
					continue;
				}
				total += switch (abilityType) {
					case PET_ABILITY_PLAYER_DAMAGE_BONUS -> rule.playerDamageBonus();
					case PET_ABILITY_FALL_DAMAGE_REDUCTION -> rule.fallDamageReduction();
					case PET_ABILITY_MAX_HEALTH_BONUS -> rule.maxHealthBonus();
					case PET_ABILITY_ARMOR_BONUS -> rule.armorBonus();
					default -> 0.0D;
				};
			}
		}
		if (PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) && chickenCount > 0) {
			total += CHICKEN_FALL_BASE_REDUCTION
				+ (Math.max(0, chickenCount - 1) * CHICKEN_FALL_EXTRA_REDUCTION)
				+ (chickenLevelBonus * CHICKEN_FALL_LEVEL_REDUCTION);
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
		PetInventory inventory = petInventory(player);
		if (inventory == null) return amount;
		long now = MadokuTimeManager.getGameplayTicks();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
			PetAbilityRule ability = rule == null ? null : rule.ability(PET_ABILITY_DAMAGE_BLOCK);
			if (rule == null || ability == null || ability.damageBlockAmount <= 0.0D || ability.cooldownTicks <= 0L || !isAbilityOffCooldown(player, slot, ability.abilityType, now)) continue;
			setAbilityCooldown(player.getUUID(), slot, ability.abilityType, now + ability.cooldownTicks);
			float blocked = (float) Math.max(0.0D, amount - ability.damageBlockAmount);
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

	public static void applyPlayerArmorToughnessAbilityBonus(ServerPlayer player) {
		if (player == null) return;
		AttributeInstance attribute = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (attribute == null) return;
		attribute.removeModifier(PLAYER_ARMOR_TOUGHNESS_MODIFIER);
		double bonus = armorBonus(player);
		if (bonus > 0.0D) attribute.addOrUpdateTransientModifier(new AttributeModifier(PLAYER_ARMOR_TOUGHNESS_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
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

	private static void triggerLeftClickPetAttacks(ServerPlayer player, LivingEntity target) {
		if (!canReactiveAttackTarget(player, target)) {
			return;
		}

		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}

		long gameplayTicks = MadokuTimeManager.getGameplayTicks();
		synchronizeWebProjectileCooldown(player, inventory, gameplayTicks);
		List<ReadyReactiveAttack> readyAttacks = new ArrayList<>();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null || !rule.enabled) {
				continue;
			}
			for (PetAbilityRule ability : rule.reactiveAbilities()) {
				if (isLeftClickProjectileAbility(ability) && isAbilityOffCooldown(player, slot, ability.abilityType, gameplayTicks)) {
					readyAttacks.add(new ReadyReactiveAttack(slot, rule, ability));
				}
			}
		}
		if (readyAttacks.isEmpty()) {
			return;
		}

		List<ReadyReactiveAttack> readyWebAttacks = readyAttacks.stream()
			.filter(ready -> PET_ABILITY_WEB_PROJECTILE.equals(ready.ability.abilityType))
			.toList();
		List<ReadyReactiveAttack> readyNonWebAttacks = readyAttacks.stream()
			.filter(ready -> !PET_ABILITY_WEB_PROJECTILE.equals(ready.ability.abilityType))
			.toList();
		if (!readyWebAttacks.isEmpty()) {
			ReadyReactiveAttack webAttack = readyWebAttacks.get(0);
			Vec3 spawnPosition = resolveRangedAttackSpawn(player, 0, 1, webAttack.ability);
			if (spawnManagedWebProjectile(player, target, spawnPosition, webAttack.rule, webAttack.ability, readyWebAttacks, inventory)) {
				setSharedWebProjectileCooldown(player, inventory, gameplayTicks + WEB_PROJECTILE_COOLDOWN_TICKS);
			}
		}

		for (int index = 0; index < readyNonWebAttacks.size(); index++) {
			ReadyReactiveAttack ready = readyNonWebAttacks.get(index);
			Vec3 spawnPosition = resolveRangedAttackSpawn(player, index, readyNonWebAttacks.size(), ready.ability);
			if (index == 0) {
				if (spawnPetReactiveAttack(player, target, spawnPosition, ready.rule, ready.ability)) {
					setAbilityCooldown(player.getUUID(), ready.slot, ready.ability.abilityType, gameplayTicks + ready.ability.cooldownTicks);
				}
				continue;
			}
			long delayTicks = index * ready.ability.shotDelayTicks;
			if (enqueueDelayedPetAttack(player, ready.slot, ready.ability.abilityType, target, spawnPosition, delayTicks)) {
				setAbilityCooldown(player.getUUID(), ready.slot, ready.ability.abilityType, gameplayTicks + delayTicks);
			}
		}
	}

	private static boolean isLeftClickProjectileAbility(PetAbilityRule ability) {
		if (ability == null || !ability.canPerformReactiveAttack()) {
			return false;
		}
		return PET_ABILITY_RANGED_HOMING_ARROW.equals(ability.abilityType)
			|| PET_ABILITY_WEB_PROJECTILE.equals(ability.abilityType)
			|| PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(ability.abilityType);
	}

	private static LivingEntity resolveLeftClickTarget(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}

		Vec3 start = player.getEyePosition();
		Vec3 direction = player.getLookAngle();
		if (direction.lengthSqr() <= 1.0E-6D) {
			return null;
		}
		direction = direction.normalize();
		Vec3 end = start.add(direction.scale(LEFT_CLICK_TARGET_RANGE));
		AABB searchArea = player.getBoundingBox().expandTowards(direction.scale(LEFT_CLICK_TARGET_RANGE)).inflate(1.0D);
		LivingEntity closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchArea, entity ->
			entity != player && entity.isAlive() && !PetComponentsManager.isManaged(entity) && canReactiveAttackTarget(player, entity))) {
			if (candidate.getBoundingBox().inflate(0.3D).clip(start, end).isEmpty()) {
				continue;
			}
			double distance = player.distanceToSqr(candidate);
			if (distance < closestDistance) {
				closest = candidate;
				closestDistance = distance;
			}
		}
		return closest;
	}

	private static boolean isLeftClickTargetingBlock(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().normalize().scale(player.blockInteractionRange()));
		return player.level().clip(new ClipContext(
			start,
			end,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			player
		)).getType() == HitResult.Type.BLOCK;
	}
	static void triggerReactivePetAttacks(ServerPlayer player, LivingEntity target) {
		if (!canReactiveAttackTarget(player, target)) {
			return;
		}

		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}

		long gameplayTicks = MadokuTimeManager.getGameplayTicks();
		synchronizeWebProjectileCooldown(player, inventory, gameplayTicks);
		List<ReadyReactiveAttack> readyAttacks = new ArrayList<>();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null || !rule.enabled) {
				continue;
			}
			for (PetAbilityRule ability : rule.reactiveAbilities()) {
				if (isAbilityOffCooldown(player, slot, ability.abilityType, gameplayTicks)) {
					readyAttacks.add(new ReadyReactiveAttack(slot, rule, ability));
				}
			}
		}
		if (readyAttacks.isEmpty()) {
			return;
		}

		List<ReadyReactiveAttack> readyWebAttacks = readyAttacks.stream()
			.filter(ready -> PET_ABILITY_WEB_PROJECTILE.equals(ready.ability.abilityType))
			.toList();
		List<ReadyReactiveAttack> readyNonWebAttacks = readyAttacks.stream()
			.filter(ready -> !PET_ABILITY_WEB_PROJECTILE.equals(ready.ability.abilityType))
			.toList();
		if (!readyWebAttacks.isEmpty()) {
			ReadyReactiveAttack webAttack = readyWebAttacks.get(0);
			Vec3 spawnPosition = resolveRangedAttackSpawn(player, 0, 1, webAttack.ability);
			if (spawnManagedWebProjectile(player, target, spawnPosition, webAttack.rule, webAttack.ability, readyWebAttacks, inventory)) {
				setSharedWebProjectileCooldown(player, inventory, gameplayTicks + WEB_PROJECTILE_COOLDOWN_TICKS);
			}
		}

		for (int index = 0; index < readyNonWebAttacks.size(); index++) {
			ReadyReactiveAttack ready = readyNonWebAttacks.get(index);
			Vec3 spawnPosition = resolveRangedAttackSpawn(player, index, readyNonWebAttacks.size(), ready.ability);
			if (index == 0) {
				if (spawnPetReactiveAttack(player, target, spawnPosition, ready.rule, ready.ability)) {
					setAbilityCooldown(player.getUUID(), ready.slot, ready.ability.abilityType, gameplayTicks + ready.ability.cooldownTicks);
				}
				continue;
			}
			enqueueDelayedPetAttack(player, ready.slot, ready.ability.abilityType, target, spawnPosition, index * ready.ability.shotDelayTicks);
		}
	}

	static LivingEntity resolveOngoingReactiveTarget(ServerPlayer player) {
			if (player == null || !player.isAlive()) {
				return null;
			}

			LivingEntity attackedTarget = normalizeReactiveTarget(player, player.getLastHurtMob());
			LivingEntity attackerTarget = normalizeReactiveTarget(player, player.getLastHurtByMob());
			if (attackedTarget == null) {
				return attackerTarget;
			}
			if (attackerTarget == null) {
				return attackedTarget;
			}
			return player.getLastHurtMobTimestamp() >= player.getLastHurtByMobTimestamp() ? attackedTarget : attackerTarget;
		}

			private static LivingEntity normalizeReactiveTarget(ServerPlayer player, LivingEntity target) {
			return canReactiveAttackTarget(player, target) ? target : null;
		}

			private static boolean canReactiveAttackTarget(ServerPlayer player, LivingEntity target) {
			if (player == null || target == null || target == player || !player.isAlive() || !target.isAlive()) {
				return false;
			}
			if (target.level() != player.level()) {
				return false;
			}
			return player.hasLineOfSight(target);
		}

			static void triggerAutomaticBatMobScan(ServerPlayer player, long gameplayTicks) {
			if (player == null || !player.isAlive()) {
				return;
			}

			PetInventory inventory = petInventory(player);
			if (inventory == null) {
				return;
			}

			int[] batSlots = new int[SLOT_COUNT];
			PetRule[] batRules = new PetRule[SLOT_COUNT];
			int batCount = collectSlotsWithAbility(inventory, PET_ABILITY_MOB_SCAN, batSlots, batRules);
			if (batCount <= 0) {
				return;
			}

			PetRule sharedRule = batRules[0];
			PetAbilityRule batAbility = sharedRule == null ? null : sharedRule.ability(PET_ABILITY_MOB_SCAN);
			if (batAbility == null || batAbility.cooldownTicks <= 0L) {
				return;
			}

			long sharedCooldownTick = synchronizeSharedAbilityCooldown(player.getUUID(), PET_ABILITY_MOB_SCAN, batSlots, batCount, gameplayTicks);
			if (gameplayTicks < sharedCooldownTick) {
				return;
			}

			applyAutomaticBatMobScan(player, inventory, batSlots, batRules, batCount, sharedRule, batAbility);
			setSharedAbilityCooldown(player.getUUID(), PET_ABILITY_MOB_SCAN, batSlots, batCount, gameplayTicks + effectiveBatScanCooldownTicks(inventory, batSlots, batCount, sharedRule));
		}

		private static void applyAutomaticBatMobScan(
			ServerPlayer player,
			PetInventory inventory,
			int[] batSlots,
			PetRule[] batRules,
			int batCount,
			PetRule rule,
			PetAbilityRule ability
		) {
			if (player == null || batCount <= 0 || !(player.level() instanceof ServerLevel level)) {
				return;
			}
			SoundEvent soundEvent = rule == null || ability == null ? SoundEvents.BEACON_ACTIVATE : rule.resolveSoundEvent(ability.abilityType);
			float volume = ability == null ? 0.45F : Math.max(0.12F, ability.soundVolumeMultiplier);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), soundEvent, SoundSource.NEUTRAL, volume, 1.15F);
			float vulnerability = resolveBatScanVulnerability(inventory, batSlots, batRules, batCount);
			long glowingDurationTicks = resolveBatScanGlowingDurationTicks(inventory, batSlots, batCount);

			double horizontalRadius = BAT_SCAN_BASE_RADIUS_BLOCKS + Math.max(0, batCount - 1) * 12.0D;
			int chunkRadius = Math.max(1, (int) Math.ceil(horizontalRadius / 16.0D));
			int verticalRadius = 12 + Math.max(0, batCount - 1) * BAT_SCAN_VERTICAL_RADIUS_PER_EXTRA_BAT;
			AABB scanArea = new AABB(
				player.getX() - horizontalRadius,
				player.getY() - verticalRadius,
				player.getZ() - horizontalRadius,
				player.getX() + horizontalRadius,
				player.getY() + verticalRadius,
				player.getZ() + horizontalRadius
			);

			int centerChunkX = player.getBlockX() >> 4;
			int centerChunkZ = player.getBlockZ() >> 4;
			for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
				for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
					if (!MadokuChunkManager.isChunkAccessible(level, chunkX, chunkZ)) {
						continue;
					}

					double minX = chunkX << 4;
					double minZ = chunkZ << 4;
					double maxX = minX + 16.0d;
					double maxZ = minZ + 16.0d;
					AABB chunkScanArea = new AABB(
						minX,
						player.getY() - verticalRadius,
						minZ,
						maxX,
						player.getY() + verticalRadius,
						maxZ
					);

					for (Mob mob : level.getEntitiesOfClass(Mob.class, chunkScanArea, candidate ->
						candidate != null
							&& candidate.isAlive()
							&& !candidate.isRemoved()
							&& !isManagedPet(candidate)
							&& scanArea.intersects(candidate.getBoundingBox())
						)) {
						mob.addTag(MOB_SCAN_VULNERABILITY_TAG);
						MOB_SCAN_VULNERABILITY_BY_ENTITY.put(mob.getUUID(), vulnerability);
						mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) glowingDurationTicks, 0, false, false, true));
					}
				}
			}
		}

		private static long resolveBatScanGlowingDurationTicks(PetInventory inventory, int[] batSlots, int batCount) {
			long durationTicks = BAT_SCAN_BASE_GLOWING_DURATION_TICKS
				+ Math.max(0, batCount - 1) * BAT_SCAN_GLOWING_DURATION_PER_EXTRA_BAT_TICKS;
			if (inventory != null && batSlots != null) {
				for (int index = 0; index < Math.min(batCount, batSlots.length); index++) {
					int slot = batSlots[index];
					int level = slot < 0 || slot >= inventory.getContainerSize()
						? 1
						: PetEntitiesManager.petLevel(inventory.getItem(slot));
					durationTicks += Math.max(0, level - 1) * BAT_SCAN_GLOWING_DURATION_PER_LEVEL_TICKS;
				}
			}
			return durationTicks;
		}

		private static float resolveBatScanVulnerability(PetInventory inventory, int[] batSlots, PetRule[] batRules, int batCount) {
			double vulnerability = 0.0D;
			if (inventory != null && batSlots != null) {
				for (int index = 0; index < Math.min(batCount, batSlots.length); index++) {
					int slot = batSlots[index];
					PetAbilityRule batAbility = batRules != null && index < batRules.length && batRules[index] != null
						? batRules[index].ability(PET_ABILITY_MOB_SCAN)
						: null;
					int level = slot < 0 || slot >= inventory.getContainerSize()
						? 1
						: PetEntitiesManager.petLevel(inventory.getItem(slot));
					double perBatVulnerability = batAbility == null
						? BAT_SCAN_BASE_VULNERABILITY + Math.max(0, level - 1) * BAT_SCAN_VULNERABILITY_PER_LEVEL
						: batAbility.mobScanVulnerabilityAmount;
					vulnerability += index == 0
						? perBatVulnerability
						: (perBatVulnerability - BAT_SCAN_BASE_VULNERABILITY) + BAT_SCAN_VULNERABILITY_PER_EXTRA_BAT;
				}
			}
			return (float) Math.max(0.0D, vulnerability);
		}

		static long effectiveBatScanCooldownTicks(PetInventory inventory, int[] batSlots, int batCount, PetRule rule) {
		PetAbilityRule ability = rule == null ? null : rule.ability(PET_ABILITY_MOB_SCAN);
		long baseCooldown = Math.max(0L, ability == null ? 0L : ability.cooldownTicks);
		long cooldownReduction = Math.max(0, batCount - 1) * BAT_SCAN_COOLDOWN_REDUCTION_PER_EXTRA_BAT;
		if (inventory != null && batSlots != null) {
			for (int index = 0; index < Math.min(batCount, batSlots.length); index++) {
				int slot = batSlots[index];
				int level = slot < 0 || slot >= inventory.getContainerSize()
					? 1
					: PetEntitiesManager.petLevel(inventory.getItem(slot));
				cooldownReduction += Math.max(0, level - 1) * BAT_SCAN_COOLDOWN_REDUCTION_PER_LEVEL;
			}
		}
		return Math.max(20L, baseCooldown - cooldownReduction);
		}

			static void triggerAutomaticBeeSwarm(ServerPlayer player, long gameplayTicks) {
			if (player == null || !player.isAlive()) {
				return;
			}

			PetInventory inventory = petInventory(player);
			if (inventory == null) {
				return;
			}

			PetAbilityRule[] readyAbilities = new PetAbilityRule[SLOT_COUNT];
			int[] readySlots = new int[SLOT_COUNT];
			PetRule[] readyRules = new PetRule[SLOT_COUNT];
			int readyCount = 0;
			for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
				ItemStack stack = inventory.getItem(slot);
				PetRule rule = PetConfigManager.resolvePetRule(stack);
				PetAbilityRule ability = rule == null ? null : rule.ability(PET_ABILITY_BEE_SWARM);
				if (rule == null || !rule.enabled || ability == null) {
					stopBeeSwarmForSlot(player.getUUID(), slot);
					continue;
				}
				if (ACTIVE_BEE_SWARMS.containsKey(beeSwarmKey(player.getUUID(), slot))) {
					continue;
				}
				if (!isAbilityOffCooldown(player, slot, ability.abilityType, gameplayTicks)) {
					continue;
				}
				readySlots[readyCount] = slot;
				readyRules[readyCount] = rule;
				readyAbilities[readyCount] = ability;
				readyCount++;
			}
			if (readyCount <= 0) {
				return;
			}
			if (!isBeeTargetScanDue(player.getUUID(), gameplayTicks)) {
				return;
			}

			List<LivingEntity> prioritizedTargets = resolveAutomaticBeeSwarmTargets(player);
			scheduleNextBeeTargetScan(player, gameplayTicks);
			if (prioritizedTargets.isEmpty()) {
				return;
			}

			Set<UUID> claimedTargetIds = collectOwnerActiveBeeSwarmTargetIds(player.getUUID());
			for (int index = 0; index < readyCount; index++) {
				int slot = readySlots[index];
				PetRule rule = readyRules[index];
				PetAbilityRule ability = readyAbilities[index];
				LivingEntity target = selectBeeSwarmTarget(prioritizedTargets, claimedTargetIds);
				if (target == null) {
					break;
				}
				if (!startBeeSwarm(player, slot, target, rule, ability, gameplayTicks)) {
					continue;
				}
				claimedTargetIds.add(target.getUUID());
				setAbilityCooldown(player.getUUID(), slot, ability.abilityType, gameplayTicks + ability.cooldownTicks);
			}
		}

			private static List<LivingEntity> resolveAutomaticBeeSwarmTargets(ServerPlayer player) {
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				return List.of();
			}

			List<LivingEntity> nearestCandidates = new ArrayList<>(BEE_SWARM_MAX_TARGET_CANDIDATES);
			addNearestBeeTargetCandidate(player, player.getLastHurtMob(), nearestCandidates);
			addNearestBeeTargetCandidate(player, player.getLastHurtByMob(), nearestCandidates);

			AABB scanArea = new AABB(
				player.getX() - BEE_SWARM_SCAN_RADIUS,
				player.getY() - BEE_SWARM_SCAN_VERTICAL_RADIUS,
				player.getZ() - BEE_SWARM_SCAN_RADIUS,
				player.getX() + BEE_SWARM_SCAN_RADIUS,
				player.getY() + BEE_SWARM_SCAN_VERTICAL_RADIUS,
				player.getZ() + BEE_SWARM_SCAN_RADIUS
			);
			List<Mob> hostileMobs = level.getEntitiesOfClass(Mob.class, scanArea, candidate -> isValidBeeSwarmTarget(player, candidate));
			for (Mob hostile : hostileMobs) {
				addNearestBeeTargetCandidate(player, hostile, nearestCandidates);
			}

			Map<UUID, LivingEntity> prioritizedTargets = new LinkedHashMap<>();
			for (LivingEntity candidate : nearestCandidates) {
				addBeeSwarmTargetCandidate(player, candidate, prioritizedTargets);
			}
			if (prioritizedTargets.isEmpty()) {
				return List.of();
			}
			return new ArrayList<>(prioritizedTargets.values());
		}

		private static void addNearestBeeTargetCandidate(ServerPlayer player, LivingEntity target, List<LivingEntity> candidates) {
			if (candidates == null || !isValidBeeSwarmTarget(player, target) || !canBeeSwarmReachTarget(player, target)) {
				return;
			}
			for (LivingEntity candidate : candidates) {
				if (candidate.getUUID().equals(target.getUUID())) {
					return;
				}
			}

			double targetDistance = target.distanceToSqr(player);
			int insertionIndex = 0;
			while (insertionIndex < candidates.size()
				&& candidates.get(insertionIndex).distanceToSqr(player) <= targetDistance) {
				insertionIndex++;
			}
			if (insertionIndex >= BEE_SWARM_MAX_TARGET_CANDIDATES && candidates.size() >= BEE_SWARM_MAX_TARGET_CANDIDATES) {
				return;
			}
			candidates.add(insertionIndex, target);
			if (candidates.size() > BEE_SWARM_MAX_TARGET_CANDIDATES) {
				candidates.remove(candidates.size() - 1);
			}
		}

		private static boolean isBeeTargetScanDue(UUID ownerId, long gameplayTicks) {
			if (ownerId == null) {
				return false;
			}
			return gameplayTicks >= NEXT_BEE_TARGET_SCAN_TICK.getOrDefault(ownerId, Long.MIN_VALUE);
		}

		private static void scheduleNextBeeTargetScan(ServerPlayer player, long gameplayTicks) {
			if (player == null) {
				return;
			}
			MinecraftServer server = player.level().getServer();
			long interval = MadokuSchedulerManager.resolveAdaptiveDelayTicks(
				server,
				BEE_TARGET_SCAN_SCHEDULER_OWNER_ID,
				BEE_SWARM_MIN_TARGET_SCAN_INTERVAL_TICKS,
				BEE_SWARM_MAX_TARGET_SCAN_INTERVAL_TICKS
			);
			NEXT_BEE_TARGET_SCAN_TICK.put(player.getUUID(), gameplayTicks + interval);
		}

		private static void addBeeSwarmTargetCandidate(ServerPlayer player, LivingEntity target, Map<UUID, LivingEntity> out) {
			if (out == null || !isValidBeeSwarmTarget(player, target) || !canBeeSwarmReachTarget(player, target)) {
				return;
			}
			out.putIfAbsent(target.getUUID(), target);
		}

			private static Set<UUID> collectOwnerActiveBeeSwarmTargetIds(UUID ownerId) {
			if (ownerId == null || ACTIVE_BEE_SWARMS.isEmpty()) {
				return new HashSet<>();
			}
			Set<UUID> targetIds = new HashSet<>();
			for (BeeSwarmState state : ACTIVE_BEE_SWARMS.values()) {
				if (state == null || !ownerId.equals(state.ownerUuid) || state.targetUuid == null) {
					continue;
				}
				targetIds.add(state.targetUuid);
			}
			return targetIds;
		}

			private static LivingEntity selectBeeSwarmTarget(List<LivingEntity> prioritizedTargets, Set<UUID> claimedTargetIds) {
			if (prioritizedTargets == null || prioritizedTargets.isEmpty()) {
				return null;
			}
			for (LivingEntity candidate : prioritizedTargets) {
				if (candidate == null || !candidate.isAlive()) {
					continue;
				}
				if (claimedTargetIds != null && claimedTargetIds.contains(candidate.getUUID())) {
					continue;
				}
				return candidate;
			}
			for (LivingEntity candidate : prioritizedTargets) {
				if (candidate != null && candidate.isAlive()) {
					return candidate;
				}
			}
			return null;
		}

		private static boolean isValidBeeSwarmTarget(ServerPlayer player, LivingEntity target) {
			if (player == null || target == null || target == player || !player.isAlive() || !target.isAlive()) {
				return false;
			}
			if (target.level() != player.level()) {
				return false;
			}
			if (target instanceof Hag) {
				return false;
			}
			if (!(target instanceof Enemy) || isManagedPet(target)) {
				return false;
			}
			return target.distanceToSqr(player) <= (BEE_SWARM_SCAN_RADIUS * BEE_SWARM_SCAN_RADIUS);
		}

		private static boolean canBeeSwarmReachTarget(ServerPlayer player, LivingEntity target) {
			return player != null && target != null && player.hasLineOfSight(target);
		}

		private static boolean startBeeSwarm(ServerPlayer player, int slot, LivingEntity target, PetRule rule, PetAbilityRule ability, long gameplayTicks) {
			if (player == null || target == null || rule == null || ability == null || !PET_ABILITY_BEE_SWARM.equals(ability.abilityType) || !(player.level() instanceof ServerLevel level)) {
				return false;
			}
			if (!isValidBeeSwarmTarget(player, target) || !canBeeSwarmReachTarget(player, target)) {
				return false;
			}

			Vec3 startPosition = resolveBeeSwarmOrbitPosition(target, player.getRandom().nextDouble() * Math.PI * 2.0D, BEE_SWARM_ORBIT_RADIUS_BASE, 0.05D);
			emitBeeSwarmLaunch(level, startPosition, rule.resolveSoundEvent(ability.abilityType), Math.max(0.12F, ability.soundVolumeMultiplier), 1.2F);
			ACTIVE_BEE_SWARMS.put(
				beeSwarmKey(player.getUUID(), slot),

				new BeeSwarmState(
					player.getUUID(),
					slot,
					level.dimension().toString(),
					target.getUUID(),
					gameplayTicks,
					player.getLastHurtMobTimestamp(),
					player.getLastHurtByMobTimestamp(),
					gameplayTicks + BEE_SWARM_DAMAGE_INTERVAL_TICKS,
					player.getRandom().nextDouble() * Math.PI * 2.0D,
					startPosition
				)
			);
			return true;
		}

			static boolean hasAutomaticPetAbilities(PetInventory inventory) {
			return countSlotsWithAbility(inventory, PET_ABILITY_MOB_SCAN) > 0
				|| countSlotsWithAbility(inventory, PET_ABILITY_BEE_SWARM) > 0;
		}

			static int countSlotsWithAbility(PetInventory inventory, String abilityType) {
			return collectSlotsWithAbility(inventory, abilityType, null, null);
		}

		private static int collectSlotsWithAbility(
			PetInventory inventory,
			String abilityType,
			int[] slots,
			PetRule[] rules
		) {
			if (inventory == null || abilityType == null) {
				return 0;
			}

			int count = 0;
			for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
				ItemStack stack = inventory.getItem(slot);
				PetRule rule = PetConfigManager.resolvePetRule(stack);
				if (rule == null || !rule.enabled || !rule.hasAbility(abilityType)) {
					continue;
				}
				if (slots != null && count < slots.length) {
					slots[count] = slot;
				}
				if (rules != null && count < rules.length) {
					rules[count] = rule;
				}
				count++;
			}
			return count;
		}

		private static void synchronizeWebProjectileCooldown(ServerPlayer player, PetInventory inventory, long gameplayTicks) {
			if (player == null || inventory == null) {
				return;
			}
			int[] webSlots = new int[SLOT_COUNT];
			int webCount = collectSlotsWithAbility(inventory, PET_ABILITY_WEB_PROJECTILE, webSlots, null);
			if (webCount > 0) {
				synchronizeSharedAbilityCooldown(player.getUUID(), PET_ABILITY_WEB_PROJECTILE, webSlots, webCount, gameplayTicks);
			}
		}

		private static void setSharedWebProjectileCooldown(ServerPlayer player, PetInventory inventory, long cooldownTick) {
			if (player == null || inventory == null) {
				return;
			}
			int[] webSlots = new int[SLOT_COUNT];
			int webCount = collectSlotsWithAbility(inventory, PET_ABILITY_WEB_PROJECTILE, webSlots, null);
			if (webCount > 0) {
				setSharedAbilityCooldown(player.getUUID(), PET_ABILITY_WEB_PROJECTILE, webSlots, webCount, cooldownTick);
			}
		}

		private static long synchronizeSharedAbilityCooldown(UUID playerId, String abilityType, int[] slots, int slotCount, long gameplayTicks) {
			if (playerId == null || slots == null || slotCount <= 0) {
				return 0L;
			}

			long sharedCooldownTick = 0L;
			for (int index = 0; index < slotCount; index++) {
				int slot = slots[index];
				if (slot >= 0 && slot < SLOT_COUNT) sharedCooldownTick = Math.max(sharedCooldownTick, abilityCooldown(playerId, slot, abilityType));
			}
			if (sharedCooldownTick > 0L && sharedCooldownTick <= gameplayTicks) {
				sharedCooldownTick = 0L;
			}

			for (int index = 0; index < slotCount; index++) {
				int slot = slots[index];
				if (slot >= 0 && slot < SLOT_COUNT && abilityCooldown(playerId, slot, abilityType) != sharedCooldownTick) {
					setAbilityCooldown(playerId, slot, abilityType, sharedCooldownTick);
				}
			}
			return sharedCooldownTick;
		}

		private static void setSharedAbilityCooldown(UUID playerId, String abilityType, int[] slots, int slotCount, long cooldownTick) {
			if (playerId == null || slots == null || slotCount <= 0) {
				return;
			}

			long normalizedCooldownTick = Math.max(0L, cooldownTick);
			for (int index = 0; index < slotCount; index++) {
				int slot = slots[index];
				if (slot >= 0 && slot < SLOT_COUNT) setAbilityCooldown(playerId, slot, abilityType, normalizedCooldownTick);
			}
		}

			static void runPetAttack(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
			if (server == null || context == null || payload == null) {
				return;
			}

			MadokuSchedulerManager.SchedulerBinding binding = context.getBinding();
			UUID playerId = binding == null ? null : binding.getEntityUuid();
			if (playerId == null) {
				return;
			}

			PLAYER_SCHEDULER_IDS.put(playerId, context.getSchedulerId());
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null || !player.isAlive()) {
				return;
			}

			int slot = PetConfigManager.getInt(payload, FIELD_SLOT, -1);
			if (slot < 0 || slot >= SLOT_COUNT) {
				return;
			}
			long gameplayTicks = MadokuTimeManager.getGameplayTicks();

			PetInventory inventory = petInventory(player);
			if (inventory == null || slot >= inventory.getContainerSize()) {
				return;
			}

			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			String abilityType = PetConfigManager.normalizeAbilityId(PetConfigManager.getString(payload, FIELD_ABILITY_ID, ""));
			PetAbilityRule ability = rule == null ? null : rule.ability(abilityType);
			if (rule == null || ability == null || !ability.canPerformReactiveAttack() || !isAbilityOffCooldown(player, slot, ability.abilityType, gameplayTicks)) {
				return;
			}

			UUID targetId = parseUuid(PetConfigManager.getString(payload, FIELD_TARGET_UUID, ""));
			LivingEntity target = findLivingEntity(server, targetId);
			if (!canReactiveAttackTarget(player, target)) {
				return;
			}


			Vec3 spawnPosition = new Vec3(
				PetConfigManager.getDouble(payload, FIELD_SPAWN_X, player.getX()),
				PetConfigManager.getDouble(payload, FIELD_SPAWN_Y, player.getEyeY()),
				PetConfigManager.getDouble(payload, FIELD_SPAWN_Z, player.getZ())
			);
			if (spawnPetReactiveAttack(player, target, spawnPosition, rule, ability)) {
				if (PET_ABILITY_WEB_PROJECTILE.equals(ability.abilityType)) {
					setSharedWebProjectileCooldown(player, inventory, gameplayTicks + WEB_PROJECTILE_COOLDOWN_TICKS);
				} else {
					setAbilityCooldown(playerId, slot, ability.abilityType, gameplayTicks + ability.cooldownTicks);
				}
			}
		}

			private static boolean spawnPetReactiveAttack(ServerPlayer player, LivingEntity target, Vec3 spawnPosition, PetRule rule, PetAbilityRule ability) {
			if (player == null || target == null || spawnPosition == null || rule == null || ability == null || !player.isAlive() || !target.isAlive()) {
				return false;
			}

			SoundEvent soundEvent = rule.resolveSoundEvent(ability.abilityType);
			float soundVolume = Math.max(0.4F, ability.soundVolumeMultiplier);
			float soundPitch = 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F);
			boolean spawned;
			String projectileAbility = ability.abilityType;
			if (PET_ABILITY_RANGED_HOMING_ARROW.equals(projectileAbility)) {
				spawned = MobEntityManager.spawnManagedHomingArrow(player, target, spawnPosition, ability.attackSpeed, ability.attackDamage);
			} else if (PET_ABILITY_WEB_PROJECTILE.equals(projectileAbility)) {
				spawned = spawnManagedWebProjectile(
					player,
					target,
					spawnPosition,
					rule,
					ability,
					List.of(new ReadyReactiveAttack(-1, rule, ability)),
					petInventory(player)
				);
			} else if (PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(projectileAbility)) {
				spawned = spawnManagedExplosiveProjectile(player, target, spawnPosition, ability, soundEvent, soundVolume, soundPitch);
			} else {
				spawned = false;
			}
			if (!spawned) {
				return false;
			}
			if (!(PET_ABILITY_WEB_PROJECTILE.equals(ability.abilityType) || PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(ability.abilityType))
				&& player.level() instanceof ServerLevel level) {
				level.playSound(null, spawnPosition.x, spawnPosition.y, spawnPosition.z, soundEvent, SoundSource.HOSTILE, soundVolume, soundPitch);
			}

			return true;
		}

		private static boolean spawnManagedWebProjectile(
			ServerPlayer player,
			LivingEntity target,
			Vec3 spawnPosition,
			PetRule rule,
			PetAbilityRule ability,
			List<ReadyReactiveAttack> webAttacks,
			PetInventory inventory
		) {
			if (player == null || target == null || spawnPosition == null || rule == null || ability == null || !(player.level() instanceof ServerLevel level)) {
				return false;
			}
			List<ReadyReactiveAttack> attacks = webAttacks == null || webAttacks.isEmpty()
				? List.of(new ReadyReactiveAttack(-1, rule, ability))
				: webAttacks;
			ReadyReactiveAttack strongestAttack = attacks.get(0);
			int strongestLevel = strongestAttack.slot() < 0 || inventory == null || strongestAttack.slot() >= inventory.getContainerSize()
				? 1
				: PetEntitiesManager.petLevel(inventory.getItem(strongestAttack.slot()));
			for (ReadyReactiveAttack candidate : attacks) {
				int candidateLevel = candidate.slot() < 0 || inventory == null || candidate.slot() >= inventory.getContainerSize()
					? 1
					: PetEntitiesManager.petLevel(inventory.getItem(candidate.slot()));
				if (candidate.ability().attackDamage > strongestAttack.ability().attackDamage) {
					strongestAttack = candidate;
				}
				if (candidateLevel > strongestLevel) {
					strongestLevel = candidateLevel;
				}
			}
			ability = strongestAttack.ability();
			int duplicateCount = Math.max(0, attacks.size() - 1);
			float damage = Math.max(0.0F, ability.attackDamage + (duplicateCount * WEB_PROJECTILE_DUPLICATE_DAMAGE));
			int stunDurationTicks = (int) Math.max(0L, ability.stunDurationTicks + (duplicateCount * WEB_PROJECTILE_DUPLICATE_STUN_TICKS));
			double ricochetAmount = WEB_PROJECTILE_BASE_RICOCHETS
				+ (Math.max(0, strongestLevel - 1) * WEB_PROJECTILE_RICOCHETS_PER_LEVEL)
				+ duplicateCount;
			int ricochetCount = (int) Math.floor(ricochetAmount);
			double fractionalRicochet = ricochetAmount - ricochetCount;
			if (fractionalRicochet > 0.0D && player.getRandom().nextDouble() < fractionalRicochet) {
				ricochetCount++;
			}
			Vec3 initialPosition = new Vec3(spawnPosition.x, spawnPosition.y, spawnPosition.z);
			SoundEvent soundEvent = rule.resolveSoundEvent(ability.abilityType);
			float soundVolume = Math.max(0.4F, ability.soundVolumeMultiplier);
			float soundPitch = 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F);
			emitWebProjectileLaunch(level, initialPosition, soundEvent, soundVolume, soundPitch);
			UUID projectileId = UUID.randomUUID();
			ACTIVE_WEB_PROJECTILES.put(
				projectileId,
				new WebProjectileState(
					level.dimension().toString(),
					player.getUUID(),
					target.getUUID(),
					initialPosition,
					Math.max(WEB_PROJECTILE_MIN_SPEED, ability.attackSpeed),
					damage,
					(int) Math.max(0L, ability.effectDurationTicks),
					stunDurationTicks,
					(int) Math.max(0L, ability.slowDurationTicks),
					(float) Math.max(0.0D, Math.min(1.0D, ability.slowPercentage)),
					false,
					ricochetCount,
					Set.of(),
					WEB_PROJECTILE_LIFETIME_TICKS
				)
			);
			return true;
		}

		private static boolean spawnManagedExplosiveProjectile(
			ServerPlayer player,
			LivingEntity target,
			Vec3 spawnPosition,
			PetAbilityRule ability,
			SoundEvent soundEvent,
			float soundVolume,
			float soundPitch
		) {
			if (player == null || target == null || spawnPosition == null || ability == null || !(player.level() instanceof ServerLevel level)) {
				return false;
			}
			Vec3 initialPosition = new Vec3(spawnPosition.x, spawnPosition.y, spawnPosition.z);
			emitExplosiveProjectileLaunch(level, initialPosition, soundEvent, soundVolume, soundPitch);
			UUID projectileId = UUID.randomUUID();
			ACTIVE_EXPLOSIVE_PROJECTILES.put(
				projectileId,
				new ExplosiveProjectileState(
					level.dimension().toString(),
					player.getUUID(),
					target.getUUID(),
					initialPosition,
					Math.max(EXPLOSIVE_PROJECTILE_MIN_SPEED, ability.attackSpeed),
					Math.max(0.0F, ability.attackDamage),
					Math.max(0.5F, ability.explosionRadius),
					(float) Math.max(0.0D, ability.vulnerabilityAmount),
					(int) Math.max(0L, ability.vulnerabilityDurationTicks),
					EXPLOSIVE_PROJECTILE_LIFETIME_TICKS
				)
			);
			return true;
		}

			static void tickManagedWebProjectiles(MinecraftServer server) {
			if (server == null || ACTIVE_WEB_PROJECTILES.isEmpty()) {
				return;
			}

			for (Map.Entry<UUID, WebProjectileState> entry : ACTIVE_WEB_PROJECTILES.entrySet()) {
				UUID projectileId = entry.getKey();
				WebProjectileState state = entry.getValue();
				if (state.remainingTicks <= 0) {
					ACTIVE_WEB_PROJECTILES.remove(projectileId);
					continue;
				}

				ServerLevel level = findLevel(server, state.dimensionId);
				ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerUuid);
				LivingEntity target = findLivingEntity(server, state.targetUuid);
				if (level == null || owner == null || !owner.isAlive() || target == null || !target.isAlive() || target.level() != level) {
					ACTIVE_WEB_PROJECTILES.remove(projectileId);
					continue;
				}

				Vec3 targetPosition = resolveWebProjectileTargetPosition(target);
				Vec3 toTarget = targetPosition.subtract(state.position);
				double distance = toTarget.length();
				if (distance <= 1.0E-6D) {
					if (!applyWebProjectileHit(projectileId, level, owner, target, targetPosition, state)) {
						ACTIVE_WEB_PROJECTILES.remove(projectileId);
					}
					continue;
				}

				double step = Math.min(distance, state.speed);
				Vec3 nextPosition = state.position.add(toTarget.normalize().scale(step));
				emitWebProjectileTrail(level, state.position, nextPosition);
				if (nextPosition.distanceTo(targetPosition) <= WEB_PROJECTILE_HIT_DISTANCE) {
					if (!applyWebProjectileHit(projectileId, level, owner, target, targetPosition, state)) {
						ACTIVE_WEB_PROJECTILES.remove(projectileId);
					}
					continue;
				}

				ACTIVE_WEB_PROJECTILES.put(
					projectileId,
					new WebProjectileState(

						state.dimensionId,
						state.ownerUuid,
						state.targetUuid,
						nextPosition,
						state.speed,
						state.damage,
						state.effectDurationTicks,
						state.stunDurationTicks,
						state.slowDurationTicks,
						state.slowPercentage,
						state.ricochet,
						state.remainingRicochets,
						state.hitEntityUuids,
						state.remainingTicks - 1
					)
				);
			}
		}

		static void tickManagedExplosiveProjectiles(MinecraftServer server) {
			if (server == null || ACTIVE_EXPLOSIVE_PROJECTILES.isEmpty()) {
				return;
			}

			for (Map.Entry<UUID, ExplosiveProjectileState> entry : ACTIVE_EXPLOSIVE_PROJECTILES.entrySet()) {
				UUID projectileId = entry.getKey();
				ExplosiveProjectileState state = entry.getValue();
				if (state.remainingTicks <= 0) {
					ServerLevel expiredLevel = findLevel(server, state.dimensionId);
					ServerPlayer expiredOwner = server.getPlayerList().getPlayer(state.ownerUuid);
					if (expiredLevel != null && expiredOwner != null && expiredOwner.isAlive()) {
						applyExplosiveProjectileHit(expiredLevel, expiredOwner, state.position, state.damage, state.radius, state.vulnerability, state.vulnerabilityDurationTicks);
					}
					ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
					continue;
				}

				ServerLevel level = findLevel(server, state.dimensionId);
				ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerUuid);
				LivingEntity target = findLivingEntity(server, state.targetUuid);
				if (level == null || owner == null || !owner.isAlive()) {
					ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
					continue;
				}
				if (target == null || !target.isAlive() || target.level() != level) {
					applyExplosiveProjectileHit(level, owner, state.position, state.damage, state.radius, state.vulnerability, state.vulnerabilityDurationTicks);
					ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
					continue;
				}

				Vec3 targetPosition = resolveWebProjectileTargetPosition(target);
				Vec3 toTarget = targetPosition.subtract(state.position);
				double distance = toTarget.length();
				if (distance <= 1.0E-6D) {
					applyExplosiveProjectileHit(level, owner, targetPosition, state.damage, state.radius, state.vulnerability, state.vulnerabilityDurationTicks);
					ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
					continue;
				}

				double step = Math.min(distance, state.speed);
				Vec3 nextPosition = state.position.add(toTarget.normalize().scale(step));
				emitExplosiveProjectileTrail(level, state.position, nextPosition);
				if (nextPosition.distanceTo(targetPosition) <= EXPLOSIVE_PROJECTILE_HIT_DISTANCE) {
					applyExplosiveProjectileHit(level, owner, targetPosition, state.damage, state.radius, state.vulnerability, state.vulnerabilityDurationTicks);
					ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
					continue;
				}

				ACTIVE_EXPLOSIVE_PROJECTILES.put(
					projectileId,
					new ExplosiveProjectileState(
						state.dimensionId,
						state.ownerUuid,
						state.targetUuid,
						nextPosition,
						state.speed,
						state.damage,
						state.radius,
						state.vulnerability,
						state.vulnerabilityDurationTicks,
						state.remainingTicks - 1
					)
				);
			}
		}

		static void tickManagedChickenEggProjectiles(MinecraftServer server) {
			if (server == null) {
				return;
			}
			long now = MadokuTimeManager.getGameplayTicks();
			for (Map.Entry<UUID, ChickenEggVolleyState> entry : ACTIVE_CHICKEN_EGG_VOLLEYS.entrySet()) {
				UUID ownerId = entry.getKey();
				ChickenEggVolleyState state = entry.getValue();
				ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
				if (owner == null || !owner.isAlive() || !owner.level().dimension().toString().equals(state.dimensionId)) {
					ACTIVE_CHICKEN_EGG_VOLLEYS.remove(ownerId);
					continue;
				}
				if (state.remainingProjectiles <= 0) {
					ACTIVE_CHICKEN_EGG_VOLLEYS.remove(ownerId);
					continue;
				}
				if (now < state.nextLaunchTick) {
					continue;
				}
				spawnChickenEggProjectile(owner, state.targetPosition, state.damage, state.radius);
				ACTIVE_CHICKEN_EGG_VOLLEYS.put(
					ownerId,
					new ChickenEggVolleyState(
						state.ownerUuid,
						state.dimensionId,
						state.targetPosition,
						state.remainingProjectiles - 1,
						now + CHICKEN_EGG_PROJECTILE_DELAY_TICKS,
						state.damage,
						state.radius
					)
				);
			}

			for (Map.Entry<UUID, ChickenEggProjectileState> entry : ACTIVE_CHICKEN_EGG_PROJECTILES.entrySet()) {
				UUID projectileId = entry.getKey();
				ChickenEggProjectileState state = entry.getValue();
				ServerLevel level = findLevel(server, state.dimensionId);
				Entity projectile = level == null ? null : level.getEntity(projectileId);
				if (level == null || projectile == null || projectile.isRemoved()) {
					ACTIVE_CHICKEN_EGG_PROJECTILES.remove(projectileId);
					continue;
				}
				level.sendParticles(ParticleTypes.SMALL_FLAME, projectile.getX(), projectile.getY(), projectile.getZ(), 1, 0.005D, 0.005D, 0.005D, 0.0D);
				if (now >= state.expiresAtTick) {
					handleManagedChickenEggImpact(projectile, new BlockHitResult(projectile.position(), Direction.UP, projectile.blockPosition(), false));
				}
			}
		}

		private static boolean spawnChickenEggProjectile(ServerPlayer owner, Vec3 targetPosition, float damage, float radius) {
			if (owner == null || targetPosition == null || !(owner.level() instanceof ServerLevel level)) {
				return false;
			}
			Vec3 start = owner.getEyePosition().add(owner.getLookAngle().scale(0.35D));
			Vec3 direction = targetPosition.subtract(start);
			if (direction.lengthSqr() <= 1.0E-6D) {
				return false;
			}
			ThrownEgg egg = new ManagedChickenEgg(level, owner, new ItemStack(Items.EGG));
			egg.setPos(start.x, start.y, start.z);
			egg.shoot(direction.x, direction.y, direction.z, 1.5F, 0.0F);
			if (!level.addFreshEntity(egg)) {
				return false;
			}
			ACTIVE_CHICKEN_EGG_PROJECTILES.put(
				egg.getUUID(),
				new ChickenEggProjectileState(
					owner.getUUID(),
					level.dimension().toString(),
					damage,
					radius,
					MadokuTimeManager.getGameplayTicks() + CHICKEN_EGG_LIFETIME_TICKS
				)
			);
			level.playSound(null, start.x, start.y, start.z, SoundEvents.EGG_THROW, SoundSource.NEUTRAL, 0.5F, 1.0F);
			return true;
		}

		public static boolean handleManagedChickenEggImpact(Entity projectile, HitResult hitResult) {
			if (projectile == null) {
				return false;
			}
			ChickenEggProjectileState state = ACTIVE_CHICKEN_EGG_PROJECTILES.remove(projectile.getUUID());
			if (state == null) {
				return false;
			}
			if (projectile.level() instanceof ServerLevel level) {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(state.ownerUuid);
				Vec3 impactPosition = hitResult == null ? projectile.position() : hitResult.getLocation();
				applyChickenEggImpact(level, owner, impactPosition, state.damage, state.radius);
			}
			projectile.discard();
			return true;
		}

			static void tickManagedBeeSwarms(MinecraftServer server) {
			if (server == null || ACTIVE_BEE_SWARMS.isEmpty()) {
				return;
			}

			long gameplayTicks = MadokuTimeManager.getGameplayTicks();
			for (Map.Entry<String, BeeSwarmState> entry : ACTIVE_BEE_SWARMS.entrySet()) {
				String swarmKey = entry.getKey();
				BeeSwarmState state = entry.getValue();
				ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerUuid);
				if (owner == null || !owner.isAlive()) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}

				PetInventory inventory = petInventory(owner);
				if (inventory == null || state.slot < 0 || state.slot >= inventory.getContainerSize()) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}
				PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(state.slot));
				PetAbilityRule ability = rule == null ? null : rule.ability(PET_ABILITY_BEE_SWARM);
				if (rule == null || !rule.enabled || ability == null) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}

				ServerLevel level = findLevel(server, state.dimensionId);
				LivingEntity target = findLivingEntity(server, state.targetUuid);
				if (level == null
					|| target == null
					|| !target.isAlive()
					|| target.level() != level
					|| owner.level() != level
					|| !isValidBeeSwarmTarget(owner, target)) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}
				if ((gameplayTicks - state.startedGameplayTick) >= BEE_SWARM_MAX_TARGET_DURATION_TICKS) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}
				if (shouldStopBeeSwarmFromTargetPriorityChange(owner, target, state)) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}

				double crazySpin = 0.45D + (owner.getRandom().nextDouble() * 0.40D);
				double nextAngle = state.orbitAngle + crazySpin;
				double radiusWave = Math.sin((gameplayTicks + state.slot * 11L) * 0.30D) * BEE_SWARM_ORBIT_RADIUS_VARIANCE;
				double nextRadius = Math.max(0.30D, BEE_SWARM_ORBIT_RADIUS_BASE + radiusWave);
				double heightOffset = Math.sin((gameplayTicks + state.slot * 7L) * 0.55D) * BEE_SWARM_ORBIT_VERTICAL_VARIANCE;
				Vec3 desiredOrbitPosition = resolveBeeSwarmOrbitPosition(target, nextAngle, nextRadius, heightOffset);
				Mob beePet = findBeePetForOwnerSlot(server, state.ownerUuid, state.slot);
				if (beePet == null || !beePet.isAlive() || beePet.level() != level) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
					continue;
				}
				Vec3 currentBeePosition = beePet.position();
				Vec3 toOrbit = desiredOrbitPosition.subtract(currentBeePosition);
				double orbitDistance = toOrbit.length();
				Vec3 movementStep;
				if (orbitDistance <= 1.0E-6D) {
					movementStep = Vec3.ZERO;
				} else {
					double moveCap = BEE_SWARM_MAX_MOVE_PER_TICK + owner.getRandom().nextDouble() * 0.10D;
					double step = Math.min(orbitDistance, moveCap);
					movementStep = toOrbit.normalize().scale(step);
				}
				Vec3 nextPosition = currentBeePosition.add(movementStep);
				beePet.setPos(nextPosition.x, nextPosition.y, nextPosition.z);
				beePet.setDeltaMovement(movementStep);
				beePet.getNavigation().stop();
				beePet.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ(), 35.0F, 35.0F);
				if ((gameplayTicks & 1L) == 0L) {
					emitBeeSwarmTrail(level, currentBeePosition, nextPosition);
				}

				long nextDamageTick = state.nextDamageTick;
				if (gameplayTicks >= nextDamageTick) {
					float damage = Math.max(0.0F, ability.attackDamage > 0.0F ? ability.attackDamage : BEE_SWARM_DEFAULT_DAMAGE_PER_SECOND);
					if (damage > 0.0F) {
						resetDamageImmunity(target);
						target.hurtServer(level, owner.damageSources().generic(), damage);
					}
					nextDamageTick = gameplayTicks + BEE_SWARM_DAMAGE_INTERVAL_TICKS;
				}

				ACTIVE_BEE_SWARMS.put(
					swarmKey,
					new BeeSwarmState(
						state.ownerUuid,
						state.slot,
						state.dimensionId,
						state.targetUuid,
						state.startedGameplayTick,
						state.ownerLastHurtMobTimestampAtStart,
						state.ownerLastHurtByMobTimestampAtStart,
						nextDamageTick,
						nextAngle,
						beePet.position()
					)
				);
			}
		}

		private static boolean shouldStopBeeSwarmFromTargetPriorityChange(ServerPlayer owner, LivingEntity target, BeeSwarmState state) {
			if (owner == null || target == null || state == null) {
				return true;
			}

			int lastHurtMobTimestamp = owner.getLastHurtMobTimestamp();
			if (lastHurtMobTimestamp > state.ownerLastHurtMobTimestampAtStart) {
				LivingEntity latestTarget = owner.getLastHurtMob();
				if (isValidBeeSwarmTarget(owner, latestTarget) && !latestTarget.getUUID().equals(target.getUUID())) {
					return true;
				}
			}

			int lastHurtByMobTimestamp = owner.getLastHurtByMobTimestamp();
			if (lastHurtByMobTimestamp > state.ownerLastHurtByMobTimestampAtStart) {
				LivingEntity latestAttacker = owner.getLastHurtByMob();

				return isValidBeeSwarmTarget(owner, latestAttacker) && !latestAttacker.getUUID().equals(target.getUUID());
			}
			return false;
		}

			private static Vec3 resolveBeeSwarmOrbitPosition(LivingEntity target, double angleRadians, double radius, double heightOffset) {
			Vec3 center = resolveWebProjectileTargetPosition(target);
			return new Vec3(
				center.x + Math.cos(angleRadians) * radius,
				center.y + heightOffset,
				center.z + Math.sin(angleRadians) * radius
			);
		}

		private static void emitBeeSwarmLaunch(ServerLevel level, Vec3 position, SoundEvent soundEvent, float soundVolume, float soundPitch) {
			if (level == null || position == null) {
				return;
			}
			if (soundEvent != null && soundVolume > 0.0F) {
				level.playSound(null, position.x, position.y, position.z, soundEvent, SoundSource.NEUTRAL, soundVolume, soundPitch);
			}
			level.sendParticles(ParticleTypes.FALLING_NECTAR, position.x, position.y, position.z, 4, 0.08D, 0.05D, 0.08D, 0.0D);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, position.x, position.y, position.z, 3, 0.10D, 0.10D, 0.10D, 0.0D);
		}

			private static void emitBeeSwarmTrail(ServerLevel level, Vec3 start, Vec3 end) {
			if (level == null || start == null || end == null) {
				return;
			}

			Vec3 delta = end.subtract(start);
			int steps = Math.max(1, (int) Math.ceil(delta.length() * 2.0D));
			for (int index = 0; index <= steps; index++) {
				double progress = (double) index / (double) steps;
				Vec3 sample = start.add(delta.scale(progress));
				level.sendParticles(ParticleTypes.FALLING_NECTAR, sample.x, sample.y, sample.z, 1, 0.006D, 0.004D, 0.006D, 0.0D);
			}
			if ((level.getGameTime() & 3L) == 0L) {
				Vec3 mid = start.add(delta.scale(0.5D));
				level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 1, 0.006D, 0.006D, 0.006D, 0.0D);
			}
		}

			private static String beeSwarmKey(UUID ownerId, int slot) {
			if (ownerId == null) {
				return "";
			}
			return ownerId + ":" + slot;
		}

			static void stopBeeSwarmForSlot(UUID ownerId, int slot) {
			if (ownerId == null) {
				return;
			}
			ACTIVE_BEE_SWARMS.remove(beeSwarmKey(ownerId, slot));
		}

		static void stopBeeSwarmsForOwner(UUID ownerId) {
			if (ownerId == null) {
				return;
			}
			NEXT_BEE_TARGET_SCAN_TICK.remove(ownerId);
			if (ACTIVE_BEE_SWARMS.isEmpty()) {
				return;
			}
			for (String swarmKey : new ArrayList<>(ACTIVE_BEE_SWARMS.keySet())) {
				if (swarmKey != null && swarmKey.startsWith(ownerId.toString() + ":")) {
					ACTIVE_BEE_SWARMS.remove(swarmKey);
				}
			}
		}

			static boolean isBeeSwarmActive(UUID ownerId, int slot) {
			if (ownerId == null || slot < 0) {
				return false;
			}
			return ACTIVE_BEE_SWARMS.containsKey(beeSwarmKey(ownerId, slot));
		}

			private static Mob findBeePetForOwnerSlot(MinecraftServer server, UUID ownerId, int slot) {
			if (server == null || ownerId == null || slot < 0 || slot >= SLOT_COUNT) {
				return null;
			}
			UUID[] petIds = PetEntitiesManager.PET_IDS_BY_PLAYER.get(ownerId);
			if (petIds == null || slot >= petIds.length) {
				return null;
			}
			return PetEntitiesManager.findMob(server, petIds[slot]);
		}

			private static ServerLevel findLevel(MinecraftServer server, String dimensionId) {
			if (server == null || dimensionId == null || dimensionId.isBlank()) {
				return null;
			}

			for (ServerLevel level : server.getAllLevels()) {
				if (dimensionId.equals(level.dimension().toString())) {
					return level;
				}
			}
			return null;

		}

			private static LivingEntity findLivingEntity(MinecraftServer server, UUID entityId) {
			if (server == null || entityId == null) {
				return null;
			}

			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(entityId) instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
					return livingEntity;
				}
			}
			return null;
		}

			private static Vec3 resolveWebProjectileTargetPosition(LivingEntity target) {
			if (target == null) {
				return Vec3.ZERO;
			}
			return new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.35D, target.getZ());
		}

		private static void emitWebProjectileLaunch(ServerLevel level, Vec3 position, SoundEvent soundEvent, float soundVolume, float soundPitch) {
			if (level == null || position == null) {
				return;
			}
			if (soundEvent != null && soundVolume > 0.0F) {
				level.playSound(null, position.x, position.y, position.z, soundEvent, SoundSource.HOSTILE, soundVolume, soundPitch);
			}
			level.sendParticles(ParticleTypes.ITEM_COBWEB, position.x, position.y, position.z, 2, 0.015D, 0.015D, 0.015D, 0.0D);
			level.sendParticles(ParticleTypes.WHITE_ASH, position.x, position.y, position.z, 2, 0.02D, 0.01D, 0.02D, 0.0D);
		}

		private static void emitExplosiveProjectileLaunch(ServerLevel level, Vec3 position, SoundEvent soundEvent, float soundVolume, float soundPitch) {
			if (level == null || position == null) {
				return;
			}
			if (soundEvent != null && soundVolume > 0.0F) {
				level.playSound(null, position.x, position.y, position.z, soundEvent, SoundSource.HOSTILE, soundVolume, soundPitch);
			}
			level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 4, 0.04D, 0.04D, 0.04D, 0.0D);
			level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 3, 0.02D, 0.02D, 0.02D, 0.0D);
		}

			private static void emitWebProjectileTrail(ServerLevel level, Vec3 start, Vec3 end) {
			if (level == null || start == null || end == null) {
				return;
			}

			Vec3 delta = end.subtract(start);
			int steps = Math.max(1, (int) Math.ceil(delta.length() * 4.0D));
			for (int index = 0; index <= steps; index++) {
				double progress = (double) index / (double) steps;
				Vec3 sample = start.add(delta.scale(progress));
				level.sendParticles(ParticleTypes.WHITE_ASH, sample.x, sample.y, sample.z, 1, 0.008D, 0.004D, 0.008D, 0.0D);
				if ((index & 1) == 0) {
					level.sendParticles(ParticleTypes.ITEM_COBWEB, sample.x, sample.y, sample.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				}
			}
		}

			private static void emitExplosiveProjectileTrail(ServerLevel level, Vec3 start, Vec3 end) {
			if (level == null || start == null || end == null) {
				return;
			}

			Vec3 delta = end.subtract(start);
			int steps = Math.max(1, (int) Math.ceil(delta.length() * 4.0D));
			for (int index = 0; index <= steps; index++) {
				double progress = (double) index / (double) steps;
				Vec3 sample = start.add(delta.scale(progress));
				level.sendParticles(ParticleTypes.SMOKE, sample.x, sample.y, sample.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
				if ((index & 1) == 0) {
					level.sendParticles(ParticleTypes.FLAME, sample.x, sample.y, sample.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				}
			}
		}

			private static void emitWebProjectileImpact(ServerLevel level, Vec3 position) {
			if (level == null || position == null) {
				return;
			}
			level.sendParticles(ParticleTypes.SMALL_GUST, position.x, position.y, position.z, 8, 0.18D, 0.22D, 0.18D, 0.0D);
			level.sendParticles(ParticleTypes.CLOUD, position.x, position.y, position.z, 6, 0.15D, 0.10D, 0.15D, 0.0D);
		}

			private static void emitExplosiveProjectileImpact(ServerLevel level, Vec3 position, float radius) {
			if (level == null || position == null) {
				return;
			}
			double spread = Math.max(0.10D, radius * 0.25D);
			level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.0F);
			level.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 2, spread, spread, spread, 0.0D);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y, position.z, 10, spread, spread, spread, 0.02D);
			level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 8, spread, spread * 0.5D, spread, 0.01D);
		}

		private static boolean applyWebProjectileHit(
			UUID projectileId,
			ServerLevel level,
			ServerPlayer owner,
			LivingEntity target,
			Vec3 position,
			WebProjectileState projectileState
		) {
			if (projectileId == null || owner == null || target == null || projectileState == null || !target.isAlive()) {
				return false;
			}
			Set<UUID> hitEntityUuids = new HashSet<>(projectileState.hitEntityUuids);
			if (!hitEntityUuids.add(target.getUUID())) {
				return false;
			}

			emitWebProjectileImpact(level, position);
			applyWebProjectileHitToEntity(
				level,
				owner,
				target,
				projectileState.damage,
				projectileState.stunDurationTicks,
				projectileState.slowDurationTicks,
				projectileState.slowPercentage
			);

			if (projectileState.remainingRicochets <= 0) {
				return false;
			}
			LivingEntity ricochetTarget = findNearestWebRicochetTarget(level, owner, target, hitEntityUuids);
			if (ricochetTarget == null) {
				return false;
			}

			ACTIVE_WEB_PROJECTILES.put(
				projectileId,
				new WebProjectileState(
					projectileState.dimensionId,
					projectileState.ownerUuid,
					ricochetTarget.getUUID(),
					position,
					projectileState.speed,
					projectileState.ricochet ? projectileState.damage : Math.max(0.0F, projectileState.damage * 0.5F),
					projectileState.ricochet ? projectileState.effectDurationTicks : halfDurationTicks(projectileState.effectDurationTicks),
					projectileState.ricochet ? projectileState.stunDurationTicks : halfDurationTicks(projectileState.stunDurationTicks),
					projectileState.ricochet ? projectileState.slowDurationTicks : halfDurationTicks(projectileState.slowDurationTicks),
					projectileState.ricochet ? projectileState.slowPercentage : projectileState.slowPercentage * 0.5F,
					true,
					projectileState.remainingRicochets - 1,
					Set.copyOf(hitEntityUuids),
					projectileState.remainingTicks
				)
			);
			return true;
		}

		private static LivingEntity findNearestWebRicochetTarget(
			ServerLevel level,
			ServerPlayer owner,
			LivingEntity source,
			Set<UUID> hitEntityUuids
		) {
			if (level == null || owner == null || source == null || hitEntityUuids == null) {
				return null;
			}
			AABB searchArea = source.getBoundingBox().inflate(WEB_PROJECTILE_RICOCHET_RADIUS);
			LivingEntity nearest = null;
			double nearestDistance = Double.MAX_VALUE;
			for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchArea, entity ->
				entity != null
					&& entity.isAlive()
					&& entity instanceof Mob
					&& !entity.getUUID().equals(owner.getUUID())
					&& !hitEntityUuids.contains(entity.getUUID())
					&& !isManagedPet(entity)
					&& source.distanceToSqr(entity) <= WEB_PROJECTILE_RICOCHET_RADIUS * WEB_PROJECTILE_RICOCHET_RADIUS
			)) {
				double distance = source.distanceToSqr(candidate);
				if (distance < nearestDistance) {
					nearest = candidate;
					nearestDistance = distance;
				}
			}
			return nearest;
		}

		private static int halfDurationTicks(int durationTicks) {
			return Math.max(0, (int) Math.round(Math.max(0, durationTicks) * 0.5D));
		}

		private static void applyWebProjectileHitToEntity(
			ServerLevel level,
			ServerPlayer owner,
			LivingEntity target,
			float damage,
			int stunDurationTicks,
			int slowDurationTicks,
			float slowPercentage
		) {
			if (level == null || owner == null || target == null || !target.isAlive()) {
				return;
			}
			if (!(target instanceof Player)) {
				Vec3 movement = target.getDeltaMovement();
				target.setDeltaMovement(new Vec3(0.0D, movement.y, 0.0D));
			}
			if (damage > 0.0F) {
				target.hurtServer(level, owner.damageSources().generic(), damage);
				if (!(target instanceof Player)) {
					Vec3 movement = target.getDeltaMovement();
					target.setDeltaMovement(new Vec3(0.0D, movement.y, 0.0D));
				}
			}

			long now = MadokuTimeManager.getGameplayTicks();
			WebControlState existing = ACTIVE_WEB_CONTROLS.get(target.getUUID());
			long initialStunDuration = target instanceof Player ? 0L : Math.max(0L, stunDurationTicks);
			long initialSlowDuration = Math.max(0L, slowDurationTicks);
			long stunUntil = existing == null
				? now + initialStunDuration
				: now < existing.stunUntilTick
					? existing.stunUntilTick + initialStunDuration
					: now + initialStunDuration;
			long slowUntil = existing == null
				? stunUntil + initialSlowDuration
				: now < existing.slowUntilTick
					? Math.max(stunUntil, existing.slowUntilTick) + initialSlowDuration
					: stunUntil + initialSlowDuration;
			ACTIVE_WEB_CONTROLS.put(
				target.getUUID(),
				new WebControlState(
					stunUntil,
					slowUntil,
					Math.max(0.0F, Math.min(1.0F, slowPercentage))
				)
			);
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}

		private static void applyExplosiveProjectileHit(
			ServerLevel level,
			ServerPlayer owner,
			Vec3 position,
			float damage,
			float radius,
			float vulnerability,
			int vulnerabilityDurationTicks
		) {
			emitExplosiveProjectileImpact(level, position, radius);
			if (level == null || owner == null || !owner.isAlive() || position == null || radius <= 0.0F || damage <= 0.0F) {
				return;
			}

			AABB area = new AABB(
				position.x - radius,
				position.y - radius,
				position.z - radius,
				position.x + radius,
				position.y + radius,
				position.z + radius
			);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, area, candidate ->
				candidate != null && candidate.isAlive() && !isManagedPet(candidate) && !candidate.getUUID().equals(owner.getUUID())
			)) {
				double distance = mob.position().distanceTo(position);
				if (distance > radius) {
					continue;
				}
				resetDamageImmunity(mob);
				mob.hurtServer(level, owner.damageSources().generic(), damage);
				addExplosiveVulnerability(mob, vulnerability, vulnerabilityDurationTicks);
				Vec3 knockback = mob.position().subtract(position);
				if (knockback.lengthSqr() > 1.0E-6D) {
					double strength = Math.max(0.0D, 0.35D * (1.0D - (distance / radius)));
					mob.push(knockback.normalize().x * strength, 0.12D, knockback.normalize().z * strength);
				}
			}
		}

		private static void applyChickenEggImpact(ServerLevel level, ServerPlayer owner, Vec3 position, float damage, float radius) {
			if (level == null || position == null) {
				return;
			}
			level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			double spread = Math.max(0.15D, radius * 0.5D);
			level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 18, spread, spread, spread, 0.02D);
			level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 8, spread, spread, spread, 0.01D);
			if (owner == null || !owner.isAlive() || damage <= 0.0F || radius <= 0.0F) {
				return;
			}
			AABB area = new AABB(
				position.x - radius,
				position.y - radius,
				position.z - radius,
				position.x + radius,
				position.y + radius,
				position.z + radius
			);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, candidate ->
				candidate != null
					&& candidate.isAlive()
					&& !candidate.getUUID().equals(owner.getUUID())
					&& !isManagedPet(candidate)
			)) {
				if (target.position().distanceTo(position) > radius) {
					continue;
				}
				Vec3 velocity = target.getDeltaMovement();
				resetDamageImmunity(target);
				if (target.hurtServer(level, owner.damageSources().generic(), damage)) {
					target.setDeltaMovement(velocity);
				}
			}
		}

			private static void resetDamageImmunity(LivingEntity entity) {
			if (entity == null) {
				return;
			}
			entity.invulnerableTime = 0;
			entity.hurtTime = 0;
		}

		private static Vec3 resolveRangedAttackSpawn(ServerPlayer player, int index, int count, PetAbilityRule ability) {
			Vec3 forward = player.getLookAngle();
			forward = new Vec3(forward.x, 0.0D, forward.z);
			if (forward.lengthSqr() <= 1.0E-6D) {
				forward = new Vec3(0.0D, 0.0D, 1.0D);
			} else {
				forward = forward.normalize();
			}

			Vec3 back = forward.scale(-1.0D);
			Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
			double angleRadians = Math.toRadians(resolveShotArcAngle(index, count, ability));
			double lateralOffset = Math.sin(angleRadians) * ability.attackLateralRadius;
			double rearOffset = ability.attackRearOffset + Math.cos(angleRadians) * ability.attackRearSpread;
			return player.getEyePosition()
				.add(back.scale(rearOffset))
				.add(right.scale(lateralOffset))
				.add(0.0D, ability.attackVerticalOffset, 0.0D);
		}

		private static double resolveShotArcAngle(int index, int count, PetAbilityRule ability) {
			int clampedCount = Math.max(1, Math.min(SLOT_COUNT, count));
			if (clampedCount == 1) {
				return 0.0D;
			}

			double centeringOffset = (clampedCount - 1) * 0.5D;
			return (index - centeringOffset) * Math.max(0.0D, ability.attackArcStepDegrees);
		}

		private static boolean enqueueDelayedPetAttack(ServerPlayer player, int slot, String abilityType, LivingEntity target, Vec3 spawnPosition, long delayTicks) {
			MinecraftServer server = player == null ? null : player.level().getServer();
			if (server == null || player == null || target == null || spawnPosition == null) {
				return false;
			}

			UUID playerId = player.getUUID();
			String schedulerId = ensureSchedulerExists(playerId);
			if (enqueuePetAttackTask(schedulerId, slot, abilityType, target, spawnPosition, delayTicks)) {
				return true;
			}

			String created = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId));
			PLAYER_SCHEDULER_IDS.put(playerId, created);
			if (!enqueuePetAttackTask(created, slot, abilityType, target, spawnPosition, delayTicks)) {
				LOGGER.error("Failed to enqueue delayed pet attack for player={} slot={}", playerId, slot);
				return false;
			}
			return true;
		}

			private static String ensureSchedulerExists(UUID playerId) {
			String schedulerId = PLAYER_SCHEDULER_IDS.get(playerId);
			if (schedulerId == null || schedulerId.isBlank()) {
				schedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId));
				PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
			}
			return schedulerId;
		}

		private static boolean enqueuePetAttackTask(String schedulerId, int slot, String abilityType, LivingEntity target, Vec3 spawnPosition, long delayTicks) {
			if (schedulerId == null || schedulerId.isBlank() || target == null || spawnPosition == null) {
				return false;
			}

			JsonObject payload = madoku.craft.api.json.JSONFormatManager.object()
				.put(FIELD_SLOT, slot)
				.put(FIELD_ABILITY_ID, abilityType)
				.put(FIELD_TARGET_UUID, target.getUUID().toString())
				.put(FIELD_SPAWN_X, spawnPosition.x)
				.put(FIELD_SPAWN_Y, spawnPosition.y)
				.put(FIELD_SPAWN_Z, spawnPosition.z)
				.build();
			MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
				schedulerId,
				Math.max(0L, delayTicks),
				TASK_TYPE_PET_ATTACK,
				payload,
				MadokuSchedulerManager.TickDomain.GAMEPLAY
			);
			return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED;
		}

		static boolean isAbilityOffCooldown(ServerPlayer player, int slot, String abilityType, long gameplayTicks) {
			if (player == null || slot < 0 || slot >= SLOT_COUNT || abilityType == null || abilityType.isBlank()) return false;
			return gameplayTicks >= abilityCooldown(player.getUUID(), slot, abilityType);
		}

		static void clearSlotCooldowns(UUID playerId) {
			if (playerId != null) {
				PLAYER_ABILITY_COOLDOWNS.remove(playerId);
				PetHudManager.markAbilityHudDirty(playerId);
			}
		}

			static void refreshPlayerPassiveAbilityBonuses(MinecraftServer server) {
			if (server == null) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PetAbilitiesManager.applyPlayerMaxHealthAbilityBonus(player);
				PetAbilitiesManager.applyPlayerArmorAbilityBonus(player);
				PetAbilitiesManager.applyPlayerArmorToughnessAbilityBonus(player);
				PetAbilitiesManager.applyPlayerDamageAbilityBonus(player);
			}
		}

		static void setAbilityCooldown(UUID playerId, int slot, String abilityType, long cooldownTick) {
			if (playerId == null || slot < 0 || slot >= SLOT_COUNT || abilityType == null || abilityType.isBlank()) return;
			PLAYER_ABILITY_COOLDOWNS
				.computeIfAbsent(playerId, ignored -> new HashMap<>())
				.computeIfAbsent(slot, ignored -> new HashMap<>())
				.put(PetConfigManager.normalizeAbilityId(abilityType), Math.max(0L, cooldownTick));
			PetHudManager.markAbilityHudDirty(playerId);
		}

		private static long abilityCooldown(UUID playerId, int slot, String abilityType) {
			Map<Integer, Map<String, Long>> playerCooldowns = PLAYER_ABILITY_COOLDOWNS.get(playerId);
			Map<String, Long> slotCooldowns = playerCooldowns == null ? null : playerCooldowns.get(slot);
			return slotCooldowns == null ? 0L : slotCooldowns.getOrDefault(PetConfigManager.normalizeAbilityId(abilityType), 0L);
		}

		static void pruneCooldowns(UUID playerId, PetInventory inventory) {
			if (playerId == null || inventory == null) {
				return;
			}
			Map<Integer, Map<String, Long>> cooldowns = PLAYER_ABILITY_COOLDOWNS.get(playerId);
			long gameplayTicks = MadokuTimeManager.getGameplayTicks();
			boolean changed = false;
			if (cooldowns != null) {
				for (Map<String, Long> slotCooldowns : cooldowns.values()) {
					if (slotCooldowns != null) {
						int sizeBefore = slotCooldowns.size();
						slotCooldowns.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= gameplayTicks);
						changed |= sizeBefore != slotCooldowns.size();
					}
				}
				int slotsBefore = cooldowns.size();
				cooldowns.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
				changed |= slotsBefore != cooldowns.size();
				if (cooldowns.isEmpty()) {
					PLAYER_ABILITY_COOLDOWNS.remove(playerId);
					changed = true;
				}
			}
			if (changed) PetHudManager.markAbilityHudDirty(playerId);
		}

		static int[] currentAbilityCooldowns(ServerPlayer player) {
			int[] remaining = new int[SLOT_COUNT * MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET];
			if (player == null) {
				return remaining;
			}

			UUID playerId = player.getUUID();
			PetInventory inventory = petInventory(player);
			if (inventory == null) return remaining;
			long now = MadokuTimeManager.getGameplayTicks();
			for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
				PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
				if (rule == null || !rule.enabled) continue;
				int cooldownIndex = 0;
				for (PetAbilityRule ability : rule.abilities) {
					if (ability.cooldownTicks <= 0L || cooldownIndex >= MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET) continue;
					long cooldown = abilityCooldown(playerId, slot, ability.abilityType);
					remaining[slot * MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET + cooldownIndex] =
						(int) Math.min(Integer.MAX_VALUE, Math.max(0L, cooldown - now));
					cooldownIndex++;
				}
			}
			return remaining;
		}

		static JsonObject toPersistedData() {
			madoku.craft.api.json.JSONFormatManager.ArrayBuilder players = madoku.craft.api.json.JSONFormatManager.array();
			long now = MadokuTimeManager.getGameplayTicks();
			for (Map.Entry<UUID, Map<Integer, Map<String, Long>>> playerEntry : PLAYER_ABILITY_COOLDOWNS.entrySet()) {
				if (playerEntry.getKey() == null) continue;
				madoku.craft.api.json.JSONFormatManager.ArrayBuilder playerCooldowns = madoku.craft.api.json.JSONFormatManager.array();
				boolean hasActiveCooldown = false;
				for (Map.Entry<Integer, Map<String, Long>> slotEntry : playerEntry.getValue().entrySet()) {
					for (Map.Entry<String, Long> abilityEntry : slotEntry.getValue().entrySet()) {
						if (abilityEntry.getValue() == null) continue;
						long remainingTicks = abilityEntry.getValue() - now;
						if (remainingTicks <= 0L) continue;
						hasActiveCooldown = true;
						playerCooldowns.object(playerCooldown -> playerCooldown
							.put("slot", slotEntry.getKey())
							.put("ability-id", abilityEntry.getKey())
							.put("remaining-ticks", remainingTicks));
					}
				}
				if (hasActiveCooldown) {
					players.object(player -> player
						.put("uuid", playerEntry.getKey().toString())
						.put("cooldowns", playerCooldowns.build()));
				}
			}
			return madoku.craft.api.json.JSONFormatManager.object()
				.put("ability-cooldowns", players.build())
				.build();
		}

			static void applyPersistedData(JsonObject source) {
			PLAYER_SCHEDULER_IDS.clear();

			PLAYER_ABILITY_COOLDOWNS.clear();
			if (source == null) {
				return;
			}

			JsonArray abilityCooldowns = getArray(source, "ability-cooldowns");
			if (abilityCooldowns != null) for (JsonElement element : abilityCooldowns) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject cooldownData = element.getAsJsonObject();
				UUID playerId = parseUuid(PetConfigManager.getString(cooldownData, "uuid", ""));
				JsonArray groupedCooldowns = getArray(cooldownData, "cooldowns");
				if (playerId != null && groupedCooldowns != null) {
					for (JsonElement groupedElement : groupedCooldowns) {
						if (groupedElement != null && groupedElement.isJsonObject()) {
							restorePersistedCooldown(playerId, groupedElement.getAsJsonObject());
						}
					}
				} else {
					restorePersistedCooldown(playerId, cooldownData);
				}
			}
		}

		private static void restorePersistedCooldown(UUID playerId, JsonObject cooldownData) {
			if (playerId == null || cooldownData == null) return;
			int slot = PetConfigManager.getInt(cooldownData, "slot", -1);
			String abilityType = PetConfigManager.normalizeAbilityId(PetConfigManager.getString(cooldownData, "ability-id", ""));
			long remainingTicks = Math.max(0L, PetConfigManager.getLong(cooldownData, "remaining-ticks", 0L));
			if (remainingTicks <= 0L && cooldownData.has("cooldown")) {
				long legacyCooldownTick = Math.max(0L, PetConfigManager.getLong(cooldownData, "cooldown", 0L));
				long now = MadokuTimeManager.getGameplayTicks();
				remainingTicks = legacyCooldownTick > now ? legacyCooldownTick - now : 0L;
			}
			if (slot < 0 || slot >= SLOT_COUNT || abilityType.isBlank() || remainingTicks <= 0L) return;
			long now = MadokuTimeManager.getGameplayTicks();
			long cooldownTick = remainingTicks > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + remainingTicks;
			setAbilityCooldown(playerId, slot, abilityType, cooldownTick);
		}

			private static JsonArray getArray(JsonObject source, String key) {
			if (source == null || key == null || !source.has(key) || !source.get(key).isJsonArray()) {
				return null;
			}
			return source.getAsJsonArray(key);
		}

	private static PetInventory petInventory(Player player) {
		return PetComponentsManager.petInventory(player);
	}

	private static boolean isManagedPet(Entity entity) {
		return PetEntitiesManager.isManaged(entity);
	}

	private record WebProjectileState(
		String dimensionId,
		UUID ownerUuid,
		UUID targetUuid,
		Vec3 position,
		double speed,
		float damage,
		int effectDurationTicks,
		int stunDurationTicks,
		int slowDurationTicks,
		float slowPercentage,
		boolean ricochet,
		int remainingRicochets,
		Set<UUID> hitEntityUuids,
		int remainingTicks
	) {}

	private record WebControlState(
		long stunUntilTick,
		long slowUntilTick,
		float slowPercentage
	) {}

	private record HealthRegenerationState(
		long nextHealTick,
		long untilTick,
		double healPercentage
	) {}

	private record ExplosiveProjectileState(
		String dimensionId,
		UUID ownerUuid,
		UUID targetUuid,
		Vec3 position,
		double speed,
		float damage,
		float radius,
		float vulnerability,
		int vulnerabilityDurationTicks,
		int remainingTicks
	) {}

	private record ExplosiveVulnerabilityState(
		float vulnerability,
		long expiresAtTick
	) {}

	private record ChickenEggProjectileState(
		UUID ownerUuid,
		String dimensionId,
		float damage,
		float radius,
		long expiresAtTick
	) {}

	private record ChickenEggVolleyState(
		UUID ownerUuid,
		String dimensionId,
		Vec3 targetPosition,
		int remainingProjectiles,
		long nextLaunchTick,
		float damage,
		float radius
	) {}

	private static final class ManagedChickenEgg extends ThrownEgg {
		private ManagedChickenEgg(ServerLevel level, ServerPlayer owner, ItemStack itemStack) {
			super(level, owner, itemStack);
		}

		@Override
		protected void onHit(HitResult hitResult) {
			if (!level().isClientSide()) {
				PetAbilitiesManager.handleManagedChickenEggImpact(this, hitResult);
				return;
			}
			super.onHit(hitResult);
		}
	}

	private record ReadyReactiveAttack(int slot, PetRule rule, PetAbilityRule ability) {}

	private record BeeSwarmState(
		UUID ownerUuid,
		int slot,
		String dimensionId,
		UUID targetUuid,
		long startedGameplayTick,
		int ownerLastHurtMobTimestampAtStart,
		int ownerLastHurtByMobTimestampAtStart,
			long nextDamageTick,
			double orbitAngle,
			Vec3 position
	) {}
	private static UUID parseUuid(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			try {
				return UUID.fromString(value.trim());
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}
}
