package madoku.craft.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.mixin.CreeperAccessor;
import madoku.craft.mixin.CreeperPoweredAccessor;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.mixin.MobExperienceAccessor;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.concurrent.ConcurrentHashMap;

public final class MobEntityManager {
	public interface DifficultyState {
		int madokuCraft$getSpawnDifficultyAdjustment();

		void madokuCraft$setSpawnDifficultyAdjustment(int adjustment);
	}
	private static final String TASK_TYPE_MOB_RUNTIME_TICK = "mob_runtime_tick";
	private static final String MOB_SCHEDULER_OWNER_ID = "madoku-mob-runtime";
	private static final double HEALTH_DIFFICULTY_STEP = 4.0D;
	private static final double MOVEMENT_SPEED_DIFFICULTY_STEP = 0.01D;
	private static final double DAMAGE_DIFFICULTY_STEP = 1.0D;
	private static final double ARMOR_DIFFICULTY_STEP = 0.5D;
	private static final double KNOCKBACK_RESISTANCE_DIFFICULTY_STEP = 0.1D;
	private static final double SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP = 2.0D;
	private static final double RANGED_DAMAGE_DIFFICULTY_STEP = 1.0D;
	private static final double CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP = 1.0D;
	private static final double CREEPER_POWER_PER_DAMAGE = 0.2D;
	private static final double MIN_HOMING_SPEED = 0.75D;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final int MOB_ARROW_LIFETIME_TICKS = 15 * 20;
	private static final String HOMING_PROJECTILE_TAG = "madoku-craft.projectile.homing";
	private static final String BEE_VARIANT_TAG_PREFIX = "madoku-craft.bee.variant:";
	private static final String BEE_VARIANT_DEFAULT_KEY = "default";
	private static final String FIELD_FLYING_SPEED = MobConfigManager.FIELD_FLYING_SPEED;

	private static final Map<UUID, HomingArrowState> HOMING_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FIXED_ARROW_DAMAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MANAGED_MOB_ARROWS = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> INVULNERABILITY_BYPASS_ARROWS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, EntitySpawnReason> PENDING_SPIDER_JOCKEY_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, EntitySpawnReason> PENDING_CAVE_SPIDER_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingZombieReplacement> PENDING_ZOMBIE_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Entity> TRACKED_ENTITIES = new ConcurrentHashMap<>();

	private static volatile String runtimeSchedulerId = "";
	private static volatile boolean runtimeTaskScheduled = false;

	private MobEntityManager() {
	}

	public static void initialize() {
		EntityBehaviorsManager.initialize();
		EntityComponentsManager.initialize();
		EntitySpawnRulesManager.initialize();
		EntityGoalsManager.initialize();
		EntityConfigManager.initialize();
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_MOB_RUNTIME_TICK, MobEntityManager::runRuntimeTask);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			TRACKED_ENTITIES.put(entity.getUUID(), entity);
			if (entity instanceof LivingEntity livingEntity && !PlayerEntitiesSystem.isManagedPet(livingEntity)) {
				boolean reappliedMobOverrides = applyLoadedEntityRules(livingEntity);
				applyDifficultyScalingAfterMobOverrides(livingEntity, world, reappliedMobOverrides);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			TRACKED_ENTITIES.remove(entity.getUUID());
			cleanupEntityState(entity);
		});
	}

	public static void onServerStarted(MinecraftServer server) {
		runtimeSchedulerId = "";
		runtimeTaskScheduled = false;
		HOMING_ARROWS.clear();
		FIXED_ARROW_DAMAGE.clear();
		MANAGED_MOB_ARROWS.clear();
		INVULNERABILITY_BYPASS_ARROWS.clear();
		PENDING_SPIDER_JOCKEY_REPLACEMENTS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		PENDING_ZOMBIE_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		EntityBehaviorsManager.BeeBehavior.resetRuntimeState();
		EntityBehaviorsManager.SkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.WitherSkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.StrayBehavior.resetRuntimeState();
		EntityBehaviorsManager.BoggedBehavior.resetRuntimeState();
		EntityBehaviorsManager.ParchedBehavior.resetRuntimeState();
		runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		MadokuSchedulerManager.clearQueuedRequests(runtimeSchedulerId);
		requestRuntimeProcessing(server, 1L);
	}

	public static void onServerTick(MinecraftServer server) {
		requestRuntimeProcessing(server, 1L);
	}

	public static void onServerStopped() {
		runtimeSchedulerId = "";
		runtimeTaskScheduled = false;
		HOMING_ARROWS.clear();
		FIXED_ARROW_DAMAGE.clear();
		MANAGED_MOB_ARROWS.clear();
		INVULNERABILITY_BYPASS_ARROWS.clear();
		PENDING_SPIDER_JOCKEY_REPLACEMENTS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		PENDING_ZOMBIE_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		EntityBehaviorsManager.BeeBehavior.resetRuntimeState();
		EntityBehaviorsManager.SkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.WitherSkeletonBehavior.resetRuntimeState();
		EntityBehaviorsManager.StrayBehavior.resetRuntimeState();
		EntityBehaviorsManager.BoggedBehavior.resetRuntimeState();
		EntityBehaviorsManager.ParchedBehavior.resetRuntimeState();
	}

	public static boolean isEnabled() {
		return MobConfigManager.isEnabled();
	}
	public static boolean applyMobSpawnOverridesBeforeVanilla(
		Mob mob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		return EntitySpawnRulesManager.applyBeforeVanilla(mob, world, difficulty, spawnReason);
	}

	public static void applyMobSpawnOverridesAfterVanilla(
		Mob mob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		EntitySpawnRulesManager.applyAfterVanilla(mob, world, difficulty, spawnReason);
	}
	public static void applyZombieSpawnOverrides(
		Zombie zombie,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (zombie instanceof ZombieVillager zombieVillager) {
			EntityBehaviorsManager.ZombieVillagerBehavior.applySpawnOverrides(zombieVillager, world, difficulty, spawnReason);
			return;
		}
		EntityBehaviorsManager.ZombieBehavior.applySpawnOverrides(zombie, world, difficulty, spawnReason);
	}

	public static void applyDrownedSpawnOverrides(
		Drowned drowned,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (drowned == null || world == null || difficulty == null) {
			return;
		}
		EntityBehaviorsManager.DrownedBehavior.applySpawnOverrides(drowned, world, difficulty, spawnReason);
	}

	public static boolean applyConfiguredMobJockey(
		Mob sourceMob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		JsonObject variantRoot,
		EntitySpawnReason spawnReason,
		boolean sourceIsPassenger,
		boolean sourceIsBaby
	) {
		if (sourceMob == null || world == null || difficulty == null || variantRoot == null || !sourceMob.isAlive() || sourceMob.getVehicle() != null) {
			return false;
		}

		JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
		JsonObject jockeyRoot = readObject(spawnRules, MobConfigManager.FIELD_MOB_JOCKEY);
		if (jockeyRoot.entrySet().isEmpty() || !readBoolean(jockeyRoot, MobConfigManager.FIELD_ENABLED, false)) {
			return false;
		}

		JsonObject passengerRoot = readObject(jockeyRoot, MobConfigManager.FIELD_JOCKEY_PASSENGER);
		JsonObject mountRoot = readObject(jockeyRoot, MobConfigManager.FIELD_JOCKEY_MOUNT);

		ServerLevel level = world.getLevel();
		EntitySpawnReason jockeyReason = EntitySpawnReason.JOCKEY;
		if (sourceIsPassenger) {
			applyConfiguredMainHand(sourceMob, passengerRoot);

			EntityType<?> mountType = resolveConfiguredMobEntityType(mountRoot, sourceIsBaby);
			if (mountType == null) {
				return false;
			}

			Entity mount = spawnConfiguredJockeyPartner(sourceMob, world, difficulty, level, jockeyReason, mountType, false, mountRoot);
			if (mount == null) {
				return false;
			}
			if (!sourceMob.startRiding(mount) && mount.isAlive()) {
				mount.discard();
				return false;
			}
			return true;
		}

		EntityType<?> passengerType = resolveConfiguredMobEntityType(passengerRoot, sourceIsBaby);
		if (passengerType == null) {
			return false;
		}

		Entity passenger = spawnConfiguredJockeyPartner(sourceMob, world, difficulty, level, jockeyReason, passengerType, true, passengerRoot);
		if (passenger == null) {
			return false;
		}
		if (!passenger.startRiding(sourceMob) && passenger.isAlive()) {
			passenger.discard();
			return false;
		}
		return true;
	}

	static void queueZombieReplacement(Zombie zombie, EntityType<?> replacementType, EntitySpawnReason spawnReason) {
		if (zombie == null || replacementType == null || replacementType == madoku.craft.entity.MadokuEntityTypes.ZOMBIE || spawnReason == null) {
			return;
		}
		PENDING_ZOMBIE_REPLACEMENTS.put(zombie.getUUID(), new PendingZombieReplacement(replacementType, spawnReason));
		MinecraftServer server = resolveServer(zombie);
		requestRuntimeProcessing(server, 1L);
	}

	static void queueCaveSpiderReplacement(Spider spider, EntitySpawnReason spawnReason) {
		if (spider == null || spawnReason == null || spider.getType() != madoku.craft.entity.MadokuEntityTypes.SPIDER) {
			return;
		}
		PENDING_CAVE_SPIDER_REPLACEMENTS.put(spider.getUUID(), spawnReason);
		MinecraftServer server = resolveServer(spider);
		requestRuntimeProcessing(server, 1L);
	}

	static void queueSpiderJockeyReplacement(Spider spider, EntitySpawnReason spawnReason) {
		if (spider == null || spawnReason == null || spider.getType() != madoku.craft.entity.MadokuEntityTypes.SPIDER) {
			return;
		}
		PENDING_SPIDER_JOCKEY_REPLACEMENTS.put(spider.getUUID(), spawnReason);
		MinecraftServer server = resolveServer(spider);
		requestRuntimeProcessing(server, 1L);
	}

	public static boolean applySpiderSpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		return EntityBehaviorsManager.SpiderBehavior.applySpawnOverrides(spider, world, difficulty, spawnReason);
	}

	public static EntityType<?> resolveConfiguredMobEntityType(JsonObject mobRoot) {
		return resolveConfiguredMobEntityType(mobRoot, false);
	}

	public static EntityType<?> resolveConfiguredMobEntityType(JsonObject mobRoot, boolean preferBabyVariant) {
		if (mobRoot == null || mobRoot.entrySet().isEmpty()) {
			return null;
		}
		JsonElement mobElement = mobRoot.get(MobConfigManager.FIELD_MOB);
		if (mobElement == null || mobElement.isJsonNull()) {
			return null;
		}

		String mobId = "";
		if (mobElement.isJsonPrimitive() && mobElement.getAsJsonPrimitive().isString()) {
			mobId = mobElement.getAsString();
		} else if (mobElement.isJsonObject()) {
			JsonObject byAge = mobElement.getAsJsonObject();
			String primaryKey = preferBabyVariant ? MobConfigManager.FIELD_BABY_GROUP : MobConfigManager.FIELD_ADULT_GROUP;
			String fallbackKey = preferBabyVariant ? MobConfigManager.FIELD_ADULT_GROUP : MobConfigManager.FIELD_BABY_GROUP;
			mobId = readString(byAge, primaryKey, "");
			if (mobId.isBlank()) {
				mobId = readString(byAge, fallbackKey, "");
			}
		}
		return resolveEntityTypeById(mobId);
	}

	public static void applySkeletonSpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (skeleton == null || world == null || difficulty == null || !MobConfigManager.isEnabled()) {
			return;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			EntityBehaviorsManager.WitherSkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
			return;
		}
		if (!isSupportedSkeletonRuntimeType(skeleton)) {
			return;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			EntityBehaviorsManager.StrayBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else {
			EntityBehaviorsManager.SkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
		}
	}

	public static boolean applyCustomSkeletonRangedAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		if (skeleton == null || PlayerEntitiesSystem.isManagedPet(skeleton)) {
			return false;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.SkeletonBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
		}
		return EntityBehaviorsManager.SkeletonBehavior.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
	}

	public static int resolveSkeletonRangedAttackIntervalTicks(AbstractSkeleton skeleton) {
		if (skeleton == null || PlayerEntitiesSystem.isManagedPet(skeleton)) {
			return -1;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.SkeletonBehavior.resolveBowAttackIntervalTicks(skeleton);
		}
		return EntityBehaviorsManager.SkeletonBehavior.resolveBowAttackIntervalTicks(skeleton);
	}

	public static int resolveSkeletonChargeUpTicks(Monster attacker) {
		if (attacker instanceof AbstractSkeleton skeleton) {
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
				return EntityBehaviorsManager.StrayBehavior.resolveBowChargeUpTicks(skeleton);
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
				return EntityBehaviorsManager.BoggedBehavior.resolveBowChargeUpTicks(skeleton);
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
				return EntityBehaviorsManager.ParchedBehavior.resolveBowChargeUpTicks(skeleton);
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
				return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(skeleton);
			}
			return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(attacker);
		}
		return EntityBehaviorsManager.SkeletonBehavior.resolveBowChargeUpTicks(attacker);
	}

	public static void applyWitherSkeletonArrowHitEffect(LivingEntity target, Entity attacker) {
		EntityBehaviorsManager.WitherSkeletonBehavior.applyWitherSkeletonHitEffect(target, attacker);
	}

	public static boolean applyWitherSkeletonMeleeHitEffect(LivingEntity target, Entity attacker) {
		return EntityBehaviorsManager.WitherSkeletonBehavior.applyWitherSkeletonHitEffect(target, attacker);
	}

	public static void applySkeletonArrowHitEffect(LivingEntity target, Entity attacker) {
		if (target == null || attacker == null || target.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return;
		}
		if (!(attacker instanceof AbstractSkeleton skeleton)) {
			return;
		}
		JsonObject runtimeRoot = resolveSkeletonVariantRuntimeRoot(skeleton);
		if (runtimeRoot.entrySet().isEmpty()) {
			return;
		}
		MobEffectInstance configuredEffect = resolveConfiguredMobEffectInstance(readMobStatsRoot(runtimeRoot), null);
		if (configuredEffect != null) {
			target.addEffect(configuredEffect, skeleton);
		}
	}

	public static void handleMobDamaged(LivingEntity victim, DamageSource source) {
		if (victim == null || source == null || !MobConfigManager.isEnabled() || victim.level().isClientSide()) {
			return;
		}
		LivingEntity attacker = resolveDamageSourceLivingAttacker(source);
		if (attacker == null || !attacker.isAlive() || attacker == victim) {
			return;
		}
		if (!(victim.level() instanceof ServerLevel)) {
			return;
		}

	}

	public static float resolveProjectileDamageOverride(AbstractArrow arrow, float fallbackDamage) {
		if (arrow == null) {
			return fallbackDamage;
		}
		Float fixed = FIXED_ARROW_DAMAGE.get(arrow.getUUID());
		if (fixed != null) {
			return Math.max(0.0F, fixed);
		}
		if (arrow.getOwner() instanceof AbstractSkeleton skeleton && MobConfigManager.isEnabled() && isBowAttackEnabledForRuntimeSkeleton(skeleton)) {
			JsonObject root = resolveSkeletonVariantRuntimeRoot(skeleton);
			if (!root.entrySet().isEmpty()) {
				return (float) Math.max(0.0D, resolveSkeletonRangedDamage(skeleton, root));
			}
		}
		return fallbackDamage;
	}

	public static void setProjectileDamageOverride(AbstractArrow arrow, float damage) {
		if (arrow == null) {
			return;
		}
		FIXED_ARROW_DAMAGE.put(arrow.getUUID(), Math.max(0.0F, damage));
	}

	public static void startProjectileHoming(AbstractArrow arrow, LivingEntity target, MinecraftServer server) {
		if (arrow == null || target == null || server == null) {
			return;
		}
		double homingSpeed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
		arrow.setNoGravity(true);
		HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
		requestRuntimeProcessing(server, 1L);
	}

	public static void clearProjectileHoming(AbstractArrow arrow) {
		if (arrow == null) {
			return;
		}
		HOMING_ARROWS.remove(arrow.getUUID());
		if (arrow.isAlive()) {
			arrow.setNoGravity(false);
		}
	}

	public static boolean spawnManagedHomingArrow(
		LivingEntity shooter,
		LivingEntity target,
		Vec3 spawnPosition,
		float speed,
		float damage
	) {
		if (shooter == null || target == null || !target.isAlive() || spawnPosition == null || !(shooter.level() instanceof ServerLevel level)) {
			return false;
		}

		Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		arrow.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
		Vec3 desired = target.getEyePosition().subtract(spawnPosition);
		if (desired.lengthSqr() <= 1.0E-6D) {
			desired = shooter.getLookAngle();
		}
		arrow.shoot(desired.x, desired.y, desired.z, Math.max(0.1F, speed), 0.0F);
		arrow.setCritArrow(false);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		FIXED_ARROW_DAMAGE.put(arrow.getUUID(), Math.max(0.0F, damage));
		INVULNERABILITY_BYPASS_ARROWS.add(arrow.getUUID());
		trackManagedMobArrow(arrow, level.getServer());
		double homingSpeed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
		arrow.setNoGravity(true);
		arrow.addTag(HOMING_PROJECTILE_TAG);
		HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
		requestRuntimeProcessing(level.getServer(), 1L);
		shooter.level().addFreshEntity(arrow);
		return true;
	}

	public static boolean shouldBypassInvulnerability(AbstractArrow arrow) {
		return arrow != null && INVULNERABILITY_BYPASS_ARROWS.contains(arrow.getUUID());
	}

	public static boolean isManagedHomingArrow(AbstractArrow arrow) {
		return arrow != null && (HOMING_ARROWS.containsKey(arrow.getUUID()) || arrow.entityTags().contains(HOMING_PROJECTILE_TAG));
	}

	public static void clearInvulnerabilityBypass(AbstractArrow arrow) {
		if (arrow != null) {
			INVULNERABILITY_BYPASS_ARROWS.remove(arrow.getUUID());
		}
	}

	public static void applyCreeperExplosionOverride(
		Creeper creeper,
		ServerLevel level,
		Entity source,
		double x,
		double y,
		double z,
		float vanillaPower,
		Level.ExplosionInteraction vanillaInteraction
	) {
		if (level == null || creeper == null || !MobConfigManager.isEnabled()) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER) || !shouldUseCreeperMobExplodeBehavior(creeper, root)) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		JsonObject mobExplode = resolveCreeperMobExplodeRoot(variant);
		Double baseChance = clampOptional(readOptionalDouble(mobExplode, MobConfigManager.FIELD_DESTRUCTION_CHANCE), 0.0D, 1.0D);
		if (baseChance == null) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		double chance = Mth.clamp(
			resolveDifficultyAdjustedValue(
				level.getDifficulty(),
				isHardcoreWorld(level),
				baseChance,
				0.2D,
				0.0D
			),
			0.0D,
			1.0D
		);
		chance = MadokuLuckManager.reduceCreeperGriefChanceForTarget(creeper.getTarget(), chance);
		float power = (float) resolveCreeperExplosionPower(creeper, root, variant, vanillaPower);
		Level.ExplosionInteraction interaction = level.getRandom().nextDouble() < chance
			? Level.ExplosionInteraction.MOB
			: Level.ExplosionInteraction.NONE;
		level.explode(source, x, y, z, power, interaction);
	}

	public static float resolveCreeperGriefExplosionRadius(ServerExplosion explosion, float fallbackRadius) {
		if (explosion == null || !(explosion.getDirectSourceEntity() instanceof Creeper creeper) || !MobConfigManager.isEnabled()) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER) || !shouldUseCreeperMobExplodeBehavior(creeper, root)) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		JsonObject mobExplode = resolveCreeperMobExplodeRoot(variant);
		Double griefPower = readOptionalDouble(mobExplode, MobConfigManager.FIELD_GREIF_POWER);
		return (float) (Math.max(0.0F, fallbackRadius)
			* Mth.clamp(griefPower == null ? 0.5D : griefPower, 0.0D, 1.0D));
	}

	public static float resolveFixedPlayerExplosionDamage(Creeper creeper, float fallbackExplosionRadius) {
		double explosionPower = Math.max(0.0D, fallbackExplosionRadius);
		if (creeper == null || !MobConfigManager.isEnabled()) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER) || !shouldUseCreeperMobExplodeBehavior(creeper, root)) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		explosionPower = resolveCreeperExplosionPower(creeper, root, variant, explosionPower);
		return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			EntityBehaviorsManager.StrayBehavior.ensureBowEquipped(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			EntityBehaviorsManager.BoggedBehavior.ensureBowEquipped(skeleton);
			return;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			EntityBehaviorsManager.ParchedBehavior.ensureBowEquipped(skeleton);
			return;
		}
		EntityBehaviorsManager.SkeletonBehavior.ensureBowEquipped(skeleton);
	}

	public static boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) {
		if (!MobConfigManager.isEnabled() || source == null) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof AbstractSkeleton skeleton) || source.getDirectEntity() != attacker) {
			return false;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			return false;
		}
		return resolveBowHand(skeleton) == null;
	}

	public static boolean shouldBypassArmorForMobDamage(DamageSource source) {
		if (!MobConfigManager.isEnabled() || source == null) {
			return false;
		}
		if (shouldSkeletonMeleeIgnoreArmor(source)) {
			return true;
		}
		LivingEntity attacker = resolveDamageSourceLivingAttacker(source);
		if (attacker == null || !attacker.isAlive()) {
			return false;
		}
		return hasTrueDamageConfigured(attacker);
	}

	private static boolean applyLoadedEntityRules(LivingEntity entity) {
		boolean configuredComponentsApplied = EntityComponentsManager.applyConfiguredComponents(
			entity,
			resolveConfiguredEntityVariantForRuntime(entity)
		);
		return configuredComponentsApplied || applyConfiguredLoadedEntityRules(entity);
	}

	private static boolean applyConfiguredLoadedEntityRules(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled() || PlayerEntitiesSystem.isManagedPet(entity)) {
			return false;
		}
		if (entity instanceof ZombieVillager zombieVillager) {
			return EntityBehaviorsManager.ZombieVillagerBehavior.applyLoadedEntityOverrides(zombieVillager);
		}
		if (entity instanceof Drowned drowned) {
			return EntityBehaviorsManager.DrownedBehavior.applyLoadedEntityOverrides(drowned);
		}
		if (entity instanceof Husk husk) {
			return EntityBehaviorsManager.HuskBehavior.applyLoadedEntityOverrides(husk);
		}
		if (entity instanceof Zombie zombie) {
			return EntityBehaviorsManager.ZombieBehavior.applyLoadedEntityOverrides(zombie);
		}
		if (entity instanceof Spider spider) {
			if (spider.getType() == madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER) {
				return EntityBehaviorsManager.CaveSpiderBehavior.applyLoadedEntityOverrides(spider);
			}
			JsonObject root = readObject(fileMobRoot(MobConfigManager.FILE_SPIDER), MobConfigManager.FIELD_DEFAULT_GROUP);
			return isMobFileEnabled(MobConfigManager.FILE_SPIDER) && applyUniversalBaseStatsForRuntime(spider, root);
		}
		if (entity instanceof AbstractSkeleton skeleton) {
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
				return EntityBehaviorsManager.WitherSkeletonBehavior.applyLoadedEntityOverrides(skeleton);
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
				return EntityBehaviorsManager.StrayBehavior.applyLoadedEntityOverrides(skeleton);
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
				return EntityBehaviorsManager.BoggedBehavior.applyLoadedEntityOverrides(skeleton);
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
				return EntityBehaviorsManager.ParchedBehavior.applyLoadedEntityOverrides(skeleton);
			}
			return EntityBehaviorsManager.SkeletonBehavior.applyLoadedEntityOverrides(skeleton);
		}
		if (entity instanceof Creeper creeper) {
			return EntityBehaviorsManager.CreeperBehavior.applyLoadedEntityOverrides(creeper);
		}
			if (entity.getType() == madoku.craft.entity.MadokuEntityTypes.BEE) {
				return EntityBehaviorsManager.BeeBehavior.applyLoadedEntityOverrides(entity);
			}
		if (entity.getType() == MadokuEntities.HAG) {
			JsonObject root = resolveHagDefaultGroup(root(MobConfigManager.FILE_HAG));
			return isMobFileEnabled(MobConfigManager.FILE_HAG) && applyUniversalBaseStatsForRuntime(entity, root);
		}
			return false;
	}

	private static void applyDifficultyScalingAfterMobOverrides(LivingEntity entity, ServerLevel level, boolean loadedMobOverridesApplied) {
		if (!MobConfigManager.isEnabled() || entity == null || !(entity instanceof Mob mob) || level == null || !MobRegionalDifficultyManager.isEnabled() || PlayerEntitiesSystem.isManagedPet(entity)) {
			return;
		}
		if (!isRegionalDifficultyScalingEnabledForMob(entity)) {
			return;
		}
		if (loadedMobOverridesApplied && mob instanceof DifficultyState scaledMob && scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
			MobRegionalDifficultyManager.reapplySpawnScalingFromStoredAdjustment(mob);
		} else {
			MobRegionalDifficultyManager.applySpawnScalingIfUnscaled(mob, level);
		}
		applyLoadedEntityDifficultyScaling(entity);
	}

	private static void applyLoadedEntityDifficultyScaling(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return;
		}
		EntityComponentsManager.applyWorldDifficultyScaling(entity);
		if (entity instanceof ZombieVillager zombieVillager) {
			EntityBehaviorsManager.ZombieVillagerBehavior.applyLoadedEntityDifficultyOverrides(zombieVillager);
			return;
		}
		if (entity instanceof Drowned drowned) {
			EntityBehaviorsManager.DrownedBehavior.applyLoadedEntityDifficultyOverrides(drowned);
			return;
		}
		if (entity instanceof Husk husk) {
			EntityBehaviorsManager.HuskBehavior.applyLoadedEntityDifficultyOverrides(husk);
			return;
		}
		if (entity instanceof Spider spider) {
			if (spider.getType() == madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER) {
				EntityBehaviorsManager.CaveSpiderBehavior.applyLoadedEntityDifficultyOverrides(spider);
				return;
			}
			JsonObject root = readObject(fileMobRoot(MobConfigManager.FILE_SPIDER), MobConfigManager.FIELD_DEFAULT_GROUP);
			if (isMobFileEnabled(MobConfigManager.FILE_SPIDER)) {
				applyUniversalDifficultyStatsForRuntime(spider, root);
			}
			return;
		}
		if (entity instanceof AbstractSkeleton skeleton) {
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
				EntityBehaviorsManager.WitherSkeletonBehavior.applyLoadedEntityDifficultyOverrides(skeleton);
				return;
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
				EntityBehaviorsManager.StrayBehavior.applyLoadedEntityDifficultyOverrides(skeleton);
			} else if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
				EntityBehaviorsManager.BoggedBehavior.applyLoadedEntityDifficultyOverrides(skeleton);
			} else if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
				EntityBehaviorsManager.ParchedBehavior.applyLoadedEntityDifficultyOverrides(skeleton);
			} else {
				EntityBehaviorsManager.SkeletonBehavior.applyLoadedEntityDifficultyOverrides(skeleton);
			}
			return;
		}
		if (entity instanceof Creeper creeper) {
			EntityBehaviorsManager.CreeperBehavior.applyLoadedEntityDifficultyOverrides(creeper);
			return;
		}
		if (entity instanceof Zombie zombie) {
			EntityBehaviorsManager.ZombieBehavior.applyLoadedEntityDifficultyOverrides(zombie);
			return;
		}
		if (entity.getType() == madoku.craft.entity.MadokuEntityTypes.BEE) {
			EntityBehaviorsManager.BeeBehavior.applyLoadedEntityDifficultyOverrides(entity);
			return;
		}
		if (entity.getType() == MadokuEntities.HAG) {
			JsonObject root = resolveHagDefaultGroup(root(MobConfigManager.FILE_HAG));
			if (isMobFileEnabled(MobConfigManager.FILE_HAG)) {
				applyUniversalDifficultyStatsForRuntime(entity, root);
			}
		}
	}

	static void applyCreeperSpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
		if (creeper == null || world == null || difficulty == null || !MobConfigManager.isEnabled() || creeper.isPowered()) {
			return;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER)) {
			return;
		}
		JsonObject creeperRoot = readObject(root, MobConfigManager.FIELD_CREEPER);
		JsonObject creeperVariant = readObject(creeperRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		JsonObject chargedVariant = readObject(creeperRoot, MobConfigManager.FIELD_CHARGED_CREEPER);
		SpawnWeightPair shifted = resolveDifficultyShiftedSpawnWeights(
			readSpawnRuleDouble(creeperVariant, MobConfigManager.FIELD_SPAWN_WEIGHT, 95.0D),
			readSpawnRuleDouble(chargedVariant, MobConfigManager.FIELD_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(creeper.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		double total = shifted.regularWeight + shifted.specialWeight;
		if (total <= 0.0D) {
			return;
		}
		boolean charged = (world.getRandom().nextDouble() * total) >= shifted.regularWeight;
		if (charged) {
			creeper.getEntityData().set(CreeperPoweredAccessor.madokuCraft$getDataIsPowered(), true);
		}
	}

	static void applyBeeSpawnOverrides(Mob mob, ServerLevelAccessor world) {
		if (mob == null || world == null || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(mob, beeFileRoot, world.getRandom(), true);
		applyBeeOverrides(mob, beeFileRoot, resolved);
	}

	static boolean applyBeeLoadedEntityOverrides(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return applyBeeBaseOverrides(entity, beeFileRoot, resolved);
	}

	static boolean applyBeeBaseOverrides(LivingEntity entity, JsonObject beeFileRoot, JsonObject resolvedRoot) {
		if (entity == null || beeFileRoot == null || resolvedRoot == null || resolvedRoot.entrySet().isEmpty()) {
			return false;
		}
		boolean overrideStats = readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
		if (!overrideStats) {
			return false;
		}
		return applyUniversalBaseStatsForRuntime(entity, resolvedRoot);
	}

	static boolean applyBeeDifficultyOverrides(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		if (resolved.entrySet().isEmpty() || !readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true)) {
			return false;
		}
		return applyUniversalDifficultyStatsForRuntime(entity, resolved);
	}

	static JsonObject resolveBeeBehaviorRoot(LivingEntity entity) {
		if (entity == null || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return new JsonObject();
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		if (!readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true)) {
			return new JsonObject();
		}
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return readMobBehaviorRoot(resolved);
	}

	static boolean isBeeBehaviorOverrideEnabled() {
		if (!MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		return readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIORS, true);
	}

	static boolean isBeeGoalsOverrideEnabled() {
		if (!MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		return readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_GOALS, true);
	}

	public static boolean isBeeCustomMobDropsEnabled(LivingEntity entity) {
		if (entity == null
			|| entity.getType() != madoku.craft.entity.MadokuEntityTypes.BEE
			|| !MobConfigManager.isEnabled()
			|| !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolvedBeeRoot = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return readBoolean(
			resolvedBeeRoot,
			MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
			readBoolean(beeFileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
		);
	}

	public static String resolveBeeMobDropsConfigReference(LivingEntity entity) {
		if (entity == null || entity.getType() != madoku.craft.entity.MadokuEntityTypes.BEE) {
			return "";
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		JsonObject resolvedBeeRoot = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		JsonObject statsRoot = readMobStatsRoot(resolvedBeeRoot);
		return readString(statsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
	}

	public static JsonObject resolveBeeRootForRuntime(LivingEntity entity) {
		if (entity == null || entity.getType() != madoku.craft.entity.MadokuEntityTypes.BEE || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_BEE)) {
			return new JsonObject();
		}
		JsonObject beeFileRoot = root(MobConfigManager.FILE_BEE);
		return resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
	}

	public static MobEffectInstance resolveBeeAttackEffect(LivingEntity entity, MobEffectInstance fallbackEffect) {
		if (entity == null || entity.getType() != madoku.craft.entity.MadokuEntityTypes.BEE || fallbackEffect == null) {
			return fallbackEffect;
		}
		JsonObject resolvedBeeRoot = resolveBeeRootForRuntime(entity);
		if (resolvedBeeRoot.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		return resolveConfiguredMobEffectInstance(readMobStatsRoot(resolvedBeeRoot), fallbackEffect);
	}

	public static boolean isZombieCustomMobDropsEnabled(LivingEntity entity) {
		if (entity instanceof ZombieVillager zombieVillager) {
			return EntityBehaviorsManager.ZombieVillagerBehavior.isCustomMobDropsEnabled(zombieVillager);
		}
		if (entity instanceof Drowned drowned) {
			return EntityBehaviorsManager.DrownedBehavior.isCustomMobDropsEnabled(drowned);
		}
		if (entity instanceof Husk husk) {
			return EntityBehaviorsManager.HuskBehavior.isCustomMobDropsEnabled(husk);
		}
		return EntityBehaviorsManager.ZombieBehavior.isCustomMobDropsEnabled(entity);
	}

	public static String resolveZombieMobDropsConfigReference(LivingEntity entity) {
		if (entity instanceof ZombieVillager zombieVillager) {
			return EntityBehaviorsManager.ZombieVillagerBehavior.resolveMobDropsConfigReference(zombieVillager);
		}
		if (entity instanceof Drowned drowned) {
			return EntityBehaviorsManager.DrownedBehavior.resolveMobDropsConfigReference(drowned);
		}
		if (entity instanceof Husk husk) {
			return EntityBehaviorsManager.HuskBehavior.resolveMobDropsConfigReference(husk);
		}
		return EntityBehaviorsManager.ZombieBehavior.resolveMobDropsConfigReference(entity);
	}

	private static boolean isRegionalDifficultyScalingEnabledForMob(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		return fileKey.isBlank() || EntityConfigManager.isRegionalDifficultyScalingEnabled(root(fileKey));
	}

	private static String resolveRegionalDifficultyMobFileKey(LivingEntity entity) {
		if (entity == null) {
			return "";
		}
		if (entity instanceof Spider spider) {
			return spider.getType() == madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER ? MobConfigManager.FILE_CAVE_SPIDER : MobConfigManager.FILE_SPIDER;
		}
		if (entity instanceof AbstractSkeleton skeleton) {
			return skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.SKELETON ? MobConfigManager.FILE_SKELETON
				: skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY ? MobConfigManager.FILE_STRAY
				: skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED ? MobConfigManager.FILE_BOGGED
				: skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED ? MobConfigManager.FILE_PARCHED
				: skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON ? MobConfigManager.FILE_WITHER_SKELETON
				: "";
		}
		if (entity instanceof ZombieVillager) {
			return MobConfigManager.FILE_ZOMBIE_VILLAGER;
		}
		if (entity instanceof Drowned) {
			return MobConfigManager.FILE_DROWNED;
		}
		if (entity instanceof Zombie zombie) {
			return zombie.getType() == madoku.craft.entity.MadokuEntityTypes.ZOMBIE ? MobConfigManager.FILE_ZOMBIE
				: zombie.getType() == madoku.craft.entity.MadokuEntityTypes.HUSK ? MobConfigManager.FILE_HUSK
				: "";
		}
		if (entity instanceof Creeper) {
			return MobConfigManager.FILE_CREEPER;
		}
		if (entity.getType() == madoku.craft.entity.MadokuEntityTypes.BEE) {
			return MobConfigManager.FILE_BEE;
		}
		if (entity.getType() == MadokuEntities.HAG) {
			return MobConfigManager.FILE_HAG;
		}
		return "";
	}

	static void applyHagSpawnOverrides(Mob mob) {
		if (mob == null || !MobConfigManager.isEnabled()) {
			return;
		}
		JsonObject root = resolveHagDefaultGroup(root(MobConfigManager.FILE_HAG));
		if (isMobFileEnabled(MobConfigManager.FILE_HAG)) {
			applyUniversalStats(mob, root);
		}
	}

	private static boolean applyBeeOverrides(LivingEntity entity, JsonObject beeFileRoot, JsonObject resolvedRoot) {
		if (entity == null || beeFileRoot == null || resolvedRoot == null || resolvedRoot.entrySet().isEmpty()) {
			return false;
		}
		boolean overrideStats = readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_COMPONENTS, true);
		if (!overrideStats) {
			return false;
		}
		return applyUniversalStats(entity, resolvedRoot);
	}

	private static boolean applyUniversalStats(LivingEntity entity, JsonObject root) {
		if (entity == null || root == null) {
			return false;
		}
		boolean modified = false;
		double oldMaxHealth = entity.getMaxHealth();
		boolean hardcore = isHardcoreWorld(entity.level());
		JsonObject statsRoot = readMobStatsRoot(root);
		JsonObject difficultyScale = readObject(root, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
		boolean difficultyScalingEnabled = readBoolean(root, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING, false);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.MAX_HEALTH,
			resolveScaledStat(
				readOptionalPositive(statsRoot, MobConfigManager.FIELD_HEALTH),
				entity.level().getDifficulty(),
				hardcore,
				HEALTH_DIFFICULTY_STEP,
				0.0D,
				difficultyScalingEnabled,
				difficultyScale,
				MobConfigManager.FIELD_HEALTH
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.ARMOR,
			resolveUniversalBaseStat(
				readOptionalNonNegative(statsRoot, MobConfigManager.FIELD_ARMOR),
				entity.level().getDifficulty(),
				hardcore,
				ARMOR_DIFFICULTY_STEP,
				0.0D
			)
		);
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.ATTACK_DAMAGE,
				resolveScaledStat(
					readOptionalNonNegative(statsRoot, MobConfigManager.FIELD_DAMAGE),
					entity.level().getDifficulty(),
					hardcore,
					DAMAGE_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_DAMAGE
				)
			);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.MOVEMENT_SPEED,
			resolveScaledStat(
				readOptionalPositive(statsRoot, MobConfigManager.FIELD_MOVEMENT_SPEED),
					entity.level().getDifficulty(),
					hardcore,
					MOVEMENT_SPEED_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
				MobConfigManager.FIELD_MOVEMENT_SPEED
			)
		);
		if (entity instanceof Drowned) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.WATER_MOVEMENT_EFFICIENCY,
				resolveScaledStat(
					readOptionalPositive(statsRoot, MobConfigManager.FIELD_SWIMMING_SPEED),
					entity.level().getDifficulty(),
					hardcore,
					MOVEMENT_SPEED_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_SWIMMING_SPEED
				)
			);
		}
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.FLYING_SPEED,
			resolveScaledStat(
				readOptionalPositive(statsRoot, FIELD_FLYING_SPEED),
				entity.level().getDifficulty(),
				hardcore,
				MOVEMENT_SPEED_DIFFICULTY_STEP,
				0.0D,
				difficultyScalingEnabled,
				difficultyScale,
				MobConfigManager.FIELD_FLYING_SPEED
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.KNOCKBACK_RESISTANCE,
			resolveUniversalBaseStat(
				clampOptional(readOptionalDouble(statsRoot, MobConfigManager.FIELD_KNOCKBACK_RESISTANCE), 0.0D, 1.0D),
				entity.level().getDifficulty(),
				hardcore,
				KNOCKBACK_RESISTANCE_DIFFICULTY_STEP,
				0.0D
			)
		);
		Double baseScale = readOptionalPositive(statsRoot, MobConfigManager.FIELD_SCALE);
		Double resolvedScale = baseScale;
		if (baseScale != null) {
			if (difficultyScalingEnabled) {
				Double perStep = readOptionalDouble(difficultyScale, MobConfigManager.FIELD_SCALE);
				if (perStep != null) {
					resolvedScale = resolveDifficultyAdjustedPercentValue(
						entity.level().getDifficulty(),
						hardcore,
						baseScale,
						perStep,
						0.01D
					);
				}
			}
		}
		boolean scaleApplied = setBaseValueIfPresent(entity, Attributes.SCALE, resolvedScale);
		modified |= scaleApplied;
		Integer baseExperienceDrop = readOptionalIntNonNegative(statsRoot, MobConfigManager.FIELD_EXPERIENCE_DROP);
		if (baseExperienceDrop != null) {
			int resolvedExperienceDrop = baseExperienceDrop;
			if (difficultyScalingEnabled) {
				Double perStep = readOptionalNonNegative(difficultyScale, MobConfigManager.FIELD_EXPERIENCE_DROP);
				if (perStep != null) {
					resolvedExperienceDrop = (int) Math.round(
						resolveDifficultyAdjustedPercentValue(
							entity.level().getDifficulty(),
							hardcore,
							baseExperienceDrop,
							perStep,
							0.0D
						)
					);
				}
			}
			applyExperienceDrop(entity, Math.max(0, resolvedExperienceDrop));
		}
		modified |= applyConfiguredMobWeapon(entity, root);
		rescaleCurrentHealth(entity, oldMaxHealth);
		return modified;
	}

	static boolean applyUniversalStatsForRuntime(LivingEntity entity, JsonObject root) {
		return applyUniversalStats(entity, root);
	}

	static boolean applyUniversalBaseStatsForRuntime(LivingEntity entity, JsonObject root) {
		if (entity == null || root == null) {
			return false;
		}
		boolean modified = false;
		double oldMaxHealth = entity.getMaxHealth();
		JsonObject statsRoot = readMobStatsRoot(root);
		modified |= setBaseValueIfPresent(entity, Attributes.MAX_HEALTH, readOptionalPositive(statsRoot, MobConfigManager.FIELD_HEALTH));
		modified |= setBaseValueIfPresent(entity, Attributes.ARMOR, readOptionalNonNegative(statsRoot, MobConfigManager.FIELD_ARMOR));
		modified |= setBaseValueIfPresent(entity, Attributes.ATTACK_DAMAGE, readOptionalNonNegative(statsRoot, MobConfigManager.FIELD_DAMAGE));
		modified |= setBaseValueIfPresent(entity, Attributes.MOVEMENT_SPEED, readOptionalPositive(statsRoot, MobConfigManager.FIELD_MOVEMENT_SPEED));
		modified |= setBaseValueIfPresent(entity, Attributes.FLYING_SPEED, readOptionalPositive(statsRoot, FIELD_FLYING_SPEED));
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.KNOCKBACK_RESISTANCE,
			clampOptional(readOptionalDouble(statsRoot, MobConfigManager.FIELD_KNOCKBACK_RESISTANCE), 0.0D, 1.0D)
		);
		Double baseScale = readOptionalPositive(statsRoot, MobConfigManager.FIELD_SCALE);
		if (baseScale != null) {
			modified |= setBaseValue(entity, Attributes.SCALE, baseScale);
		}
		Integer baseExperienceDrop = readOptionalIntNonNegative(statsRoot, MobConfigManager.FIELD_EXPERIENCE_DROP);
		if (baseExperienceDrop != null) {
			applyExperienceDrop(entity, Math.max(0, baseExperienceDrop));
			modified = true;
		}
		modified |= applyConfiguredMobWeapon(entity, root);
		rescaleCurrentHealth(entity, oldMaxHealth);
		return modified;
	}

	static boolean applyUniversalDifficultyStatsForRuntime(LivingEntity entity, JsonObject root) {
		if (entity == null || root == null) {
			return false;
		}
		boolean modified = false;
		double oldMaxHealth = entity.getMaxHealth();
		boolean hardcore = isHardcoreWorld(entity.level());
		JsonObject statsRoot = readMobStatsRoot(root);
		JsonObject difficultyScale = readObject(root, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
		boolean difficultyScalingEnabled = readBoolean(root, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING, false);
		Double baseHealth = readOptionalPositive(statsRoot, MobConfigManager.FIELD_HEALTH);
		if (baseHealth != null) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.MAX_HEALTH,
				resolveScaledStat(
					readAttributeBaseValue(entity, Attributes.MAX_HEALTH),
					entity.level().getDifficulty(),
					hardcore,
					HEALTH_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_HEALTH
				)
			);
		}
		Double baseArmor = readOptionalNonNegative(statsRoot, MobConfigManager.FIELD_ARMOR);
		if (baseArmor != null) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.ARMOR,
				resolveUniversalBaseStat(
					readAttributeBaseValue(entity, Attributes.ARMOR),
					entity.level().getDifficulty(),
					hardcore,
					ARMOR_DIFFICULTY_STEP,
					0.0D
				)
			);
		}
		Double baseDamage = readOptionalNonNegative(statsRoot, MobConfigManager.FIELD_DAMAGE);
		if (baseDamage != null) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.ATTACK_DAMAGE,
				resolveScaledStat(
					readAttributeBaseValue(entity, Attributes.ATTACK_DAMAGE),
					entity.level().getDifficulty(),
					hardcore,
					DAMAGE_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_DAMAGE
				)
			);
		}
		Double baseMovementSpeed = readOptionalPositive(statsRoot, MobConfigManager.FIELD_MOVEMENT_SPEED);
		if (baseMovementSpeed != null) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.MOVEMENT_SPEED,
				resolveScaledStat(
					readAttributeBaseValue(entity, Attributes.MOVEMENT_SPEED),
					entity.level().getDifficulty(),
					hardcore,
					MOVEMENT_SPEED_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_MOVEMENT_SPEED
				)
			);
		}
		if (entity instanceof Drowned) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.WATER_MOVEMENT_EFFICIENCY,
				resolveScaledStat(
					readOptionalPositive(statsRoot, MobConfigManager.FIELD_SWIMMING_SPEED),
					entity.level().getDifficulty(),
					hardcore,
					MOVEMENT_SPEED_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_SWIMMING_SPEED
				)
			);
		}
		Double baseFlyingSpeed = readOptionalPositive(statsRoot, FIELD_FLYING_SPEED);
		if (baseFlyingSpeed != null) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.FLYING_SPEED,
				resolveScaledStat(
					readAttributeBaseValue(entity, Attributes.FLYING_SPEED),
					entity.level().getDifficulty(),
					hardcore,
					MOVEMENT_SPEED_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MobConfigManager.FIELD_FLYING_SPEED
				)
			);
		}
		Double baseKnockbackResistance = clampOptional(readOptionalDouble(statsRoot, MobConfigManager.FIELD_KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
		if (baseKnockbackResistance != null) {
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.KNOCKBACK_RESISTANCE,
				resolveUniversalBaseStat(
					readAttributeBaseValue(entity, Attributes.KNOCKBACK_RESISTANCE),
					entity.level().getDifficulty(),
					hardcore,
					KNOCKBACK_RESISTANCE_DIFFICULTY_STEP,
					0.0D
				)
			);
		}
		Double baseScale = readOptionalPositive(statsRoot, MobConfigManager.FIELD_SCALE);
		if (baseScale != null) {
			Double resolvedScale = readAttributeBaseValue(entity, Attributes.SCALE);
			if (difficultyScalingEnabled) {
				Double perStep = readOptionalDouble(difficultyScale, MobConfigManager.FIELD_SCALE);
				if (perStep != null) {
					resolvedScale = resolveDifficultyAdjustedPercentValue(
						entity.level().getDifficulty(),
						hardcore,
						Math.max(0.0D, resolvedScale),
						perStep,
						0.01D
					);
				}
			}
			modified |= setBaseValueIfPresent(entity, Attributes.SCALE, resolvedScale);
		}
		Integer baseExperienceDrop = readOptionalIntNonNegative(statsRoot, MobConfigManager.FIELD_EXPERIENCE_DROP);
		if (baseExperienceDrop != null) {
			int currentExperienceDrop = resolveMobExperienceDrop(entity);
			int resolvedExperienceDrop = currentExperienceDrop;
			if (difficultyScalingEnabled) {
				Double perStep = readOptionalNonNegative(difficultyScale, MobConfigManager.FIELD_EXPERIENCE_DROP);
				if (perStep != null) {
					resolvedExperienceDrop = (int) Math.round(
						resolveDifficultyAdjustedPercentValue(
							entity.level().getDifficulty(),
							hardcore,
							currentExperienceDrop,
							perStep,
							0.0D
						)
					);
				}
			}
			applyExperienceDrop(entity, Math.max(0, resolvedExperienceDrop));
			modified = true;
		}
		modified |= applyConfiguredMobWeapon(entity, root);
		rescaleCurrentHealth(entity, oldMaxHealth);
		return modified;
	}

	static boolean applyCreeperLoadedEntityDifficultyOverrides(Creeper creeper) {
		if (creeper == null || creeper.level().isClientSide() || !MobConfigManager.isEnabled()) {
			return false;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER)) {
			return false;
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		Double explosionPower = readOptionalDouble(readMobStatsRoot(variant), MobConfigManager.FIELD_EXPLOSION_POWER);
		if (explosionPower == null) {
			return false;
		}
		double resolvedPower = resolveCreeperExplosionPower(creeper, root, variant, explosionPower);
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		accessor.madokuCraft$setExplosionRadius(Math.max(0, (int) Math.round(resolvedPower)));
		return true;
	}

	static boolean applyCreeperRuntimeStats(Creeper creeper, JsonObject root) {
		boolean modified = false;
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, root);
		if (variant.entrySet().isEmpty()) {
			return false;
		}
		modified |= applyUniversalStats(creeper, variant);
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		Double fuseLength = readOptionalPositive(readMobStatsRoot(variant), MobConfigManager.FIELD_FUSE_LENGTH);
		if (fuseLength != null) {
			int fuse = Math.max(1, (int) Math.round(fuseLength));
			accessor.madokuCraft$setMaxSwell(fuse);
			if (accessor.madokuCraft$getSwell() > fuse) {
				accessor.madokuCraft$setSwell(fuse);
			}
			if (accessor.madokuCraft$getOldSwell() > fuse) {
				accessor.madokuCraft$setOldSwell(fuse);
			}
		}
		Double explosionPower = readOptionalDouble(readMobStatsRoot(variant), MobConfigManager.FIELD_EXPLOSION_POWER);
		if (explosionPower != null) {
			double resolvedPower = resolveCreeperExplosionPower(creeper, root, variant, explosionPower);
			int radius = Math.max(0, (int) Math.round(resolvedPower));
			accessor.madokuCraft$setExplosionRadius(radius);
		}
		return modified;
	}

	private static JsonObject resolveCreeperRuntimeVariantRoot(Creeper creeper, JsonObject fileRoot) {
		if (creeper == null || fileRoot == null || fileRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject creeperRoot = readObject(fileRoot, MobConfigManager.FIELD_CREEPER);
		JsonObject defaultGroup = readObject(creeperRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return new JsonObject();
		}
		if (!creeper.isPowered()) {
			return defaultGroup;
		}
		JsonObject chargedVariant = readObject(creeperRoot, MobConfigManager.FIELD_CHARGED_CREEPER);
		if (chargedVariant.entrySet().isEmpty()) {
			return defaultGroup;
		}
		return resolveVariantGroupRoot(defaultGroup, chargedVariant);
	}

	public static boolean shouldUseCreeperMobExplodeBehavior(Creeper creeper) {
		if (creeper == null || !MobConfigManager.isEnabled()) {
			return false;
		}
		JsonObject root = root(MobConfigManager.FILE_CREEPER);
		return isMobFileEnabled(MobConfigManager.FILE_CREEPER) && shouldUseCreeperMobExplodeBehavior(creeper, root);
	}

	private static boolean shouldUseCreeperMobExplodeBehavior(Creeper creeper, JsonObject fileRoot) {
		JsonObject mobExplode = resolveCreeperMobExplodeRoot(creeper, fileRoot);
		return !mobExplode.entrySet().isEmpty() && readBoolean(mobExplode, MobConfigManager.FIELD_ENABLED, true);
	}

	private static JsonObject resolveCreeperMobExplodeRoot(Creeper creeper, JsonObject fileRoot) {
		if (creeper == null || fileRoot == null || fileRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject variant = resolveCreeperRuntimeVariantRoot(creeper, fileRoot);
		return resolveCreeperMobExplodeRoot(variant);
	}

	private static JsonObject resolveCreeperMobExplodeRoot(JsonObject variantRoot) {
		if (variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		return readObject(readMobBehaviorRoot(variantRoot), MobConfigManager.FIELD_MOB_EXPLODE);
	}

	private static double resolveCreeperExplosionPower(Creeper creeper, JsonObject fileRoot, JsonObject variantRoot, double fallbackPower) {
		double explosionPower = Math.max(0.0D, fallbackPower);
		if (creeper == null || fileRoot == null || variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return explosionPower;
		}
		JsonObject statsRoot = readMobStatsRoot(variantRoot);
		Double configuredPower = readOptionalDouble(statsRoot, MobConfigManager.FIELD_EXPLOSION_POWER);
		if (configuredPower != null) {
			explosionPower = Math.max(0.0D, configuredPower);
		}
		JsonObject difficultyScale = readObject(fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
		boolean difficultyScalingEnabled = readBoolean(fileRoot, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING, false);
		if (difficultyScalingEnabled) {
			Double perStep = readOptionalNonNegative(difficultyScale, MobConfigManager.FIELD_EXPLOSION_POWER);
			if (perStep != null) {
				explosionPower = resolveDifficultyAdjustedPercentValue(
					creeper.level().getDifficulty(),
					isHardcoreWorld(creeper.level()),
					explosionPower,
					perStep,
					0.0D
				);
			} else {
				explosionPower = resolveDifficultyAdjustedValue(
					creeper.level().getDifficulty(),
					isHardcoreWorld(creeper.level()),
					explosionPower,
					CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP,
					0.0D
				);
			}
		} else {
			explosionPower = resolveDifficultyAdjustedValue(
				creeper.level().getDifficulty(),
				isHardcoreWorld(creeper.level()),
				explosionPower,
				CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP,
				0.0D
			);
		}
		return Math.max(0.0D, explosionPower + MobRegionalDifficultyManager.resolveCreeperExplosionPowerScaling(creeper, explosionPower));
	}

	private static JsonObject resolveHagDefaultGroup(JsonObject fileRoot) {
		return readObject(readObject(fileRoot, MobConfigManager.FILE_HAG), MobConfigManager.FIELD_DEFAULT_GROUP);
	}

	private static void disableZombieReinforcements(Zombie zombie) {
		AttributeInstance instance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (instance == null) {
			return;
		}
		instance.removeModifiers();
		instance.setBaseValue(0.0D);
	}

	static void disableZombieReinforcementsForRuntime(Zombie zombie) {
		disableZombieReinforcements(zombie);
	}

	static void clearArmorSlotsForRuntime(Mob mob) {
		clearArmorSlots(mob);
	}

	private static void clearArmorSlots(Mob mob) {
		if (mob == null) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
	}

	private static void applyExperienceDrop(LivingEntity entity, Integer experienceDrop) {
		if (entity instanceof Mob mob && experienceDrop != null) {
			((MobExperienceAccessor) mob).madokuCraft$setXpReward(Math.max(0, experienceDrop));
		}
	}

	private static int resolveMobExperienceDrop(LivingEntity entity) {
		if (entity instanceof Mob mob) {
			return ((MobExperienceAccessor) mob).madokuCraft$getXpReward();
		}
		return 0;
	}

	private static double readAttributeBaseValue(LivingEntity entity, Holder<Attribute> attribute) {
		if (entity == null || attribute == null) {
			return 0.0D;
		}
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance == null ? 0.0D : instance.getBaseValue();
	}

	private static boolean setBaseValue(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null || Double.compare(instance.getBaseValue(), value) == 0) {
			return false;
		}
		instance.setBaseValue(value);
		return true;
	}

	private static boolean setBaseValueIfPresent(LivingEntity entity, Holder<Attribute> attribute, Double value) {
		if (value != null) {
			return setBaseValue(entity, attribute, value);
		}
		return false;
	}

	private static void rescaleCurrentHealth(LivingEntity entity, double oldMaxHealth) {
		double newMax = entity.getMaxHealth();
		if (newMax <= 0.0D) {
			return;
		}
		float scaled = oldMaxHealth > 0.0D ? (float) (newMax * (entity.getHealth() / oldMaxHealth)) : (float) newMax;
		entity.setHealth(Math.min((float) newMax, Math.max(0.0F, scaled)));
	}

	private static void cleanupEntityState(Entity entity) {
		if (entity == null) {
			return;
		}
		UUID id = entity.getUUID();
		if (entity instanceof AbstractArrow) {
			HOMING_ARROWS.remove(id);
			FIXED_ARROW_DAMAGE.remove(id);
			MANAGED_MOB_ARROWS.remove(id);
		}
		PENDING_SPIDER_JOCKEY_REPLACEMENTS.remove(id);
		PENDING_CAVE_SPIDER_REPLACEMENTS.remove(id);
		PENDING_ZOMBIE_REPLACEMENTS.remove(id);
		EntityBehaviorsManager.SkeletonBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.WitherSkeletonBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.StrayBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.BoggedBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.ParchedBehavior.onEntityCleanup(entity);
		EntityBehaviorsManager.BeeBehavior.onEntityCleanup(entity);
	}

	private static void runRuntimeTask(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
		if (context != null) {
			runtimeSchedulerId = context.getSchedulerId();
		}
		runtimeTaskScheduled = false;
		tickManagedMobArrows(server);
		tickHomingProjectiles(server);
		processPendingSpiderJockeyReplacements(server);
		processPendingCaveSpiderReplacements(server);
		processPendingZombieReplacements(server);
		boolean beeRuntimeActive = EntityBehaviorsManager.BeeBehavior.tickRuntime(
			server,
			TRACKED_ENTITIES.values(),
			MobConfigManager.isEnabled(),
			isMobFileEnabled(MobConfigManager.FILE_BEE)
		);
		if (!MANAGED_MOB_ARROWS.isEmpty()
			|| !HOMING_ARROWS.isEmpty()
			|| !PENDING_SPIDER_JOCKEY_REPLACEMENTS.isEmpty()
			|| !PENDING_CAVE_SPIDER_REPLACEMENTS.isEmpty()
			|| !PENDING_ZOMBIE_REPLACEMENTS.isEmpty()
			|| beeRuntimeActive) {
			requestRuntimeProcessing(server, 1L);
		}
	}

	private static void requestRuntimeProcessing(MinecraftServer server, long delayTicks) {
		if (server == null || !MobConfigManager.isEnabled() || runtimeTaskScheduled) {
			return;
		}
		String schedulerId = ensureRuntimeSchedulerExists();
		if (enqueueRuntimeTask(schedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
			return;
		}
		runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		if (enqueueRuntimeTask(runtimeSchedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
		}
	}

	private static String ensureRuntimeSchedulerExists() {
		if (runtimeSchedulerId == null || runtimeSchedulerId.isBlank()) {
			runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		}
		return runtimeSchedulerId;
	}

	private static boolean enqueueRuntimeTask(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_MOB_RUNTIME_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED || status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}

	private static void tickHomingProjectiles(MinecraftServer server) {
		if (server == null || HOMING_ARROWS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, HomingArrowState> entry : HOMING_ARROWS.entrySet()) {
			UUID arrowId = entry.getKey();
			HomingArrowState state = entry.getValue();
			if (state.remainingTicks <= 0) {
				releaseHomingArrow(arrowId, findArrow(server, arrowId));
				continue;
			}
			AbstractArrow arrow = findArrow(server, arrowId);
			if (arrow == null || !arrow.isAlive() || arrow.onGround()) {
				HOMING_ARROWS.remove(arrowId);
				FIXED_ARROW_DAMAGE.remove(arrowId);
				continue;
			}
			Entity target = findEntity(server, state.targetUuid);
			if (!(target instanceof LivingEntity living) || !living.isAlive()) {
				releaseHomingArrow(arrowId, arrow);
				continue;
			}
			Vec3 toTarget = new Vec3(target.getX() - arrow.getX(), target.getY(0.5D) - arrow.getY(), target.getZ() - arrow.getZ());
			if (toTarget.lengthSqr() <= 1.0E-6D) {
				releaseHomingArrow(arrowId, arrow);
				continue;
			}
			double speed = Math.max(state.speed, arrow.getDeltaMovement().length());
			arrow.setNoGravity(true);
			arrow.setDeltaMovement(toTarget.normalize().scale(speed));
			HOMING_ARROWS.put(arrowId, new HomingArrowState(state.targetUuid, speed, state.remainingTicks - 1));
		}
	}

	private static void tickManagedMobArrows(MinecraftServer server) {
		if (server == null || MANAGED_MOB_ARROWS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Integer> entry : MANAGED_MOB_ARROWS.entrySet()) {
			UUID arrowId = entry.getKey();
			int remainingTicks = entry.getValue() == null ? 0 : entry.getValue();
			AbstractArrow arrow = findArrow(server, arrowId);
			if (arrow == null || !arrow.isAlive()) {
				removeArrowRuntimeState(arrowId);
				continue;
			}
			if (remainingTicks <= 0) {
				arrow.discard();
				removeArrowRuntimeState(arrowId);
				continue;
			}
			MANAGED_MOB_ARROWS.put(arrowId, remainingTicks - 1);
		}
	}

	private static void processPendingCaveSpiderReplacements(MinecraftServer server) {
		if (server == null || PENDING_CAVE_SPIDER_REPLACEMENTS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, EntitySpawnReason> entry : PENDING_CAVE_SPIDER_REPLACEMENTS.entrySet()) {
			processQueuedRuntimeReplacement(
				server,
				entry.getKey(),
				madoku.craft.entity.MadokuEntityTypes.SPIDER,
				entry.getValue(),
				madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER,
				false,
				"process_cave_spider_missing",
				"process_cave_spider_create_failed",
				"process_cave_spider_spawned",
				"replaced_with_cave_spider"
			);
			PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
		}
	}

	private static void processPendingSpiderJockeyReplacements(MinecraftServer server) {
		if (server == null || PENDING_SPIDER_JOCKEY_REPLACEMENTS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, EntitySpawnReason> entry : PENDING_SPIDER_JOCKEY_REPLACEMENTS.entrySet()) {
			processQueuedRuntimeReplacement(
				server,
				entry.getKey(),
				madoku.craft.entity.MadokuEntityTypes.SPIDER,
				entry.getValue(),
				madoku.craft.entity.MadokuEntityTypes.SKELETON,
				true,
				"process_spider_jockey_missing",
				"process_spider_jockey_create_failed",
				"process_spider_jockey_spawned",
				"skeleton_attached"
			);
			PENDING_SPIDER_JOCKEY_REPLACEMENTS.remove(entry.getKey());
		}
	}

	private static void processPendingZombieReplacements(MinecraftServer server) {
		if (server == null || PENDING_ZOMBIE_REPLACEMENTS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, PendingZombieReplacement> entry : PENDING_ZOMBIE_REPLACEMENTS.entrySet()) {
			PendingZombieReplacement replacement = entry.getValue();
			if (replacement == null) {
				PENDING_ZOMBIE_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			processQueuedRuntimeReplacement(
				server,
				entry.getKey(),
				madoku.craft.entity.MadokuEntityTypes.ZOMBIE,
				replacement.reason(),
				replacement.replacementType(),
				false,
				"process_zombie_missing",
				"process_zombie_create_failed",
				"process_zombie_spawned",
				"replaced_with_custom_entity"
			);
			PENDING_ZOMBIE_REPLACEMENTS.remove(entry.getKey());
		}
	}

	private static void processQueuedRuntimeReplacement(
		MinecraftServer server,
		UUID sourceId,
		EntityType<?> expectedSourceType,
		EntitySpawnReason spawnReason,
		EntityType<?> spawnedType,
		boolean attachToSource,
		String missingPhase,
		String createFailedPhase,
		String completedPhase,
		String completedDetail
	) {
		if (server == null || sourceId == null || spawnedType == null) {
			return;
		}
		Entity source = findEntity(server, sourceId);
		if (source == null || !source.isAlive() || (expectedSourceType != null && source.getType() != expectedSourceType)) {
			return;
		}
		if (!(source.level() instanceof ServerLevel level)) {
			return;
		}

		EntitySpawnReason reason = spawnReason == null ? EntitySpawnReason.NATURAL : spawnReason;
		Entity spawnedEntity = spawnedType.create(level, reason);
		if (spawnedEntity == null) {
			return;
		}

		spawnedEntity.setPos(source.getX(), source.getY(), source.getZ());
		spawnedEntity.setYRot(source.getYRot());
		spawnedEntity.setXRot(source.getXRot());
		if (spawnedEntity instanceof Zombie replacementZombie && source instanceof Zombie zombie) {
			replacementZombie.setBaby(zombie.isBaby());
		}
		if (spawnedEntity instanceof Mob mobSpawned) {
			mobSpawned.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(source.position())), reason, null);
		}
		if (spawnedEntity instanceof AbstractSkeleton skeleton
			&& isBowAttackEnabledForRuntimeSkeleton(skeleton)) {
			ensureBowEquipped(skeleton);
		}
		level.tryAddFreshEntityWithPassengers(spawnedEntity);
		if (attachToSource) {
			if (!spawnedEntity.startRiding(source) && spawnedEntity.isAlive()) {
				spawnedEntity.discard();
				return;
			}
		} else {
			source.discard();
		}
	}

	private static Entity findEntity(MinecraftServer server, UUID entityId) {
		if (server == null || entityId == null) {
			return null;
		}
		Entity entity = TRACKED_ENTITIES.get(entityId);
		if (entity != null && entity.isAlive()) {
			return entity;
		}
		if (entity != null && !entity.isAlive()) {
			TRACKED_ENTITIES.remove(entityId);
		}
		return null;
	}

	private static AbstractArrow findArrow(MinecraftServer server, UUID arrowId) {
		Entity entity = findEntity(server, arrowId);
		return entity instanceof AbstractArrow arrow ? arrow : null;
	}

	private static void releaseHomingArrow(UUID arrowId, AbstractArrow arrow) {
		HOMING_ARROWS.remove(arrowId);
		if (arrow != null && arrow.isAlive()) {
			arrow.setNoGravity(false);
		}
	}

	private static void trackManagedMobArrow(AbstractArrow arrow, MinecraftServer server) {
		if (arrow == null) {
			return;
		}
		MANAGED_MOB_ARROWS.put(arrow.getUUID(), MOB_ARROW_LIFETIME_TICKS);
		requestRuntimeProcessing(server, 1L);
	}

	static void trackManagedMobArrowForRuntime(AbstractArrow arrow, MinecraftServer server) {
		trackManagedMobArrow(arrow, server);
	}

	private static void removeArrowRuntimeState(UUID arrowId) {
		if (arrowId == null) {
			return;
		}
		HOMING_ARROWS.remove(arrowId);
		FIXED_ARROW_DAMAGE.remove(arrowId);
		MANAGED_MOB_ARROWS.remove(arrowId);
		INVULNERABILITY_BYPASS_ARROWS.remove(arrowId);
	}

	private record PendingZombieReplacement(EntityType<?> replacementType, EntitySpawnReason reason) {}

	private static MinecraftServer resolveServer(Entity entity) {
		if (entity == null || !(entity.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getServer();
	}

	private static LivingEntity resolveDamageSourceLivingAttacker(DamageSource source) {
		if (source == null) {
			return null;
		}
		Entity owner = source.getEntity();
		if (owner instanceof LivingEntity livingOwner) {
			return livingOwner;
		}
		Entity direct = source.getDirectEntity();
		return direct instanceof LivingEntity livingDirect ? livingDirect : null;
	}

	private static boolean hasTrueDamageConfigured(LivingEntity attacker) {
		JsonObject statsRoot = readMobStatsRoot(resolveMobAttackRoot(attacker));
		if (statsRoot.entrySet().isEmpty()) {
			return false;
		}
		JsonElement element = statsRoot.get(MobConfigManager.FIELD_TRUE_DAMAGE);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return false;
		}
		if (element.getAsJsonPrimitive().isBoolean()) {
			return element.getAsBoolean();
		}
		if (!element.getAsJsonPrimitive().isNumber()) {
			return false;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) && value > 0.0D;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static JsonObject resolveMobAttackRoot(LivingEntity attacker) {
		if (attacker == null) {
			return new JsonObject();
		}
		if (attacker.getType() == madoku.craft.entity.MadokuEntityTypes.BEE) {
			return resolveBeeRootForRuntime(attacker);
		}
		if (attacker instanceof ZombieVillager zombieVillager) {
			if (!isMobFileEnabled(MobConfigManager.FILE_ZOMBIE_VILLAGER)) {
				return new JsonObject();
			}
			JsonObject root = resolveZombieVillagerRootForRuntime(zombieVillager.getType());
			JsonObject defaultGroup = readObject(root, MobConfigManager.FIELD_DEFAULT_GROUP);
			return resolveAgeVariantRoot(defaultGroup, zombieVillager.isBaby());
		}
		if (attacker instanceof Drowned drowned) {
			if (!isMobFileEnabled(MobConfigManager.FILE_DROWNED)) {
				return new JsonObject();
			}
			JsonObject root = resolveDrownedRootForRuntime(drowned.getType());
			JsonObject defaultGroup = readObject(root, MobConfigManager.FIELD_DEFAULT_GROUP);
			return resolveAgeVariantRoot(defaultGroup, drowned.isBaby());
		}
		if (attacker instanceof Husk husk) {
			if (!isMobFileEnabled(MobConfigManager.FILE_HUSK)) {
				return new JsonObject();
			}
			JsonObject root = resolveHuskRootForRuntime(husk.getType());
			JsonObject defaultGroup = readObject(root, MobConfigManager.FIELD_DEFAULT_GROUP);
			return resolveAgeVariantRoot(defaultGroup, husk.isBaby());
		}
		if (attacker instanceof Zombie zombie) {
			if (!isMobFileEnabled(MobConfigManager.FILE_ZOMBIE)) {
				return new JsonObject();
			}
			JsonObject root = resolveZombieRootForRuntime(zombie.getType());
			return zombie.isBaby()
				? resolveZombieBabyRootForRuntime(root)
				: resolveZombieAdultRootForRuntime(root);
		}
		if (attacker instanceof Creeper creeper) {
			if (!isMobFileEnabled(MobConfigManager.FILE_CREEPER)) {
				return new JsonObject();
			}
			return resolveCreeperRuntimeVariantRoot(creeper, root(MobConfigManager.FILE_CREEPER));
		}
		if (attacker instanceof AbstractSkeleton skeleton) {
			return resolveSkeletonVariantRuntimeRoot(skeleton);
		}
		if (attacker instanceof Spider spider) {
			String fileKey = spider.getType() == madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER
				? MobConfigManager.FILE_CAVE_SPIDER
				: MobConfigManager.FILE_SPIDER;
			if (!isMobFileEnabled(fileKey)) {
				return new JsonObject();
			}
			JsonObject fileRoot = spider.getType() == madoku.craft.entity.MadokuEntityTypes.CAVE_SPIDER
				? fileMobRoot(MobConfigManager.FILE_CAVE_SPIDER)
				: fileMobRoot(MobConfigManager.FILE_SPIDER);
			return readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		}
		if (attacker.getType() == MadokuEntities.HAG) {
			if (!isMobFileEnabled(MobConfigManager.FILE_HAG)) {
				return new JsonObject();
			}
			return resolveHagDefaultGroup(root(MobConfigManager.FILE_HAG));
		}
		return new JsonObject();
	}

    private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			return InteractionHand.MAIN_HAND;
		}
		if (skeleton.getOffhandItem().is(Items.BOW)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	private static Double resolveUniversalBaseStat(
		Double baseValue,
		Difficulty difficulty,
		boolean hardcore,
		double step,
		double minimum
	) {
		if (baseValue == null) {
			return null;
		}
		return Math.max(minimum, baseValue);
	}

	private static Double resolveScaledStat(
		Double baseValue,
		Difficulty difficulty,
		boolean hardcore,
		double fallbackStep,
		double minimum,
		boolean difficultyScalingEnabled,
		JsonObject difficultyScaleRoot,
		String scalingKey
	) {
		if (baseValue == null) {
			return null;
		}
		if (difficultyScalingEnabled) {
			Double perStep = readOptionalNonNegative(difficultyScaleRoot, scalingKey);
			if (perStep != null) {
				return resolveDifficultyAdjustedPercentValue(difficulty, hardcore, baseValue, perStep, minimum);
			}
		}
		return Math.max(minimum, baseValue);
	}

	private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
		double resolved = Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore)));
		return roundDifficultyScaleValue(baseValue, resolved);
	}

	private static double resolveDifficultyAdjustedPercentValue(Difficulty difficulty, boolean hardcore, double baseValue, double stepRatio, double minimum) {
		double multiplier = 1.0D + (stepRatio * resolveDifficultyTier(difficulty, hardcore));
		double resolved = Math.max(minimum, baseValue * Math.max(0.0D, multiplier));
		return roundDifficultyScaleValue(baseValue, resolved);
	}

	private static double resolveScaledRangedDamage(double base, Difficulty difficulty, boolean hardcore) {
		return resolveDifficultyAdjustedValue(difficulty, hardcore, Math.max(0.0D, base), RANGED_DAMAGE_DIFFICULTY_STEP, 0.0D);
	}

	private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
		if (skeleton == null) {
			return 0.0D;
		}
		JsonObject difficultyScale = readObject(root, MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
		double rangedDamage = resolveScaledRangedDamage(
			readMobStatDouble(root, MobConfigManager.FIELD_RANGED_DAMAGE, 4.0D),
			skeleton.level().getDifficulty(),
			isHardcoreWorld(skeleton.level())
		);
		Double rangedDamageScale = readOptionalNonNegative(difficultyScale, MobConfigManager.FIELD_RANGED_DAMAGE);
		if (rangedDamageScale != null) {
			rangedDamage = resolveDifficultyAdjustedPercentValue(
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level()),
				rangedDamage,
				rangedDamageScale,
				0.0D
			);
		}
		return MobRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, rangedDamage);
	}

	private static double roundDifficultyScaleValue(double originalValue, double value) {
		if (!Double.isFinite(value)) {
			return value;
		}
		double step = isWholeNumber(originalValue) ? 0.05D : 0.005D;
		return Math.round(value / step) * step;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	private static SpawnWeightPair resolveDifficultyShiftedSpawnWeights(
		double regularWeight,
		double specialWeight,
		Difficulty difficulty,
		boolean hardcore,
		double difficultyStep
	) {
		double regular = Math.max(0.0D, regularWeight);
		double special = Math.max(0.0D, specialWeight);
		if (difficulty == null) {
			return new SpawnWeightPair(regular, special);
		}
		double shift = Math.max(0.0D, difficultyStep) * resolveDifficultyTier(difficulty, hardcore);
		if (shift > 0.0D) {
			double transferred = Math.min(shift, regular);
			regular -= transferred;
			special += transferred;
		} else if (shift < 0.0D) {
			double transferred = Math.min(-shift, special);
			special -= transferred;
			regular += transferred;
		}
		return new SpawnWeightPair(regular, special);
	}

	private static double resolveBabyChance(double adultWeight, double babyWeight, Difficulty difficulty, boolean hardcore) {
		SpawnWeightPair shifted = resolveDifficultyShiftedSpawnWeights(adultWeight, babyWeight, difficulty, hardcore, SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP);
		double total = shifted.regularWeight + shifted.specialWeight;
		return total <= 0.0D ? 0.05D : Mth.clamp(shifted.specialWeight / total, 0.0D, 1.0D);
	}

	static double resolveZombieBabyChanceForRuntime(
		double adultWeight,
		double babyWeight,
		DifficultyInstance difficulty,
		Zombie zombie
	) {
		return resolveAgeVariantChanceForRuntime(adultWeight, babyWeight, difficulty, zombie);
	}

	static double resolveAgeVariantChanceForRuntime(
		double adultWeight,
		double babyWeight,
		DifficultyInstance difficulty,
		LivingEntity entity
	) {
		if (difficulty == null || entity == null) {
			return 0.0D;
		}
		return resolveBabyChance(
			adultWeight,
			babyWeight,
			difficulty.getDifficulty(),
			isHardcoreWorld(entity.level())
		);
	}

	private static boolean isHardcoreWorld(Level level) {
		return level != null && level.getServer() != null && level.getServer().isHardcore();
	}

	private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
		Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
		return switch (resolved) {
			case PEACEFUL -> -2;
			case EASY -> -1;
			case NORMAL -> 0;
			case HARD -> hardcore ? 2 : 1;
		};
	}

	private static JsonObject root(String key) {
		return MobConfigManager.getRuntimeMobFiles().getOrDefault(normalizeKey(key), new JsonObject());
	}

	private static boolean isMobFileEnabled(String fileKey) {
		return readBoolean(root(fileKey), MobConfigManager.FIELD_ENABLED, true);
	}

	static boolean isMobFileEnabledForRuntime(String fileKey) {
		return isMobFileEnabled(fileKey);
	}

	static JsonObject resolveMobFileConfigRootForRuntime(String fileKey) {
		return root(fileKey);
	}

	static JsonObject resolveMobFileSectionForRuntime(String fileKey) {
		return fileMobRoot(fileKey);
	}

	private static JsonObject fileMobRoot(String fileKey) {
		return EntityConfigManager.resolveDefaultVariant(root(fileKey));
	}

	private static boolean isSupportedSkeletonRuntimeType(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return false;
		}
		return skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.SKELETON
			|| skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY
			|| skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED
			|| skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED
			|| skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON;
	}

	private static boolean isBowAttackEnabledForRuntimeSkeleton(AbstractSkeleton skeleton) {
		if (skeleton == null || !MobConfigManager.isEnabled()) {
			return false;
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.isBowAttackEnabled(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.isBowAttackEnabled(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.isBowAttackEnabled(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.SkeletonBehavior.isBowAttackEnabled(skeleton);
		}
		return EntityBehaviorsManager.SkeletonBehavior.isBowAttackEnabled(skeleton);
	}

	private static JsonObject resolveSkeletonVariantRuntimeRoot(AbstractSkeleton skeleton) {
		if (skeleton == null || !MobConfigManager.isEnabled()) {
			return new JsonObject();
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			return EntityBehaviorsManager.StrayBehavior.resolveRuntimeRoot(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			return EntityBehaviorsManager.BoggedBehavior.resolveRuntimeRoot(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			return EntityBehaviorsManager.ParchedBehavior.resolveRuntimeRoot(skeleton);
		}
		if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			return EntityBehaviorsManager.WitherSkeletonBehavior.resolveRuntimeRoot(skeleton);
		}
		return EntityBehaviorsManager.SkeletonBehavior.resolveRuntimeRoot(skeleton);
	}

	private static JsonObject zombieRoot(EntityType<?> type) {
		if (type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return fileMobRoot(MobConfigManager.FILE_ZOMBIE);
		}
		return new JsonObject();
	}

	static JsonObject resolveZombieRootForRuntime(EntityType<?> type) {
		return zombieRoot(type);
	}

	private static JsonObject drownedRoot(EntityType<?> type) {
		if (type == madoku.craft.entity.MadokuEntityTypes.DROWNED) {
			return fileMobRoot(MobConfigManager.FILE_DROWNED);
		}
		return new JsonObject();
	}

	static JsonObject resolveDrownedRootForRuntime(EntityType<?> type) {
		return drownedRoot(type);
	}

	private static JsonObject zombieVillagerRoot(EntityType<?> type) {
		if (type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE_VILLAGER) {
			return fileMobRoot(MobConfigManager.FILE_ZOMBIE_VILLAGER);
		}
		return new JsonObject();
	}

	static JsonObject resolveZombieVillagerRootForRuntime(EntityType<?> type) {
		return zombieVillagerRoot(type);
	}

	private static JsonObject huskRoot(EntityType<?> type) {
		if (type == madoku.craft.entity.MadokuEntityTypes.HUSK) {
			return fileMobRoot(MobConfigManager.FILE_HUSK);
		}
		return new JsonObject();
	}

	static JsonObject resolveHuskRootForRuntime(EntityType<?> type) {
		return huskRoot(type);
	}

	public static MobEffectInstance resolveHuskAttackEffect(Husk husk, MobEffectInstance fallbackEffect) {
		if (husk == null || fallbackEffect == null || !MobConfigManager.isEnabled() || !isMobFileEnabled(MobConfigManager.FILE_HUSK)) {
			return fallbackEffect;
		}
		JsonObject huskFileRoot = resolveHuskRootForRuntime(husk.getType());
		JsonObject defaultGroup = readObject(huskFileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		JsonObject variant = resolveAgeVariantRoot(defaultGroup, husk.isBaby());
		if (variant.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		return resolveConfiguredMobEffectInstance(readMobStatsRoot(variant), fallbackEffect);
	}

	private static JsonObject zombieAdultRoot(JsonObject root) {
		JsonObject defaultGroup = readObject(root, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject shared = defaultGroup.deepCopy();
		shared.remove(MobConfigManager.FIELD_ADULT_GROUP);
		shared.remove(MobConfigManager.FIELD_BABY_GROUP);
		JsonObject adultOverride = readObject(defaultGroup, MobConfigManager.FIELD_ADULT_GROUP);
		if (adultOverride.entrySet().isEmpty()) {
			return shared;
		}
		return mergeJsonWithOverride(shared, adultOverride);
	}

	static JsonObject resolveZombieAdultRootForRuntime(JsonObject root) {
		return zombieAdultRoot(root);
	}

	private static JsonObject zombieBabyRoot(JsonObject root) {
		JsonObject defaultGroup = readObject(root, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject shared = defaultGroup.deepCopy();
		shared.remove(MobConfigManager.FIELD_ADULT_GROUP);
		shared.remove(MobConfigManager.FIELD_BABY_GROUP);
		JsonObject babyOverride = readObject(defaultGroup, MobConfigManager.FIELD_BABY_GROUP);
		if (babyOverride.entrySet().isEmpty()) {
			return shared;
		}
		return mergeJsonWithOverride(shared, babyOverride);
	}

	static JsonObject resolveZombieBabyRootForRuntime(JsonObject root) {
		return zombieBabyRoot(root);
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = EntityConfigManager.resolveConfiguredElement(root, key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static JsonObject readMobStatsRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_COMPONENTS);
	}

	private static MobEffectInstance resolveConfiguredMobEffectInstance(JsonObject statsRoot, MobEffectInstance fallbackEffect) {
		if (statsRoot == null || statsRoot.entrySet().isEmpty() || fallbackEffect == null) {
			return fallbackEffect;
		}
		JsonObject mobEffectRoot = readObject(statsRoot, MobConfigManager.FIELD_MOB_EFFECT);
		if (mobEffectRoot.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		String effectId = normalizeKey(readString(mobEffectRoot, MobConfigManager.FIELD_EFFECT, ""));
		if (effectId.isBlank()) {
			return fallbackEffect;
		}
		Identifier effectIdentifier = Identifier.tryParse(effectId);
		if (effectIdentifier == null || !BuiltInRegistries.MOB_EFFECT.containsKey(effectIdentifier)) {
			return fallbackEffect;
		}
		MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.getValue(effectIdentifier);
		if (mobEffect == null) {
			return fallbackEffect;
		}
		Integer durationSeconds = readOptionalIntNonNegative(mobEffectRoot, MobConfigManager.FIELD_DURATION);
		if (durationSeconds == null || durationSeconds <= 0) {
			return fallbackEffect;
		}
		long durationTicks = Math.min(Integer.MAX_VALUE, durationSeconds.longValue() * 20L);
		return new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect), (int) durationTicks);
	}

	private static JsonObject readMobBehaviorRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_BEHAVIORS);
	}

	static JsonObject readMobBehaviorRootForRuntime(JsonObject root) {
		return readMobBehaviorRoot(root);
	}

	private static JsonObject readMobGoalsRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_GOALS);
	}

	static JsonObject readMobGoalsRootForRuntime(JsonObject root) {
		return readMobGoalsRoot(root);
	}

	private static JsonObject readSpawnRulesRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_SPAWN_RULES);
	}

	static JsonObject resolveVariantGroupRoot(JsonObject defaultGroupRoot, JsonObject variantGroupRoot) {
		if (variantGroupRoot == null || variantGroupRoot.entrySet().isEmpty()) {
			return defaultGroupRoot == null ? new JsonObject() : defaultGroupRoot;
		}
		return mergeJsonWithOverride(defaultGroupRoot, variantGroupRoot);
	}

	static String resolveRuntimeMobFileKey(LivingEntity entity) {
		return resolveRegionalDifficultyMobFileKey(entity);
	}

	static JsonObject resolveConfiguredEntityVariantForRuntime(LivingEntity entity) {
		String fileKey = resolveRegionalDifficultyMobFileKey(entity);
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) return new JsonObject();
		return EntityConfigManager.resolveDefaultVariant(root(fileKey));
	}

	static JsonObject resolveAgeVariantRoot(JsonObject variantGroupRoot, boolean baby) {
		if (variantGroupRoot == null || variantGroupRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject sharedRoot = variantGroupRoot.deepCopy();
		sharedRoot.remove(MobConfigManager.FIELD_ADULT_GROUP);
		sharedRoot.remove(MobConfigManager.FIELD_BABY_GROUP);
		JsonObject ageOverride = readObject(
			variantGroupRoot,
			baby ? MobConfigManager.FIELD_BABY_GROUP : MobConfigManager.FIELD_ADULT_GROUP
		);
		if (ageOverride.entrySet().isEmpty()) {
			return sharedRoot;
		}
		return mergeJsonWithOverride(sharedRoot, ageOverride);
	}

	static Map<String, JsonObject> collectVariantRoots(JsonObject root, Predicate<String> reservedKeyPredicate) {
		Map<String, JsonObject> variants = new LinkedHashMap<>();
		if (root == null) {
			return variants;
		}
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			String key = normalizeKey(entry.getKey());
			if (!EntityConfigManager.isVariantKey(key)) {
				continue;
			}
			if (reservedKeyPredicate != null && reservedKeyPredicate.test(key)) {
				continue;
			}
			variants.putIfAbsent(key, entry.getValue().getAsJsonObject());
		}
		return variants;
	}

	static JsonObject resolveVariantRootByKey(JsonObject root, String variantKey, String defaultGroupKey, Predicate<String> reservedKeyPredicate) {
		if (root == null || variantKey == null || variantKey.isBlank()) {
			return new JsonObject();
		}
		if (defaultGroupKey != null && normalizeKey(variantKey).equals(normalizeKey(defaultGroupKey))) {
			return readObject(root, defaultGroupKey);
		}
		JsonObject variant = collectVariantRoots(root, reservedKeyPredicate).get(normalizeKey(variantKey));
		return variant == null ? new JsonObject() : variant;
	}

	static String selectWeightedVariantKey(
		JsonObject root,
		RandomSource random,
		String defaultGroupKey,
		String defaultVariantKey,
		Predicate<String> reservedKeyPredicate,
		ToDoubleFunction<JsonObject> weightResolver
	) {
		if (root == null || random == null || defaultVariantKey == null || defaultVariantKey.isBlank()) {
			return defaultVariantKey == null ? "" : defaultVariantKey;
		}
		JsonObject defaultGroup = readObject(root, defaultGroupKey);
		double defaultWeight = Math.max(0.0D, weightResolver == null ? 0.0D : weightResolver.applyAsDouble(defaultGroup));
		double total = defaultWeight;
		java.util.List<WeightedVariant> weightedVariants = new ArrayList<>();
		for (Map.Entry<String, JsonObject> entry : collectVariantRoots(root, reservedKeyPredicate).entrySet()) {
			double weight = Math.max(0.0D, weightResolver == null ? 0.0D : weightResolver.applyAsDouble(entry.getValue()));
			if (weight <= 0.0D) {
				continue;
			}
			total += weight;
			weightedVariants.add(new WeightedVariant(entry.getKey(), weight));
		}
		if (total <= 0.0D) {
			return defaultVariantKey;
		}
		double roll = random.nextDouble() * total;
		if (roll < defaultWeight) {
			return defaultVariantKey;
		}
		double cursor = defaultWeight;
		for (WeightedVariant variant : weightedVariants) {
			cursor += variant.weight();
			if (roll < cursor) {
				return variant.key();
			}
		}
		return defaultVariantKey;
	}

	static double resolveVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		if (variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return fallback;
		}
		JsonObject spawnRules = readSpawnRulesRoot(variantRoot);
		Double nested = readOptionalDouble(spawnRules, MobConfigManager.FIELD_SPAWN_WEIGHT);
		if (nested != null) {
			return nested;
		}
		Double direct = readOptionalDouble(variantRoot, MobConfigManager.FIELD_SPAWN_WEIGHT);
		if (direct != null) {
			return direct;
		}
		JsonObject adult = readObject(variantRoot, MobConfigManager.FIELD_ADULT_GROUP);
		JsonObject baby = readObject(variantRoot, MobConfigManager.FIELD_BABY_GROUP);
		double adultWeight = Math.max(0.0D, readSpawnRuleDouble(adult, MobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D));
		double babyWeight = Math.max(0.0D, readSpawnRuleDouble(baby, MobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D));
		double summed = adultWeight + babyWeight;
		return summed > 0.0D ? summed : fallback;
	}

	private static JsonObject resolveBeeRoot(LivingEntity entity, JsonObject beeFileRoot, RandomSource random, boolean spawnContext) {
		if (entity == null || beeFileRoot == null) {
			return new JsonObject();
		}
		JsonObject beeRoot = fileMobRoot(MobConfigManager.FILE_BEE);
		if (beeRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject baseVariantRoot = resolveBeeVariantRoot(entity, beeFileRoot, beeRoot, random, spawnContext);
		JsonObject resolvedAgeRoot = resolveBeeAgeRoot(entity, beeFileRoot, baseVariantRoot, random, spawnContext);
		if (resolvedAgeRoot.entrySet().isEmpty()) {
			return mergeBeeMobSettings(beeRoot, baseVariantRoot);
		}
		return mergeBeeMobSettings(beeRoot, resolvedAgeRoot);
	}

	private static JsonObject resolveBeeVariantRoot(
		LivingEntity entity,
		JsonObject beeFileRoot,
		JsonObject beeRoot,
		RandomSource random,
		boolean spawnContext
	) {
		JsonObject defaultGroup = readObject(beeRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return beeRoot;
		}
		boolean overrideSpawnRules = readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean variantEnabled = readBoolean(beeFileRoot, MobConfigManager.FIELD_MOB_VARIANT, true);
		if (!variantEnabled) {
			clearBeeVariantTag(entity);
			return defaultGroup;
		}

		String storedVariant = readStoredBeeVariantKey(entity);
		if (!storedVariant.isBlank()) {
			if (BEE_VARIANT_DEFAULT_KEY.equals(storedVariant)) {
				return defaultGroup;
			}
			JsonObject knownVariant = resolveBeeVariantRootByKey(beeRoot, storedVariant);
			if (!knownVariant.entrySet().isEmpty()) {
				return knownVariant;
			}
		}
		if (!overrideSpawnRules) {
			return defaultGroup;
		}

		String selectedVariant = selectBeeVariantKey(beeRoot, random);
		if (selectedVariant.isBlank()) {
			selectedVariant = BEE_VARIANT_DEFAULT_KEY;
		}
		writeBeeVariantTag(entity, selectedVariant);
		if (BEE_VARIANT_DEFAULT_KEY.equals(selectedVariant)) {
			return defaultGroup;
		}
		JsonObject selected = resolveBeeVariantRootByKey(beeRoot, selectedVariant);
		if (!selected.entrySet().isEmpty()) {
			return selected;
		}
		if (!spawnContext) {
			clearBeeVariantTag(entity);
		}
		return defaultGroup;
	}

	private static JsonObject resolveBeeAgeRoot(
		LivingEntity entity,
		JsonObject beeFileRoot,
		JsonObject baseVariantRoot,
		RandomSource random,
		boolean spawnContext
	) {
		if (baseVariantRoot == null || baseVariantRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject adult = readObject(baseVariantRoot, MobConfigManager.FIELD_ADULT_GROUP);
		JsonObject baby = readObject(baseVariantRoot, MobConfigManager.FIELD_BABY_GROUP);
		if (adult.entrySet().isEmpty() && baby.entrySet().isEmpty()) {
			return baseVariantRoot;
		}
		boolean babyEnabled = readBoolean(beeFileRoot, MobConfigManager.FIELD_MOB_BABY, true);
		boolean overrideSpawnRules = readBoolean(beeFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean isBaby = entity instanceof AgeableMob ageableMob && ageableMob.isBaby();
		if (entity instanceof AgeableMob ageableMob && spawnContext && babyEnabled && overrideSpawnRules && random != null) {
			double adultWeight = Math.max(0.0D, readSpawnRuleDouble(adult, MobConfigManager.FIELD_SPAWN_WEIGHT, 100.0D));
			double babyWeight = Math.max(0.0D, readSpawnRuleDouble(baby, MobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D));
			double total = adultWeight + babyWeight;
			if (total > 0.0D) {
				isBaby = (random.nextDouble() * total) >= adultWeight;
				ageableMob.setBaby(isBaby);
			}
		}
		return resolveAgeVariantRoot(baseVariantRoot, isBaby);
	}

	private static String selectBeeVariantKey(JsonObject beeRoot, RandomSource random) {
		return selectWeightedVariantKey(
			beeRoot,
			random,
			MobConfigManager.FIELD_DEFAULT_GROUP,
			BEE_VARIANT_DEFAULT_KEY,
			MobEntityManager::isReservedBeeGroupKey,
			variantRoot -> resolveBeeVariantSpawnWeight(variantRoot, 0.0D)
		);
	}

	private static JsonObject resolveBeeVariantRootByKey(JsonObject beeRoot, String variantKey) {
		return resolveVariantRootByKey(beeRoot, variantKey, MobConfigManager.FIELD_DEFAULT_GROUP, MobEntityManager::isReservedBeeGroupKey);
	}

	private static boolean isReservedBeeGroupKey(String normalizedKey) {
		if (normalizedKey == null || normalizedKey.isBlank()) {
			return true;
		}
		return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW));
	}

	private static double resolveBeeVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		return resolveVariantSpawnWeight(variantRoot, fallback);
	}

	private static String readStoredBeeVariantKey(LivingEntity entity) {
		if (entity == null) {
			return "";
		}
		for (String tag : entity.entityTags()) {
			if (tag == null || !tag.startsWith(BEE_VARIANT_TAG_PREFIX)) {
				continue;
			}
			String raw = tag.substring(BEE_VARIANT_TAG_PREFIX.length());
			String normalized = normalizeKey(raw);
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private static void writeBeeVariantTag(LivingEntity entity, String variantKey) {
		if (entity == null || variantKey == null || variantKey.isBlank()) {
			return;
		}
		clearBeeVariantTag(entity);
		entity.addTag(BEE_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
	}

	private static void clearBeeVariantTag(LivingEntity entity) {
		if (entity == null) {
			return;
		}
		String existing = null;
		for (String tag : entity.entityTags()) {
			if (tag != null && tag.startsWith(BEE_VARIANT_TAG_PREFIX)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			entity.removeTag(existing);
		}
	}

	private static JsonObject mergeBeeMobSettings(JsonObject beeRoot, JsonObject groupRoot) {
		if (groupRoot == null) {
			return new JsonObject();
		}
		JsonObject merged = groupRoot.deepCopy();
		if (beeRoot != null) {
			if (!merged.has(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING) && beeRoot.has(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING)) {
				merged.add(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING, beeRoot.get(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING).deepCopy());
			}
			if (!merged.has(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING) && beeRoot.has(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING)) {
				merged.add(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING, beeRoot.get(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING).deepCopy());
			}
			if (!merged.has(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW) && beeRoot.has(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW)) {
				merged.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW, beeRoot.get(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW).deepCopy());
			}
		}
		return merged;
	}

	private static JsonObject mergeJsonWithOverride(JsonObject base, JsonObject override) {
		JsonObject merged = base == null ? new JsonObject() : base.deepCopy();
		if (override == null) {
			return merged;
		}
		deepMergeOverride(merged, override);
		return merged;
	}

	private static void deepMergeOverride(JsonObject target, JsonObject override) {
		if (target == null || override == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value != null
				&& value.isJsonObject()
				&& target.has(key)
				&& target.get(key).isJsonObject()) {
				deepMergeOverride(target.getAsJsonObject(key), value.getAsJsonObject());
				continue;
			}
			target.add(key, value == null ? com.google.gson.JsonNull.INSTANCE : value.deepCopy());
		}
	}

	private static double readMobStatDouble(JsonObject root, String key, double fallback) {
		return readDouble(readMobStatsRoot(root), key, fallback);
	}

	private static double readSpawnRuleDouble(JsonObject root, String key, double fallback) {
		return readDouble(readSpawnRulesRoot(root), key, fallback);
	}

	static double readSpawnRuleDoubleForRuntime(JsonObject root, String key, double fallback) {
		return readSpawnRuleDouble(root, key, fallback);
	}

	private static boolean readMobBehaviorBoolean(JsonObject root, String key, boolean fallback) {
		return readBoolean(readMobBehaviorRoot(root), key, fallback);
	}

	static boolean readMobBehaviorBooleanForRuntime(JsonObject root, String key, boolean fallback) {
		return readMobBehaviorBoolean(root, key, fallback);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static Double readOptionalDouble(JsonObject root, String key) {
		if (root == null) {
			return null;
		}
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Double readOptionalPositive(JsonObject root, String key) {
		Double value = readOptionalDouble(root, key);
		return value != null && value > 0.0D ? value : null;
	}

	private static Double readOptionalNonNegative(JsonObject root, String key) {
		Double value = readOptionalDouble(root, key);
		if (value == null && MobConfigManager.FIELD_MOVEMENT_SPEED.equals(key)) {
			value = readOptionalDouble(root, "movement_speed");
		}
		if (value == null && MobConfigManager.FIELD_EXPERIENCE_DROP.equals(key)) {
			value = readOptionalDouble(root, "experience_drop");
		}
		return value != null && value >= 0.0D ? value : null;
	}

	private static Integer readOptionalIntNonNegative(JsonObject root, String key) {
		if (root == null) {
			return null;
		}
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			int value = element.getAsInt();
			return value >= 0 ? value : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Double clampOptional(Double value, double min, double max) {
		if (value == null || !Double.isFinite(value)) {
			return null;
		}
		return Mth.clamp(value, min, max);
	}

	private static Entity spawnConfiguredJockeyPartner(
		Mob sourceMob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		ServerLevel level,
		EntitySpawnReason spawnReason,
		EntityType<?> partnerType,
		boolean partnerIsPassenger,
		JsonObject partnerRoot
	) {
		if (sourceMob == null || world == null || difficulty == null || level == null || partnerType == null) {
			return null;
		}

		Entity partner = partnerType.create(level, spawnReason);
		if (partner == null) {
			return null;
		}

		partner.setPos(sourceMob.getX(), sourceMob.getY(), sourceMob.getZ());
		partner.setYRot(sourceMob.getYRot());
		partner.setXRot(sourceMob.getXRot());
		if (partner instanceof Mob mobPartner) {
			mobPartner.finalizeSpawn(world, difficulty, spawnReason, null);
			if (partnerIsPassenger) {
				applyConfiguredMainHand(mobPartner, partnerRoot);
			}
		}
		level.tryAddFreshEntityWithPassengers(partner);
		return partner;
	}

	private static void applyConfiguredMainHand(Mob mob, JsonObject mobRoot) {
		if (mob == null || mobRoot == null || mobRoot.entrySet().isEmpty()) {
			return;
		}
		String itemId = readString(mobRoot, MobConfigManager.FIELD_MAIN_HAND, "");
		if (itemId.isBlank()) {
			return;
		}
		ItemStack stack = resolveItemStackById(itemId);
		if (!stack.isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
		}
	}

	private static boolean applyConfiguredMobWeapon(LivingEntity entity, JsonObject root) {
		if (!(entity instanceof Mob mob) || root == null || root.entrySet().isEmpty()) {
			return false;
		}
		JsonObject statsRoot = readMobStatsRoot(root);
		JsonObject weaponRoot = readObject(statsRoot, MobConfigManager.FIELD_MOB_WEAPON);
		if (weaponRoot.entrySet().isEmpty()) {
			return false;
		}
		String itemId = readString(weaponRoot, MobConfigManager.FIELD_ITEM, "");
		String normalizedItemId = normalizeKey(itemId);
		if (normalizedItemId.isBlank()) {
			return false;
		}
		if ("hand".equals(normalizedItemId)) {
			return true;
		}
		if ("empty".equals(normalizedItemId)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return true;
		}
		ItemStack stack = resolveItemStackById(itemId);
		if (stack.isEmpty()) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
		if (!readBoolean(root, MobConfigManager.FIELD_WEAPON_DAMAGE, true)) {
			stripHeldAttackDamageModifiers(mob, EquipmentSlot.MAINHAND);
			stripHeldAttackDamageModifiers(mob, EquipmentSlot.OFFHAND);
		}
		return true;
	}

	private static void stripHeldAttackDamageModifiers(Mob mob, EquipmentSlot slot) {
		if (mob == null || slot == null) {
			return;
		}
		ItemStack stack = mob.getItemBySlot(slot);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack normalized = stack.copy();
		normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		mob.setItemSlot(slot, normalized);
	}

	private static EntityType<?> resolveEntityTypeById(String entityTypeId) {
		if (entityTypeId == null || entityTypeId.isBlank()) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(entityTypeId.trim());
		if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
	}

	private static ItemStack resolveItemStackById(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return ItemStack.EMPTY;
		}
		Identifier identifier = Identifier.tryParse(itemId.trim());
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return ItemStack.EMPTY;
		}
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(identifier);
		if (item == null || item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(item);
	}

	static String resolveItemIdForRuntime(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getItem() == null) {
			return "empty";
		}
		Identifier identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return identifier == null ? "unknown" : identifier.toString();
	}

	private record HomingArrowState(UUID targetUuid, double speed, int remainingTicks) {}
	private record SpawnWeightPair(double regularWeight, double specialWeight) {}
	private record WeightedVariant(String key, double weight) {}

}








