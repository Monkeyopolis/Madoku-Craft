package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.difficulty.system.DifficultyScaledMob;
import madoku.craft.difficulty.system.MadokuRegionalDifficultyManager;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.luck.MadokuLuck;
import madoku.craft.mixin.AbstractSkeletonArrowInvoker;
import madoku.craft.mixin.CreeperAccessor;
import madoku.craft.mixin.CreeperPoweredAccessor;
import madoku.craft.mixin.MobExperienceAccessor;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuMobManager {
	private static final String MOB_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-mobs";
	private static final String MOB_CONFIG_SETTINGS_FILE_NAME = "madoku-mobs";
	private static final String MOB_CONFIG_MOBS_FOLDER_NAME = "madoku-mobs";
	private static final String TASK_TYPE_MOB_RUNTIME_TICK = "mob_runtime_tick";
	private static final String MOB_SCHEDULER_OWNER_ID = "madoku-mob-runtime";
	private static final double HEALTH_DIFFICULTY_STEP = 4.0D;
	private static final double MOVEMENT_SPEED_DIFFICULTY_STEP = 0.01D;
	private static final double DAMAGE_DIFFICULTY_STEP = 1.0D;
	private static final double ARMOR_DIFFICULTY_STEP = 0.5D;
	private static final double KNOCKBACK_RESISTANCE_DIFFICULTY_STEP = 0.1D;
	private static final double SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP = 2.0D;
	private static final double RANGED_DAMAGE_DIFFICULTY_STEP = 1.0D;
	private static final double ATTACK_ACCURACY_DIFFICULTY_STEP = 0.05D;
	private static final double CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP = 1.0D;
	private static final double CREEPER_POWER_PER_DAMAGE = 0.2D;
	private static final double ZOMBIFIED_PIGLIN_PLAYER_AGGRO_RANGE = 8.0D;
	private static final double ZOMBIFIED_PIGLIN_PIGLIN_AGGRO_RANGE = 4.0D;
	private static final double ZOMBIFIED_PIGLIN_ANGER_BROADCAST_RANGE = 8.0D;
	private static final double PIGLIN_ANGER_BROADCAST_RANGE = 8.0D;
	private static final double MIN_HOMING_SPEED = 0.75D;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final int MOB_ARROW_LIFETIME_TICKS = 15 * 20;
	private static final int WITHER_EFFECT_DURATION_TICKS = 5 * 20;
	private static final String HOMING_PROJECTILE_TAG = "madoku-craft.projectile.homing";
	private static final String BEE_VARIANT_TAG_PREFIX = "madoku-craft.bee.variant:";
	private static final String BEE_VARIANT_DEFAULT_KEY = "default";
	private static final String FIELD_FLYING_SPEED = "flying_speed";
	private static final String METRIC_BEE_OVERRIDE = "mob.bee_override";
	private static final String METRIC_BEE_RESOLVED_ROOT = "mob.bee_resolved_root";
	private static final String METRIC_BEE_SCALE_APPLY = "mob.bee_scale_apply";

	private static final Map<UUID, HomingArrowState> HOMING_ARROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FIXED_ARROW_DAMAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MANAGED_MOB_ARROWS = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> INVULNERABILITY_BYPASS_ARROWS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Integer> PILLAGER_ATTACK_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<UUID, EntitySpawnReason> PENDING_CAVE_SPIDER_REPLACEMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Entity> TRACKED_ENTITIES = new ConcurrentHashMap<>();

	private static volatile Snapshot snapshot = Snapshot.disabled();
	private static volatile String runtimeSchedulerId = "";
	private static volatile boolean runtimeTaskScheduled = false;

	private MadokuMobManager() {
	}

	public static void initialize() {
		loadConfig();
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_MOB_RUNTIME_TICK, MadokuMobManager::runRuntimeTask);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			TRACKED_ENTITIES.put(entity.getUUID(), entity);
			if (entity instanceof LivingEntity livingEntity) {
				preapplyBeeRegionalDifficultyAdjustment(livingEntity, world);
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
		PILLAGER_ATTACK_COOLDOWNS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		MadokuMobBee.resetRuntimeState();
		runtimeSchedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		SchedulerManagerSystem.clearQueuedRequests(runtimeSchedulerId);
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
		PILLAGER_ATTACK_COOLDOWNS.clear();
		PENDING_CAVE_SPIDER_REPLACEMENTS.clear();
		TRACKED_ENTITIES.clear();
		MadokuMobBee.resetRuntimeState();
	}

	public static boolean isEnabled() {
		return snapshot.enabled;
	}

	public static boolean shouldReplaceVanillaZombifiedPiglinBroadcast() {
		return snapshot.enabled && isMobFileEnabled(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN);
	}

	public static boolean shouldReplaceVanillaPiglinZombifiedAvoid() {
		return snapshot.enabled && isMobFileEnabled(MadokuMobConfigManager.FILE_PIGLIN);
	}

	public static void applyCustomZombifiedPiglinAggressionTick(Zombie pigman) {
		if (pigman == null || !snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN) || !pigman.isAlive()) {
			return;
		}
		enforceZombifiedPiglinWeaponLoadout(pigman);
		if (pigman.isBaby()) {
			pigman.setTarget(null);
			pigman.setAggressive(false);
			return;
		}
		if (!(pigman.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity target = resolveZombifiedPiglinTarget(level, pigman);
		if (target != null && target.isAlive() && target != pigman) {
			pigman.setTarget(target);
		}
	}

	public static void applyMobSpawnOverridesFromGenericMixin(
		Mob mob,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
		) {
			if (mob instanceof Creeper creeper) {
				MadokuMobCreeper.applySpawnOverrides(creeper, world, difficulty);
		} else if (mob.getType() == EntityType.ZOMBIFIED_PIGLIN && mob instanceof Zombie zombifiedPiglin) {
			MadokuMobZombifiedPiglin.applySpawnOverrides(zombifiedPiglin, world);
		} else if (mob instanceof Piglin piglin) {
			MadokuMobPiglin.applySpawnOverrides(piglin, world);
		} else if (mob.getType() == EntityType.BEE) {
			MadokuMobBee.applySpawnOverrides(mob, world);
		} else if (mob.getType() == MadokuEntities.HAG) {
			MadokuMobHag.applySpawnOverrides(mob);
		}
	}

	public static void applyZombieSpawnOverrides(
		Zombie zombie,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (zombie == null || world == null || difficulty == null || !snapshot.enabled) {
			return;
		}
		String fileKey = zombie.getType() == EntityType.ZOMBIE ? MadokuMobConfigManager.FILE_ZOMBIE
			: zombie.getType() == EntityType.HUSK ? MadokuMobConfigManager.FILE_HUSK
			: zombie.getType() == EntityType.DROWNED ? MadokuMobConfigManager.FILE_DROWNED
			: zombie.getType() == EntityType.ZOMBIE_VILLAGER ? MadokuMobConfigManager.FILE_ZOMBIE_VILLAGER
			: "";
		JsonObject root = zombieRoot(zombie.getType());
		if (fileKey.isBlank() || !isMobFileEnabled(fileKey)) {
			return;
		}

		JsonObject adult = zombieAdultRoot(zombie.getType(), root);
		JsonObject baby = zombieBabyRoot(zombie.getType(), root);
		double babyChance = resolveBabyChance(
			readSpawnRuleDouble(adult, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 95.0D),
			readSpawnRuleDouble(baby, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(zombie.level())
		);
		boolean shouldBeBaby = world.getRandom().nextFloat() < babyChance;
		zombie.setBaby(shouldBeBaby);
		if (!shouldBeBaby && zombie.getType() == EntityType.ZOMBIE && zombie.getVehicle() != null && zombie.getVehicle().getType() == EntityType.CHICKEN) {
			zombie.stopRiding();
		}
		JsonObject variant = zombie.isBaby() ? baby : adult;

		clearMobEquipment(zombie);
		disableZombieReinforcements(zombie);
		applySpawnArmorLoadout(zombie, variant, world.getRandom());
		applyUniversalStats(zombie, variant);
		zombie.setCanBreakDoors(readMobBehaviorBoolean(variant, MadokuMobConfigManager.FIELD_CAN_BREAK_DOORS, false));
		zombie.setCanPickUpLoot(readMobBehaviorBoolean(variant, MadokuMobConfigManager.FIELD_CAN_PICK_UP_LOOT, false));
	}

	public static void applySpiderSpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (spider == null || world == null || difficulty == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_SPIDER);
		if (spider.getType() != EntityType.SPIDER || spawnReason == EntitySpawnReason.JOCKEY || !isMobFileEnabled(MadokuMobConfigManager.FILE_SPIDER)) {
			return;
		}

		clearExistingSkeletonPassengers(spider);
		SpawnWeightPair caveShifted = resolveDifficultyShiftedSpawnWeights(
			readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_SPIDER_SPAWN_WEIGHT, 90.0D),
			readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_CAVE_SPIDER_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(spider.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		SpawnWeightPair jockeyShifted = resolveDifficultyShiftedSpawnWeights(
			caveShifted.regularWeight,
			readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(spider.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		SpawnOutcome outcome = rollSpiderSpawnOutcome(world.getRandom(), jockeyShifted.regularWeight, caveShifted.specialWeight, jockeyShifted.specialWeight);
		if (outcome == SpawnOutcome.CAVE_SPIDER) {
			PENDING_CAVE_SPIDER_REPLACEMENTS.put(spider.getUUID(), spawnReason);
			requestRuntimeProcessing(world.getLevel().getServer(), 1L);
			return;
		}
		if (outcome == SpawnOutcome.SPIDER_JOCKEY) {
			spawnSpiderJockey(spider, world, difficulty);
		}
	}

	public static void applySkeletonSpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (skeleton == null || world == null || difficulty == null || !snapshot.enabled) {
			return;
		}
		if (skeleton.getType() == EntityType.WITHER_SKELETON) {
			applyWitherSkeletonSpawnOverrides(skeleton, world);
			return;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		if (skeleton.getType() != EntityType.SKELETON
			&& skeleton.getType() != EntityType.STRAY
			&& skeleton.getType() != EntityType.BOGGED
			&& skeleton.getType() != EntityType.PARCHED) {
			return;
		}
		String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
			: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
			: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
			: MadokuMobConfigManager.FILE_PARCHED;
		if (!isMobFileEnabled(skeletonFileKey)) {
			return;
		}

		double withBow = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_WITH_BOW_SPAWN_WEIGHT, 95.0D));
		double withoutBow = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0D));
		if (withBow + withoutBow > 0.0D) {
			boolean spawnWithBow = (world.getRandom().nextDouble() * (withBow + withoutBow)) < withBow;
			if (spawnWithBow) {
				ensureBowEquipped(skeleton);
			} else {
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}

		if (spawnReason == EntitySpawnReason.JOCKEY) {
			ensureBowEquipped(skeleton);
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}

		SpawnWeightPair jockeyWeights = resolveDifficultyShiftedSpawnWeights(
			readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_REGULAR_SPAWN_WEIGHT, 95.0D),
			readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0D),
			difficulty.getDifficulty(),
			isHardcoreWorld(skeleton.level()),
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		if (jockeyWeights.regularWeight + jockeyWeights.specialWeight <= 0.0D || skeleton.getVehicle() != null) {
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}
		boolean spawnJockey = (world.getRandom().nextDouble() * (jockeyWeights.regularWeight + jockeyWeights.specialWeight))
			< jockeyWeights.specialWeight;
		if (!spawnJockey) {
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}

		ServerLevel level = world.getLevel();
		Spider spider = EntityType.SPIDER.create(level, EntitySpawnReason.JOCKEY);
		if (spider == null) {
			applySpawnArmorLoadout(skeleton, root, world.getRandom());
			return;
		}
		spider.setPos(skeleton.getX(), skeleton.getY(), skeleton.getZ());
		spider.setYRot(skeleton.getYRot());
		spider.setXRot(skeleton.getXRot());
		spider.finalizeSpawn(world, difficulty, EntitySpawnReason.JOCKEY, null);
		level.tryAddFreshEntityWithPassengers(spider);
		skeleton.startRiding(spider);
		ensureBowEquipped(skeleton);
		applySpawnArmorLoadout(skeleton, root, world.getRandom());
	}

	public static boolean applyCustomSkeletonRangedAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		if (skeleton == null || target == null || !target.isAlive() || !snapshot.enabled) {
			return false;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
			: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
			: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
			: skeleton.getType() == EntityType.PARCHED ? MadokuMobConfigManager.FILE_PARCHED
			: skeleton.getType() == EntityType.WITHER_SKELETON ? MadokuMobConfigManager.FILE_WITHER_SKELETON
			: "";
		if (skeletonFileKey.isBlank() || !isMobFileEnabled(skeletonFileKey)) {
			return false;
		}
		InteractionHand bowHand = resolveBowHand(skeleton);
		if (bowHand == null) {
			return false;
		}
		ItemStack bowStack = skeleton.getItemInHand(bowHand);
		ItemStack projectileStack = skeleton.getProjectile(bowStack);
		if (projectileStack.isEmpty()) {
			projectileStack = new ItemStack(Items.ARROW);
		}
		AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, pullProgress, bowStack);
		if (arrow == null) {
			return false;
		}

		double accuracy = resolveScaledAttackAccuracy(readMobStatDouble(root, MadokuMobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D), skeleton.level().getDifficulty(), isHardcoreWorld(skeleton.level()));
		double rangedDamage = resolveSkeletonRangedDamage(skeleton, root);
		accuracy = MadokuRegionalDifficultyManager.resolveMobAttackAccuracyScaling(skeleton, accuracy);
		ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
		arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
			arrow.setCritArrow(false);
			if (skeleton.getType() == EntityType.WITHER_SKELETON) {
				arrow.setRemainingFireTicks(0);
			}
			FIXED_ARROW_DAMAGE.put(arrow.getUUID(), (float) Math.max(0.0D, rangedDamage));
		trackManagedMobArrow(arrow, resolveServer(skeleton));
		if (shot.guaranteedHit) {
			double speed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
			arrow.setNoGravity(true);
			HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), speed, HOMING_LIFETIME_TICKS));
			requestRuntimeProcessing(resolveServer(skeleton), 1L);
		}
		skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		skeleton.level().addFreshEntity(arrow);
		return true;
	}

	public static int resolveSkeletonRangedAttackIntervalTicks(AbstractSkeleton skeleton) {
		if (skeleton == null || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
			: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
			: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
			: skeleton.getType() == EntityType.PARCHED ? MadokuMobConfigManager.FILE_PARCHED
			: skeleton.getType() == EntityType.WITHER_SKELETON ? MadokuMobConfigManager.FILE_WITHER_SKELETON
			: "";
		if (skeletonFileKey.isBlank() || !isMobFileEnabled(skeletonFileKey)) {
			return -1;
		}
		double interval = readMobStatDouble(root, MadokuMobConfigManager.FIELD_ATTACK_INTERVAL, 20.0D);
		return Math.max(1, (int) Math.round(interval));
	}

	public static int resolveSkeletonChargeUpTicks(Monster attacker) {
		if (!(attacker instanceof AbstractSkeleton skeleton) || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
			: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
			: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
			: skeleton.getType() == EntityType.PARCHED ? MadokuMobConfigManager.FILE_PARCHED
			: skeleton.getType() == EntityType.WITHER_SKELETON ? MadokuMobConfigManager.FILE_WITHER_SKELETON
			: "";
		if (skeletonFileKey.isBlank() || !isMobFileEnabled(skeletonFileKey)) {
			return -1;
		}
		double charge = readMobStatDouble(root, MadokuMobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0D);
		return Math.max(1, (int) Math.round(charge));
	}

	public static boolean applyCustomPillagerRangedShot(Pillager pillager, LivingEntity target, float speed) {
		if (pillager == null || target == null || !target.isAlive() || !snapshot.enabled) {
			return false;
		}
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_PILLAGER)) {
			return false;
		}
		InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(pillager, Items.CROSSBOW);
		ItemStack stack = pillager.getItemInHand(hand);
		if (!(stack.getItem() instanceof CrossbowItem crossbowItem)) {
			return false;
		}
		float spread = 14.0F - (pillager.level().getDifficulty().getId() * 4.0F);
			crossbowItem.performShooting(pillager.level(), pillager, hand, stack, speed, spread, target);
				pillager.onCrossbowAttackPerformed();
				markCrossbowAttackCooldown(pillager);
				return true;
			}

	public static void applyWitherSkeletonArrowHitEffect(LivingEntity target, Entity attacker) {
		applyWitherSkeletonHitEffect(target, attacker, WITHER_EFFECT_DURATION_TICKS);
	}

	public static void handleMobDamaged(LivingEntity victim, DamageSource source) {
		if (victim == null || source == null || !snapshot.enabled || victim.level().isClientSide()) {
			return;
		}
		LivingEntity attacker = resolveDamageSourceLivingAttacker(source);
		if (attacker == null || !attacker.isAlive() || attacker == victim) {
			return;
		}
		if (!(victim.level() instanceof ServerLevel level)) {
			return;
		}

		if (victim.getType() == EntityType.ZOMBIFIED_PIGLIN && victim instanceof Zombie pigman) {
			if (!isMobFileEnabled(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN) || pigman.isBaby()) {
				return;
			}
			pigman.setTarget(attacker);
			pigman.setAggressive(true);
			broadcastZombifiedPiglinAnger(level, pigman, attacker);
			return;
		}
		if (victim instanceof Piglin piglin) {
			if (!isMobFileEnabled(MadokuMobConfigManager.FILE_PIGLIN) || piglin.isBaby()) {
				return;
			}
			piglin.setTarget(attacker);
			piglin.setAggressive(true);
			piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
			clearPiglinFleeMemories(piglin);
			if (attacker.getType() == EntityType.ZOMBIFIED_PIGLIN) {
				broadcastPiglinAnger(level, piglin, attacker);
			}
		}
	}

	private static void applyWitherSkeletonHitEffect(LivingEntity target, Entity attacker, int durationTicks) {
		if (target == null || attacker == null || target.level().isClientSide() || !snapshot.enabled) {
			return;
		}
		if (!(attacker instanceof AbstractSkeleton skeleton) || skeleton.getType() != EntityType.WITHER_SKELETON) {
			return;
		}
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_WITHER_SKELETON)) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, durationTicks), skeleton);
	}

	static void applyPiglinSpawnOverrides(Piglin piglin, ServerLevelAccessor world) {
		if (piglin == null || world == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_PIGLIN);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_PIGLIN)) {
			return;
		}
			boolean baby = rollPiglinBabySpawn(root, world);
			piglin.setBaby(baby);
			JsonObject variant = piglinVariantRoot(root, baby);
			clearMobEquipment(piglin);
			applySpawnArmorLoadout(piglin, variant, world.getRandom());
			if (!baby) {
				equipPiglinWeapon(piglin, piglinAdultRoot(root), world.getRandom());
				normalizePiglinWeapon(piglin);
			}
			applyUniversalStats(piglin, variant);
	}

	static void applyZombifiedPiglinSpawnOverrides(Zombie pigman, ServerLevelAccessor world) {
		if (pigman == null || world == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN)) {
			return;
		}
		boolean baby = rollZombifiedPiglinBabySpawn(root, world);
		pigman.setBaby(baby);
		JsonObject variant = zombifiedPiglinVariantRoot(root, baby);
		clearMobEquipment(pigman);
		applySpawnArmorLoadout(pigman, variant, world.getRandom());
		enforceZombifiedPiglinWeaponLoadout(pigman);
		applyUniversalStats(pigman, variant);
	}

	public static boolean tickPillagerAttackCooldown(Monster attacker) {
		String fileKey = resolveCrossbowShooterFileKey(attacker);
		if (fileKey == null || !snapshot.enabled) {
			return false;
		}
		if (!isMobFileEnabled(fileKey)) {
			return false;
		}
		Integer remaining = PILLAGER_ATTACK_COOLDOWNS.get(attacker.getUUID());
		if (remaining == null || remaining <= 0) {
			PILLAGER_ATTACK_COOLDOWNS.remove(attacker.getUUID());
			return false;
		}
		if (remaining == 1) {
			PILLAGER_ATTACK_COOLDOWNS.remove(attacker.getUUID());
			return false;
		}
		PILLAGER_ATTACK_COOLDOWNS.put(attacker.getUUID(), remaining - 1);
		return true;
	}

	public static void markPillagerAttackCooldownFromShot(Monster attacker) {
		if (attacker != null) {
			markCrossbowAttackCooldown(attacker);
		}
	}

	public static int resolveCrossbowPostChargeDelay(Monster attacker, int vanillaDelay) {
		if (resolveCrossbowShooterFileKey(attacker) == null) {
			return vanillaDelay;
		}
		return resolveCrossbowChargeUpTicks(attacker) > 0 ? 1 : vanillaDelay;
	}

	public static int resolveCrossbowChargeDurationOverride(LivingEntity user) {
		return user instanceof Monster monster ? resolveCrossbowChargeUpTicks(monster) : -1;
	}

	public static boolean applyPillagerProjectileAccuracyOverride(
		Projectile projectile,
		LivingEntity shooter,
		LivingEntity target,
		double velocityX,
		double velocityY,
		double velocityZ,
		float speed,
		float divergence
	) {
		String fileKey = resolveCrossbowShooterFileKey(shooter);
		if (fileKey == null || target == null || !target.isAlive() || !snapshot.enabled) {
			return false;
		}
		JsonObject root = crossbowShooterRoot(shooter);
		if (!isMobFileEnabled(fileKey)) {
			return false;
		}
		if (root.entrySet().isEmpty()) {
			return false;
		}
		double accuracy = resolveScaledAttackAccuracy(readMobStatDouble(root, MadokuMobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D), shooter.level().getDifficulty(), isHardcoreWorld(shooter.level()));
		accuracy = shooter instanceof Mob mob ? MadokuRegionalDifficultyManager.resolveMobAttackAccuracyScaling(mob, accuracy) : accuracy;
		accuracy = MadokuLuck.reduceHostileRangedAccuracyForTarget(target, accuracy);
		if (shooter.getRandom().nextDouble() <= accuracy) {
			projectile.shoot(velocityX, velocityY, velocityZ, speed, 0.0F);
			if (projectile instanceof AbstractArrow arrow) {
				trackManagedMobArrow(arrow, resolveServer(shooter));
				if (shooter instanceof Pillager || shooter instanceof Piglin) {
					double homingSpeed = Math.max(MIN_HOMING_SPEED, arrow.getDeltaMovement().length());
					arrow.setNoGravity(true);
					arrow.addTag(HOMING_PROJECTILE_TAG);
					HOMING_ARROWS.put(arrow.getUUID(), new HomingArrowState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
					requestRuntimeProcessing(resolveServer(shooter), 1L);
					if (shooter instanceof Piglin) {
						arrow.setRemainingFireTicks(100);
					}
				}
			}
			return true;
		}
		Vec3 missed = resolveMissVector(velocityX, velocityY, velocityZ, accuracy, shooter);
		projectile.shoot(missed.x, missed.y, missed.z, speed, 0.0F);
		if (projectile instanceof AbstractArrow arrow) {
			trackManagedMobArrow(arrow, resolveServer(shooter));
			if (shooter instanceof Piglin) {
				arrow.setRemainingFireTicks(100);
			}
			HOMING_ARROWS.remove(arrow.getUUID());
		}
		return true;
	}

	public static float resolveProjectileDamageOverride(AbstractArrow arrow, float fallbackDamage) {
		if (arrow == null) {
			return fallbackDamage;
		}
		Float fixed = FIXED_ARROW_DAMAGE.remove(arrow.getUUID());
		if (fixed != null) {
			return Math.max(0.0F, fixed);
		}
		if (arrow.getOwner() instanceof AbstractSkeleton skeleton && snapshot.enabled) {
			JsonObject root = skeletonRoot(skeleton.getType());
			String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
				: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
				: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
				: skeleton.getType() == EntityType.PARCHED ? MadokuMobConfigManager.FILE_PARCHED
				: skeleton.getType() == EntityType.WITHER_SKELETON ? MadokuMobConfigManager.FILE_WITHER_SKELETON
				: "";
			if (!skeletonFileKey.isBlank() && isMobFileEnabled(skeletonFileKey)) {
				return (float) Math.max(0.0D, resolveSkeletonRangedDamage(skeleton, root));
			}
		}
		Entity owner = arrow.getOwner();
		if (!(owner instanceof LivingEntity livingOwner) || !snapshot.enabled) {
			return fallbackDamage;
		}
		String fileKey = resolveCrossbowShooterFileKey(livingOwner);
		if (fileKey == null) {
			return fallbackDamage;
		}
		JsonObject root = crossbowShooterRoot(livingOwner);
		if (!isMobFileEnabled(fileKey)) {
			return fallbackDamage;
		}
		if (root.entrySet().isEmpty()) {
			return fallbackDamage;
		}
		double damage = resolveScaledRangedDamage(readMobStatDouble(root, MadokuMobConfigManager.FIELD_RANGED_DAMAGE, 6.0D), livingOwner.level().getDifficulty(), isHardcoreWorld(livingOwner.level()));
		damage = livingOwner instanceof Mob mob ? MadokuRegionalDifficultyManager.resolveMobRangedDamageScaling(mob, damage) : damage;
		return (float) Math.max(0.0D, damage);
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
		if (level == null || creeper == null || !snapshot.enabled) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject root = root(MadokuMobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_CREEPER)) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject variant = creeper.isPowered() ? readObject(root, MadokuMobConfigManager.FIELD_CHARGED_CREEPER) : readObject(root, MadokuMobConfigManager.FIELD_CREEPER);
		Double baseChance = clampOptional(readOptionalDouble(readMobStatsRoot(variant), MadokuMobConfigManager.FIELD_EXPLOSION_DESTRUCTION_CHANCE), 0.0D, 1.0D);
		if (baseChance == null) {
			level.explode(source, x, y, z, vanillaPower, vanillaInteraction);
			return;
		}
		JsonObject variantSpawnRules = readSpawnRulesRoot(variant);
		double chance = Mth.clamp(
			resolveDifficultyAdjustedValue(
				level.getDifficulty(),
				isHardcoreWorld(level),
				baseChance,
				readDouble(variantSpawnRules, MadokuMobConfigManager.FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2D),
				0.0D
			),
			0.0D,
			1.0D
		);
		chance = MadokuLuck.reduceCreeperGriefChanceForTarget(creeper.getTarget(), chance);
		Double configuredPower = readOptionalDouble(readMobStatsRoot(variant), MadokuMobConfigManager.FIELD_EXPLOSION_POWER);
		float power = configuredPower == null ? vanillaPower : configuredPower.floatValue();
		power = (float) resolveDifficultyAdjustedValue(level.getDifficulty(), isHardcoreWorld(level), Math.max(0.0D, power), CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP, 0.0D);
		power = (float) Math.max(0.0D, power + MadokuRegionalDifficultyManager.resolveCreeperExplosionPowerScaling(creeper));
		Level.ExplosionInteraction interaction = level.getRandom().nextDouble() < chance
			? Level.ExplosionInteraction.MOB
			: Level.ExplosionInteraction.NONE;
		level.explode(source, x, y, z, power, interaction);
	}

	public static float resolveCreeperGriefExplosionRadius(ServerExplosion explosion, float fallbackRadius) {
		if (explosion == null || !(explosion.getDirectSourceEntity() instanceof Creeper) || !snapshot.enabled) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject root = root(MadokuMobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_CREEPER)) {
			return Math.max(0.0F, fallbackRadius);
		}
		JsonObject variant = readObject(root, MadokuMobConfigManager.FIELD_CREEPER);
		return (float) (Math.max(0.0F, fallbackRadius)
			* Mth.clamp(readMobStatDouble(variant, MadokuMobConfigManager.FIELD_GRIEF_POWER_MULTIPLIER, 0.5D), 0.0D, 1.0D));
	}

	public static float resolveFixedPlayerExplosionDamage(Creeper creeper, float fallbackExplosionRadius) {
		double explosionPower = Math.max(0.0D, fallbackExplosionRadius);
		if (creeper == null || !snapshot.enabled) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject root = root(MadokuMobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_CREEPER)) {
			return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
		}
		JsonObject variant = creeper.isPowered() ? readObject(root, MadokuMobConfigManager.FIELD_CHARGED_CREEPER) : readObject(root, MadokuMobConfigManager.FIELD_CREEPER);
		Double configuredPower = readOptionalDouble(readMobStatsRoot(variant), MadokuMobConfigManager.FIELD_EXPLOSION_POWER);
		if (configuredPower != null) {
			explosionPower = Math.max(0.0D, configuredPower);
		}
		explosionPower = resolveDifficultyAdjustedValue(creeper.level().getDifficulty(), isHardcoreWorld(creeper.level()), explosionPower, CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP, 0.0D);
		explosionPower = Math.max(0.0D, explosionPower + MadokuRegionalDifficultyManager.resolveCreeperExplosionPowerScaling(creeper));
		return (float) (explosionPower / CREEPER_POWER_PER_DAMAGE);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
		if (main.isEmpty() || !main.is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	public static boolean shouldSkeletonMeleeIgnoreArmor(DamageSource source) {
		if (!snapshot.enabled || source == null) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof AbstractSkeleton skeleton) || source.getDirectEntity() != attacker) {
			return false;
		}
		if (skeleton.getType() == EntityType.WITHER_SKELETON) {
			return false;
		}
		JsonObject root = skeletonRoot(skeleton.getType());
		String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
			: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
			: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
			: skeleton.getType() == EntityType.PARCHED ? MadokuMobConfigManager.FILE_PARCHED
			: skeleton.getType() == EntityType.WITHER_SKELETON ? MadokuMobConfigManager.FILE_WITHER_SKELETON
			: "";
		if (skeletonFileKey.isBlank() || !isMobFileEnabled(skeletonFileKey)) {
			return false;
		}
		double withoutBow = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0D));
		if (withoutBow <= 0.0D) {
			return false;
		}
		return resolveBowHand(skeleton) == null;
	}

	private static boolean applyLoadedEntityRules(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !snapshot.enabled) {
			return false;
		}
		if (entity instanceof Zombie zombie) {
			JsonObject root = zombieRoot(zombie.getType());
			String fileKey = zombie.getType() == EntityType.ZOMBIE ? MadokuMobConfigManager.FILE_ZOMBIE
				: zombie.getType() == EntityType.HUSK ? MadokuMobConfigManager.FILE_HUSK
				: zombie.getType() == EntityType.DROWNED ? MadokuMobConfigManager.FILE_DROWNED
				: zombie.getType() == EntityType.ZOMBIE_VILLAGER ? MadokuMobConfigManager.FILE_ZOMBIE_VILLAGER
				: "";
			if (!fileKey.isBlank() && isMobFileEnabled(fileKey)) {
				disableZombieReinforcements(zombie);
				JsonObject variant = zombie.isBaby() ? zombieBabyRoot(zombie.getType(), root) : zombieAdultRoot(zombie.getType(), root);
				return applyUniversalStats(zombie, variant);
			}
			return false;
		}
		if (entity instanceof Spider spider) {
			if (spider.getType() == EntityType.CAVE_SPIDER) {
				JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_CAVE_SPIDER);
				return isMobFileEnabled(MadokuMobConfigManager.FILE_CAVE_SPIDER) && applyUniversalStats(spider, root);
			}
			JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_SPIDER);
			return isMobFileEnabled(MadokuMobConfigManager.FILE_SPIDER) && applyUniversalStats(spider, root);
		}
			if (entity instanceof AbstractSkeleton skeleton) {
				JsonObject root = skeletonRoot(skeleton.getType());
				String skeletonFileKey = skeleton.getType() == EntityType.SKELETON ? MadokuMobConfigManager.FILE_SKELETON
					: skeleton.getType() == EntityType.STRAY ? MadokuMobConfigManager.FILE_STRAY
					: skeleton.getType() == EntityType.BOGGED ? MadokuMobConfigManager.FILE_BOGGED
					: skeleton.getType() == EntityType.PARCHED ? MadokuMobConfigManager.FILE_PARCHED
					: skeleton.getType() == EntityType.WITHER_SKELETON ? MadokuMobConfigManager.FILE_WITHER_SKELETON
					: "";
				boolean modified = !skeletonFileKey.isBlank() && isMobFileEnabled(skeletonFileKey) && applyUniversalStats(skeleton, root);
				if (skeleton.getType() == EntityType.WITHER_SKELETON) {
					modified |= normalizeWitherSkeletonWeapon(skeleton);
				}
				return modified;
			}
			if (entity instanceof Pillager pillager) {
				JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_PILLAGER);
				return isMobFileEnabled(MadokuMobConfigManager.FILE_PILLAGER) && applyUniversalStats(pillager, root);
			}
			if (entity.getType() == EntityType.ZOMBIFIED_PIGLIN && entity instanceof Zombie pigman) {
				JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN);
				if (!isMobFileEnabled(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN)) {
					return false;
				}
				JsonObject variant = zombifiedPiglinVariantRoot(root, pigman.isBaby());
				boolean modified = applyUniversalStats(pigman, variant);
				enforceZombifiedPiglinWeaponLoadout(pigman);
				return modified;
			}
			if (entity instanceof Piglin piglin) {
				JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_PIGLIN);
				if (!isMobFileEnabled(MadokuMobConfigManager.FILE_PIGLIN)) {
					return false;
				}
				JsonObject variant = piglinVariantRoot(root, piglin.isBaby());
				boolean modified = applyUniversalStats(piglin, variant);
				if (modified) {
					if (piglin.isBaby()) {
						clearPiglinMainHand(piglin);
					} else {
						normalizePiglinWeapon(piglin);
					}
				}
				return modified;
			}
			if (entity instanceof Creeper creeper) {
				JsonObject root = root(MadokuMobConfigManager.FILE_CREEPER);
				return isMobFileEnabled(MadokuMobConfigManager.FILE_CREEPER) && applyCreeperRuntimeStats(creeper, root);
			}
			if (entity.getType() == EntityType.BEE) {
				return MadokuMobBee.applyLoadedEntityOverrides(entity);
			}
			if (entity.getType() == MadokuEntities.HAG) {
				JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_HAG);
				return isMobFileEnabled(MadokuMobConfigManager.FILE_HAG) && applyUniversalStats(entity, root);
			}
			return false;
	}

	private static void applyDifficultyScalingAfterMobOverrides(LivingEntity entity, ServerLevel level, boolean loadedMobOverridesApplied) {
		if (!snapshot.enabled || entity == null || !(entity instanceof Mob mob) || level == null || !MadokuRegionalDifficultyManager.isEnabled()) {
			return;
		}
		if (isBeeRegionalDifficultyScalingEnabled(entity)) {
			return;
		}
		if (loadedMobOverridesApplied && mob instanceof DifficultyScaledMob scaledMob && scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
			MadokuRegionalDifficultyManager.reapplySpawnScalingFromStoredAdjustment(mob);
			return;
		}
		MadokuRegionalDifficultyManager.applySpawnScalingIfUnscaled(mob, level);
	}

	private static void preapplyBeeRegionalDifficultyAdjustment(LivingEntity entity, ServerLevel level) {
		if (entity == null || level == null || !(entity instanceof Mob mob) || !snapshot.enabled || !MadokuRegionalDifficultyManager.isEnabled()) {
			return;
		}
		if (!isBeeRegionalDifficultyScalingEnabled(entity)) {
			return;
		}
		MadokuRegionalDifficultyManager.ensureSpawnDifficultyAdjustment(mob, level);
	}

	static void applyCreeperSpawnOverrides(Creeper creeper, ServerLevelAccessor world, DifficultyInstance difficulty) {
		if (creeper == null || world == null || difficulty == null || !snapshot.enabled || creeper.isPowered()) {
			return;
		}
		JsonObject root = root(MadokuMobConfigManager.FILE_CREEPER);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_CREEPER)) {
			return;
		}
		JsonObject creeperVariant = readObject(root, MadokuMobConfigManager.FIELD_CREEPER);
		JsonObject chargedVariant = readObject(root, MadokuMobConfigManager.FIELD_CHARGED_CREEPER);
		SpawnWeightPair shifted = resolveDifficultyShiftedSpawnWeights(
			readSpawnRuleDouble(creeperVariant, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 95.0D),
			readSpawnRuleDouble(chargedVariant, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 5.0D),
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
		if (mob == null || world == null || !snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)) {
			if (mob != null && mob.getType() == EntityType.BEE) {
				emitBeeOverrideDebug(METRIC_BEE_OVERRIDE, mob, "spawn", "skipped");
			}
			return;
		}
		JsonObject beeFileRoot = root(MadokuMobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(mob, beeFileRoot, world.getRandom(), true);
		emitBeeResolvedRootDebug(mob, "spawn", beeFileRoot, resolved);
		applyBeeOverrides(mob, beeFileRoot, resolved);
	}

	static boolean applyBeeLoadedEntityOverrides(LivingEntity entity) {
		if (entity == null || entity.level().isClientSide() || !snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)) {
			if (entity != null && entity.getType() == EntityType.BEE) {
				emitBeeOverrideDebug(METRIC_BEE_OVERRIDE, entity, "load", "skipped");
			}
			return false;
		}
		JsonObject beeFileRoot = root(MadokuMobConfigManager.FILE_BEE);
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		emitBeeResolvedRootDebug(entity, "load", beeFileRoot, resolved);
		return applyBeeOverrides(entity, beeFileRoot, resolved);
	}

	static JsonObject resolveBeeBehaviorRoot(LivingEntity entity) {
		if (entity == null || !snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)) {
			return new JsonObject();
		}
		JsonObject beeFileRoot = root(MadokuMobConfigManager.FILE_BEE);
		if (!readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)) {
			return new JsonObject();
		}
		JsonObject resolved = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		return readMobBehaviorRoot(resolved);
	}

	static boolean isBeeBehaviorOverrideEnabled() {
		if (!snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MadokuMobConfigManager.FILE_BEE);
		return readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
	}

	static boolean isBeeGoalsOverrideEnabled() {
		if (!snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MadokuMobConfigManager.FILE_BEE);
		return readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_GOALS, true);
	}

	private static boolean isBeeRegionalDifficultyScalingEnabled(LivingEntity entity) {
		if (entity == null || entity.getType() != EntityType.BEE || !snapshot.enabled || !isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)) {
			return false;
		}
		JsonObject beeFileRoot = root(MadokuMobConfigManager.FILE_BEE);
		JsonObject resolvedBeeRoot = resolveBeeRoot(entity, beeFileRoot, entity.getRandom(), false);
		JsonObject regionalRoot = readObject(resolvedBeeRoot, MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING);
		return readBoolean(regionalRoot, MadokuMobConfigManager.FIELD_ENABLED, false);
	}

	static void applyHagSpawnOverrides(Mob mob) {
		if (mob == null || !snapshot.enabled) {
			return;
		}
		JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_HAG);
		if (isMobFileEnabled(MadokuMobConfigManager.FILE_HAG)) {
			applyUniversalStats(mob, root);
		}
	}

	private static boolean applyBeeOverrides(LivingEntity entity, JsonObject beeFileRoot, JsonObject resolvedRoot) {
		if (entity == null || beeFileRoot == null || resolvedRoot == null || resolvedRoot.entrySet().isEmpty()) {
			return false;
		}
		boolean overrideStats = readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_STATS, true);
		if (!overrideStats) {
			emitBeeOverrideDebug(METRIC_BEE_OVERRIDE, entity, "apply", "override_stats_disabled");
			return false;
		}
		boolean applied = applyUniversalStats(entity, resolvedRoot);
		emitBeeOverrideDebug(METRIC_BEE_OVERRIDE, entity, "apply", applied ? "applied" : "no_changes");
		return applied;
	}

	private static boolean applyUniversalStats(LivingEntity entity, JsonObject root) {
		if (entity == null || root == null) {
			return false;
		}
		boolean modified = false;
		double oldMaxHealth = entity.getMaxHealth();
		boolean hardcore = isHardcoreWorld(entity.level());
		JsonObject statsRoot = readMobStatsRoot(root);
		JsonObject difficultyScale = readObject(root, MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE);
		boolean difficultyScalingEnabled = readBoolean(root, MadokuMobConfigManager.FIELD_DIFFICULTY_SCALING, false);
		JsonObject regionalDifficultyScale = readObject(root, MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING);
		boolean regionalDifficultyScalingEnabled = readBoolean(regionalDifficultyScale, MadokuMobConfigManager.FIELD_ENABLED, false);
		int regionalAdjustment = regionalDifficultyScalingEnabled ? resolveRegionalDifficultyAdjustment(entity) : 0;
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.MAX_HEALTH,
			resolveScaledStat(
				resolveRegionalDifficultyScaledStat(
					readOptionalPositive(statsRoot, MadokuMobConfigManager.FIELD_HEALTH),
					regionalAdjustment,
					regionalDifficultyScale,
					MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH,
					0.0D
				),
				entity.level().getDifficulty(),
				hardcore,
				HEALTH_DIFFICULTY_STEP,
				0.0D,
				difficultyScalingEnabled,
				difficultyScale,
				MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.ARMOR,
			resolveUniversalBaseStat(
				readOptionalNonNegative(statsRoot, MadokuMobConfigManager.FIELD_ARMOR),
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
					resolveRegionalDifficultyScaledStat(
						readOptionalNonNegative(statsRoot, MadokuMobConfigManager.FIELD_DAMAGE),
						regionalAdjustment,
						regionalDifficultyScale,
						MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE,
						0.0D
					),
					entity.level().getDifficulty(),
					hardcore,
					DAMAGE_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE
				)
			);
			modified |= setBaseValueIfPresent(
				entity,
				Attributes.MOVEMENT_SPEED,
				resolveScaledStat(
					resolveRegionalDifficultyScaledStat(
						readOptionalPositive(statsRoot, MadokuMobConfigManager.FIELD_MOVEMENT_SPEED),
						regionalAdjustment,
						regionalDifficultyScale,
						MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED,
						0.0D
					),
					entity.level().getDifficulty(),
					hardcore,
					MOVEMENT_SPEED_DIFFICULTY_STEP,
					0.0D,
					difficultyScalingEnabled,
					difficultyScale,
					MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED
				)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.FLYING_SPEED,
			resolveScaledStat(
				resolveRegionalDifficultyScaledStat(
					readOptionalPositive(statsRoot, FIELD_FLYING_SPEED),
					regionalAdjustment,
					regionalDifficultyScale,
					FIELD_FLYING_SPEED,
					0.0D
				),
				entity.level().getDifficulty(),
				hardcore,
				MOVEMENT_SPEED_DIFFICULTY_STEP,
				0.0D,
				difficultyScalingEnabled,
				difficultyScale,
				MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED
			)
		);
		modified |= setBaseValueIfPresent(
			entity,
			Attributes.KNOCKBACK_RESISTANCE,
			resolveUniversalBaseStat(
				clampOptional(readOptionalDouble(statsRoot, MadokuMobConfigManager.FIELD_KNOCKBACK_RESISTANCE), 0.0D, 1.0D),
				entity.level().getDifficulty(),
				hardcore,
				KNOCKBACK_RESISTANCE_DIFFICULTY_STEP,
				0.0D
			)
		);
		Double baseScale = readOptionalPositive(statsRoot, MadokuMobConfigManager.FIELD_SCALE);
		Double resolvedScale = baseScale;
		if (baseScale != null) {
			if (difficultyScalingEnabled) {
				Double perStep = readOptionalDouble(difficultyScale, MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_SCALE);
				if (perStep != null) {
					resolvedScale = resolveDifficultyAdjustedPercentValue(
						entity.level().getDifficulty(),
						hardcore,
						baseScale,
						perStep,
						0.01D
					);
				}
			} else {
				double scaleDifficultyStep = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_SCALE_DIFFICULTY_STEP, 0.0D));
				if (scaleDifficultyStep > 0.0D) {
					resolvedScale = resolveDifficultyAdjustedValue(
						entity.level().getDifficulty(),
						hardcore,
						baseScale,
						scaleDifficultyStep,
						0.01D
					);
				}
			}
		}
		AttributeInstance scaleInstance = entity.getAttribute(Attributes.SCALE);
		Double scaleBefore = scaleInstance == null ? null : scaleInstance.getBaseValue();
		boolean scaleApplied = setBaseValueIfPresent(entity, Attributes.SCALE, resolvedScale);
		modified |= scaleApplied;
		Double scaleAfter = scaleInstance == null ? null : scaleInstance.getBaseValue();
		emitBeeScaleApplyDebug(entity, baseScale, resolvedScale, difficultyScalingEnabled, scaleInstance != null, scaleApplied, scaleBefore, scaleAfter);
		Integer baseExperienceDrop = resolveRegionalDifficultyScaledIntStat(
			readOptionalIntNonNegative(statsRoot, MadokuMobConfigManager.FIELD_EXPERIENCE_DROP),
			regionalAdjustment,
			regionalDifficultyScale,
			MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP
		);
		if (baseExperienceDrop != null) {
			int resolvedExperienceDrop = baseExperienceDrop;
			if (difficultyScalingEnabled) {
				Double perStep = readOptionalNonNegative(difficultyScale, MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP);
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
		rescaleCurrentHealth(entity, oldMaxHealth);
		return modified;
	}

	private static void emitBeeOverrideDebug(String metricId, LivingEntity entity, String phase, String outcome) {
		if (entity == null || entity.getType() != EntityType.BEE || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("bee:" + entity.getUUID())
			.field("phase", phase)
			.field("outcome", outcome)
			.field("is_baby", entity instanceof AgeableMob ageableMob && ageableMob.isBaby())
			.field("has_scale_attr", entity.getAttribute(Attributes.SCALE) != null)
			.field("scale_base", attributeBaseText(entity, Attributes.SCALE))
			.field("health_base", attributeBaseText(entity, Attributes.MAX_HEALTH))
			.field("move_base", attributeBaseText(entity, Attributes.MOVEMENT_SPEED))
			.field("fly_base", attributeBaseText(entity, Attributes.FLYING_SPEED));
		if (entity.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static void emitBeeResolvedRootDebug(LivingEntity entity, String phase, JsonObject beeFileRoot, JsonObject resolvedRoot) {
		if (entity == null || entity.getType() != EntityType.BEE || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_BEE_RESOLVED_ROOT)) {
			return;
		}
		JsonObject statsRoot = readMobStatsRoot(resolvedRoot);
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_BEE_RESOLVED_ROOT, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("bee:" + entity.getUUID())
			.field("phase", phase)
			.field("override_stats", readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_STATS, true))
			.field("variant_tag", readStoredBeeVariantKey(entity))
			.field("is_baby", entity instanceof AgeableMob ageableMob && ageableMob.isBaby())
			.field("cfg_health", optionalNumberText(readOptionalDouble(statsRoot, MadokuMobConfigManager.FIELD_HEALTH)))
			.field("cfg_movement_speed", optionalNumberText(readOptionalDouble(statsRoot, MadokuMobConfigManager.FIELD_MOVEMENT_SPEED)))
			.field("cfg_flying_speed", optionalNumberText(readOptionalDouble(statsRoot, FIELD_FLYING_SPEED)))
			.field("cfg_scale", optionalNumberText(readOptionalDouble(statsRoot, MadokuMobConfigManager.FIELD_SCALE)));
		if (entity.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static void emitBeeScaleApplyDebug(
		LivingEntity entity,
		Double baseScale,
		Double resolvedScale,
		boolean difficultyScalingEnabled,
		boolean hasScaleAttribute,
		boolean scaleApplied,
		Double scaleBefore,
		Double scaleAfter
	) {
		if (entity == null || entity.getType() != EntityType.BEE || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_BEE_SCALE_APPLY)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_BEE_SCALE_APPLY, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("bee:" + entity.getUUID())
			.field("is_baby", entity instanceof AgeableMob ageableMob && ageableMob.isBaby())
			.field("difficulty_scaling", difficultyScalingEnabled)
			.field("cfg_scale", optionalNumberText(baseScale))
			.field("resolved_scale", optionalNumberText(resolvedScale))
			.field("has_scale_attr", hasScaleAttribute)
			.field("scale_applied", scaleApplied)
			.field("scale_before", optionalNumberText(scaleBefore))
			.field("scale_after", optionalNumberText(scaleAfter));
		if (entity.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static String optionalNumberText(Double value) {
		if (value == null || !Double.isFinite(value)) {
			return "unset";
		}
		return Double.toString(value);
	}

	private static String attributeBaseText(LivingEntity entity, Holder<Attribute> attribute) {
		if (entity == null || attribute == null) {
			return "missing";
		}
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return "missing";
		}
		return Double.toString(instance.getBaseValue());
	}

	private static boolean applyCreeperRuntimeStats(Creeper creeper, JsonObject root) {
		boolean modified = false;
		JsonObject variant = creeper.isPowered() ? readObject(root, MadokuMobConfigManager.FIELD_CHARGED_CREEPER) : readObject(root, MadokuMobConfigManager.FIELD_CREEPER);
		modified |= applyUniversalStats(creeper, variant);
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		Double fuseLength = readOptionalPositive(readMobStatsRoot(variant), MadokuMobConfigManager.FIELD_FUSE_LENGTH);
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
		Double explosionPower = readOptionalDouble(readMobStatsRoot(variant), MadokuMobConfigManager.FIELD_EXPLOSION_POWER);
		if (explosionPower != null) {
			double resolvedPower = resolveDifficultyAdjustedValue(
				creeper.level().getDifficulty(),
				isHardcoreWorld(creeper.level()),
				explosionPower,
				CREEPER_EXPLOSION_POWER_DIFFICULTY_STEP,
				0.0D
			) + MadokuRegionalDifficultyManager.resolveCreeperExplosionPowerScaling(creeper);
			int radius = Math.max(0, (int) Math.round(resolvedPower));
			accessor.madokuCraft$setExplosionRadius(radius);
		}
		return modified;
	}

	private static void disableZombieReinforcements(Zombie zombie) {
		AttributeInstance instance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (instance == null) {
			return;
		}
		instance.removeModifiers();
		instance.setBaseValue(0.0D);
	}

	private static void clearMobEquipment(Mob mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!mob.getItemBySlot(slot).isEmpty()) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void applySpawnArmorLoadout(Mob mob, JsonObject root, RandomSource random) {
		if (mob == null || root == null || random == null) {
			return;
		}
		clearArmorSlots(mob);
		JsonObject spawnRules = readSpawnRulesRoot(root);
		double armorWeight = Math.max(0.0D, readDouble(spawnRules, MadokuMobConfigManager.FIELD_ARMOR_SPAWN_WEIGHT, 10.0D));
		double noArmorWeight = Math.max(0.0D, readDouble(spawnRules, MadokuMobConfigManager.FIELD_NO_ARMOR_SPAWN_WEIGHT, 90.0D));
		double total = armorWeight + noArmorWeight;
		if (total <= 0.0D) {
			return;
		}
		if ((random.nextDouble() * total) >= noArmorWeight) {
			equipSpawnArmorLoadout(mob, root, random);
		}
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

	private static void equipPiglinWeapon(Piglin piglin, JsonObject root, RandomSource random) {
		if (piglin == null || root == null || random == null) {
			return;
		}
		ItemStack weapon = rollPiglinSpawnWeapon(root, random);
		if (!weapon.isEmpty()) {
			piglin.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
	}

	private static ItemStack rollPiglinSpawnWeapon(JsonObject root, RandomSource random) {
		double crossbow = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_CROSSBOW_SPAWN_WEIGHT, 50.0D));
		double goldenSword = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_GOLDEN_SWORD_SPAWN_WEIGHT, 50.0D));
		double total = crossbow + goldenSword;
		if (total <= 0.0D) {
			return ItemStack.EMPTY;
		}
		double roll = random.nextDouble() * total;
		if (roll < crossbow) {
			return new ItemStack(Items.CROSSBOW);
		}
		return piglinSwordStack();
	}

	private static ItemStack piglinSwordStack() {
		ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
		sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		return sword;
	}

	private static boolean rollPiglinBabySpawn(JsonObject root, ServerLevelAccessor world) {
		if (root == null || world == null) {
			return false;
		}
		JsonObject adult = piglinAdultRoot(root);
		JsonObject baby = piglinBabyRoot(root);
		double adultWeight = Math.max(0.0D, readSpawnRuleDouble(adult, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 90.0D));
		double babyWeight = Math.max(0.0D, readSpawnRuleDouble(baby, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 10.0D));
		double total = adultWeight + babyWeight;
		if (total <= 0.0D) {
			return false;
		}
		return (world.getRandom().nextDouble() * total) < babyWeight;
	}

	private static JsonObject piglinVariantRoot(JsonObject root, boolean baby) {
		return baby ? piglinBabyRoot(root) : piglinAdultRoot(root);
	}

	private static JsonObject piglinAdultRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_ADULT_PIGLIN);
	}

	private static JsonObject piglinBabyRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_BABY_PIGLIN);
	}

	private static boolean rollZombifiedPiglinBabySpawn(JsonObject root, ServerLevelAccessor world) {
		if (root == null || world == null) {
			return false;
		}
		JsonObject adult = zombifiedPiglinAdultRoot(root);
		JsonObject baby = zombifiedPiglinBabyRoot(root);
		double adultWeight = Math.max(0.0D, readSpawnRuleDouble(adult, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 90.0D));
		double babyWeight = Math.max(0.0D, readSpawnRuleDouble(baby, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 10.0D));
		double total = adultWeight + babyWeight;
		if (total <= 0.0D) {
			return false;
		}
		return (world.getRandom().nextDouble() * total) < babyWeight;
	}

	private static JsonObject zombifiedPiglinVariantRoot(JsonObject root, boolean baby) {
		return baby ? zombifiedPiglinBabyRoot(root) : zombifiedPiglinAdultRoot(root);
	}

	private static JsonObject zombifiedPiglinAdultRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_ADULT_ZOMBIFIED_PIGLIN);
	}

	private static JsonObject zombifiedPiglinBabyRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_BABY_ZOMBIFIED_PIGLIN);
	}

	private static void clearPiglinMainHand(Piglin piglin) {
		if (piglin == null) {
			return;
		}
		piglin.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	private static void normalizePiglinWeapon(Piglin piglin) {
		if (piglin == null) {
			return;
		}
		ItemStack mainHand = piglin.getMainHandItem();
		if (mainHand.is(Items.GOLDEN_SWORD)) {
			stripWeaponAttributeModifiers(mainHand);
		}
	}

	private static void equipZombifiedPiglinWeapon(Mob pigman) {
		if (pigman == null) {
			return;
		}
		pigman.setItemSlot(EquipmentSlot.MAINHAND, zombifiedPiglinSwordStack());
	}

	private static ItemStack zombifiedPiglinSwordStack() {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		return sword;
	}

	private static void normalizeZombifiedPiglinWeapon(Mob pigman) {
		if (pigman == null) {
			return;
		}
		ItemStack mainHand = pigman.getMainHandItem();
		if (mainHand.is(Items.IRON_SWORD)) {
			stripWeaponAttributeModifiers(mainHand);
		}
	}

	private static void enforceZombifiedPiglinWeaponLoadout(Zombie pigman) {
		if (pigman == null || pigman.getType() != EntityType.ZOMBIFIED_PIGLIN) {
			return;
		}
		if (pigman.isBaby()) {
			pigman.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return;
		}
		ItemStack mainHand = pigman.getMainHandItem();
		if (!mainHand.is(Items.IRON_SWORD)) {
			equipZombifiedPiglinWeapon(pigman);
		}
		normalizeZombifiedPiglinWeapon(pigman);
	}

	private static void applyWitherSkeletonSpawnOverrides(AbstractSkeleton skeleton, ServerLevelAccessor world) {
		if (skeleton == null || world == null) {
			return;
		}
		JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_WITHER_SKELETON);
		if (!isMobFileEnabled(MadokuMobConfigManager.FILE_WITHER_SKELETON)) {
			return;
		}
		clearMobEquipment(skeleton);
		applyUniversalStats(skeleton, root);
		applySpawnArmorLoadout(skeleton, root, world.getRandom());
		ItemStack weapon = rollWitherSkeletonSpawnWeapon(root, world.getRandom());
		if (!weapon.isEmpty()) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
	}

	private static ItemStack rollWitherSkeletonSpawnWeapon(JsonObject root, RandomSource random) {
		double sword = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_WITHER_SWORD_SPAWN_WEIGHT, 90.0D));
		double bow = Math.max(0.0D, readSpawnRuleDouble(root, MadokuMobConfigManager.FIELD_WITHER_BOW_SPAWN_WEIGHT, 10.0D));
		double total = sword + bow;
		if (total <= 0.0D) {
			return ItemStack.EMPTY;
		}
		double roll = random.nextDouble() * total;
		if (roll < sword) {
			return witherSkeletonSwordStack();
		}
		return new ItemStack(Items.BOW);
	}

	private static ItemStack witherSkeletonSwordStack() {
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		stripWeaponAttributeModifiers(sword);
		return sword;
	}

	private static boolean normalizeWitherSkeletonWeapon(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return false;
		}
		ItemStack mainHand = skeleton.getMainHandItem();
		if (!mainHand.is(Items.NETHERITE_SWORD)) {
			return false;
		}
		return stripWeaponAttributeModifiers(mainHand);
	}

	private static boolean stripWeaponAttributeModifiers(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		return true;
	}

	private static void equipSpawnArmorLoadout(Mob mob, JsonObject root, RandomSource random) {
		SpawnArmorMaterial material = rollSpawnArmorMaterial(root, random);
		SpawnArmorCoverage coverage = rollSpawnArmorCoverage(root, random);
		if (material == null || coverage == null) {
			return;
		}
		switch (coverage) {
			case HELMET_ONLY -> equipArmorPiece(mob, material, EquipmentSlot.HEAD);
			case HELMET_BOOTS -> {
				equipArmorPiece(mob, material, EquipmentSlot.HEAD);
				equipArmorPiece(mob, material, EquipmentSlot.FEET);
			}
			case FULL_SET -> {
				equipArmorPiece(mob, material, EquipmentSlot.HEAD);
				equipArmorPiece(mob, material, EquipmentSlot.CHEST);
				equipArmorPiece(mob, material, EquipmentSlot.LEGS);
				equipArmorPiece(mob, material, EquipmentSlot.FEET);
			}
		}
	}

	private static void equipArmorPiece(Mob mob, SpawnArmorMaterial material, EquipmentSlot slot) {
		if (mob == null || material == null || slot == null) {
			return;
		}
		ItemStack stack = armorStack(material, slot);
		if (!stack.isEmpty()) {
			mob.setItemSlot(slot, stack);
		}
	}

	private static ItemStack armorStack(SpawnArmorMaterial material, EquipmentSlot slot) {
		if (material == null || slot == null) {
			return ItemStack.EMPTY;
		}
		return switch (material) {
			case NETHERITE -> switch (slot) {
				case HEAD -> new ItemStack(Items.NETHERITE_HELMET);
				case CHEST -> new ItemStack(Items.NETHERITE_CHESTPLATE);
				case LEGS -> new ItemStack(Items.NETHERITE_LEGGINGS);
				case FEET -> new ItemStack(Items.NETHERITE_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case DIAMOND -> switch (slot) {
				case HEAD -> new ItemStack(Items.DIAMOND_HELMET);
				case CHEST -> new ItemStack(Items.DIAMOND_CHESTPLATE);
				case LEGS -> new ItemStack(Items.DIAMOND_LEGGINGS);
				case FEET -> new ItemStack(Items.DIAMOND_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case GOLD -> switch (slot) {
				case HEAD -> new ItemStack(Items.GOLDEN_HELMET);
				case CHEST -> new ItemStack(Items.GOLDEN_CHESTPLATE);
				case LEGS -> new ItemStack(Items.GOLDEN_LEGGINGS);
				case FEET -> new ItemStack(Items.GOLDEN_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case IRON -> switch (slot) {
				case HEAD -> new ItemStack(Items.IRON_HELMET);
				case CHEST -> new ItemStack(Items.IRON_CHESTPLATE);
				case LEGS -> new ItemStack(Items.IRON_LEGGINGS);
				case FEET -> new ItemStack(Items.IRON_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case COPPER -> switch (slot) {
				case HEAD -> new ItemStack(Items.COPPER_HELMET);
				case CHEST -> new ItemStack(Items.COPPER_CHESTPLATE);
				case LEGS -> new ItemStack(Items.COPPER_LEGGINGS);
				case FEET -> new ItemStack(Items.COPPER_BOOTS);
				default -> ItemStack.EMPTY;
			};
			case LEATHER -> switch (slot) {
				case HEAD -> new ItemStack(Items.LEATHER_HELMET);
				case CHEST -> new ItemStack(Items.LEATHER_CHESTPLATE);
				case LEGS -> new ItemStack(Items.LEATHER_LEGGINGS);
				case FEET -> new ItemStack(Items.LEATHER_BOOTS);
				default -> ItemStack.EMPTY;
			};
		};
	}

	private static SpawnArmorMaterial rollSpawnArmorMaterial(JsonObject root, RandomSource random) {
		JsonObject armorRarity = readSpawnArmorRarityRoot(root);
		double netherite = Math.max(0.0D, readDouble(armorRarity, MadokuMobConfigManager.FIELD_ARMOR_NETHERITE_WEIGHT, 1.0D));
		double diamond = Math.max(0.0D, readDouble(armorRarity, MadokuMobConfigManager.FIELD_ARMOR_DIAMOND_WEIGHT, 5.0D));
		double gold = Math.max(0.0D, readDouble(armorRarity, MadokuMobConfigManager.FIELD_ARMOR_GOLD_WEIGHT, 10.0D));
		double iron = Math.max(0.0D, readDouble(armorRarity, MadokuMobConfigManager.FIELD_ARMOR_IRON_WEIGHT, 17.0D));
		double copper = Math.max(0.0D, readDouble(armorRarity, MadokuMobConfigManager.FIELD_ARMOR_COPPER_WEIGHT, 28.0D));
		double leather = Math.max(0.0D, readDouble(armorRarity, MadokuMobConfigManager.FIELD_ARMOR_LEATHER_WEIGHT, 39.0D));
		double total = netherite + diamond + gold + iron + copper + leather;
		if (total <= 0.0D) {
			return null;
		}
		double roll = random.nextDouble() * total;
		if (roll < netherite) {
			return SpawnArmorMaterial.NETHERITE;
		}
		roll -= netherite;
		if (roll < diamond) {
			return SpawnArmorMaterial.DIAMOND;
		}
		roll -= diamond;
		if (roll < gold) {
			return SpawnArmorMaterial.GOLD;
		}
		roll -= gold;
		if (roll < iron) {
			return SpawnArmorMaterial.IRON;
		}
		roll -= iron;
		if (roll < copper) {
			return SpawnArmorMaterial.COPPER;
		}
		return SpawnArmorMaterial.LEATHER;
	}

	private static SpawnArmorCoverage rollSpawnArmorCoverage(JsonObject root, RandomSource random) {
		JsonObject armorSet = readSpawnArmorSetRoot(root);
		double helmetOnly = Math.max(0.0D, readDouble(armorSet, MadokuMobConfigManager.FIELD_ARMOR_HELMET_ONLY_WEIGHT, 60.0D));
		double helmetBoots = Math.max(0.0D, readDouble(armorSet, MadokuMobConfigManager.FIELD_ARMOR_HELMET_BOOTS_WEIGHT, 30.0D));
		double fullSet = Math.max(0.0D, readDouble(armorSet, MadokuMobConfigManager.FIELD_ARMOR_FULL_SET_WEIGHT, 10.0D));
		double total = helmetOnly + helmetBoots + fullSet;
		if (total <= 0.0D) {
			return null;
		}
		double roll = random.nextDouble() * total;
		if (roll < helmetOnly) {
			return SpawnArmorCoverage.HELMET_ONLY;
		}
		roll -= helmetOnly;
		if (roll < helmetBoots) {
			return SpawnArmorCoverage.HELMET_BOOTS;
		}
		return SpawnArmorCoverage.FULL_SET;
	}

	private static void applyExperienceDrop(LivingEntity entity, Integer experienceDrop) {
		if (entity instanceof Mob mob && experienceDrop != null) {
			((MobExperienceAccessor) mob).madokuCraft$setXpReward(Math.max(0, experienceDrop));
		}
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
		if (entity instanceof Pillager) {
			PILLAGER_ATTACK_COOLDOWNS.remove(id);
		}
		PENDING_CAVE_SPIDER_REPLACEMENTS.remove(id);
		MadokuMobBee.onEntityCleanup(entity);
	}

	private static void runRuntimeTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			runtimeSchedulerId = context.getSchedulerId();
		}
		runtimeTaskScheduled = false;
		tickManagedMobArrows(server);
		tickHomingProjectiles(server);
		processPendingCaveSpiderReplacements(server);
		suppressBabyPiglinCombat(server);
		boolean beeRuntimeActive = MadokuMobBee.tickRuntime(
			server,
			TRACKED_ENTITIES.values(),
			snapshot.enabled,
			isMobFileEnabled(MadokuMobConfigManager.FILE_BEE)
		);
		if (!MANAGED_MOB_ARROWS.isEmpty()
			|| !HOMING_ARROWS.isEmpty()
			|| !PENDING_CAVE_SPIDER_REPLACEMENTS.isEmpty()
			|| beeRuntimeActive) {
			requestRuntimeProcessing(server, 1L);
		}
	}

	private static void requestRuntimeProcessing(MinecraftServer server, long delayTicks) {
		if (server == null || !snapshot.enabled || runtimeTaskScheduled) {
			return;
		}
		String schedulerId = ensureRuntimeSchedulerExists();
		if (enqueueRuntimeTask(schedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
			return;
		}
		runtimeSchedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		if (enqueueRuntimeTask(runtimeSchedulerId, delayTicks)) {
			runtimeTaskScheduled = true;
		}
	}

	private static String ensureRuntimeSchedulerExists() {
		if (runtimeSchedulerId == null || runtimeSchedulerId.isBlank()) {
			runtimeSchedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(MOB_SCHEDULER_OWNER_ID));
		}
		return runtimeSchedulerId;
	}

	private static boolean enqueueRuntimeTask(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_MOB_RUNTIME_TICK,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED || status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
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
				emitManagedArrowExpired(arrow);
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
			Entity entity = findEntity(server, entry.getKey());
			if (!(entity instanceof Spider spider) || !spider.isAlive() || spider.getType() != EntityType.SPIDER) {
				PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			if (!(spider.level() instanceof ServerLevel level)) {
				PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			EntitySpawnReason reason = entry.getValue() == null ? EntitySpawnReason.NATURAL : entry.getValue();
			CaveSpider caveSpider = EntityType.CAVE_SPIDER.create(level, reason);
			if (caveSpider == null) {
				PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
				continue;
			}
			caveSpider.setPos(spider.getX(), spider.getY(), spider.getZ());
			caveSpider.setYRot(spider.getYRot());
			caveSpider.setXRot(spider.getXRot());
			caveSpider.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spider.position())), reason, null);
			level.tryAddFreshEntityWithPassengers(caveSpider);
			spider.discard();
			PENDING_CAVE_SPIDER_REPLACEMENTS.remove(entry.getKey());
		}
	}

	private static void clearPiglinFleeMemories(Piglin piglin) {
		if (piglin == null) {
			return;
		}
		piglin.getBrain().eraseMemory(MemoryModuleType.AVOID_TARGET);
		piglin.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED);
	}

	private static void broadcastPiglinAnger(ServerLevel level, Piglin source, LivingEntity target) {
		if (level == null || source == null || target == null || !target.isAlive()) {
			return;
		}
		AABB bounds = source.getBoundingBox().inflate(PIGLIN_ANGER_BROADCAST_RANGE);
		for (Piglin piglin : level.getEntitiesOfClass(Piglin.class, bounds, candidate -> candidate != null && candidate.isAlive() && !candidate.isBaby())) {
			piglin.setTarget(target);
			piglin.setAggressive(true);
			piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
			clearPiglinFleeMemories(piglin);
		}
	}

	private static void broadcastZombifiedPiglinAnger(ServerLevel level, Zombie source, LivingEntity target) {
		if (level == null || source == null || target == null || !target.isAlive()) {
			return;
		}
		AABB bounds = source.getBoundingBox().inflate(ZOMBIFIED_PIGLIN_ANGER_BROADCAST_RANGE);
		for (Zombie pigman : level.getEntitiesOfClass(
			Zombie.class,
			bounds,
			candidate -> candidate != null
				&& candidate.isAlive()
				&& candidate.getType() == EntityType.ZOMBIFIED_PIGLIN
				&& !candidate.isBaby()
		)) {
			pigman.setTarget(target);
			pigman.setAggressive(true);
		}
	}

	private static LivingEntity resolveZombifiedPiglinTarget(ServerLevel level, Zombie pigman) {
		if (level == null || pigman == null || !pigman.isAlive()) {
			return null;
		}
		Piglin nearbyPiglin = findNearestPiglin(level, pigman, ZOMBIFIED_PIGLIN_PIGLIN_AGGRO_RANGE);
		if (nearbyPiglin != null) {
			return nearbyPiglin;
		}
		LivingEntity current = pigman.getTarget();
		if (isValidZombifiedPiglinCurrentTarget(pigman, current)) {
			return current;
		}
		return findNearestUnarmoredPlayer(level, pigman, ZOMBIFIED_PIGLIN_PLAYER_AGGRO_RANGE);
	}

	private static Player findNearestUnarmoredPlayer(ServerLevel level, Mob pigman, double radius) {
		double safeRadius = Math.max(0.0D, radius);
		double radiusSqr = safeRadius * safeRadius;
		AABB bounds = pigman.getBoundingBox().inflate(safeRadius);
		Player nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Player player : level.getEntitiesOfClass(Player.class, bounds, MadokuMobManager::isValidAggroPlayerTarget)) {
			if (isPlayerWearingIronArmor(player)) {
				continue;
			}
			double distance = pigman.distanceToSqr(player);
			if (distance > radiusSqr || !pigman.hasLineOfSight(player)) {
				continue;
			}
			if (distance < nearestDistance) {
				nearest = player;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static Piglin findNearestPiglin(ServerLevel level, Mob pigman, double radius) {
		double safeRadius = Math.max(0.0D, radius);
		double radiusSqr = safeRadius * safeRadius;
		AABB bounds = pigman.getBoundingBox().inflate(safeRadius);
		Piglin nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Piglin piglin : level.getEntitiesOfClass(Piglin.class, bounds, candidate -> candidate != null && candidate.isAlive())) {
			double distance = pigman.distanceToSqr(piglin);
			if (distance > radiusSqr || !pigman.hasLineOfSight(piglin)) {
				continue;
			}
			if (distance < nearestDistance) {
				nearest = piglin;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static boolean isValidZombifiedPiglinCurrentTarget(Zombie pigman, LivingEntity current) {
		if (pigman == null || current == null || !current.isAlive()) {
			return false;
		}
		if (current instanceof Piglin) {
			return isVisibleWithinRange(pigman, current, ZOMBIFIED_PIGLIN_PIGLIN_AGGRO_RANGE);
		}
		if (current instanceof Player player) {
			return !isPlayerWearingIronArmor(player)
				&& isValidAggroPlayerTarget(player)
				&& isVisibleWithinRange(pigman, player, ZOMBIFIED_PIGLIN_PLAYER_AGGRO_RANGE);
		}
		return true;
	}

	private static boolean isVisibleWithinRange(Mob viewer, LivingEntity target, double range) {
		if (viewer == null || target == null || !target.isAlive()) {
			return false;
		}
		double safeRange = Math.max(0.0D, range);
		return viewer.distanceToSqr(target) <= (safeRange * safeRange) && viewer.hasLineOfSight(target);
	}

	private static void suppressBabyPiglinCombat(MinecraftServer server) {
		if (server == null || !snapshot.enabled) {
			return;
		}
		for (Entity entity : TRACKED_ENTITIES.values()) {
			if (entity instanceof Piglin piglin && piglin.isAlive() && piglin.isBaby()) {
				piglin.setTarget(null);
				piglin.setAggressive(false);
				continue;
			}
			if (entity instanceof Zombie zombie && zombie.isAlive() && zombie.isBaby() && zombie.getType() == EntityType.ZOMBIFIED_PIGLIN) {
				zombie.setTarget(null);
				zombie.setAggressive(false);
			}
		}
	}

	private static boolean isValidAggroPlayerTarget(Player player) {
		return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static boolean isPlayerWearingIronArmor(Player player) {
		if (player == null) {
			return false;
		}
		return isIronArmorPiece(player.getItemBySlot(EquipmentSlot.HEAD))
			|| isIronArmorPiece(player.getItemBySlot(EquipmentSlot.CHEST))
			|| isIronArmorPiece(player.getItemBySlot(EquipmentSlot.LEGS))
			|| isIronArmorPiece(player.getItemBySlot(EquipmentSlot.FEET));
	}

	private static boolean isIronArmorPiece(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.is(Items.IRON_HELMET)
			|| stack.is(Items.IRON_CHESTPLATE)
			|| stack.is(Items.IRON_LEGGINGS)
			|| stack.is(Items.IRON_BOOTS);
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
		emitManagedArrowTracked(arrow);
		requestRuntimeProcessing(server, 1L);
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

	private static void spawnSpiderJockey(Spider spider, ServerLevelAccessor world, DifficultyInstance difficulty) {
		ServerLevel level = world.getLevel();
		AbstractSkeleton skeleton = EntityType.SKELETON.create(level, EntitySpawnReason.JOCKEY);
		if (skeleton == null) {
			return;
		}
		skeleton.setPos(spider.getX(), spider.getY(), spider.getZ());
		skeleton.setYRot(spider.getYRot());
		skeleton.setXRot(0.0F);
		skeleton.finalizeSpawn(world, difficulty, EntitySpawnReason.JOCKEY, null);
		ensureBowEquipped(skeleton);
		skeleton.startRiding(spider);
	}

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

	private static SpawnOutcome rollSpiderSpawnOutcome(RandomSource random, double spiderWeight, double caveWeight, double jockeyWeight) {
		double s = Math.max(0.0D, spiderWeight);
		double c = Math.max(0.0D, caveWeight);
		double j = Math.max(0.0D, jockeyWeight);
		double total = s + c + j;
		if (total <= 0.0D) {
			return SpawnOutcome.SPIDER;
		}
		double roll = random.nextDouble() * total;
		if (roll < s) {
			return SpawnOutcome.SPIDER;
		}
		roll -= s;
		return roll < c ? SpawnOutcome.CAVE_SPIDER : SpawnOutcome.SPIDER_JOCKEY;
	}

	private static void clearExistingSkeletonPassengers(Spider spider) {
		for (Entity passenger : new ArrayList<>(spider.getPassengers())) {
			if (passenger.getType() == EntityType.SKELETON) {
				passenger.stopRiding();
				passenger.discard();
			}
		}
	}

	private static void markCrossbowAttackCooldown(Monster attacker) {
		int cooldownTicks = Math.max(0, resolveCrossbowAttackIntervalTicks(attacker));
		if (cooldownTicks <= 0) {
			PILLAGER_ATTACK_COOLDOWNS.remove(attacker.getUUID());
			return;
		}
		PILLAGER_ATTACK_COOLDOWNS.put(attacker.getUUID(), cooldownTicks);
	}

	private static int resolveCrossbowAttackIntervalTicks(Monster attacker) {
		String fileKey = resolveCrossbowShooterFileKey(attacker);
		if (fileKey == null || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = crossbowShooterRoot(attacker);
		if (!isMobFileEnabled(fileKey)) {
			return -1;
		}
		if (root.entrySet().isEmpty()) {
			return -1;
		}
		double interval = readMobStatDouble(root, MadokuMobConfigManager.FIELD_ATTACK_INTERVAL, 20.0D);
		return Math.max(1, (int) Math.round(interval));
	}

	private static int resolveCrossbowChargeUpTicks(Monster attacker) {
		String fileKey = resolveCrossbowShooterFileKey(attacker);
		if (fileKey == null || !snapshot.enabled) {
			return -1;
		}
		JsonObject root = crossbowShooterRoot(attacker);
		if (!isMobFileEnabled(fileKey)) {
			return -1;
		}
		if (root.entrySet().isEmpty()) {
			return -1;
		}
		double charge = readMobStatDouble(root, MadokuMobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0D);
		return Math.max(1, (int) Math.round(charge));
	}

	private static JsonObject crossbowShooterRoot(LivingEntity shooter) {
		if (shooter instanceof Pillager) {
			return fileMobRoot(MadokuMobConfigManager.FILE_PILLAGER);
		}
		if (shooter instanceof Piglin piglin) {
			JsonObject root = fileMobRoot(MadokuMobConfigManager.FILE_PIGLIN);
			return piglinVariantRoot(root, piglin.isBaby());
		}
		return new JsonObject();
	}

	private static String resolveCrossbowShooterFileKey(LivingEntity shooter) {
		if (shooter instanceof Pillager) {
			return MadokuMobConfigManager.FILE_PILLAGER;
		}
		if (shooter instanceof Piglin) {
			return MadokuMobConfigManager.FILE_PIGLIN;
		}
		return null;
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

	private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
		accuracy = MadokuLuck.reduceHostileRangedAccuracyForTarget(target, accuracy);
		double dx = target.getX() - skeleton.getX();
		double dz = target.getZ() - skeleton.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
		Vec3 desired = new Vec3(dx, dy, dz);
		if (desired.lengthSqr() <= 1.0E-6D) {
			return new ShotVector(desired, true);
		}
		if (skeleton.getRandom().nextDouble() <= Mth.clamp(accuracy, 0.0D, 1.0D)) {
			return new ShotVector(desired, true);
		}
		return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, accuracy, skeleton), false);
	}

	private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
		Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
		if (desired.lengthSqr() <= 1.0E-6D) {
			return desired;
		}
		Vec3 normalized = desired.normalize();
		Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (lateral.lengthSqr() <= 1.0E-6D) {
			lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
		}
		lateral = lateral.normalize();
		double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
		double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
		double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
		double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
		Vec3 backwardBias = normalized.scale(-0.35D);
		Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
		return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
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
		return resolveDifficultyAdjustedValue(difficulty, hardcore, baseValue, step, minimum);
	}

	private static int resolveRegionalDifficultyAdjustment(LivingEntity entity) {
		if (!(entity instanceof DifficultyScaledMob scaledMob)) {
			return 0;
		}
		return Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
	}

	private static Double resolveRegionalDifficultyScaledStat(
		Double baseValue,
		int regionalAdjustment,
		JsonObject regionalDifficultyScaleRoot,
		String scalingKey,
		double minimum
	) {
		if (baseValue == null) {
			return null;
		}
		if (regionalAdjustment <= 0 || regionalDifficultyScaleRoot == null || scalingKey == null || scalingKey.isBlank()) {
			return baseValue;
		}
		Double percentPerAdjustment = readOptionalNonNegative(regionalDifficultyScaleRoot, scalingKey);
		if (percentPerAdjustment == null || !Double.isFinite(percentPerAdjustment) || percentPerAdjustment <= 0.0D) {
			return baseValue;
		}
		double multiplier = 1.0D + ((percentPerAdjustment / 100.0D) * regionalAdjustment);
		return Math.max(minimum, baseValue * Math.max(0.0D, multiplier));
	}

	private static Integer resolveRegionalDifficultyScaledIntStat(
		Integer baseValue,
		int regionalAdjustment,
		JsonObject regionalDifficultyScaleRoot,
		String scalingKey
	) {
		if (baseValue == null) {
			return null;
		}
		Double scaled = resolveRegionalDifficultyScaledStat(
			(double) Math.max(0, baseValue),
			regionalAdjustment,
			regionalDifficultyScaleRoot,
			scalingKey,
			0.0D
		);
		if (scaled == null || !Double.isFinite(scaled)) {
			return Math.max(0, baseValue);
		}
		return Math.max(0, (int) Math.round(scaled));
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
		return resolveDifficultyAdjustedValue(difficulty, hardcore, baseValue, fallbackStep, minimum);
	}

	private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
		return Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore)));
	}

	private static double resolveDifficultyAdjustedPercentValue(Difficulty difficulty, boolean hardcore, double baseValue, double stepRatio, double minimum) {
		double multiplier = 1.0D + (stepRatio * resolveDifficultyTier(difficulty, hardcore));
		return Math.max(minimum, baseValue * Math.max(0.0D, multiplier));
	}

	private static double resolveScaledRangedDamage(double base, Difficulty difficulty, boolean hardcore) {
		return resolveDifficultyAdjustedValue(difficulty, hardcore, Math.max(0.0D, base), RANGED_DAMAGE_DIFFICULTY_STEP, 0.0D);
	}

	private static double resolveSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
		if (skeleton == null) {
			return 0.0D;
		}
		double rangedDamage = resolveScaledRangedDamage(
			readMobStatDouble(root, MadokuMobConfigManager.FIELD_RANGED_DAMAGE, 4.0D),
			skeleton.level().getDifficulty(),
			isHardcoreWorld(skeleton.level())
		);
		return MadokuRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, rangedDamage);
	}

	private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
		return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), ATTACK_ACCURACY_DIFFICULTY_STEP, 0.0D), 0.0D, 1.0D);
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

	private static void loadConfig() {
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(MOB_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, MOB_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = JsonStaticSystem.ensureManagedFile(settingsFile, MadokuMobConfigManager.buildMobSystemDefaults());
			boolean enabled = readBoolean(settingsRoot, MadokuMobConfigManager.FIELD_ENABLED, true);
			Path mobsDirectory = rootDirectory.resolve(MOB_CONFIG_MOBS_FOLDER_NAME);
			Map<String, JsonObject> files = DynamicStaticSystem.ensureManagedFolder(
				mobsDirectory,
				MadokuMobConfigManager.buildDefaultMobFileDefaults(),
				MadokuMobConfigManager::buildDynamicMobDefaults,
				(fileKey, sourceRoot) -> true,
				(key, sourceValue) -> null
			);
			snapshot = enabled ? new Snapshot(true, Map.copyOf(files)) : Snapshot.disabled();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
		}
		emitConfigLoaded();
	}

	private static void emitConfigLoaded() {
		String metricId = "mob.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId)) {
			return;
		}
		Snapshot config = snapshot;
		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("mob:global")
			.field("enabled", config.enabled())
			.field("mob_files", config.files().size())
			.log();
	}

	private static void emitManagedArrowTracked(AbstractArrow arrow) {
		String metricId = "mob.arrow_tracked";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId) || arrow == null) {
			return;
		}
		Entity owner = arrow.getOwner();
		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("arrow:" + arrow.getUUID())
			.field("owner", owner == null ? "unknown" : owner.getType().toShortString())
			.field("ttl_ticks", MOB_ARROW_LIFETIME_TICKS)
			.log();
	}

	private static void emitManagedArrowExpired(AbstractArrow arrow) {
		String metricId = "mob.arrow_expired";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, metricId) || arrow == null) {
			return;
		}
		Entity owner = arrow.getOwner();
		MadokuDebug.event(metricId, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("arrow:" + arrow.getUUID())
			.field("owner", owner == null ? "unknown" : owner.getType().toShortString())
			.field("reason", "lifetime_reached")
			.log();
	}

	private static JsonObject root(String key) {
		return snapshot.files.getOrDefault(normalizeKey(key), new JsonObject());
	}

	private static boolean isMobFileEnabled(String fileKey) {
		return readBoolean(root(fileKey), MadokuMobConfigManager.FIELD_ENABLED, true);
	}

	private static JsonObject fileMobRoot(String fileKey) {
		return readObject(root(fileKey), fileKey);
	}

	private static JsonObject skeletonRoot(EntityType<?> type) {
		if (type == EntityType.SKELETON) {
			return fileMobRoot(MadokuMobConfigManager.FILE_SKELETON);
		}
		if (type == EntityType.STRAY) {
			return fileMobRoot(MadokuMobConfigManager.FILE_STRAY);
		}
		if (type == EntityType.BOGGED) {
			return fileMobRoot(MadokuMobConfigManager.FILE_BOGGED);
		}
		if (type == EntityType.PARCHED) {
			return fileMobRoot(MadokuMobConfigManager.FILE_PARCHED);
		}
		if (type == EntityType.WITHER_SKELETON) {
			return fileMobRoot(MadokuMobConfigManager.FILE_WITHER_SKELETON);
		}
		return new JsonObject();
	}

	private static JsonObject zombieRoot(EntityType<?> type) {
		if (type == EntityType.ZOMBIE) {
			return fileMobRoot(MadokuMobConfigManager.FILE_ZOMBIE);
		}
		if (type == EntityType.HUSK) {
			return fileMobRoot(MadokuMobConfigManager.FILE_HUSK);
		}
		if (type == EntityType.DROWNED) {
			return fileMobRoot(MadokuMobConfigManager.FILE_DROWNED);
		}
		if (type == EntityType.ZOMBIE_VILLAGER) {
			return fileMobRoot(MadokuMobConfigManager.FILE_ZOMBIE_VILLAGER);
		}
		return new JsonObject();
	}

	private static JsonObject zombieAdultRoot(EntityType<?> type, JsonObject root) {
		String field = type == EntityType.ZOMBIE ? MadokuMobConfigManager.FIELD_ADULT_ZOMBIE
			: type == EntityType.HUSK ? MadokuMobConfigManager.FIELD_ADULT_HUSK
			: type == EntityType.DROWNED ? MadokuMobConfigManager.FIELD_ADULT_DROWNED
			: MadokuMobConfigManager.FIELD_ADULT_ZOMBIE_VILLAGER;
		return readObject(root, field);
	}

	private static JsonObject zombieBabyRoot(EntityType<?> type, JsonObject root) {
		String field = type == EntityType.ZOMBIE ? MadokuMobConfigManager.FIELD_BABY_ZOMBIE
			: type == EntityType.HUSK ? MadokuMobConfigManager.FIELD_BABY_HUSK
			: type == EntityType.DROWNED ? MadokuMobConfigManager.FIELD_BABY_DROWNED
			: MadokuMobConfigManager.FIELD_BABY_ZOMBIE_VILLAGER;
		return readObject(root, field);
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static JsonObject readMobStatsRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_MOB_STATS);
	}

	private static JsonObject readMobBehaviorRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_MOB_BEHAVIOR);
	}

	private static JsonObject readSpawnRulesRoot(JsonObject root) {
		return readObject(root, MadokuMobConfigManager.FIELD_SPAWN_RULES);
	}

	private static JsonObject resolveBeeRoot(LivingEntity entity, JsonObject beeFileRoot, RandomSource random, boolean spawnContext) {
		if (entity == null || beeFileRoot == null) {
			return new JsonObject();
		}
		JsonObject beeRoot = fileMobRoot(MadokuMobConfigManager.FILE_BEE);
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
		JsonObject defaultGroup = readObject(beeRoot, MadokuMobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return beeRoot;
		}
		boolean overrideSpawnRules = readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean variantEnabled = readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_MOB_VARIANT, false);
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
		JsonObject adult = readObject(baseVariantRoot, MadokuMobConfigManager.FIELD_ADULT_GROUP);
		JsonObject baby = readObject(baseVariantRoot, MadokuMobConfigManager.FIELD_BABY_GROUP);
		if (adult.entrySet().isEmpty() && baby.entrySet().isEmpty()) {
			return baseVariantRoot;
		}
		boolean babyEnabled = readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_MOB_BABY, true);
		boolean overrideSpawnRules = readBoolean(beeFileRoot, MadokuMobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean isBaby = entity instanceof AgeableMob ageableMob && ageableMob.isBaby();
		if (entity instanceof AgeableMob ageableMob && spawnContext && babyEnabled && overrideSpawnRules && random != null) {
			double adultWeight = Math.max(0.0D, readSpawnRuleDouble(adult, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 100.0D));
			double babyWeight = Math.max(0.0D, readSpawnRuleDouble(baby, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D));
			double total = adultWeight + babyWeight;
			if (total > 0.0D) {
				isBaby = (random.nextDouble() * total) >= adultWeight;
				ageableMob.setBaby(isBaby);
			}
		}
		JsonObject sharedRoot = baseVariantRoot.deepCopy();
		sharedRoot.remove(MadokuMobConfigManager.FIELD_ADULT_GROUP);
		sharedRoot.remove(MadokuMobConfigManager.FIELD_BABY_GROUP);
		if (isBaby && !baby.entrySet().isEmpty()) {
			return mergeJsonWithOverride(sharedRoot, baby);
		}
		if (!adult.entrySet().isEmpty()) {
			return mergeJsonWithOverride(sharedRoot, adult);
		}
		return mergeJsonWithOverride(sharedRoot, baby);
	}

	private static String selectBeeVariantKey(JsonObject beeRoot, RandomSource random) {
		if (beeRoot == null || random == null) {
			return BEE_VARIANT_DEFAULT_KEY;
		}
		JsonObject defaultGroup = readObject(beeRoot, MadokuMobConfigManager.FIELD_DEFAULT_GROUP);
		double defaultWeight = Math.max(0.0D, readDouble(defaultGroup, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 100.0D));
		double total = defaultWeight;
		java.util.List<BeeVariantWeight> weightedVariants = new ArrayList<>();
		for (Map.Entry<String, JsonObject> entry : collectBeeVariantRoots(beeRoot).entrySet()) {
			String key = entry.getKey();
			JsonObject variantRoot = entry.getValue();
			double weight = Math.max(0.0D, resolveBeeVariantSpawnWeight(variantRoot, 0.0D));
			if (weight <= 0.0D) {
				continue;
			}
			total += weight;
			weightedVariants.add(new BeeVariantWeight(key, weight));
		}
		if (total <= 0.0D) {
			return BEE_VARIANT_DEFAULT_KEY;
		}
		double roll = random.nextDouble() * total;
		if (roll < defaultWeight) {
			return BEE_VARIANT_DEFAULT_KEY;
		}
		double cursor = defaultWeight;
		for (BeeVariantWeight weighted : weightedVariants) {
			cursor += weighted.weight();
			if (roll < cursor) {
				return weighted.key();
			}
		}
		return BEE_VARIANT_DEFAULT_KEY;
	}

	private static Map<String, JsonObject> collectBeeVariantRoots(JsonObject beeRoot) {
		Map<String, JsonObject> variants = new java.util.LinkedHashMap<>();
		if (beeRoot == null) {
			return variants;
		}
		for (Map.Entry<String, JsonElement> entry : beeRoot.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			String key = normalizeKey(entry.getKey());
			if (isReservedBeeGroupKey(key)) {
				continue;
			}
			variants.putIfAbsent(key, entry.getValue().getAsJsonObject());
		}
		return variants;
	}

	private static JsonObject resolveBeeVariantRootByKey(JsonObject beeRoot, String variantKey) {
		if (beeRoot == null || variantKey == null || variantKey.isBlank()) {
			return new JsonObject();
		}
		JsonObject variant = collectBeeVariantRoots(beeRoot).get(normalizeKey(variantKey));
		return variant == null ? new JsonObject() : variant;
	}

	private static boolean isReservedBeeGroupKey(String normalizedKey) {
		if (normalizedKey == null || normalizedKey.isBlank()) {
			return true;
		}
		return normalizedKey.equals(normalizeKey(MadokuMobConfigManager.FIELD_DEFAULT_GROUP))
			|| normalizedKey.equals(normalizeKey(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE))
			|| normalizedKey.equals(normalizeKey(MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING));
	}

	private static double resolveBeeVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		if (variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return fallback;
		}
		Double direct = readOptionalDouble(variantRoot, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT);
		if (direct != null) {
			return direct;
		}
		if (!readMobStatsRoot(variantRoot).entrySet().isEmpty()) {
			return readSpawnRuleDouble(variantRoot, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, fallback);
		}
		JsonObject adult = readObject(variantRoot, MadokuMobConfigManager.FIELD_ADULT_GROUP);
		JsonObject baby = readObject(variantRoot, MadokuMobConfigManager.FIELD_BABY_GROUP);
		return Math.max(0.0D, readSpawnRuleDouble(adult, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D))
			+ Math.max(0.0D, readSpawnRuleDouble(baby, MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 0.0D));
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
			if (!merged.has(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALING) && beeRoot.has(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALING)) {
				merged.add(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALING, beeRoot.get(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALING).deepCopy());
			}
			if (!merged.has(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE) && beeRoot.has(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE)) {
				merged.add(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE, beeRoot.get(MadokuMobConfigManager.FIELD_DIFFICULTY_SCALE).deepCopy());
			}
			if (!merged.has(MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING) && beeRoot.has(MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING)) {
				merged.add(MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, beeRoot.get(MadokuMobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING).deepCopy());
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

	private static JsonObject readSpawnArmorSetRoot(JsonObject root) {
		return readObject(readSpawnRulesRoot(root), MadokuMobConfigManager.FIELD_ARMOR_SET);
	}

	private static JsonObject readSpawnArmorRarityRoot(JsonObject root) {
		return readObject(readSpawnRulesRoot(root), MadokuMobConfigManager.FIELD_ARMOR_RARITY);
	}

	private static double readMobStatDouble(JsonObject root, String key, double fallback) {
		return readDouble(readMobStatsRoot(root), key, fallback);
	}

	private static double readSpawnRuleDouble(JsonObject root, String key, double fallback) {
		return readDouble(readSpawnRulesRoot(root), key, fallback);
	}

	private static boolean readMobBehaviorBoolean(JsonObject root, String key, boolean fallback) {
		return readBoolean(readMobBehaviorRoot(root), key, fallback);
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

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private record HomingArrowState(UUID targetUuid, double speed, int remainingTicks) {}
	private record ShotVector(Vec3 vector, boolean guaranteedHit) {}
	private record SpawnWeightPair(double regularWeight, double specialWeight) {}
	private record BeeVariantWeight(String key, double weight) {}
	private enum SpawnArmorMaterial {
		NETHERITE,
		DIAMOND,
		GOLD,
		IRON,
		COPPER,
		LEATHER
	}
	private enum SpawnArmorCoverage {
		HELMET_ONLY,
		HELMET_BOOTS,
		FULL_SET
	}

	private enum SpawnOutcome {
		SPIDER,
		CAVE_SPIDER,
		SPIDER_JOCKEY
	}

	private record Snapshot(boolean enabled, Map<String, JsonObject> files) {
		private static Snapshot disabled() {
			return new Snapshot(false, Map.of());
		}
	}
}



