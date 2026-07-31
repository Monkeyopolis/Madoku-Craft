package madoku.craft.pet;

import com.google.gson.JsonObject;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.data.DataPlayerManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import madoku.craft.pet.PetComponentsManager.PetInventory;

public final class MadokuPetManager {
	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "MadokuPets";

	private static final String DATA_FILE_NAME = "madoku-pets";

	private static final String TASK_TYPE_PET_RUNTIME_TICK = "pet_runtime_tick";
	private static final String PET_RUNTIME_SCHEDULER_KEY = "pet_runtime_tick";
	static final String PET_RUNTIME_SCHEDULER_OWNER_ID = "pet_runtime";
	static final long PET_RUNTIME_MIN_INTERVAL_TICKS = 1L;
	static final long PET_RUNTIME_MAX_INTERVAL_TICKS = 5L;

	static final String PET_ABILITY_NONE = "none";
	static final String PET_ABILITY_RANGED_HOMING_ARROW = "ranged_homing_arrow";
	static final String PET_ABILITY_WEB_PROJECTILE = "web_projectile";
	static final String PET_ABILITY_EXPLOSIVE_PROJECTILE = "explosive_projectile";
	static final String PET_ABILITY_EGG_PROJECTILE = "egg_projectile";
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
	static final String PET_RARITY_LEGENDARY = "legendary";
	static final String PET_RARITY_MYTHIC = "mythic";



























	private static final Map<UUID, Long> NEXT_PROCESS_TICKS_BY_PLAYER = new HashMap<>();




	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile String runtimeSchedulerId = "";
	private static volatile boolean runtimeTickQueued;

	private MadokuPetManager() {
	}

	public static void initialize() {
		PetEntitiesManager.initialize();
		PetConfigManager.initialize();
		PetAbilitiesManager.initialize();
		PetHudManager.initialize();
		PetHagManager.initialize();
		PetComponentsManager.initialize();
		MadokuSchedulerManager.registerTaskHandler(PetAbilitiesManager.TASK_TYPE_PET_ATTACK, PetAbilitiesManager::runPetAttack);
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_PET_RUNTIME_TICK, MadokuPetManager::runPetRuntimeTick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}

			PetComponentsManager.clearPendingRespawnPetInventory(player.getUUID());
			PetEntitiesManager.removeAllPets(player.level().getServer(), player.getUUID());
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
				return;
			}

			PetComponentsManager.dropAll(player);
			PetComponentsManager.cachePendingRespawnPetInventory(player);
			PetAbilitiesManager.clearSlotCooldowns(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PetAbilitiesManager::handleAfterDamage);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			PetEntitiesManager.removeAllPets(newPlayer.level().getServer(), oldPlayer.getUUID());
			if (alive) {
				PetComponentsManager.copyToNewPlayer(oldPlayer, newPlayer);
			} else if (!PetComponentsManager.applyPendingRespawnPetInventory(newPlayer, oldPlayer.getUUID())) {
				PetComponentsManager.copyToNewPlayer(oldPlayer, newPlayer);
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
	}

	public static void reset() {
		PetEntitiesManager.reset();
		PetComponentsManager.reset();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		PetHudManager.clear();
		PetAbilitiesManager.reset();
		MadokuSchedulerManager.clearAdaptiveDelayState(PET_RUNTIME_SCHEDULER_OWNER_ID);
		lastAutosaveBucket = Long.MIN_VALUE;
		runtimeSchedulerId = "";
		runtimeTickQueued = false;
	}

	public static void onServerStarted(MinecraftServer server) {
		ensureRuntimeQueued(server, adaptiveSchedulerInterval(server));
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		reloadConfig();
		JsonObject data = DataPlayerManager.getSystemData(DATA_FILE_NAME, "slot-cooldowns", "uuid");
		PetAbilitiesManager.applyPersistedData(data);
		PetEntitiesManager.removeAllPetEntitiesOnServerStart(server);
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

		DataPlayerManager.setSystemData(DATA_FILE_NAME, PetAbilitiesManager.toPersistedData(), "slot-cooldowns", "uuid");
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

		PetAbilitiesManager.refreshPlayerPassiveAbilityBonuses(server);

		if (!PetConfigManager.settings().enabled) {
			PetEntitiesManager.clearAllManagedPetState(server);
			PetHudManager.flushAbilityHudSyncs(server);
			return;
		}

		if (!PetConfigManager.areEntitiesEnabled()) {
			PetEntitiesManager.clearManagedPetEntityState(server);
		}

		PetAbilitiesManager.tickWebControls(server);
		PetAbilitiesManager.tickManagedWebProjectiles(server);
		PetAbilitiesManager.tickManagedExplosiveProjectiles(server);
		PetAbilitiesManager.tickManagedChickenEggProjectiles(server);
		PetAbilitiesManager.tickManagedBeeSwarms(server);
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
		ensureRuntimeQueued(server, adaptiveSchedulerInterval(server));
	}

	static long adaptiveSchedulerInterval(MinecraftServer server) {
		return MadokuSchedulerManager.resolveAdaptiveDelayTicks(
			server,
			PET_RUNTIME_SCHEDULER_OWNER_ID,
			PET_RUNTIME_MIN_INTERVAL_TICKS,
			PET_RUNTIME_MAX_INTERVAL_TICKS
		);
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
		return PetEntitiesManager.isManaged(entity);
	}



	public static long managedPetSteeringInterval(MinecraftServer server) {
		return PetEntitiesManager.activeSchedulerTickInterval(server);
	}

	public static Vec3 managedPetMovementTarget(Mob pet) {
		return PetEntitiesManager.movementTarget(pet);

	}

	public static double managedPetMovementSpeed(Mob pet, double fallbackSpeed) {
		return PetEntitiesManager.movementSpeed(pet, fallbackSpeed);
	}

	public static boolean isEnabled() {
		return PetConfigManager.settings().enabled;
	}

	public static boolean areEntitiesEnabled() {
		return PetConfigManager.areEntitiesEnabled();
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
		if (PetConfigManager.settings().enabled) {
			requestPetProcessing(server, player.getUUID(), 0L);
		}
	}

	private static void handlePlayerLeave(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		PetEntitiesManager.removeAllPets(server, playerId);
		PetAbilitiesManager.stopBeeSwarmsForOwner(playerId);
		NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);
		PetHudManager.clearPlayer(playerId);
		PetComponentsManager.clearPendingRespawnPetInventory(playerId);
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
			PetEntitiesManager.removeAllPets(server, playerId);
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			PetEntitiesManager.removeAllPets(server, playerId);
			return;
		}


		PetInventory inventory = PetComponentsManager.petInventory(player);
		long nextDelay;
		if (PetConfigManager.areEntitiesEnabled()) {
			nextDelay = PetEntitiesManager.syncPlayerPets(player);
		} else {
			PetEntitiesManager.removeAllPets(server, playerId);
			nextDelay = PetAbilitiesManager.hasAutomaticPetAbilities(inventory) ? PetEntitiesManager.activeSchedulerTickInterval(server) : -1L;
		}
		LivingEntity ongoingReactiveTarget = PetAbilitiesManager.resolveOngoingReactiveTarget(player);

		if (ongoingReactiveTarget != null) {
			PetAbilitiesManager.triggerReactiveAbilities(player, ongoingReactiveTarget);
			nextDelay = nextDelay < 0L
				? PetEntitiesManager.activeSchedulerTickInterval(server)
				: Math.min(nextDelay, PetEntitiesManager.activeSchedulerTickInterval(server));
		}
		PetAbilitiesManager.tickAutomaticAbilities(player, gameplayTick);
		if (nextDelay >= 0L) {
			requestPetProcessing(server, playerId, nextDelay);
		} else {
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

}
