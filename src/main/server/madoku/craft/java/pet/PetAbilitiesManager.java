package madoku.craft.java.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import madoku.craft.java.entity.Hag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import java.util.*;

import com.google.gson.JsonObject;
import madoku.craft.java.MadokuCraft;
import madoku.craft.java.core.chunk.ChunkAPIManager;
import madoku.craft.java.core.helper.HelperProjectileAPIManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.scheduler.SchedulerAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.pet.PetComponentsAPIManager.PetInventory;
import madoku.craft.java.pet.PetConfigManager.PetAbilityRule;
import madoku.craft.java.pet.PetConfigManager.PetRule;
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
	private static final String PLAYER_SCHEDULER_KEY = "player_entities";
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
	private static final int WEB_PROJECTILE_LIFETIME_TICKS = 20;
	private static final int WEB_PROJECTILE_BASE_RICOCHETS = 3;
	private static final double WEB_PROJECTILE_RICOCHET_RADIUS = 5.0D;
	private static final double WEB_PROJECTILE_RICOCHET_RADIUS_PER_LEVEL = 0.25D;
	private static final double WEB_PROJECTILE_RICOCHET_RADIUS_PER_DUPLICATE = 1.0D;
	private static final double WEB_PROJECTILE_RICOCHETS_PER_LEVEL = 0.25D;
	private static final int WEB_PROJECTILE_DUPLICATE_DAMAGE = 1;
	private static final int WEB_PROJECTILE_DUPLICATE_STUN_TICKS = 10;
	private static final long WEB_PROJECTILE_DUPLICATE_SLOW_DURATION_TICKS = 2L * 20L;
	private static final long HEALTH_REGEN_DUPLICATE_DURATION_TICKS = 20L;
	private static final long HEALTH_REGEN_TICK_INTERVAL_TICKS = 20L;
	private static final double WEB_PROJECTILE_HIT_DISTANCE = 0.75D;
	private static final double WEB_PROJECTILE_MIN_SPEED = 0.20D;
	private static final int EXPLOSIVE_PROJECTILE_LIFETIME_TICKS = 20;
	private static final double EXPLOSIVE_PROJECTILE_HIT_DISTANCE = 1.0D;
	private static final double EXPLOSIVE_PROJECTILE_MIN_SPEED = 0.5D;
	private static final double LEFT_CLICK_TARGET_RANGE = 32.0D;
	private static final long EGG_ABILITY_COOLDOWN_TICKS = 15L * 20L;
	private static final int EGG_PROJECTILE_LIFETIME_TICKS = 100;
	private static final double FALL_DAMAGE_REDUCTION_BASE = 0.30D;
	private static final double FALL_DAMAGE_REDUCTION_PER_DUPLICATE = 0.10D;
	private static final int BAT_SCAN_BASE_RADIUS_BLOCKS = 24;
	private static final int MOB_SCAN_VERTICAL_RADIUS_PER_DUPLICATE_ABILITY = 4;
	private static final long BAT_SCAN_BASE_GLOWING_DURATION_TICKS = 60L * 20L;
	private static final long BAT_SCAN_GLOWING_DURATION_PER_LEVEL_TICKS = 2L * 20L + 10L;
	private static final long MOB_SCAN_GLOWING_DURATION_PER_DUPLICATE_ABILITY_TICKS = 5L * 20L;
	private static final float BAT_SCAN_BASE_VULNERABILITY = 0.15F;
	private static final float MOB_SCAN_VULNERABILITY_PER_DUPLICATE_ABILITY = 0.05F;
	private static final String MOB_SCAN_VULNERABILITY_TAG = "madoku-craft.mob-scan-vulnerability";
	private static final long MOB_SCAN_COOLDOWN_REDUCTION_PER_DUPLICATE_ABILITY = 5L * 20L;
	private static final long BAT_SCAN_COOLDOWN_REDUCTION_PER_LEVEL = 2L * 20L + 10L;
	private static final double BEE_SWARM_SCAN_RADIUS = 16.0D;
	private static final double BEE_SWARM_SCAN_VERTICAL_RADIUS = 8.0D;
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
	private static final Map<UUID, ProjectileVolleyState> ACTIVE_PROJECTILE_VOLLEYS = new ConcurrentHashMap<>();
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
		SchedulerAPIManager.clearAdaptiveDelayState(BEE_TARGET_SCAN_SCHEDULER_OWNER_ID);
		ACTIVE_WEB_PROJECTILES.clear();
		ACTIVE_PROJECTILE_VOLLEYS.clear();
		ACTIVE_WEB_CONTROLS.clear();
		ACTIVE_HEALTH_REGENERATIONS.clear();
		ACTIVE_EXPLOSIVE_PROJECTILES.clear();
		ACTIVE_CHICKEN_EGG_PROJECTILES.clear();
		ACTIVE_CHICKEN_EGG_VOLLEYS.clear();
		ACTIVE_BEE_SWARMS.clear();
		MOB_SCAN_VULNERABILITY_BY_ENTITY.clear();
		EXPLOSIVE_VULNERABILITY_BY_ENTITY.clear();
	}

	static boolean hasRuntimeWork() {
		return !ACTIVE_WEB_PROJECTILES.isEmpty()
			|| !ACTIVE_PROJECTILE_VOLLEYS.isEmpty()
			|| !ACTIVE_WEB_CONTROLS.isEmpty()
			|| !ACTIVE_HEALTH_REGENERATIONS.isEmpty()
			|| !ACTIVE_EXPLOSIVE_PROJECTILES.isEmpty()
			|| !ACTIVE_CHICKEN_EGG_PROJECTILES.isEmpty()
			|| !ACTIVE_CHICKEN_EGG_VOLLEYS.isEmpty()
			|| !ACTIVE_BEE_SWARMS.isEmpty();
	}

	static void tickWebControls(MinecraftServer server) {
		if (server == null || ACTIVE_WEB_CONTROLS.isEmpty()) {
			return;
		}
		long now = TimeAPIManager.getGameplayTicks();
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
		long now = TimeAPIManager.getGameplayTicks();
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
		long now = TimeAPIManager.getGameplayTicks();
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
		long now = TimeAPIManager.getGameplayTicks();
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
		return state != null && TimeAPIManager.getGameplayTicks() < state.stunUntilTick;
	}

	public static float scaleWebMovementSpeed(LivingEntity entity, float speed) {
		if (entity == null || speed <= 0.0F) {
			return speed;
		}
		WebControlState state = ACTIVE_WEB_CONTROLS.get(entity.getUUID());
		if (state == null) {
			return speed;
		}
		long now = TimeAPIManager.getGameplayTicks();
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
		long now = TimeAPIManager.getGameplayTicks();
		if (now < state.stunUntilTick || now >= state.slowUntilTick) {
			return movement;
		}
		double multiplier = Math.max(0.0D, 1.0D - state.slowPercentage);
		return movement.scale(multiplier);
	}

	private PetAbilitiesManager() {
	}

	public static void initialize() {
		PetAbilitiesAPIManager.registerProvider(new MadokuPetAbilitiesProvider());
		ServerPlayNetworking.registerGlobalReceiver(PetPayloadAPIManager.LeftClickAirPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!isWebStunned(player)) {
				handlePlayerLeftClick(player);
			}
		});
	}

	/** Owns the dynamic ability JSON definitions under madoku-abilities. */
	public static final class AbilitiesConfigManager {
		private static final Map<String, JsonObject> definitions = new LinkedHashMap<>();
		private static volatile Map<String, JsonObject> clientSynchronizedDefinitions;

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
				Map<String, JsonObject> loaded = JSONFormatAPIManager.ensureManagedFolder(
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
			Map<String, JsonObject> synchronizedDefinitions = clientSynchronizedDefinitions;
			return copyDefinitions(synchronizedDefinitions == null ? definitions : synchronizedDefinitions);
		}

		static Map<String, JsonObject> snapshotDefinitions() {
			return copyDefinitions(definitions);
		}

		static void applyClientSynchronizedDefinitions(Map<String, JsonObject> synchronizedDefinitions) {
			clientSynchronizedDefinitions = copyDefinitions(synchronizedDefinitions);
		}

		static void resetClientSynchronizedDefinitions() {
			clientSynchronizedDefinitions = null;
		}

		private static Map<String, JsonObject> copyDefinitions(Map<String, JsonObject> source) {
			Map<String, JsonObject> copy = new LinkedHashMap<>();
			if (source != null) {
				for (Map.Entry<String, JsonObject> entry : source.entrySet()) {
					if (entry.getKey() != null && entry.getValue() != null) {
						copy.put(entry.getKey(), entry.getValue().deepCopy());
					}
				}
			}
			return Map.copyOf(copy);
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

		long now = TimeAPIManager.getGameplayTicks();
		synchronizeAllAbilityCooldowns(player, inventory, now);
		int eggAbilityCount = 0;
		float damage = 0.0F;
		float radius = 0.0F;
		double configuredProjectileCount = 0.0D;
		long configuredProjectileIntervalTicks = 0L;
		boolean hasReadyTargetedPet = false;
		int[] eggAbilitySlots = new int[SLOT_COUNT];
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
			if (rule == null || !rule.enabled || eggAbility == null
				|| !isAbilityOffCooldown(player, slot, eggAbility.abilityType, now)) {
				continue;
			}
			eggAbilitySlots[eggAbilityCount++] = slot;
			damage = Math.max(damage, eggAbility.attackDamage);
			radius = Math.max(radius, eggAbility.explosionRadius);
			configuredProjectileCount = Math.max(configuredProjectileCount, eggAbility.projectileCount);
			configuredProjectileIntervalTicks = Math.max(configuredProjectileIntervalTicks, eggAbility.projectileIntervalTicks);
		}
		if (eggAbilityCount <= 0 && !hasReadyTargetedPet) {
			return;
		}
		LivingEntity target = hasReadyTargetedPet ? resolveLeftClickTarget(player) : null;
		if (eggAbilityCount <= 0 || ACTIVE_CHICKEN_EGG_VOLLEYS.containsKey(player.getUUID())) {
		} else {
			Vec3 targetPosition = player.getEyePosition().add(player.getLookAngle().scale(32.0D));
			configuredProjectileCount += Math.max(0, eggAbilityCount - 1);
			int projectileCount = resolveProjectileCount(player, configuredProjectileCount);
			damage = Math.max(0.0F, damage);
			damage += Math.max(0, eggAbilityCount - 1);
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
						radius,
						configuredProjectileIntervalTicks
					)
				);
				for (int index = 0; index < eggAbilityCount; index++) {
					setAbilityCooldown(player.getUUID(), eggAbilitySlots[index], PET_ABILITY_EGG_PROJECTILE, now + EGG_ABILITY_COOLDOWN_TICKS);
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
		long now = TimeAPIManager.getGameplayTicks();
		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}
		synchronizeAllAbilityCooldowns(player, inventory, now);
		int abilityCount = 0;
		int[] healthSlots = new int[SLOT_COUNT];
		int healthSlotCount = 0;
		long sharedCooldownTicks = 0L;
		double healPercentage = 0.0D;
		long durationTicks = 0L;
		double strongestHealLevelBonus = 0.0D;
		long strongestDurationLevelBonus = 0L;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			PetRule baseRule = PetConfigManager.resolvePetRule(PetEntitiesManager.petId(stack));
			if (rule == null || baseRule == null || !rule.enabled) {
				continue;
			}
			for (PetAbilityRule ability : rule.abilities) {
				if (!PET_ABILITY_HEALTH_REGENERATION.equals(ability.abilityType)) {
					continue;
				}
				PetAbilityRule baseAbility = baseRule.ability(PET_ABILITY_HEALTH_REGENERATION);
				if (baseAbility == null) {
					continue;
				}
				abilityCount++;
				if (healthSlotCount == 0 || healthSlots[healthSlotCount - 1] != slot) {
					healthSlots[healthSlotCount++] = slot;
				}
				sharedCooldownTicks = Math.max(sharedCooldownTicks, ability.cooldownTicks);
				healPercentage += Math.max(0.0D, baseAbility.healthRegenerationAmount);
				durationTicks = Math.max(durationTicks, Math.max(0L, baseAbility.effectDurationTicks));
				strongestHealLevelBonus = Math.max(
					strongestHealLevelBonus,
					Math.max(0.0D, ability.healthRegenerationAmount - baseAbility.healthRegenerationAmount)
				);
				strongestDurationLevelBonus = Math.max(
					strongestDurationLevelBonus,
					Math.max(0L, ability.effectDurationTicks - baseAbility.effectDurationTicks)
				);
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
		healPercentage += strongestHealLevelBonus;
		durationTicks += strongestDurationLevelBonus
			+ (long) (abilityCount - 1) * HEALTH_REGEN_DUPLICATE_DURATION_TICKS;
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

	/**
	 * Rebuilds all passive player modifiers from one inventory traversal. Passive
	 * modifiers are refreshed together, so armor and armor toughness do not scan
	 * the same pet slots independently.
	 */
	public static void applyPlayerPassiveAbilityBonuses(ServerPlayer player) {
		if (player == null) return;
		PassiveBonuses bonuses = resolvePassiveBonuses(player);

		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.removeModifier(PLAYER_HEALTH_MODIFIER);
			if (bonuses.maxHealth() > 0.0D) {
				maxHealth.addOrUpdateTransientModifier(new AttributeModifier(
					PLAYER_HEALTH_MODIFIER, bonuses.maxHealth(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
				));
			}
			if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
		}

		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(PLAYER_ARMOR_MODIFIER);
			if (bonuses.armor() > 0.0D) {
				armor.addOrUpdateTransientModifier(new AttributeModifier(
					PLAYER_ARMOR_MODIFIER, bonuses.armor(), AttributeModifier.Operation.ADD_VALUE
				));
			}
		}

		AttributeInstance armorToughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (armorToughness != null) {
			armorToughness.removeModifier(PLAYER_ARMOR_TOUGHNESS_MODIFIER);
			if (bonuses.armor() > 0.0D) {
				armorToughness.addOrUpdateTransientModifier(new AttributeModifier(
					PLAYER_ARMOR_TOUGHNESS_MODIFIER, bonuses.armor(), AttributeModifier.Operation.ADD_VALUE
				));
			}
		}

		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.removeModifier(PLAYER_DAMAGE_MODIFIER);
			if (bonuses.damage() > 0.0D) {
				damage.addOrUpdateTransientModifier(new AttributeModifier(
					PLAYER_DAMAGE_MODIFIER, bonuses.damage(), AttributeModifier.Operation.ADD_VALUE
				));
			}
		}
	}

	private static PassiveBonuses resolvePassiveBonuses(ServerPlayer player) {
		if (player == null || !PetConfigManager.isEnabled()) return PassiveBonuses.EMPTY;
		PetInventory inventory = petInventory(player);
		if (inventory == null) return PassiveBonuses.EMPTY;
		double damage = 0.0D;
		double maxHealth = 0.0D;
		double armor = 0.0D;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
			if (rule == null) continue;
			if (rule.hasAbility(PET_ABILITY_PLAYER_DAMAGE_BONUS)) damage += rule.playerDamageBonus();
			if (rule.hasAbility(PET_ABILITY_MAX_HEALTH_BONUS)) maxHealth += rule.maxHealthBonus();
			if (rule.hasAbility(PET_ABILITY_ARMOR_BONUS)) armor += rule.armorBonus();
		}
		return new PassiveBonuses(Math.max(0.0D, damage), Math.max(0.0D, maxHealth), Math.max(0.0D, armor));
	}

	private record PassiveBonuses(double damage, double maxHealth, double armor) {
		private static final PassiveBonuses EMPTY = new PassiveBonuses(0.0D, 0.0D, 0.0D);
	}

	private static double sumAbility(ServerPlayer player, String abilityType) {
		if (player == null || !PetConfigManager.isEnabled()) return 0.0D;
		PetInventory inventory = petInventory(player);
		if (inventory == null) return 0.0D;
		double total = 0.0D;
		int fallAbilityCount = 0;
		double strongestFallLevelBonus = 0.0D;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null || !rule.hasAbility(abilityType)) {
				continue;
			}
			if (PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType)) {
				PetRule baseRule = PetConfigManager.resolvePetRule(PetEntitiesManager.petId(stack));
				if (baseRule != null && baseRule.hasAbility(abilityType)) {
					fallAbilityCount++;
					strongestFallLevelBonus = Math.max(
						strongestFallLevelBonus,
						Math.max(0.0D, rule.fallDamageReduction() - baseRule.fallDamageReduction())
					);
				}
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
		if (PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) && fallAbilityCount > 0) {
			return FALL_DAMAGE_REDUCTION_BASE
				+ (Math.max(0, fallAbilityCount - 1) * FALL_DAMAGE_REDUCTION_PER_DUPLICATE)
				+ strongestFallLevelBonus;
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
		long now = TimeAPIManager.getGameplayTicks();
		synchronizeAllAbilityCooldowns(player, inventory, now);
		int selectedSlot = -1;
		PetAbilityRule selectedAbility = null;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
			PetAbilityRule ability = rule == null ? null : rule.ability(PET_ABILITY_DAMAGE_BLOCK);
			if (ability == null || ability.damageBlockAmount <= 0.0D || ability.cooldownTicks <= 0L
				|| !isAbilityOffCooldown(player, slot, ability.abilityType, now)) {
				continue;
			}
			if (selectedAbility == null || ability.damageBlockAmount > selectedAbility.damageBlockAmount) {
				selectedSlot = slot;
				selectedAbility = ability;
			}
		}
		if (selectedAbility != null) {
			setAbilityCooldown(player.getUUID(), selectedSlot, selectedAbility.abilityType, now + selectedAbility.cooldownTicks);
			float blocked = (float) Math.max(0.0D, amount - selectedAbility.damageBlockAmount);
			if (blocked < amount) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.0F);
			return blocked;
		}
		return amount;
	}

	static void tickManagedProjectileVolleys(MinecraftServer server) {
		if (server == null || ACTIVE_PROJECTILE_VOLLEYS.isEmpty()) {
			return;
		}
		long now = TimeAPIManager.getGameplayTicks();
		for (Map.Entry<UUID, ProjectileVolleyState> entry : ACTIVE_PROJECTILE_VOLLEYS.entrySet()) {
			UUID volleyId = entry.getKey();
			ProjectileVolleyState state = entry.getValue();
			if (state == null || state.nextProjectileIndex >= state.projectileCount || now < state.nextLaunchTick) {
				if (state == null || state.nextProjectileIndex >= state.projectileCount) {
					ACTIVE_PROJECTILE_VOLLEYS.remove(volleyId);
				}
				continue;
			}

			ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerUuid);
			LivingEntity target = findLivingEntity(server, state.targetUuid);
			if (owner == null || !owner.isAlive() || target == null || !target.isAlive() || target.level() != owner.level()) {
				ACTIVE_PROJECTILE_VOLLEYS.remove(volleyId);
				continue;
			}

			Vec3 projectileSpawnPosition = state.projectileCount == 1
				? state.spawnPosition
				: resolveRangedAttackSpawn(owner, state.nextProjectileIndex, state.projectileCount, state.ability);
			boolean spawned = spawnQueuedProjectile(owner, target, projectileSpawnPosition, state);
			if (!spawned) {
				ACTIVE_PROJECTILE_VOLLEYS.remove(volleyId);
				continue;
			}

			int nextProjectileIndex = state.nextProjectileIndex + 1;
			if (nextProjectileIndex >= state.projectileCount) {
				ACTIVE_PROJECTILE_VOLLEYS.remove(volleyId);
			} else {
				ACTIVE_PROJECTILE_VOLLEYS.put(
					volleyId,
					new ProjectileVolleyState(
						state.abilityType,
						state.ownerUuid,
						state.dimensionId,
						state.targetUuid,
						state.spawnPosition,
						state.rule,
						state.ability,
						state.abilityGroup,
						state.projectileCount,
						nextProjectileIndex,
						now + state.ability.projectileIntervalTicks,
						state.soundEvent,
						state.soundVolume,
						state.soundPitch
					)
				);
			}
		}
	}

	private static boolean startProjectileVolley(
		ServerPlayer owner,
		LivingEntity target,
		Vec3 spawnPosition,
		PetRule rule,
		PetAbilityRule ability,
		List<ReadyReactiveAttack> abilityGroup,
		int projectileCount,
		SoundEvent soundEvent,
		float soundVolume,
		float soundPitch
	) {
		if (owner == null || target == null || spawnPosition == null || rule == null || ability == null || projectileCount <= 0) {
			return false;
		}
		List<ReadyReactiveAttack> resolvedAbilityGroup = abilityGroup == null || abilityGroup.isEmpty()
			? List.of(new ReadyReactiveAttack(-1, rule, ability))
			: List.copyOf(abilityGroup);
		long intervalTicks = Math.max(0L, ability.projectileIntervalTicks);
		ProjectileVolleyState initialState = new ProjectileVolleyState(
			ability.abilityType,
			owner.getUUID(),
			owner.level().dimension().toString(),
			target.getUUID(),
			spawnPosition,
			rule,
			ability,
			resolvedAbilityGroup,
			projectileCount,
			0,
			TimeAPIManager.getGameplayTicks(),
			soundEvent,
			soundVolume,
			soundPitch
		);
		for (int index = 0; index < projectileCount; index++) {
			if (index > 0 && intervalTicks > 0L) {
				ACTIVE_PROJECTILE_VOLLEYS.put(
					UUID.randomUUID(),
					new ProjectileVolleyState(
						initialState.abilityType,
						initialState.ownerUuid,
						initialState.dimensionId,
						initialState.targetUuid,
						initialState.spawnPosition,
						initialState.rule,
						initialState.ability,
						initialState.abilityGroup,
						initialState.projectileCount,
						index,
						initialState.nextLaunchTick + (index * intervalTicks),
						initialState.soundEvent,
						initialState.soundVolume,
						initialState.soundPitch
					)
				);
				break;
			}
			Vec3 projectileSpawnPosition = projectileCount == 1
				? spawnPosition
				: resolveRangedAttackSpawn(owner, index, projectileCount, ability);
			if (!spawnQueuedProjectile(owner, target, projectileSpawnPosition, initialState.withNextProjectileIndex(index))) {
				return index > 0;
			}
		}
		return true;
	}

	private static boolean spawnQueuedProjectile(
		ServerPlayer owner,
		LivingEntity target,
		Vec3 projectileSpawnPosition,
		ProjectileVolleyState state
	) {
		if (PET_ABILITY_RANGED_HOMING_ARROW.equals(state.abilityType)) {
			return HelperProjectileAPIManager.spawnManagedHomingArrow(
				owner,
				target,
				projectileSpawnPosition,
				state.ability.attackSpeed,
				state.ability.attackDamage
			);
		}
		if (PET_ABILITY_WEB_PROJECTILE.equals(state.abilityType)) {
			return spawnManagedWebProjectile(
				owner,
				target,
				projectileSpawnPosition,
				state.rule,
				state.ability,
				state.abilityGroup,
				petInventory(owner)
			);
		}
		return spawnManagedExplosiveProjectile(
			owner,
			target,
			projectileSpawnPosition,
			state.ability,
			state.soundEvent,
			state.soundVolume,
			state.soundPitch
		);
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

		long gameplayTicks = TimeAPIManager.getGameplayTicks();
		synchronizeAllAbilityCooldowns(player, inventory, gameplayTicks);
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

		for (List<ReadyReactiveAttack> abilityGroup : groupReadyAbilities(readyAttacks).values()) {
			if (PET_ABILITY_WEB_PROJECTILE.equals(abilityGroup.get(0).ability.abilityType)) {
				ReadyReactiveAttack strongest = strongestReadyAttack(abilityGroup);
				Vec3 spawnPosition = resolveRangedAttackSpawn(player, 0, 1, strongest.ability);
				if (spawnPetReactiveAttack(player, target, spawnPosition, abilityGroup)) {
					setAbilityGroupCooldown(player, abilityGroup, gameplayTicks);
				}
				continue;
			}
			triggerNonSharedAbilityGroup(player, target, abilityGroup, gameplayTicks);
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

	private static Map<String, List<ReadyReactiveAttack>> groupReadyAbilities(List<ReadyReactiveAttack> readyAttacks) {
		Map<String, List<ReadyReactiveAttack>> grouped = new LinkedHashMap<>();
		if (readyAttacks == null) {
			return grouped;
		}
		for (ReadyReactiveAttack attack : readyAttacks) {
			if (attack == null || attack.ability == null) {
				continue;
			}
			grouped.computeIfAbsent(attack.ability.abilityType, ignored -> new ArrayList<>()).add(attack);
		}
		return grouped;
	}

	private static ReadyReactiveAttack strongestReadyAttack(List<ReadyReactiveAttack> abilityGroup) {
		ReadyReactiveAttack strongest = null;
		if (abilityGroup == null) {
			return null;
		}
		for (ReadyReactiveAttack attack : abilityGroup) {
			if (attack == null || attack.ability == null) {
				continue;
			}
			if (strongest == null || attack.ability.attackDamage > strongest.ability.attackDamage) {
				strongest = attack;
			}
		}
		return strongest;
	}

	private static void setAbilityGroupCooldown(ServerPlayer player, List<ReadyReactiveAttack> abilityGroup, long gameplayTicks) {
		if (player == null || abilityGroup == null || abilityGroup.isEmpty()) {
			return;
		}
		for (ReadyReactiveAttack attack : abilityGroup) {
			if (attack == null || attack.slot < 0 || attack.ability == null) {
				continue;
			}
			long cooldown = Math.max(0L, attack.ability.cooldownTicks);
			setAbilityCooldown(player.getUUID(), attack.slot, attack.ability.abilityType, gameplayTicks + cooldown);
		}
	}

	/**
	 * Fires duplicate non-shared abilities in sequence. The first attack is immediate;
	 * later attacks reserve their slot and are launched using the configured shot delay.
	 * This prevents duplicate arrow/explosive pets from activating on the same tick while
	 * preserving independent cooldowns for each pet.
	 */
	private static void triggerNonSharedAbilityGroup(
		ServerPlayer player,
		LivingEntity target,
		List<ReadyReactiveAttack> abilityGroup,
		long gameplayTicks
	) {
		if (player == null || target == null || abilityGroup == null || abilityGroup.isEmpty()) {
			return;
		}

		long delayTicks = 0L;
		for (int index = 0; index < abilityGroup.size(); index++) {
			ReadyReactiveAttack attack = abilityGroup.get(index);
			if (attack == null || attack.ability == null) {
				continue;
			}

			Vec3 spawnPosition = resolveRangedAttackSpawn(player, index, abilityGroup.size(), attack.ability);
			List<ReadyReactiveAttack> singleAttack = List.of(attack);
			boolean launched;
			if (delayTicks <= 0L) {
				launched = spawnPetReactiveAttack(player, target, spawnPosition, singleAttack);
				if (launched) {
					setAbilityCooldown(player.getUUID(), attack.slot, attack.ability.abilityType, gameplayTicks + attack.ability.cooldownTicks);
				}
			} else {
				launched = enqueueDelayedPetAttack(player, attack.slot, attack.ability.abilityType, target, spawnPosition, delayTicks);
				if (launched) {
					// Reserve the ability until its delayed task executes. The task replaces
					// this reservation with the normal full cooldown after spawning.
					setAbilityCooldown(player.getUUID(), attack.slot, attack.ability.abilityType, gameplayTicks + delayTicks);
				}
			}

			if (index + 1 < abilityGroup.size()) {
				delayTicks = safeAddTicks(delayTicks, duplicateDelayTicks(attack.ability));
			}
		}
	}

	private static long duplicateDelayTicks(PetAbilityRule ability) {
		if (ability == null) {
			return 0L;
		}
		// shot-delay-ticks is the explicit duplicate-pet delay. Fall back to the
		// projectile interval so older/custom configs still get staggered shots.
		return Math.max(0L, ability.shotDelayTicks > 0L ? ability.shotDelayTicks : ability.projectileIntervalTicks);
	}

	private static long safeAddTicks(long first, long second) {
		if (second <= 0L || first >= Long.MAX_VALUE - second) {
			return second <= 0L ? Math.max(0L, first) : Long.MAX_VALUE;
		}
		return first + second;
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

	static void triggerReactivePetAttacks(ServerPlayer player, LivingEntity target) {
		if (!canReactiveAttackTarget(player, target)) {
			return;
		}

		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}

		long gameplayTicks = TimeAPIManager.getGameplayTicks();
		synchronizeAllAbilityCooldowns(player, inventory, gameplayTicks);
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

		for (List<ReadyReactiveAttack> abilityGroup : groupReadyAbilities(readyAttacks).values()) {
			if (!isLeftClickProjectileAbility(abilityGroup.get(0).ability)) {
				continue;
			}
			if (PET_ABILITY_WEB_PROJECTILE.equals(abilityGroup.get(0).ability.abilityType)) {
				ReadyReactiveAttack strongest = strongestReadyAttack(abilityGroup);
				Vec3 spawnPosition = resolveRangedAttackSpawn(player, 0, 1, strongest.ability);
				if (spawnPetReactiveAttack(player, target, spawnPosition, abilityGroup)) {
					setAbilityGroupCooldown(player, abilityGroup, gameplayTicks);
				}
				continue;
			}
			triggerNonSharedAbilityGroup(player, target, abilityGroup, gameplayTicks);
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
			synchronizeAllAbilityCooldowns(player, inventory, gameplayTicks);

			int[] abilitySlots = new int[SLOT_COUNT];
			PetRule[] abilityRules = new PetRule[SLOT_COUNT];
			int abilityCount = collectSlotsWithAbility(inventory, PET_ABILITY_MOB_SCAN, abilitySlots, abilityRules);
			if (abilityCount <= 0) {
				return;
			}

			PetRule sharedRule = abilityRules[0];
			PetAbilityRule batAbility = sharedRule == null ? null : sharedRule.ability(PET_ABILITY_MOB_SCAN);
			if (batAbility == null || batAbility.cooldownTicks <= 0L) {
				return;
			}

			long sharedCooldownTick = synchronizeSharedAbilityCooldown(player.getUUID(), PET_ABILITY_MOB_SCAN, abilitySlots, abilityCount, gameplayTicks);
			if (gameplayTicks < sharedCooldownTick) {
				return;
			}

			applyAutomaticBatMobScan(player, inventory, abilitySlots, abilityRules, abilityCount, sharedRule, batAbility);
			setSharedAbilityCooldown(player.getUUID(), PET_ABILITY_MOB_SCAN, abilitySlots, abilityCount, gameplayTicks + effectiveBatScanCooldownTicks(inventory, abilitySlots, abilityCount, sharedRule));
		}

		private static void applyAutomaticBatMobScan(
			ServerPlayer player,
			PetInventory inventory,
			int[] abilitySlots,
			PetRule[] abilityRules,
			int abilityCount,
			PetRule rule,
			PetAbilityRule ability
		) {
			if (player == null || abilityCount <= 0 || !(player.level() instanceof ServerLevel level)) {
				return;
			}
			SoundEvent soundEvent = rule == null || ability == null ? SoundEvents.BEACON_ACTIVATE : rule.resolveSoundEvent(ability.abilityType);
			float volume = ability == null ? 0.45F : Math.max(0.12F, ability.soundVolumeMultiplier);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), soundEvent, SoundSource.NEUTRAL, volume, 1.15F);
			float vulnerability = resolveBatScanVulnerability(inventory, abilitySlots, abilityRules, abilityCount);
			long glowingDurationTicks = resolveBatScanGlowingDurationTicks(inventory, abilitySlots, abilityCount);

			double horizontalRadius = BAT_SCAN_BASE_RADIUS_BLOCKS + Math.max(0, abilityCount - 1) * 12.0D;
			int chunkRadius = Math.max(1, (int) Math.ceil(horizontalRadius / 16.0D));
			int verticalRadius = 12 + Math.max(0, abilityCount - 1) * MOB_SCAN_VERTICAL_RADIUS_PER_DUPLICATE_ABILITY;
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
					if (!ChunkAPIManager.isChunkAccessible(level, chunkX, chunkZ)) {
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

		private static long resolveBatScanGlowingDurationTicks(PetInventory inventory, int[] abilitySlots, int abilityCount) {
			long durationTicks = BAT_SCAN_BASE_GLOWING_DURATION_TICKS
				+ Math.max(0, abilityCount - 1) * MOB_SCAN_GLOWING_DURATION_PER_DUPLICATE_ABILITY_TICKS;
			int level = resolveBatScanAbilityLevel(inventory, abilitySlots, abilityCount);
			durationTicks += Math.max(0, level - 1) * BAT_SCAN_GLOWING_DURATION_PER_LEVEL_TICKS;
			return durationTicks;
		}

		private static int resolveBatScanAbilityLevel(PetInventory inventory, int[] abilitySlots, int abilityCount) {
			int level = 1;
			if (inventory == null || abilitySlots == null) {
				return level;
			}
			for (int index = 0; index < Math.min(abilityCount, abilitySlots.length); index++) {
				int slot = abilitySlots[index];
				if (slot >= 0 && slot < inventory.getContainerSize()) {
					level = Math.max(level, PetEntitiesManager.petLevel(inventory.getItem(slot)));
				}
			}
			return level;
		}

		private static float resolveBatScanVulnerability(PetInventory inventory, int[] abilitySlots, PetRule[] abilityRules, int abilityCount) {
			double vulnerability = 0.0D;
			double strongestLevelBonus = 0.0D;
			if (inventory != null && abilitySlots != null) {
				for (int index = 0; index < Math.min(abilityCount, abilitySlots.length); index++) {
					int slot = abilitySlots[index];
					PetAbilityRule batAbility = abilityRules != null && index < abilityRules.length && abilityRules[index] != null
						? abilityRules[index].ability(PET_ABILITY_MOB_SCAN)
						: null;
					PetRule baseRule = slot < 0 || slot >= inventory.getContainerSize()
						? null
						: PetConfigManager.resolvePetRule(PetEntitiesManager.petId(inventory.getItem(slot)));
					PetAbilityRule baseAbility = baseRule == null ? null : baseRule.ability(PET_ABILITY_MOB_SCAN);
					double baseVulnerability = baseAbility == null
						? BAT_SCAN_BASE_VULNERABILITY
						: baseAbility.mobScanVulnerabilityAmount;
					vulnerability += index == 0 ? baseVulnerability : MOB_SCAN_VULNERABILITY_PER_DUPLICATE_ABILITY;
					if (batAbility != null) {
						strongestLevelBonus = Math.max(
							strongestLevelBonus,
							Math.max(0.0D, batAbility.mobScanVulnerabilityAmount - baseVulnerability)
						);
					}
				}
			}
			return (float) Math.max(0.0D, vulnerability + strongestLevelBonus);
		}

		static long effectiveBatScanCooldownTicks(PetInventory inventory, int[] abilitySlots, int abilityCount, PetRule rule) {
		PetAbilityRule ability = rule == null ? null : rule.ability(PET_ABILITY_MOB_SCAN);
		long baseCooldown = Math.max(0L, ability == null ? 0L : ability.cooldownTicks);
		long cooldownReduction = Math.max(0, abilityCount - 1) * MOB_SCAN_COOLDOWN_REDUCTION_PER_DUPLICATE_ABILITY;
		int level = resolveBatScanAbilityLevel(inventory, abilitySlots, abilityCount);
		cooldownReduction += Math.max(0, level - 1) * BAT_SCAN_COOLDOWN_REDUCTION_PER_LEVEL;
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
			synchronizeAllAbilityCooldowns(player, inventory, gameplayTicks);

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
			long interval = SchedulerAPIManager.resolveAdaptiveDelayTicks(
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

		private static void synchronizeAllAbilityCooldowns(ServerPlayer player, PetInventory inventory, long gameplayTicks) {
			if (player == null || inventory == null) {
				return;
			}
			Map<String, List<Integer>> slotsByAbility = new LinkedHashMap<>();
			for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
				PetRule rule = PetConfigManager.resolvePetRule(inventory.getItem(slot));
				if (rule == null || !rule.enabled) {
					continue;
				}
				for (PetAbilityRule ability : rule.abilities) {
					if (ability == null || ability.cooldownTicks <= 0L || !isSharedAbilityType(ability.abilityType)) {
						continue;
					}
					slotsByAbility.computeIfAbsent(ability.abilityType, ignored -> new ArrayList<>()).add(slot);
				}
			}
			for (Map.Entry<String, List<Integer>> entry : slotsByAbility.entrySet()) {
				int[] slots = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
				synchronizeSharedAbilityCooldown(player.getUUID(), entry.getKey(), slots, slots.length, gameplayTicks);
			}
		}

		private static boolean isSharedAbilityType(String abilityType) {
			return PET_ABILITY_WEB_PROJECTILE.equals(abilityType)
				|| PET_ABILITY_MOB_SCAN.equals(abilityType)
				|| PET_ABILITY_HEALTH_REGENERATION.equals(abilityType);
		}

		private static void setSharedAbilityCooldownForInventory(ServerPlayer player, PetInventory inventory, String abilityType, long cooldownTick) {
			if (player == null || inventory == null || abilityType == null || abilityType.isBlank()) {
				return;
			}
			int[] slots = new int[SLOT_COUNT];
			int slotCount = collectSlotsWithAbility(inventory, abilityType, slots, null);
			if (slotCount > 0) {
				setSharedAbilityCooldown(player.getUUID(), abilityType, slots, slotCount, cooldownTick);
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

			static void runPetAttack(MinecraftServer server, SchedulerAPIManager.TaskContext context, JsonObject payload) {
			if (server == null || context == null || payload == null) {
				return;
			}

			SchedulerAPIManager.SchedulerBinding binding = context.getBinding();
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
			long gameplayTicks = TimeAPIManager.getGameplayTicks();

			PetInventory inventory = petInventory(player);
			if (inventory == null || slot >= inventory.getContainerSize()) {
				return;
			}
			synchronizeAllAbilityCooldowns(player, inventory, gameplayTicks);

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
					setSharedAbilityCooldownForInventory(player, inventory, PET_ABILITY_WEB_PROJECTILE, gameplayTicks + ability.cooldownTicks);
				} else {
					setAbilityCooldown(playerId, slot, ability.abilityType, gameplayTicks + ability.cooldownTicks);
				}
			}
		}

		private static boolean spawnPetReactiveAttack(ServerPlayer player, LivingEntity target, Vec3 spawnPosition, PetRule rule, PetAbilityRule ability) {
			return spawnPetReactiveAttack(player, target, spawnPosition, List.of(new ReadyReactiveAttack(-1, rule, ability)));
		}

		private static boolean spawnPetReactiveAttack(ServerPlayer player, LivingEntity target, Vec3 spawnPosition, List<ReadyReactiveAttack> abilityGroup) {
			ReadyReactiveAttack strongest = strongestReadyAttack(abilityGroup);
			PetRule rule = strongest == null ? null : strongest.rule;
			PetAbilityRule ability = strongest == null ? null : strongest.ability;
			if (player == null || target == null || spawnPosition == null || rule == null || ability == null || !player.isAlive() || !target.isAlive()) {
				return false;
			}

			SoundEvent soundEvent = rule.resolveSoundEvent(ability.abilityType);
			float soundVolume = Math.max(0.4F, ability.soundVolumeMultiplier);
			float soundPitch = 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F);
			boolean spawned;
			String projectileAbility = ability.abilityType;
			if (PET_ABILITY_RANGED_HOMING_ARROW.equals(projectileAbility)) {
				spawned = spawnPetArrowVolley(player, target, spawnPosition, abilityGroup);
			} else if (PET_ABILITY_WEB_PROJECTILE.equals(projectileAbility)) {
				spawned = spawnManagedWebProjectileVolley(
					player,
					target,
					spawnPosition,
					rule,
					ability,
					abilityGroup,
					petInventory(player)
				);
			} else if (PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(projectileAbility)) {
				spawned = spawnManagedExplosiveProjectileVolley(player, target, spawnPosition, ability, abilityGroup, soundEvent, soundVolume, soundPitch);
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

		private static boolean spawnPetArrowVolley(ServerPlayer player, LivingEntity target, Vec3 spawnPosition, List<ReadyReactiveAttack> abilityGroup) {
			ReadyReactiveAttack strongest = strongestReadyAttack(abilityGroup);
			PetAbilityRule ability = strongest == null ? null : strongest.ability;
			if (player == null || target == null || spawnPosition == null || ability == null) {
				return false;
			}
			int projectileCount = resolveProjectileCount(player, ability.projectileCount);
			return startProjectileVolley(
				player,
				target,
				spawnPosition,
				strongest.rule,
				ability,
				abilityGroup,
				projectileCount,
				null,
				0.0F,
				0.0F
			);
		}

		private static int resolveProjectileCount(ServerPlayer player, double configuredCount) {
			double count = Math.max(1.0D, configuredCount);
			int wholeCount = (int) Math.floor(count);
			double fractionalChance = count - wholeCount;
			if (fractionalChance > 0.0D && player.getRandom().nextDouble() < fractionalChance) {
				wholeCount++;
			}
			return Math.max(1, wholeCount);
		}

		private static boolean spawnManagedWebProjectileVolley(
			ServerPlayer player,
			LivingEntity target,
			Vec3 spawnPosition,
			PetRule rule,
			PetAbilityRule ability,
			List<ReadyReactiveAttack> webAttacks,
			PetInventory inventory
		) {
			if (player == null || target == null || spawnPosition == null || rule == null || ability == null) {
				return false;
			}
			List<ReadyReactiveAttack> attacks = webAttacks == null || webAttacks.isEmpty()
				? List.of(new ReadyReactiveAttack(-1, rule, ability))
				: webAttacks;
			int projectileCount = 1;
		return startProjectileVolley(
				player,
				target,
				spawnPosition,
				rule,
				ability,
				attacks,
				projectileCount,
				rule.resolveSoundEvent(ability.abilityType),
				Math.max(0.4F, ability.soundVolumeMultiplier),
				1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F)
			);
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
			for (ReadyReactiveAttack candidate : attacks) {
				if (candidate.ability().attackDamage > strongestAttack.ability().attackDamage) {
					strongestAttack = candidate;
				}
			}
			ability = strongestAttack.ability();
			int strongestLevel = strongestAttack.slot() < 0 || inventory == null || strongestAttack.slot() >= inventory.getContainerSize()
				? 1
				: PetEntitiesManager.petLevel(inventory.getItem(strongestAttack.slot()));
			int duplicateCount = Math.max(0, attacks.size() - 1);
			float damage = Math.max(0.0F, ability.attackDamage + (duplicateCount * WEB_PROJECTILE_DUPLICATE_DAMAGE));
			int stunDurationTicks = (int) Math.max(0L, ability.stunDurationTicks + (duplicateCount * WEB_PROJECTILE_DUPLICATE_STUN_TICKS));
			double ricochetRadius = WEB_PROJECTILE_RICOCHET_RADIUS
				+ (Math.max(0, strongestLevel - 1) * WEB_PROJECTILE_RICOCHET_RADIUS_PER_LEVEL)
				+ (duplicateCount * WEB_PROJECTILE_RICOCHET_RADIUS_PER_DUPLICATE);
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
					(int) Math.max(0L, ability.slowDurationTicks + (duplicateCount * WEB_PROJECTILE_DUPLICATE_SLOW_DURATION_TICKS)),
					(float) Math.max(0.0D, Math.min(1.0D, ability.slowPercentage)),
					false,
					ricochetCount,
					ricochetRadius,
					Set.of(),
					WEB_PROJECTILE_LIFETIME_TICKS
				)
			);
			return true;
		}

		private static boolean spawnManagedExplosiveProjectileVolley(
			ServerPlayer player,
			LivingEntity target,
			Vec3 spawnPosition,
			PetAbilityRule ability,
			List<ReadyReactiveAttack> abilityGroup,
			SoundEvent soundEvent,
			float soundVolume,
			float soundPitch
		) {
			if (player == null || target == null || spawnPosition == null || ability == null) {
				return false;
			}
			int projectileCount = resolveProjectileCount(player, ability.projectileCount);
			return startProjectileVolley(
				player,
				target,
				spawnPosition,
				strongestReadyAttack(abilityGroup).rule,
				ability,
				abilityGroup,
				projectileCount,
				soundEvent,
				soundVolume,
				soundPitch
			);
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
					state.ricochetRadius,
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
			long now = TimeAPIManager.getGameplayTicks();
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
						now + state.projectileIntervalTicks,
						state.damage,
						state.radius,
						state.projectileIntervalTicks
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
					TimeAPIManager.getGameplayTicks() + EGG_PROJECTILE_LIFETIME_TICKS
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

			long gameplayTicks = TimeAPIManager.getGameplayTicks();
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
			LivingEntity ricochetTarget = findNearestWebRicochetTarget(level, owner, target, hitEntityUuids, projectileState.ricochetRadius);
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
					projectileState.ricochetRadius,
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
			Set<UUID> hitEntityUuids,
			double radius
		) {
			if (level == null || owner == null || source == null || hitEntityUuids == null || radius <= 0.0D) {
				return null;
			}
			AABB searchArea = source.getBoundingBox().inflate(radius);
			LivingEntity nearest = null;
			double nearestDistance = Double.MAX_VALUE;
			for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchArea, entity ->
				entity != null
					&& entity.isAlive()
					&& entity instanceof Mob
					&& !entity.getUUID().equals(owner.getUUID())
					&& !hitEntityUuids.contains(entity.getUUID())
					&& !isManagedPet(entity)
					&& source.distanceToSqr(entity) <= radius * radius
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

			long now = TimeAPIManager.getGameplayTicks();
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

		private static boolean enqueueDelayedPetAttack(
			ServerPlayer player,
			int slot,
			String abilityType,
			LivingEntity target,
			Vec3 spawnPosition,
			long delayTicks
		) {
			MinecraftServer server = player == null ? null : player.level().getServer();
			if (server == null || player == null || target == null || spawnPosition == null) {
				return false;
			}

			UUID playerId = player.getUUID();
			String schedulerId = ensureSchedulerExists(playerId);
			if (enqueuePetAttackTask(schedulerId, slot, abilityType, target, spawnPosition, delayTicks)) {
				return true;
			}

			String created = SchedulerAPIManager.createOrGetScheduler(
				SchedulerAPIManager.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId)
			);
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
				schedulerId = SchedulerAPIManager.createOrGetScheduler(
					SchedulerAPIManager.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId)
				);
				PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
			}
			return schedulerId;
		}

		private static boolean enqueuePetAttackTask(
			String schedulerId,
			int slot,
			String abilityType,
			LivingEntity target,
			Vec3 spawnPosition,
			long delayTicks
		) {
			if (schedulerId == null || schedulerId.isBlank() || target == null || spawnPosition == null) {
				return false;
			}

			JsonObject payload = madoku.craft.java.core.json.JSONFormatAPIManager.object()
				.put(FIELD_SLOT, slot)
				.put(FIELD_ABILITY_ID, abilityType)
				.put(FIELD_TARGET_UUID, target.getUUID().toString())
				.put(FIELD_SPAWN_X, spawnPosition.x)
				.put(FIELD_SPAWN_Y, spawnPosition.y)
				.put(FIELD_SPAWN_Z, spawnPosition.z)
				.build();
			SchedulerAPIManager.EnqueueStatus status = SchedulerAPIManager.enqueue(
				schedulerId,
				Math.max(0L, delayTicks),
				TASK_TYPE_PET_ATTACK,
				payload,
				SchedulerAPIManager.TickDomain.GAMEPLAY
			);
			return status == SchedulerAPIManager.EnqueueStatus.ACCEPTED;
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
				PetAbilitiesManager.applyPlayerPassiveAbilityBonuses(player);
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
			long gameplayTicks = TimeAPIManager.getGameplayTicks();
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
			long now = TimeAPIManager.getGameplayTicks();
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
			madoku.craft.java.core.json.JSONFormatAPIManager.ArrayBuilder players = madoku.craft.java.core.json.JSONFormatAPIManager.array();
			long now = TimeAPIManager.getGameplayTicks();
			for (Map.Entry<UUID, Map<Integer, Map<String, Long>>> playerEntry : PLAYER_ABILITY_COOLDOWNS.entrySet()) {
				if (playerEntry.getKey() == null) continue;
				madoku.craft.java.core.json.JSONFormatAPIManager.ArrayBuilder playerCooldowns = madoku.craft.java.core.json.JSONFormatAPIManager.array();
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
			return madoku.craft.java.core.json.JSONFormatAPIManager.object()
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
				long now = TimeAPIManager.getGameplayTicks();
				remainingTicks = legacyCooldownTick > now ? legacyCooldownTick - now : 0L;
			}
			if (slot < 0 || slot >= SLOT_COUNT || abilityType.isBlank() || remainingTicks <= 0L) return;
			long now = TimeAPIManager.getGameplayTicks();
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

	private record ProjectileVolleyState(
		String abilityType,
		UUID ownerUuid,
		String dimensionId,
		UUID targetUuid,
		Vec3 spawnPosition,
		PetRule rule,
		PetAbilityRule ability,
		List<ReadyReactiveAttack> abilityGroup,
		int projectileCount,
		int nextProjectileIndex,
		long nextLaunchTick,
		SoundEvent soundEvent,
		float soundVolume,
		float soundPitch
	) {
		private ProjectileVolleyState withNextProjectileIndex(int nextIndex) {
			return new ProjectileVolleyState(
				abilityType,
				ownerUuid,
				dimensionId,
				targetUuid,
				spawnPosition,
				rule,
				ability,
				abilityGroup,
				projectileCount,
				nextIndex,
				nextLaunchTick,
				soundEvent,
				soundVolume,
				soundPitch
			);
		}
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
		double ricochetRadius,
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
		float radius,
		long projectileIntervalTicks
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
