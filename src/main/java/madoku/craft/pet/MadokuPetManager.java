package madoku.craft.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.data.DataPlayerManager;
import madoku.craft.entity.Hag;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.mob.MobEntityManager;
import madoku.craft.mixin.MobTargetSelectorAccessor;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import madoku.craft.pet.PetComponentsManager.PetHolder;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetConfigManager.PetRule;
import madoku.craft.pet.PetEntitiesManager.FollowCommand;
import madoku.craft.pet.PetEntitiesManager.MovementController;

public final class MadokuPetManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuPetManager.class);

	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "MadokuPets";

	private static final String DATA_FILE_NAME = "madoku-pets";
	private static final String TASK_TYPE_PET_ATTACK = "pet_attack";
	private static final String TASK_TYPE_PET_RUNTIME_TICK = "pet_runtime_tick";
	private static final String PET_RUNTIME_SCHEDULER_KEY = "pet_runtime_tick";
	private static final long PET_RUNTIME_TICK_DELAY = 1L;
	private static final String PLAYER_SCHEDULER_KEY = "player_entities";
	private static final String MANAGED_PET_TAG = "madoku-craft.pet";
	private static final String MANAGED_PET_OWNER_PREFIX = "madoku-craft.pet.owner:";
	private static final String MANAGED_PET_ITEM_PREFIX = "madoku-craft.pet.item:";
	static final String PET_ABILITY_NONE = "none";
	static final String PET_ABILITY_RANGED_HOMING_ARROW = "ranged_homing_arrow";
	static final String PET_ABILITY_WEB_PROJECTILE = "web_projectile";
	static final String PET_ABILITY_EXPLOSIVE_PROJECTILE = "explosive_projectile";
	static final String PET_ABILITY_PLAYER_DAMAGE_BONUS = "player_damage_bonus";
	static final String PET_ABILITY_FALL_DAMAGE_REDUCTION = "fall_damage_reduction";
	static final String PET_ABILITY_MAX_HEALTH_BONUS = "max_health_bonus";
	static final String PET_ABILITY_ARMOR_BONUS = "armor_bonus";
	static final String PET_ABILITY_DAMAGE_BLOCK = "damage_block";
	static final String PET_ABILITY_MOB_SCAN = "mob_scan";
	static final String PET_ABILITY_BEE_SWARM = "bee_swarm";
	static final String PET_RARITY_COMMON = "common";
	static final String PET_RARITY_RARE = "rare";
	static final String PET_RARITY_EPIC = "epic";
	static final String PET_RARITY_MYTHIC = "mythic";
	private static final int WEB_PROJECTILE_LIFETIME_TICKS = 20;
	private static final double WEB_PROJECTILE_HIT_DISTANCE = 0.75D;
	private static final double WEB_PROJECTILE_MIN_SPEED = 0.20D;
	private static final int WEB_PROJECTILE_SLOW_AMPLIFIER = 9;
	private static final int EXPLOSIVE_PROJECTILE_LIFETIME_TICKS = 20;
	private static final double EXPLOSIVE_PROJECTILE_HIT_DISTANCE = 1.0D;
	private static final double EXPLOSIVE_PROJECTILE_MIN_SPEED = 0.5D;
	private static final int BAT_SCAN_BASE_VERTICAL_RADIUS_BLOCKS = 8;
	private static final int BAT_SCAN_VERTICAL_RADIUS_PER_EXTRA_BAT = 4;
	private static final int BAT_SCAN_BASE_CHUNK_RADIUS = 1;
	private static final int BAT_SCAN_CHUNK_RADIUS_PER_EXTRA_BAT = 1;
	private static final long BAT_SCAN_GLOWING_DURATION_TICKS = 90L * 20L;
	private static final long BAT_SCAN_COOLDOWN_REDUCTION_PER_EXTRA_BAT = 30L * 20L;
	private static final double BEE_SWARM_SCAN_RADIUS = 16.0D;
	private static final double BEE_SWARM_SCAN_VERTICAL_RADIUS = 8.0D;
	private static final long BEE_SWARM_MAX_TARGET_DURATION_TICKS = 15L * 20L;
	private static final long BEE_SWARM_DAMAGE_INTERVAL_TICKS = 20L;
	private static final float BEE_SWARM_DEFAULT_DAMAGE_PER_SECOND = 2.0F;
	private static final double BEE_SWARM_ORBIT_RADIUS_BASE = 0.70D;
	private static final double BEE_SWARM_ORBIT_RADIUS_VARIANCE = 0.30D;
	private static final double BEE_SWARM_ORBIT_VERTICAL_VARIANCE = 0.30D;
	private static final double BEE_SWARM_MAX_MOVE_PER_TICK = 0.38D;
	private static final String FIELD_SLOT = "slot";
	private static final String FIELD_TARGET_UUID = "target-uuid";
	private static final String FIELD_SPAWN_X = "spawn-x";
	private static final String FIELD_SPAWN_Y = "spawn-y";
	private static final String FIELD_SPAWN_Z = "spawn-z";
	static final Map<UUID, UUID[]> PET_IDS_BY_PLAYER = new ConcurrentHashMap<>();
	static final Set<UUID> ACTIVE_PET_IDS = ConcurrentHashMap.newKeySet();
	static final Map<UUID, Long> NEXT_IDLE_MOVE_BY_PET = new ConcurrentHashMap<>();
	static final Map<UUID, FollowCommand> FOLLOW_COMMANDS_BY_PET = new ConcurrentHashMap<>();
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_PROCESS_TICKS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, long[]> PLAYER_SLOT_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, ItemStack[]> PENDING_RESPAWN_PET_INVENTORIES = new ConcurrentHashMap<>();
	private static final Map<UUID, WebProjectileState> ACTIVE_WEB_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, ExplosiveProjectileState> ACTIVE_EXPLOSIVE_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<String, BeeSwarmState> ACTIVE_BEE_SWARMS = new ConcurrentHashMap<>();

	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile String runtimeSchedulerId = "";
	private static volatile boolean runtimeTickQueued;

	private MadokuPetManager() {
	}

	public static void initialize() {
		PetConfigManager.initialize();
		PetEntitiesManager.initialize();
		PetAbilitiesManager.initialize();
		PetHudManager.initialize();
		PetHagManager.initialize();
		PetComponentsManager.initialize();
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_PET_ATTACK, MadokuPetManager::runPetAttack);
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_PET_RUNTIME_TICK, MadokuPetManager::runPetRuntimeTick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}

			PENDING_RESPAWN_PET_INVENTORIES.remove(player.getUUID());
			removeAllPets(player.level().getServer(), player.getUUID());
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
				return;
			}

			dropAll(player);
			cachePendingRespawnPetInventory(player);
			clearSlotCooldowns(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MadokuPetManager::handleAfterDamage);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			removeAllPets(newPlayer.level().getServer(), oldPlayer.getUUID());
			if (alive) {
				copyToNewPlayer(oldPlayer, newPlayer);
			} else if (!applyPendingRespawnPetInventory(newPlayer, oldPlayer.getUUID())) {
				copyToNewPlayer(oldPlayer, newPlayer);
			}
			requestPetProcessing(newPlayer.level().getServer(), newPlayer.getUUID(), 0L);
		});
		ServerPlayerEvents.JOIN.register(MadokuPetManager::handlePlayerJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler == null || handler.player == null) {
				return;
			}
			handlePlayerLeave(server, handler.player.getUUID());
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			UUID entityId = entity.getUUID();
			ACTIVE_PET_IDS.remove(entityId);
			NEXT_IDLE_MOVE_BY_PET.remove(entityId);
			FOLLOW_COMMANDS_BY_PET.remove(entityId);
			PetPayloadManager.removeSoundState(entityId);
		});
	}

	public static void reset() {
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
		PLAYER_SCHEDULER_IDS.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		PetHudManager.clear();
		PLAYER_SLOT_COOLDOWNS.clear();
		PENDING_RESPAWN_PET_INVENTORIES.clear();
		ACTIVE_WEB_PROJECTILES.clear();
		ACTIVE_EXPLOSIVE_PROJECTILES.clear();
		ACTIVE_BEE_SWARMS.clear();
		PetPayloadManager.clearSoundState();
		lastAutosaveBucket = Long.MIN_VALUE;
		runtimeSchedulerId = "";
		runtimeTickQueued = false;
	}

	public static void onServerStarted(MinecraftServer server) {
		ensureRuntimeQueued(server, PET_RUNTIME_TICK_DELAY);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		reloadConfig();
		JsonObject data = DataPlayerManager.getSystemData(DATA_FILE_NAME, "slot-cooldowns", "uuid");
		applyPersistedData(data);
		removeTaggedPets(server);
		long autoSaveIntervalTicks = DataPlayerManager.getAutoSaveIntervalTicks();
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataPlayerManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		DataPlayerManager.setSystemData(DATA_FILE_NAME, toPersistedData(), "slot-cooldowns", "uuid");
	}

	private static void onPlayerTickPhase(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long gameplayTick = MadokuTimeManager.getGameplayTicks();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			onPlayerTick(server, player, gameplayTick);
		}
	}

	private static void onServerTick(MinecraftServer server) {
		if (server == null) {
			return;
		}

		refreshPlayerPassiveAbilityBonuses(server);

		if (!PetConfigManager.settings().enabled) {
			clearAllManagedPetState(server);
			PetHudManager.flushAbilityHudSyncs(server);
			return;
		}

		if (!PetConfigManager.areEntitiesEnabled()) {
			clearManagedPetEntityState(server);
		}

		tickManagedWebProjectiles(server);
		tickManagedExplosiveProjectiles(server);
		tickManagedBeeSwarms(server);
		PetHudManager.flushAbilityHudSyncs(server);
	}

	private static void runPetRuntimeTick(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
		runtimeTickQueued = false;
		if (server == null || context == null) {
			return;
		}

		runtimeSchedulerId = context.getSchedulerId();
		onPlayerTickPhase(server);
		onServerTick(server);
		ensureRuntimeQueued(server, PET_RUNTIME_TICK_DELAY);
	}

	private static void ensureRuntimeQueued(MinecraftServer server, long delayTicks) {
		if (server == null || runtimeTickQueued) {
			return;
		}

		String currentSchedulerId = ensureRuntimeSchedulerExists();
		if (MadokuSchedulerManager.hasQueuedTask(currentSchedulerId, TASK_TYPE_PET_RUNTIME_TICK)) {
			runtimeTickQueued = true;
			return;
		}
		if (enqueueRuntimeTask(currentSchedulerId, delayTicks)) {
			runtimeTickQueued = true;
			return;
		}

		runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(PET_RUNTIME_SCHEDULER_KEY)
		);
		if (enqueueRuntimeTask(runtimeSchedulerId, delayTicks)) {
			runtimeTickQueued = true;
		}
	}

	private static String ensureRuntimeSchedulerExists() {
		String current = runtimeSchedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		runtimeSchedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(PET_RUNTIME_SCHEDULER_KEY)
		);
		return runtimeSchedulerId;
	}

	private static boolean enqueueRuntimeTask(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_PET_RUNTIME_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED
			|| status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}


	public static boolean isManagedPet(Entity entity) {
		return entity != null
			&& (ACTIVE_PET_IDS.contains(entity.getUUID())
				|| entity.entityTags().contains(MANAGED_PET_TAG)
				|| PetPayloadManager.hasSoundState(entity.getUUID()));
	}



	public static long managedPetSteeringInterval() {
		return PetEntitiesManager.activeSchedulerTickInterval();
	}

	public static Vec3 managedPetMovementTarget(Mob pet) {
		return MovementController.managedPetMovementTarget(pet, FOLLOW_COMMANDS_BY_PET);
	}

	public static double managedPetMovementSpeed(Mob pet, double fallbackSpeed) {
		return MovementController.managedPetMovementSpeed(pet, fallbackSpeed, FOLLOW_COMMANDS_BY_PET);
	}

	public static boolean isEnabled() {
		return PetConfigManager.settings().enabled;
	}

	public static boolean areEntitiesEnabled() {
		return PetConfigManager.areEntitiesEnabled();
	}





	public static void dropAll(ServerPlayer player) {
		PetInventory petInventory = petInventory(player);
		if (player == null || petInventory == null) {
			return;
		}

		List<Integer> occupiedSlots = new ArrayList<>(petInventory.getContainerSize());
		for (int slot = 0; slot < petInventory.getContainerSize(); slot++) {
			if (!petInventory.getItem(slot).isEmpty()) {
				occupiedSlots.add(slot);
			}
		}
		if (occupiedSlots.isEmpty()) {
			return;
		}

		int dropPercent = MadokuItemStack.usesManagedDeathDrop() ? MadokuItemStack.getDeathDropStackPercent() : 100;
		for (int i = occupiedSlots.size() - 1; i > 0; i--) {
			int j = player.getRandom().nextInt(i + 1);
			if (i == j) {
				continue;
			}
			int temp = occupiedSlots.get(i);
			occupiedSlots.set(i, occupiedSlots.get(j));
			occupiedSlots.set(j, temp);
		}

		int dropCount = Math.min(
			occupiedSlots.size(),
			Math.max(0, Math.round(occupiedSlots.size() * (Math.max(0, Math.min(100, dropPercent)) / 100.0F)))
		);
		for (int index = 0; index < dropCount; index++) {
			int slot = occupiedSlots.get(index);
			ItemStack stack = petInventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			player.drop(stack, true, false);
			petInventory.setItem(slot, ItemStack.EMPTY);
		}
		petInventory.setChanged();
	}

	public static int countPets(Player player) {
		PetInventory petInventory = petInventory(player);
		if (petInventory == null) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < petInventory.getContainerSize(); slot++) {
			if (PetConfigManager.isValidPet(petInventory.getItem(slot))) {
				count++;
			}
		}
		return count;
	}

	static void reloadConfig() {
		PetConfigManager.reload();
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}

		handlePlayerLeave(server, player.getUUID());
		PetHudManager.markAbilityHudDirty(player.getUUID());
		PetAbilitiesManager.applyPlayerMaxHealthAbilityBonus(player);
		PetAbilitiesManager.applyPlayerArmorAbilityBonus(player);
		PetAbilitiesManager.applyPlayerDamageAbilityBonus(player);
		syncManagedPetSoundStateTo(player, server);
		if (PetConfigManager.settings().enabled) {
			requestPetProcessing(server, player.getUUID(), 0L);
		}
	}

	private static void syncManagedPetSoundStateTo(ServerPlayer player, MinecraftServer server) {
		if (player == null || server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof Mob pet) || !isManagedPet(pet)) {
					continue;
				}
				PetRule rule = PetConfigManager.resolvePetRule(pet);
				PetHudManager.sendSoundState(player, pet.getUUID().toString(), rule == null ? "" : rule.itemId);
			}
		}
	}

	static void broadcastManagedPetSoundState(MinecraftServer server, UUID petId, String itemId) {
		if (server == null || petId == null) {
			return;
		}
		for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
			PetHudManager.sendSoundState(onlinePlayer, petId.toString(), itemId);
		}
	}

	private static void handlePlayerLeave(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		removeAllPets(server, playerId);
		stopBeeSwarmsForOwner(playerId);
		PLAYER_SCHEDULER_IDS.remove(playerId);
		NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);
		PetHudManager.clearPlayer(playerId);
		PENDING_RESPAWN_PET_INVENTORIES.remove(playerId);
	}

	private static void handleAfterDamage(
		LivingEntity entity,
		net.minecraft.world.damagesource.DamageSource source,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked
	) {
		if (!PetConfigManager.settings().enabled || damageTaken <= 0.0F || blocked) {
			return;
		}
		if (entity != null && source.getEntity() instanceof ServerPlayer playerAttacker) {
			PetAbilitiesManager.triggerReactiveAbilities(playerAttacker, entity);
		}

		if (entity instanceof ServerPlayer playerVictim) {
			LivingEntity attackerTarget = resolveDamageSourceLivingEntity(source);
			if (attackerTarget != null) {
				PetAbilitiesManager.triggerReactiveAbilities(playerVictim, attackerTarget);
			}
		}
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

	static void triggerReactivePetAttacks(ServerPlayer player, LivingEntity target) {
		if (!canReactiveAttackTarget(player, target)) {
			return;
		}

		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}

		long gameplayTicks = MadokuTimeManager.getGameplayTicks();
		int[] readySlots = new int[SLOT_COUNT];
		PetRule[] readyRules = new PetRule[SLOT_COUNT];
		int readyCount = 0;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null || !rule.canPerformReactiveAttack()) {
				continue;
			}
			if (!canPetSlotShoot(player, slot, gameplayTicks, stack)) {
				continue;
			}

			readySlots[readyCount] = slot;
			readyRules[readyCount] = rule;
			readyCount++;
		}
		if (readyCount <= 0) {
			return;
		}

		for (int index = 0; index < readyCount; index++) {
			int slot = readySlots[index];
			PetRule rule = readyRules[index];
			Vec3 spawnPosition = resolveRangedAttackSpawn(player, index, readyCount, rule);
			if (index == 0) {
				if (spawnPetReactiveAttack(player, target, spawnPosition, rule)) {
					setSlotCooldown(player.getUUID(), slot, gameplayTicks + rule.cooldownTicks);
				}
				continue;
			}

			enqueueDelayedPetAttack(player, slot, target, spawnPosition, index * rule.shotDelayTicks);
		}
	}

	private static LivingEntity resolveOngoingReactiveTarget(ServerPlayer player) {
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
		if (sharedRule == null || sharedRule.cooldownTicks <= 0L) {
			return;
		}

		long sharedCooldownTick = synchronizeSharedAbilityCooldown(player.getUUID(), batSlots, batCount, gameplayTicks);
		if (gameplayTicks < sharedCooldownTick) {
			return;
		}

		applyAutomaticBatMobScan(player, batCount, sharedRule);
		setSharedAbilityCooldown(player.getUUID(), batSlots, batCount, gameplayTicks + effectiveBatScanCooldownTicks(batCount, sharedRule));
	}

	private static void applyAutomaticBatMobScan(ServerPlayer player, int batCount, PetRule rule) {
		if (player == null || batCount <= 0 || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		SoundEvent soundEvent = rule == null ? SoundEvents.BEACON_ACTIVATE : rule.resolveSoundEvent();
		level.playSound(
			null,
			player.getX(),
			player.getEyeY(),
			player.getZ(),
			soundEvent,
			SoundSource.NEUTRAL,
			0.45F,
			1.15F
		);

		int chunkRadius = BAT_SCAN_BASE_CHUNK_RADIUS + Math.max(0, batCount - 1) * BAT_SCAN_CHUNK_RADIUS_PER_EXTRA_BAT;
		int horizontalRadius = chunkRadius * 16;
		int verticalRadius = BAT_SCAN_BASE_VERTICAL_RADIUS_BLOCKS + Math.max(0, batCount - 1) * BAT_SCAN_VERTICAL_RADIUS_PER_EXTRA_BAT;
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
					mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) BAT_SCAN_GLOWING_DURATION_TICKS, 0, false, false, true));
				}
			}
		}
	}

	static long effectiveBatScanCooldownTicks(int batCount, PetRule rule) {
		long baseCooldown = Math.max(0L, rule == null ? 0L : rule.cooldownTicks);
		int additionalBats = Math.max(0, batCount - 1);
		return Math.max(20L, baseCooldown - (additionalBats * BAT_SCAN_COOLDOWN_REDUCTION_PER_EXTRA_BAT));
	}

	static void triggerAutomaticBeeSwarm(ServerPlayer player, long gameplayTicks) {
		if (player == null || !player.isAlive()) {
			return;
		}

		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return;
		}

		int[] readySlots = new int[SLOT_COUNT];
		PetRule[] readyRules = new PetRule[SLOT_COUNT];
		int readyCount = 0;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null || !rule.enabled || !PET_ABILITY_BEE_SWARM.equals(rule.abilityType)) {
				stopBeeSwarmForSlot(player.getUUID(), slot);
				continue;
			}
			if (ACTIVE_BEE_SWARMS.containsKey(beeSwarmKey(player.getUUID(), slot))) {
				continue;
			}
			if (!isPetSlotOffCooldown(player, slot, gameplayTicks)) {
				continue;
			}
			readySlots[readyCount] = slot;
			readyRules[readyCount] = rule;
			readyCount++;
		}
		if (readyCount <= 0) {
			return;
		}

		List<LivingEntity> prioritizedTargets = resolveAutomaticBeeSwarmTargets(player);
		if (prioritizedTargets.isEmpty()) {
			return;
		}

		Set<UUID> claimedTargetIds = collectOwnerActiveBeeSwarmTargetIds(player.getUUID());
		for (int index = 0; index < readyCount; index++) {
			int slot = readySlots[index];
			PetRule rule = readyRules[index];
			LivingEntity target = selectBeeSwarmTarget(prioritizedTargets, claimedTargetIds);
			if (target == null) {
				break;
			}
			if (!startBeeSwarm(player, slot, target, rule, gameplayTicks)) {
				continue;
			}
			claimedTargetIds.add(target.getUUID());
			setSlotCooldown(player.getUUID(), slot, gameplayTicks + rule.cooldownTicks);
		}
	}

	private static List<LivingEntity> resolveAutomaticBeeSwarmTargets(ServerPlayer player) {
		if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
			return List.of();
		}

		Map<UUID, LivingEntity> prioritizedTargets = new LinkedHashMap<>();
		addBeeSwarmTargetCandidate(player, player.getLastHurtMob(), prioritizedTargets);
		addBeeSwarmTargetCandidate(player, player.getLastHurtByMob(), prioritizedTargets);

		AABB scanArea = new AABB(
			player.getX() - BEE_SWARM_SCAN_RADIUS,
			player.getY() - BEE_SWARM_SCAN_VERTICAL_RADIUS,
			player.getZ() - BEE_SWARM_SCAN_RADIUS,
			player.getX() + BEE_SWARM_SCAN_RADIUS,
			player.getY() + BEE_SWARM_SCAN_VERTICAL_RADIUS,
			player.getZ() + BEE_SWARM_SCAN_RADIUS
		);
		List<Mob> hostileMobs = level.getEntitiesOfClass(Mob.class, scanArea, candidate -> isValidBeeSwarmTarget(player, candidate));
		if (!hostileMobs.isEmpty()) {
			hostileMobs.sort((left, right) -> Double.compare(left.distanceToSqr(player), right.distanceToSqr(player)));
			for (Mob hostile : hostileMobs) {
				addBeeSwarmTargetCandidate(player, hostile, prioritizedTargets);
			}
		}
		if (prioritizedTargets.isEmpty()) {
			return List.of();
		}
		return new ArrayList<>(prioritizedTargets.values());
	}

	private static void addBeeSwarmTargetCandidate(ServerPlayer player, LivingEntity target, Map<UUID, LivingEntity> out) {
		if (out == null || !isValidBeeSwarmTarget(player, target)) {
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
		if (!canReactiveAttackTarget(player, target)) {
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

	private static boolean startBeeSwarm(ServerPlayer player, int slot, LivingEntity target, PetRule rule, long gameplayTicks) {
		if (player == null || target == null || rule == null || !PET_ABILITY_BEE_SWARM.equals(rule.abilityType) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isValidBeeSwarmTarget(player, target)) {
			return false;
		}

		Vec3 startPosition = resolveBeeSwarmOrbitPosition(target, player.getRandom().nextDouble() * Math.PI * 2.0D, BEE_SWARM_ORBIT_RADIUS_BASE, 0.05D);
		emitBeeSwarmLaunch(level, startPosition, rule.resolveSoundEvent(), Math.max(0.12F, rule.soundVolumeMultiplier), 1.2F);
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

	private static boolean hasAutomaticPetAbilities(PetInventory inventory) {
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
			if (rule == null || !rule.enabled || !abilityType.equals(rule.abilityType)) {
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

	private static long synchronizeSharedAbilityCooldown(UUID playerId, int[] slots, int slotCount, long gameplayTicks) {
		if (playerId == null || slots == null || slotCount <= 0) {
			return 0L;
		}

		long[] cooldowns = PLAYER_SLOT_COOLDOWNS.get(playerId);
		if (cooldowns == null) {
			return 0L;
		}

		long sharedCooldownTick = 0L;
		for (int index = 0; index < slotCount; index++) {
			int slot = slots[index];
			if (slot < 0 || slot >= Math.min(SLOT_COUNT, cooldowns.length)) {
				continue;
			}
			sharedCooldownTick = Math.max(sharedCooldownTick, cooldowns[slot]);
		}
		if (sharedCooldownTick > 0L && sharedCooldownTick <= gameplayTicks) {
			sharedCooldownTick = 0L;
		}

		boolean changed = false;
		for (int index = 0; index < slotCount; index++) {
			int slot = slots[index];
			if (slot < 0 || slot >= Math.min(SLOT_COUNT, cooldowns.length) || cooldowns[slot] == sharedCooldownTick) {
				continue;
			}
			cooldowns[slot] = sharedCooldownTick;
			changed = true;
		}
		if (changed) {
			if (!hasNonZeroCooldown(cooldowns)) {
				PLAYER_SLOT_COOLDOWNS.remove(playerId);
			}
			PetHudManager.markAbilityHudDirty(playerId);
		}
		return sharedCooldownTick;
	}

	private static void setSharedAbilityCooldown(UUID playerId, int[] slots, int slotCount, long cooldownTick) {
		if (playerId == null || slots == null || slotCount <= 0) {
			return;
		}

		long[] cooldowns = slotCooldowns(playerId);
		long normalizedCooldownTick = Math.max(0L, cooldownTick);
		boolean changed = false;
		for (int index = 0; index < slotCount; index++) {
			int slot = slots[index];
			if (slot < 0 || slot >= Math.min(SLOT_COUNT, cooldowns.length) || cooldowns[slot] == normalizedCooldownTick) {
				continue;
			}
			cooldowns[slot] = normalizedCooldownTick;
			changed = true;
		}
		if (changed) {
			PetHudManager.markAbilityHudDirty(playerId);
		}
	}

	static void onPlayerTick(MinecraftServer server, ServerPlayer player, long gameplayTick) {
		if (server == null || player == null) {
			return;
		}

		UUID playerId = player.getUUID();
		Long nextProcessTick = NEXT_PROCESS_TICKS_BY_PLAYER.get(playerId);
		if (nextProcessTick == null || gameplayTick < nextProcessTick) {
			return;
		}
		NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);

		if (!PetConfigManager.settings().enabled) {
			removeAllPets(server, playerId);
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			removeAllPets(server, playerId);
			return;
		}


		PetInventory inventory = petInventory(player);
		long nextDelay;
		if (PetConfigManager.areEntitiesEnabled()) {
			nextDelay = PetEntitiesManager.syncPlayerPets(player);
		} else {
			removeAllPets(server, playerId);
			nextDelay = hasAutomaticPetAbilities(inventory) ? PetEntitiesManager.activeSchedulerTickInterval() : -1L;
		}
		LivingEntity ongoingReactiveTarget = resolveOngoingReactiveTarget(player);
		if (ongoingReactiveTarget != null) {
			PetAbilitiesManager.triggerReactiveAbilities(player, ongoingReactiveTarget);
			nextDelay = nextDelay < 0L
				? PetEntitiesManager.activeSchedulerTickInterval()
				: Math.min(nextDelay, PetEntitiesManager.activeSchedulerTickInterval());
		}
		PetAbilitiesManager.tickAutomaticAbilities(player, gameplayTick);
		if (nextDelay >= 0L) {
			requestPetProcessing(server, playerId, nextDelay);
		} else {
		}
	}

	private static void runPetAttack(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
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

		PetInventory inventory = petInventory(player);
		if (inventory == null || slot >= inventory.getContainerSize()) {
			return;
		}

		ItemStack stack = inventory.getItem(slot);
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (rule == null || !rule.canPerformReactiveAttack() || !canPetSlotShoot(player, slot, context.getNowTick(), stack)) {
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
		if (spawnPetReactiveAttack(player, target, spawnPosition, rule)) {
			setSlotCooldown(playerId, slot, context.getNowTick() + rule.cooldownTicks);
		}
	}









	private static boolean spawnPetReactiveAttack(ServerPlayer player, LivingEntity target, Vec3 spawnPosition, PetRule rule) {
		if (player == null || target == null || spawnPosition == null || rule == null || !player.isAlive() || !target.isAlive()) {
			return false;
		}

		SoundEvent soundEvent = rule.resolveSoundEvent();
		float soundVolume = Math.max(0.4F, rule.soundVolumeMultiplier);
		float soundPitch = 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F);
		boolean spawned;
		if (PET_ABILITY_RANGED_HOMING_ARROW.equals(rule.abilityType)) {
			spawned = MobEntityManager.spawnManagedHomingArrow(player, target, spawnPosition, rule.attackSpeed, rule.attackDamage);
		} else if (PET_ABILITY_WEB_PROJECTILE.equals(rule.abilityType)) {
			spawned = spawnManagedWebProjectile(player, target, spawnPosition, rule, soundEvent, soundVolume, soundPitch);
		} else if (PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(rule.abilityType)) {
			spawned = spawnManagedExplosiveProjectile(player, target, spawnPosition, rule, soundEvent, soundVolume, soundPitch);
		} else {
			spawned = false;
		}
		if (!spawned) {
			return false;
		}

		if (!(PET_ABILITY_WEB_PROJECTILE.equals(rule.abilityType) || PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(rule.abilityType))
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
		SoundEvent soundEvent,
		float soundVolume,
		float soundPitch
	) {
		if (player == null || target == null || spawnPosition == null || rule == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 initialPosition = new Vec3(spawnPosition.x, spawnPosition.y, spawnPosition.z);
		emitWebProjectileLaunch(level, initialPosition, soundEvent, soundVolume, soundPitch);
		UUID projectileId = UUID.randomUUID();
		ACTIVE_WEB_PROJECTILES.put(
			projectileId,
			new WebProjectileState(
				level.dimension().toString(),
				player.getUUID(),
				target.getUUID(),
				initialPosition,
				Math.max(WEB_PROJECTILE_MIN_SPEED, rule.attackSpeed),
				Math.max(0.0F, rule.attackDamage),
				(int) Math.max(0L, rule.effectDurationTicks),
				WEB_PROJECTILE_LIFETIME_TICKS
			)
		);
		return true;
	}

	private static boolean spawnManagedExplosiveProjectile(
		ServerPlayer player,
		LivingEntity target,
		Vec3 spawnPosition,
		PetRule rule,
		SoundEvent soundEvent,
		float soundVolume,
		float soundPitch
	) {
		if (player == null || target == null || spawnPosition == null || rule == null || !(player.level() instanceof ServerLevel level)) {
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
				Math.max(EXPLOSIVE_PROJECTILE_MIN_SPEED, rule.attackSpeed),
				Math.max(0.0F, rule.attackDamage),
				Math.max(0.5F, rule.explosionRadius),
				EXPLOSIVE_PROJECTILE_LIFETIME_TICKS
			)
		);
		return true;
	}

	private static void tickManagedWebProjectiles(MinecraftServer server) {
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
				applyWebProjectileHit(level, owner, target, targetPosition, state.damage, state.effectDurationTicks);
				ACTIVE_WEB_PROJECTILES.remove(projectileId);
				continue;
			}

			double step = Math.min(distance, state.speed);
			Vec3 nextPosition = state.position.add(toTarget.normalize().scale(step));
			emitWebProjectileTrail(level, state.position, nextPosition);
			if (nextPosition.distanceTo(targetPosition) <= WEB_PROJECTILE_HIT_DISTANCE) {
				applyWebProjectileHit(level, owner, target, targetPosition, state.damage, state.effectDurationTicks);
				ACTIVE_WEB_PROJECTILES.remove(projectileId);
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
					state.remainingTicks - 1
				)
			);
		}
	}

	private static void tickManagedExplosiveProjectiles(MinecraftServer server) {
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
					applyExplosiveProjectileHit(expiredLevel, expiredOwner, state.position, state.damage, state.radius);
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
				applyExplosiveProjectileHit(level, owner, state.position, state.damage, state.radius);
				ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
				continue;
			}

			Vec3 targetPosition = resolveWebProjectileTargetPosition(target);
			Vec3 toTarget = targetPosition.subtract(state.position);
			double distance = toTarget.length();
			if (distance <= 1.0E-6D) {
				applyExplosiveProjectileHit(level, owner, targetPosition, state.damage, state.radius);
				ACTIVE_EXPLOSIVE_PROJECTILES.remove(projectileId);
				continue;
			}

			double step = Math.min(distance, state.speed);
			Vec3 nextPosition = state.position.add(toTarget.normalize().scale(step));
			emitExplosiveProjectileTrail(level, state.position, nextPosition);
			if (nextPosition.distanceTo(targetPosition) <= EXPLOSIVE_PROJECTILE_HIT_DISTANCE) {
				applyExplosiveProjectileHit(level, owner, targetPosition, state.damage, state.radius);
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
					state.remainingTicks - 1
				)
			);
		}
	}

	private static void tickManagedBeeSwarms(MinecraftServer server) {
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
			if (rule == null || !rule.enabled || !PET_ABILITY_BEE_SWARM.equals(rule.abilityType)) {
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
				float damage = Math.max(0.0F, rule.attackDamage > 0.0F ? rule.attackDamage : BEE_SWARM_DEFAULT_DAMAGE_PER_SECOND);
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

	private static void stopBeeSwarmForSlot(UUID ownerId, int slot) {
		if (ownerId == null) {
			return;
		}
		ACTIVE_BEE_SWARMS.remove(beeSwarmKey(ownerId, slot));
	}

	private static void stopBeeSwarmsForOwner(UUID ownerId) {
		if (ownerId == null || ACTIVE_BEE_SWARMS.isEmpty()) {
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
		UUID[] petIds = PET_IDS_BY_PLAYER.get(ownerId);
		if (petIds == null || slot >= petIds.length) {
			return null;
		}
		return findMob(server, petIds[slot]);
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

	private static void applyWebProjectileHit(ServerLevel level, ServerPlayer owner, LivingEntity target, Vec3 position, float damage, int effectDurationTicks) {
		emitWebProjectileImpact(level, position);
		if (owner == null || target == null || !target.isAlive()) {
			return;
		}

		target.setDeltaMovement(Vec3.ZERO);
		if (damage > 0.0F) {
			target.hurtServer(level, owner.damageSources().generic(), damage);
			target.setDeltaMovement(Vec3.ZERO);
		}
		if (effectDurationTicks > 0) {
			MobEffectInstance existingSlow = target.getEffect(MobEffects.SLOWNESS);
			int stackedDurationTicks = effectDurationTicks;
			int amplifier = WEB_PROJECTILE_SLOW_AMPLIFIER;
			if (existingSlow != null) {
				stackedDurationTicks += Math.max(0, existingSlow.getDuration());
				amplifier = Math.max(amplifier, existingSlow.getAmplifier());
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stackedDurationTicks, amplifier), owner);
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	private static void applyExplosiveProjectileHit(ServerLevel level, ServerPlayer owner, Vec3 position, float damage, float radius) {
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
			Vec3 knockback = mob.position().subtract(position);
			if (knockback.lengthSqr() > 1.0E-6D) {
				double strength = Math.max(0.0D, 0.35D * (1.0D - (distance / radius)));
				mob.push(knockback.normalize().x * strength, 0.12D, knockback.normalize().z * strength);
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

	private static Vec3 resolveRangedAttackSpawn(ServerPlayer player, int index, int count, PetRule rule) {
		Vec3 forward = player.getLookAngle();
		forward = new Vec3(forward.x, 0.0D, forward.z);
		if (forward.lengthSqr() <= 1.0E-6D) {
			forward = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			forward = forward.normalize();
		}

		Vec3 back = forward.scale(-1.0D);
		Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
		double angleRadians = Math.toRadians(resolveShotArcAngle(index, count, rule));
		double lateralOffset = Math.sin(angleRadians) * rule.attackLateralRadius;
		double rearOffset = rule.attackRearOffset + Math.cos(angleRadians) * rule.attackRearSpread;
		return player.getEyePosition()
			.add(back.scale(rearOffset))
			.add(right.scale(lateralOffset))
			.add(0.0D, rule.attackVerticalOffset, 0.0D);
	}

	private static double resolveShotArcAngle(int index, int count, PetRule rule) {
		int clampedCount = Math.max(1, Math.min(SLOT_COUNT, count));
		if (clampedCount == 1) {
			return 0.0D;
		}

		double centeringOffset = (clampedCount - 1) * 0.5D;
		return (index - centeringOffset) * Math.max(0.0D, rule.attackArcStepDegrees);
	}

	private static void enqueueDelayedPetAttack(ServerPlayer player, int slot, LivingEntity target, Vec3 spawnPosition, long delayTicks) {
		MinecraftServer server = player == null ? null : player.level().getServer();
		if (server == null || player == null || target == null || spawnPosition == null) {
			return;
		}

		UUID playerId = player.getUUID();
		String schedulerId = ensureSchedulerExists(playerId);
		if (enqueuePetAttackTask(schedulerId, slot, target, spawnPosition, delayTicks)) {
			return;
		}

		String created = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId));
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (!enqueuePetAttackTask(created, slot, target, spawnPosition, delayTicks)) {
			LOGGER.error("Failed to enqueue delayed pet attack for player={} slot={}", playerId, slot);
		}
	}

	static void requestPetProcessing(MinecraftServer server, UUID playerId, long delayTicks) {
		if (server == null || playerId == null || !PetConfigManager.settings().enabled) {
			return;
		}

		long targetTick = MadokuTimeManager.getGameplayTicks() + Math.max(0L, delayTicks);
		Long existingTick = NEXT_PROCESS_TICKS_BY_PLAYER.get(playerId);
		if (existingTick == null || targetTick < existingTick) {
			NEXT_PROCESS_TICKS_BY_PLAYER.put(playerId, targetTick);
		}
	}

	private static String ensureSchedulerExists(UUID playerId) {
		String schedulerId = PLAYER_SCHEDULER_IDS.get(playerId);
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = MadokuSchedulerManager.createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId));
			PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
		}
		return schedulerId;
	}

	private static boolean enqueuePetAttackTask(String schedulerId, int slot, LivingEntity target, Vec3 spawnPosition, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank() || target == null || spawnPosition == null) {
			return false;
		}

		JsonObject payload = madoku.craft.api.json.JSONFormatManager.object()
			.put(FIELD_SLOT, slot)
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

	private static boolean canPetSlotShoot(ServerPlayer player, int slot, long gameplayTicks, ItemStack stack) {
		if (player == null || slot < 0 || slot >= SLOT_COUNT) {
			return false;
		}
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (!PetConfigManager.isValidPet(stack) || rule == null || !rule.canPerformReactiveAttack()) {
			return false;
		}
		return isPetSlotOffCooldown(player, slot, gameplayTicks);
	}

	static boolean isPetSlotOffCooldown(ServerPlayer player, int slot, long gameplayTicks) {
		if (player == null || slot < 0 || slot >= SLOT_COUNT) {
			return false;
		}
		return gameplayTicks >= slotCooldowns(player.getUUID())[slot];
	}

	private static void clearSlotCooldowns(UUID playerId) {
		if (playerId != null) {
			PLAYER_SLOT_COOLDOWNS.remove(playerId);
			PetHudManager.markAbilityHudDirty(playerId);
		}
	}

	private static void refreshPlayerPassiveAbilityBonuses(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PetAbilitiesManager.applyPlayerMaxHealthAbilityBonus(player);
			PetAbilitiesManager.applyPlayerArmorAbilityBonus(player);
			PetAbilitiesManager.applyPlayerDamageAbilityBonus(player);
		}
	}

	static void setSlotCooldown(UUID playerId, int slot, long cooldownTick) {
		if (playerId == null || slot < 0 || slot >= SLOT_COUNT) {
			return;
		}
		slotCooldowns(playerId)[slot] = Math.max(0L, cooldownTick);
		PetHudManager.markAbilityHudDirty(playerId);
	}

	private static long[] slotCooldowns(UUID playerId) {
		return PLAYER_SLOT_COOLDOWNS.computeIfAbsent(playerId, ignored -> new long[SLOT_COUNT]);
	}

	static void pruneCooldowns(UUID playerId, PetInventory inventory) {
		if (playerId == null || inventory == null) {
			return;
		}
		long[] cooldowns = PLAYER_SLOT_COOLDOWNS.get(playerId);
		if (cooldowns == null) {
			return;
		}
		long gameplayTicks = MadokuTimeManager.getGameplayTicks();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			if (cooldowns[slot] > 0L && cooldowns[slot] <= gameplayTicks) {
				cooldowns[slot] = 0L;
			}
		}
		if (!hasNonZeroCooldown(cooldowns)) {
			PLAYER_SLOT_COOLDOWNS.remove(playerId);
		}
		PetHudManager.markAbilityHudDirty(playerId);
	}

	private static void copyToNewPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
		PetInventory oldInventory = petInventory(oldPlayer);
		PetInventory newInventory = petInventory(newPlayer);
		if (oldInventory == null || newInventory == null) {
			return;
		}

		newInventory.copyFrom(oldInventory);
		PetHudManager.markAbilityHudDirty(newPlayer.getUUID());
	}

	private static void cachePendingRespawnPetInventory(ServerPlayer player) {
		if (player == null) {
			return;
		}
		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			PENDING_RESPAWN_PET_INVENTORIES.remove(player.getUUID());
			return;
		}

		ItemStack[] snapshot = new ItemStack[SLOT_COUNT];
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
			snapshot[slot] = stack == null ? ItemStack.EMPTY : stack.copy();
		}
		PENDING_RESPAWN_PET_INVENTORIES.put(player.getUUID(), snapshot);
	}

	private static boolean applyPendingRespawnPetInventory(ServerPlayer newPlayer, UUID previousPlayerId) {
		if (newPlayer == null || previousPlayerId == null) {
			return false;
		}
		PetInventory inventory = petInventory(newPlayer);
		ItemStack[] snapshot = PENDING_RESPAWN_PET_INVENTORIES.remove(previousPlayerId);
		if (inventory == null || snapshot == null) {
			return false;
		}

		inventory.runBulkUpdate(() -> {
			for (int slot = 0; slot < SLOT_COUNT && slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = slot < snapshot.length && snapshot[slot] != null ? snapshot[slot].copy() : ItemStack.EMPTY;
				inventory.setItem(slot, stack);
			}
		});
		PetHudManager.markAbilityHudDirty(newPlayer.getUUID());
		return true;
	}

	static int[] currentAbilityCooldowns(UUID playerId) {
		int[] remaining = new int[SLOT_COUNT];
		if (playerId == null) {
			return remaining;
		}

		long[] cooldowns = PLAYER_SLOT_COOLDOWNS.get(playerId);
		if (cooldowns == null) {
			return remaining;
		}

		long now = MadokuTimeManager.getGameplayTicks();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, cooldowns.length); slot++) {
			remaining[slot] = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, cooldowns[slot] - now));
		}
		return remaining;
	}

	static void removeAllPets(MinecraftServer server, UUID playerId) {
		stopBeeSwarmsForOwner(playerId);
		UUID[] petIds = PET_IDS_BY_PLAYER.remove(playerId);
		if (petIds != null) {
			for (UUID petId : petIds) {
				removePet(server, petId);
			}
		}
		// Defensive cleanup: remove any managed pets still tagged for this owner.
		// This prevents duplicate pets if runtime maps drift out of sync across respawn.
		removeTaggedPetsForOwner(server, playerId);
	}

	static void removePet(MinecraftServer server, UUID petId) {
		Mob pet = findMob(server, petId);
		if (pet != null) {
			pet.discard();
		}
		if (petId != null) {
			ACTIVE_PET_IDS.remove(petId);
			NEXT_IDLE_MOVE_BY_PET.remove(petId);
			FOLLOW_COMMANDS_BY_PET.remove(petId);
			PetPayloadManager.removeSoundState(petId);
			broadcastManagedPetSoundState(server, petId, "");
		}
	}

	static Mob findMob(MinecraftServer server, UUID entityId) {
		if (server == null || entityId == null) {
			return null;
		}

		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(entityId) instanceof Mob pet && pet.isAlive()) {
				return pet;
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

	static void preparePet(Mob pet) {
		if (pet == null) {
			return;
		}

		pet.removeAllGoals(goal -> true);
		clearPetTargetGoals(pet);
		pet.setTarget(null);
		pet.setAggressive(false);
		pet.getNavigation().setCanFloat(true);
		if (pet instanceof Bat bat) {
			bat.setResting(false);
		}
	}

	static void clearPetTargetGoals(Mob pet) {
		if (pet == null) {
			return;
		}
		((MobTargetSelectorAccessor) pet).madokuCraft$getTargetSelector().removeAllGoals(goal -> true);
	}

	static void tagManagedPet(Mob pet, UUID ownerId) {
		if (pet == null || ownerId == null) {
			return;
		}

		pet.addTag(MANAGED_PET_TAG);
		pet.addTag(ownerTag(ownerId));
	}

	private static void removeTaggedPets(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> toDiscard = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity != null && entity.entityTags().contains(MANAGED_PET_TAG)) {
					toDiscard.add(entity);
				}
			}
			for (Entity entity : toDiscard) {
				entity.discard();
			}
		}
	}

	private static void removeTaggedPetsForOwner(MinecraftServer server, UUID ownerId) {
		if (server == null || ownerId == null) {
			return;
		}

		String ownerTag = ownerTag(ownerId);
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> toDiscard = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity == null) {
					continue;
				}
				if (!entity.entityTags().contains(MANAGED_PET_TAG) || !entity.entityTags().contains(ownerTag)) {
					continue;
				}
				toDiscard.add(entity);
			}
			for (Entity entity : toDiscard) {
				entity.discard();
				UUID entityId = entity.getUUID();
				ACTIVE_PET_IDS.remove(entityId);
				NEXT_IDLE_MOVE_BY_PET.remove(entityId);
				FOLLOW_COMMANDS_BY_PET.remove(entityId);
				PetPayloadManager.removeSoundState(entityId);
				broadcastManagedPetSoundState(server, entityId, "");
			}
		}
	}

	static void removeStrayManagedPetsForOwner(MinecraftServer server, UUID ownerId, Set<UUID> expectedPetIds) {
		if (server == null || ownerId == null) {
			return;
		}

		String ownerTag = ownerTag(ownerId);
		Set<UUID> expected = expectedPetIds == null ? Set.of() : expectedPetIds;
		for (ServerLevel level : server.getAllLevels()) {
			List<Mob> toDiscard = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof Mob pet)) {
					continue;
				}
				if (!pet.entityTags().contains(MANAGED_PET_TAG) || !pet.entityTags().contains(ownerTag)) {
					continue;
				}
				UUID petId = pet.getUUID();
				if (expected.contains(petId)) {
					continue;
				}
				toDiscard.add(pet);
			}
			for (Mob pet : toDiscard) {
				UUID petId = pet.getUUID();
				pet.discard();
				ACTIVE_PET_IDS.remove(petId);
				NEXT_IDLE_MOVE_BY_PET.remove(petId);
				FOLLOW_COMMANDS_BY_PET.remove(petId);
			PetPayloadManager.removeSoundState(petId);
				broadcastManagedPetSoundState(server, petId, "");
			}
		}
	}

	private static void clearAllManagedPetState(MinecraftServer server) {
		if (!ACTIVE_PET_IDS.isEmpty() || !PET_IDS_BY_PLAYER.isEmpty()) {
			removeTaggedPets(server);
		}
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		ACTIVE_WEB_PROJECTILES.clear();
		ACTIVE_EXPLOSIVE_PROJECTILES.clear();
		ACTIVE_BEE_SWARMS.clear();
		PetPayloadManager.clearSoundState();
	}

	private static void clearManagedPetEntityState(MinecraftServer server) {
		if (!ACTIVE_PET_IDS.isEmpty() || !PET_IDS_BY_PLAYER.isEmpty()) {
			removeTaggedPets(server);
		}
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
		PetPayloadManager.clearSoundState();
	}

	private static String ownerTag(UUID ownerId) {
		return MANAGED_PET_OWNER_PREFIX + ownerId;
	}

	static PetInventory petInventory(Player player) {
		return player instanceof PetHolder holder ? holder.madokuCraft$getPetInventory() : null;
	}

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

	private static JsonObject toPersistedData() {
		madoku.craft.api.json.JSONFormatManager.ArrayBuilder cooldowns = madoku.craft.api.json.JSONFormatManager.array();
		for (Map.Entry<UUID, long[]> entry : PLAYER_SLOT_COOLDOWNS.entrySet()) {
			if (entry.getKey() == null || !hasNonZeroCooldown(entry.getValue())) {
				continue;
			}
			long[] source = entry.getValue();
			madoku.craft.api.json.JSONFormatManager.ArrayBuilder values = madoku.craft.api.json.JSONFormatManager.array();
			for (int slot = 0; slot < SLOT_COUNT; slot++) {
				values.add(slot < source.length ? Math.max(0L, source[slot]) : 0L);
			}
			cooldowns.object(playerCooldowns -> playerCooldowns
				.put("uuid", entry.getKey().toString())
				.put("cooldowns", values.build()));
		}
		return madoku.craft.api.json.JSONFormatManager.object()
			.put("slot-cooldowns", cooldowns.build())
			.build();
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_SCHEDULER_IDS.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		PLAYER_SLOT_COOLDOWNS.clear();
		if (source == null) {
			return;
		}

		JsonArray slotCooldowns = getArray(source, "slot-cooldowns");
		if (slotCooldowns == null) {
			return;
		}
		for (JsonElement element : slotCooldowns) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject cooldownData = element.getAsJsonObject();
			UUID playerId = parseUuid(PetConfigManager.getString(cooldownData, "uuid", ""));
			JsonArray cooldownValues = getArray(cooldownData, "cooldowns");
			if (playerId == null || cooldownValues == null) {
				continue;
			}
			long[] cooldowns = new long[SLOT_COUNT];
			for (int slot = 0; slot < Math.min(SLOT_COUNT, cooldownValues.size()); slot++) {
				JsonElement cooldownElement = cooldownValues.get(slot);
				if (cooldownElement != null && cooldownElement.isJsonPrimitive() && cooldownElement.getAsJsonPrimitive().isNumber()) {
					cooldowns[slot] = Math.max(0L, cooldownElement.getAsLong());
				}
			}
			if (hasNonZeroCooldown(cooldowns)) {
				PLAYER_SLOT_COOLDOWNS.put(playerId, cooldowns);
			}
		}
	}

	private static boolean hasNonZeroCooldown(long[] cooldowns) {
		if (cooldowns == null) {
			return false;
		}
		for (long cooldown : cooldowns) {
			if (cooldown > 0L) {
				return true;
			}
		}
		return false;
	}

	static boolean setManagedPetItemId(Mob pet, String itemId) {
		if (pet == null) {
			return false;
		}
		String normalizedItemId = PetConfigManager.normalizeKey(itemId);
		PetPayloadManager.setSoundState(pet.getUUID(), normalizedItemId);
		String existingTag = null;
		for (String tag : pet.entityTags()) {
			if (tag != null && tag.startsWith(MANAGED_PET_ITEM_PREFIX)) {
				existingTag = tag;
				break;
			}
		}
		String desiredTag = normalizedItemId.isEmpty() ? "" : MANAGED_PET_ITEM_PREFIX + normalizedItemId;
		if (desiredTag.equals(existingTag)) {
			return false;
		}
		if (existingTag != null) {
			pet.removeTag(existingTag);
		}
		if (!desiredTag.isEmpty()) {
			pet.addTag(desiredTag);
		}
		return true;
	}

	static String getManagedPetItemId(Entity entity) {
		if (entity == null) {
			return "";
		}
		for (String tag : entity.entityTags()) {
			if (tag != null && tag.startsWith(MANAGED_PET_ITEM_PREFIX)) {
				return tag.substring(MANAGED_PET_ITEM_PREFIX.length());
			}
		}
		return "";
	}

	private static JsonArray getArray(JsonObject source, String key) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonArray()) {
			return null;
		}
		return source.getAsJsonArray(key);
	}

	static String formatAbilityAmount(double value) {
		double rounded = Math.round(value * 100.0D) / 100.0D;
		if (Math.abs(rounded - Math.rint(rounded)) <= 1.0E-6D) {
			return Long.toString(Math.round(rounded));
		}
		return Double.toString(rounded);
	}

	static String formatPercent(double value) {
		return formatAbilityAmount(value * 100.0D) + "%";
	}

	static String formatCooldownSeconds(long ticks) {
		double seconds = Math.max(0.0D, ticks / 20.0D);
		double rounded = Math.round(seconds * 10.0D) / 10.0D;
		if (Math.abs(rounded - Math.rint(rounded)) <= 1.0E-6D) {
			return Long.toString(Math.round(rounded));
		}
		return Double.toString(rounded);
	}

	private record WebProjectileState(
		String dimensionId,
		UUID ownerUuid,
		UUID targetUuid,
		Vec3 position,
		double speed,
		float damage,
		int effectDurationTicks,
		int remainingTicks
	) {
	}

	private record ExplosiveProjectileState(
		String dimensionId,
		UUID ownerUuid,
		UUID targetUuid,
		Vec3 position,
		double speed,
		float damage,
		float radius,
		int remainingTicks
	) {
	}

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
	) {
	}

}




