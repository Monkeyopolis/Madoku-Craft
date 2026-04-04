package madoku.craft.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.DynamicJsonSystem;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.data.MadokuData;
import madoku.craft.mob.system.MadokuMob;
import madoku.craft.scheduler.MadokuScheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerEntitiesSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerEntitiesSystem.class);

	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "PlayerEntities";

	private static final String PET_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-pets";
	private static final String PET_CONFIG_FILE_NAME = "madoku-pets";
	private static final String PET_RULES_FOLDER_NAME = "madoku-pets";
	private static final String DATA_FOLDER_NAME = "madoku-craft-pets";
	private static final String DATA_FILE_NAME = "madoku-pets";
	private static final String TASK_TYPE_PET_TICK = "pet_tick";
	private static final String TASK_TYPE_PET_ATTACK = "pet_attack";
	private static final String LEGACY_SAVE_KEY = "PlayerPets";
	private static final String MANAGED_PET_TAG = "madoku-craft.pet";
	private static final String MANAGED_PET_OWNER_PREFIX = "madoku-craft.pet.owner:";
	private static final String PET_ABILITY_NONE = "none";
	private static final String PET_ABILITY_RANGED_HOMING_ARROW = "ranged_homing_arrow";
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;
	private static final String FIELD_SLOT = "slot";
	private static final String FIELD_TARGET_UUID = "target_uuid";
	private static final String FIELD_SPAWN_X = "spawn_x";
	private static final String FIELD_SPAWN_Y = "spawn_y";
	private static final String FIELD_SPAWN_Z = "spawn_z";
	private static final SlotOffset[] SLOT_OFFSETS = {
		new SlotOffset(-0.90D, 0.85D),
		new SlotOffset(-0.30D, 1.35D),
		new SlotOffset(0.30D, 1.35D),
		new SlotOffset(0.90D, 0.85D)
	};

	private static final Map<UUID, UUID[]> PET_IDS_BY_PLAYER = new ConcurrentHashMap<>();
	private static final Set<UUID> ACTIVE_PET_IDS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> NEXT_IDLE_MOVE_BY_PET = new ConcurrentHashMap<>();
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Set<UUID> SCHEDULED_PLAYERS = new HashSet<>();
	private static final Map<UUID, Long> LAST_PROCESSED_TICKS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, long[]> PLAYER_SLOT_COOLDOWNS = new HashMap<>();

	private static volatile Settings settings = Settings.defaults();
	private static volatile Map<String, PetRule> petRulesByItemId = Map.of();
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private PlayerEntitiesSystem() {
	}

	public static void initialize() {
		loadConfig();
		MadokuScheduler.registerTaskHandler(TASK_TYPE_PET_TICK, PlayerEntitiesSystem::runPetTick);
		MadokuScheduler.registerTaskHandler(TASK_TYPE_PET_ATTACK, PlayerEntitiesSystem::runPetAttack);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}

			removeAllPets(player.level().getServer(), player.getUUID());
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
				return;
			}

			dropAll(player);
			clearSlotCooldowns(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PlayerEntitiesSystem::handleAfterDamage);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			copyToNewPlayer(oldPlayer, newPlayer);
			removeAllPets(newPlayer.level().getServer(), oldPlayer.getUUID());
			requestPetProcessing(newPlayer.level().getServer(), newPlayer.getUUID(), 0L);
		});
		ServerPlayerEvents.JOIN.register(PlayerEntitiesSystem::handlePlayerJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler == null || handler.player == null) {
				return;
			}
			handlePlayerLeave(server, handler.player.getUUID());
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> ACTIVE_PET_IDS.remove(entity.getUUID()));
	}

	public static void reset() {
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		PLAYER_SCHEDULER_IDS.clear();
		SCHEDULED_PLAYERS.clear();
		LAST_PROCESSED_TICKS_BY_PLAYER.clear();
		PLAYER_SLOT_COOLDOWNS.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadConfig();
		MadokuData.createWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		JsonObject data = MadokuData.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		applyPersistedData(data);
		removeTaggedPets(server);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		MadokuData.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null) {
			return;
		}

		if (!settings.enabled) {
			if (!ACTIVE_PET_IDS.isEmpty() || !PET_IDS_BY_PLAYER.isEmpty()) {
				removeTaggedPets(server);
				PET_IDS_BY_PLAYER.clear();
				ACTIVE_PET_IDS.clear();
				NEXT_IDLE_MOVE_BY_PET.clear();
			}
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || player.isSpectator()) {
				continue;
			}
			if (!hasAnyValidPet(player) && !PET_IDS_BY_PLAYER.containsKey(player.getUUID())) {
				continue;
			}
			requestPetProcessing(server, player.getUUID(), 0L);
		}
	}

	public static boolean isValidPlayerEntity(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpawnEggItem;
	}

	public static boolean isManagedPet(Entity entity) {
		return entity != null && (ACTIVE_PET_IDS.contains(entity.getUUID()) || entity.entityTags().contains(MANAGED_PET_TAG));
	}

	public static float soundVolume(float baseVolume) {
		return Math.max(0.0F, baseVolume * settings.soundVolumeMultiplier);
	}

	public static int ambientSoundInterval(int baseInterval) {
		return Math.max(20, baseInterval * Math.max(1, settings.ambientSoundIntervalMultiplier));
	}

	public static String legacySaveKey() {
		return LEGACY_SAVE_KEY;
	}

	public static void dropAll(ServerPlayer player) {
		PlayerEntitiesInventory playerEntitiesInventory = playerEntitiesInventory(player);
		if (playerEntitiesInventory == null) {
			return;
		}

		for (int slot = 0; slot < playerEntitiesInventory.getContainerSize(); slot++) {
			ItemStack stack = playerEntitiesInventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			player.drop(stack, true, false);
			playerEntitiesInventory.setItem(slot, ItemStack.EMPTY);
		}
		playerEntitiesInventory.setChanged();
	}

	public static int countPlayerEntities(Player player) {
		PlayerEntitiesInventory playerEntitiesInventory = playerEntitiesInventory(player);
		if (playerEntitiesInventory == null) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < playerEntitiesInventory.getContainerSize(); slot++) {
			if (isValidPlayerEntity(playerEntitiesInventory.getItem(slot))) {
				count++;
			}
		}
		return count;
	}

	private static void loadConfig() {
		loadStaticConfig();
		loadPetRules();
	}

	private static void loadStaticConfig() {
		try {
			Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(PET_CONFIG_ROOT_FOLDER_NAME);
			Path configFile = resolveJsonFile(rootDirectory, PET_CONFIG_FILE_NAME);
			JsonObject defaults = Settings.defaults().toConfigJson();
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			StaticJsonSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured;
		} catch (IOException exception) {
			settings = Settings.defaults();
			LOGGER.error("Failed to load Madoku pet settings; using defaults.", exception);
		}
	}

	private static void loadPetRules() {
		try {
			Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(PET_CONFIG_ROOT_FOLDER_NAME);
			Path rulesDirectory = rootDirectory.resolve(PET_RULES_FOLDER_NAME);
			Map<String, JsonObject> normalizedFiles = DynamicJsonSystem.ensureManagedFolder(
				rulesDirectory,
				buildDefaultPetRuleFiles(),
				PlayerEntitiesSystem::buildDynamicPetRuleDefaults,
				PlayerEntitiesSystem::isSupportedPetRuleFile,
				null
			);
			Map<String, PetRule> resolved = new LinkedHashMap<>();
			for (Map.Entry<String, JsonObject> entry : normalizedFiles.entrySet()) {
				PetRule rule = PetRule.fromJson(entry.getValue(), entry.getKey());
				if (rule == null || rule.itemId.isBlank()) {
					continue;
				}
				resolved.put(normalizeKey(rule.itemId), rule);
			}
			petRulesByItemId = Map.copyOf(resolved);
		} catch (IOException | RuntimeException exception) {
			petRulesByItemId = Map.of();
			LOGGER.error("Failed to load Madoku pet rules; using defaults.", exception);
		}
	}

	private static Map<String, JsonObject> buildDefaultPetRuleFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("skeleton_spawn_egg", PetRule.defaultsForItem("minecraft:skeleton_spawn_egg", PET_ABILITY_RANGED_HOMING_ARROW));
		return defaults;
	}

	private static JsonObject buildDynamicPetRuleDefaults(String fileKey) {
		String itemId = resolvePetItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return PetRule.defaultsForItem(itemId, PET_ABILITY_NONE);
	}

	private static boolean isSupportedPetRuleFile(String fileKey, JsonObject sourceRoot) {
		String itemId = resolvePetItemId(fileKey, sourceRoot);
		Item item = resolveItem(itemId);
		return item instanceof SpawnEggItem;
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
		requestPetProcessing(server, player.getUUID(), 0L);
	}

	private static void handlePlayerLeave(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		removeAllPets(server, playerId);
		removeTaggedPets(server, playerId);
		SCHEDULED_PLAYERS.remove(playerId);
		LAST_PROCESSED_TICKS_BY_PLAYER.remove(playerId);
	}

	private static void handleAfterDamage(
		LivingEntity entity,
		net.minecraft.world.damagesource.DamageSource source,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked
	) {
		if (!settings.enabled || damageTaken <= 0.0F || blocked) {
			return;
		}
		if (!(entity instanceof LivingEntity target) || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!player.isAlive() || !target.isAlive() || player == target) {
			return;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null) {
			return;
		}

		long gameplayTicks = MadokuTicks.getGameplayTicks();
		int[] readySlots = new int[SLOT_COUNT];
		PetRule[] readyRules = new PetRule[SLOT_COUNT];
		int readyCount = 0;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = resolvePetRule(stack);
			if (rule == null || !rule.canPerformReactiveRangedAttack()) {
				if (!stack.isEmpty()) {
					clearSlotCooldown(player.getUUID(), slot);
				}
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
				if (spawnPetRangedAttack(player, target, spawnPosition, rule)) {
					setSlotCooldown(player.getUUID(), slot, gameplayTicks + rule.cooldownTicks);
				}
				continue;
			}

			enqueueDelayedPetAttack(player, slot, target, spawnPosition, index * rule.shotDelayTicks);
		}
	}

	private static void runPetTick(MinecraftServer server, MadokuScheduler.TaskContext context, JsonObject payload) {
		if (server == null || context == null) {
			return;
		}

		UUID playerId = parsePlayerOwner(context.getOwner());
		if (playerId == null) {
			return;
		}

		PLAYER_SCHEDULER_IDS.put(playerId, context.getSchedulerId());
		SCHEDULED_PLAYERS.remove(playerId);
		Long lastProcessed = LAST_PROCESSED_TICKS_BY_PLAYER.get(playerId);
		if (lastProcessed != null && lastProcessed == context.getNowTick()) {
			return;
		}
		LAST_PROCESSED_TICKS_BY_PLAYER.put(playerId, context.getNowTick());

		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null || !settings.enabled) {
			removeAllPets(server, playerId);
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			removeAllPets(server, playerId);
			return;
		}

		boolean active = syncPlayerPets(player);
		if (active) {
			requestPetProcessing(server, playerId, Math.max(1L, settings.schedulerTickInterval));
		}
	}

	private static void runPetAttack(MinecraftServer server, MadokuScheduler.TaskContext context, JsonObject payload) {
		if (server == null || context == null || payload == null) {
			return;
		}

		UUID playerId = parsePlayerOwner(context.getOwner());
		if (playerId == null) {
			return;
		}

		PLAYER_SCHEDULER_IDS.put(playerId, context.getSchedulerId());
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null || !player.isAlive()) {
			return;
		}

		int slot = getInt(payload, FIELD_SLOT, -1);
		if (slot < 0 || slot >= SLOT_COUNT) {
			return;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null || slot >= inventory.getContainerSize()) {
			return;
		}

		ItemStack stack = inventory.getItem(slot);
		PetRule rule = resolvePetRule(stack);
		if (rule == null || !rule.canPerformReactiveRangedAttack() || !canPetSlotShoot(player, slot, context.getNowTick(), stack)) {
			return;
		}

		UUID targetId = parseUuid(getString(payload, FIELD_TARGET_UUID, ""));
		LivingEntity target = findLivingEntity(server, targetId);
		if (target == null || !target.isAlive() || target == player) {
			return;
		}

		Vec3 spawnPosition = new Vec3(
			getDouble(payload, FIELD_SPAWN_X, player.getX()),
			getDouble(payload, FIELD_SPAWN_Y, player.getEyeY()),
			getDouble(payload, FIELD_SPAWN_Z, player.getZ())
		);
		if (spawnPetRangedAttack(player, target, spawnPosition, rule)) {
			setSlotCooldown(playerId, slot, context.getNowTick() + rule.cooldownTicks);
		}
	}

	private static boolean syncPlayerPets(ServerPlayer player) {
		MinecraftServer server = player == null ? null : player.level().getServer();
		if (server == null) {
			return false;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null) {
			removeAllPets(server, player.getUUID());
			return false;
		}

		boolean anyActive = false;
		UUID[] petIds = PET_IDS_BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> new UUID[SLOT_COUNT]);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = inventory.getItem(slot);
			EntityType<?> desiredType = resolvePetType(stack);
			Mob pet = findMob(server, petIds[slot]);

			if (desiredType == null) {
				removePet(server, petIds[slot]);
				petIds[slot] = null;
				clearSlotCooldown(player.getUUID(), slot);
				continue;
			}

			if (pet == null || pet.getType() != desiredType || pet.level() != player.level()) {
				removePet(server, petIds[slot]);
				pet = spawnPet(player, desiredType, slot);
				petIds[slot] = pet == null ? null : pet.getUUID();
			}

			if (pet != null) {
				ACTIVE_PET_IDS.add(pet.getUUID());
				configurePet(pet);
				updatePetPosition(player, pet, slot);
				anyActive = true;
			}
		}

		if (!anyActive) {
			PET_IDS_BY_PLAYER.remove(player.getUUID());
			pruneCooldowns(player.getUUID(), inventory);
			return false;
		}

		return true;
	}

	private static Mob spawnPet(ServerPlayer owner, EntityType<?> entityType, int slot) {
		if (owner == null || entityType == null || !(owner.level() instanceof ServerLevel level)) {
			return null;
		}

		Entity entity = entityType.create(level, EntitySpawnReason.EVENT);
		if (!(entity instanceof Mob pet)) {
			return null;
		}

		Vec3 desiredPosition = resolveDesiredPosition(owner, slot);
		pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
		preparePet(pet);
		configurePet(pet);
		tagManagedPet(pet, owner.getUUID());
		if (!level.addFreshEntity(pet)) {
			return null;
		}

		ACTIVE_PET_IDS.add(pet.getUUID());
		return pet;
	}

	private static void configurePet(Mob pet) {
		if (pet == null) {
			return;
		}

		pet.setNoAi(false);
		pet.setInvulnerable(true);
		pet.setSilent(false);
		pet.setPersistenceRequired();
		pet.noPhysics = false;
		pet.setNoGravity(false);
		pet.blocksBuilding = false;
		pet.clearFire();
		pet.setCanPickUpLoot(false);
		pet.setTarget(null);
		pet.setAggressive(false);
		AttributeInstance scale = pet.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(settings.petScale);
		}
	}

	private static void updatePetPosition(ServerPlayer owner, Mob pet, int slot) {
		Vec3 desiredPosition = resolveDesiredPosition(owner, slot);
		double ownerDistanceSqr = pet.distanceToSqr(owner);
		double teleportDistanceSqr = settings.teleportDistance * settings.teleportDistance;
		if (ownerDistanceSqr > teleportDistanceSqr) {
			pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
			pet.getNavigation().stop();
			pet.setDeltaMovement(Vec3.ZERO);
			scheduleNextIdleMove(pet);
			return;
		}

		double idleDistanceSqr = settings.idleDistance * settings.idleDistance;
		if (ownerDistanceSqr <= idleDistanceSqr) {
			updatePetIdleMovement(owner, pet, idleDistanceSqr);
			return;
		}

		pet.getNavigation().moveTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, settings.followSpeed);
		pet.getLookControl().setLookAt(desiredPosition.x, desiredPosition.y, desiredPosition.z, 30.0F, 30.0F);
	}

	private static void updatePetIdleMovement(ServerPlayer owner, Mob pet, double idleDistanceSqr) {
		if (pet == null || !pet.getNavigation().isDone()) {
			return;
		}

		long gameTime = pet.level().getGameTime();
		long nextMoveTick = NEXT_IDLE_MOVE_BY_PET.getOrDefault(pet.getUUID(), Long.MIN_VALUE);
		if (gameTime < nextMoveTick) {
			return;
		}

		Vec3 idleTarget = resolveIdleTarget(owner, pet, idleDistanceSqr);
		if (idleTarget == null) {
			scheduleNextIdleMove(pet);
			return;
		}

		pet.getNavigation().moveTo(idleTarget.x, idleTarget.y, idleTarget.z, settings.idleMoveSpeed);
		pet.getLookControl().setLookAt(idleTarget.x, idleTarget.y, idleTarget.z, 20.0F, 20.0F);
		scheduleNextIdleMove(pet);
	}

	private static Vec3 resolveIdleTarget(ServerPlayer owner, Mob pet, double idleDistanceSqr) {
		Vec3 current = pet.position();
		Vec3 offset = new Vec3(
			(pet.getRandom().nextDouble() - 0.5D) * 2.0D * settings.idleWanderRadius,
			0.0D,
			(pet.getRandom().nextDouble() - 0.5D) * 2.0D * settings.idleWanderRadius
		);
		Vec3 candidate = current.add(offset);
		if (candidate.distanceToSqr(owner.position()) > idleDistanceSqr) {
			Vec3 ownerDelta = candidate.subtract(owner.position());
			Vec3 horizontal = new Vec3(ownerDelta.x, 0.0D, ownerDelta.z);
			if (horizontal.lengthSqr() <= 1.0E-6D) {
				return null;
			}
			candidate = owner.position().add(horizontal.normalize().scale(Math.max(0.5D, settings.idleDistance * 0.4D))).add(0.0D, 0.1D, 0.0D);
		}
		return candidate;
	}

	private static void scheduleNextIdleMove(Mob pet) {
		if (pet == null) {
			return;
		}

		long minInterval = Math.max(1L, settings.idleMinIntervalTicks);
		long maxInterval = Math.max(minInterval, settings.idleMaxIntervalTicks);
		long delay = minInterval;
		if (maxInterval > minInterval) {
			delay += pet.getRandom().nextInt((int) (maxInterval - minInterval + 1L));
		}
		NEXT_IDLE_MOVE_BY_PET.put(pet.getUUID(), pet.level().getGameTime() + delay);
	}

	private static Vec3 resolveDesiredPosition(ServerPlayer owner, int slot) {
		SlotOffset offset = SLOT_OFFSETS[Math.max(0, Math.min(SLOT_OFFSETS.length - 1, slot))];
		Vec3 horizontalForward = resolveFollowDirection(owner);
		Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
		Vec3 base = owner.position().add(0.0D, 0.10D, 0.0D);
		return base.add(right.scale(offset.side())).subtract(horizontalForward.scale(offset.back()));
	}

	private static Vec3 resolveFollowDirection(ServerPlayer owner) {
		Vec3 motion = owner.getDeltaMovement();
		Vec3 horizontalMotion = new Vec3(motion.x, 0.0D, motion.z);
		if (horizontalMotion.lengthSqr() > 1.0E-4D) {
			return horizontalMotion.normalize();
		}

		float bodyYawRadians = owner.yBodyRot * (float) (Math.PI / 180.0D);
		double x = -Mth.sin(bodyYawRadians);
		double z = Mth.cos(bodyYawRadians);
		Vec3 bodyForward = new Vec3(x, 0.0D, z);
		if (bodyForward.lengthSqr() <= 1.0E-6D) {
			return new Vec3(0.0D, 0.0D, 1.0D);
		}
		return bodyForward.normalize();
	}

	private static EntityType<?> resolvePetType(ItemStack stack) {
		if (!(stack.getItem() instanceof SpawnEggItem)) {
			return null;
		}
		return SpawnEggItem.getType(stack);
	}

	private static boolean spawnPetRangedAttack(ServerPlayer player, LivingEntity target, Vec3 spawnPosition, PetRule rule) {
		if (player == null || target == null || spawnPosition == null || rule == null || !player.isAlive() || !target.isAlive()) {
			return false;
		}
		if (!MadokuMob.spawnManagedHomingArrow(player, target, spawnPosition, rule.attackSpeed, rule.attackDamage)) {
			return false;
		}

		SoundEvent soundEvent = rule.resolveSoundEvent();
		player.playSound(soundEvent, 1.0F, 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
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
		double lateralOffset = Math.sin(angleRadians) * settings.attackLateralRadius;
		double rearOffset = settings.attackRearOffset + Math.cos(angleRadians) * settings.attackRearSpread;
		return player.getEyePosition()
			.add(back.scale(rearOffset))
			.add(right.scale(lateralOffset))
			.add(0.0D, settings.attackVerticalOffset, 0.0D);
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

		String created = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null));
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (!enqueuePetAttackTask(created, slot, target, spawnPosition, delayTicks)) {
			LOGGER.error("Failed to enqueue delayed pet attack for player={} slot={}", playerId, slot);
		}
	}

	private static void requestPetProcessing(MinecraftServer server, UUID playerId, long delayTicks) {
		if (server == null || playerId == null || !settings.enabled || SCHEDULED_PLAYERS.contains(playerId)) {
			return;
		}

		String schedulerId = ensureSchedulerExists(playerId);
		if (enqueuePetTick(schedulerId, delayTicks)) {
			SCHEDULED_PLAYERS.add(playerId);
			return;
		}

		String created = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null));
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (enqueuePetTick(created, delayTicks)) {
			SCHEDULED_PLAYERS.add(playerId);
		} else {
			LOGGER.error("Failed to enqueue pet runtime task for player={}", playerId);
		}
	}

	private static String ensureSchedulerExists(UUID playerId) {
		String schedulerId = PLAYER_SCHEDULER_IDS.get(playerId);
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null));
			PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
		}
		return schedulerId;
	}

	private static boolean enqueuePetTick(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}

		MadokuScheduler.EnqueueStatus status = MadokuScheduler.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_PET_TICK,
			new JsonObject(),
			MadokuScheduler.TickDomain.GAMEPLAY
		);
		return status == MadokuScheduler.EnqueueStatus.ACCEPTED || status == MadokuScheduler.EnqueueStatus.QUEUE_FULL;
	}

	private static boolean enqueuePetAttackTask(String schedulerId, int slot, LivingEntity target, Vec3 spawnPosition, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank() || target == null || spawnPosition == null) {
			return false;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty(FIELD_SLOT, slot);
		payload.addProperty(FIELD_TARGET_UUID, target.getUUID().toString());
		payload.addProperty(FIELD_SPAWN_X, spawnPosition.x);
		payload.addProperty(FIELD_SPAWN_Y, spawnPosition.y);
		payload.addProperty(FIELD_SPAWN_Z, spawnPosition.z);
		MadokuScheduler.EnqueueStatus status = MadokuScheduler.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_PET_ATTACK,
			payload,
			MadokuScheduler.TickDomain.GAMEPLAY
		);
		return status == MadokuScheduler.EnqueueStatus.ACCEPTED;
	}

	private static boolean canPetSlotShoot(ServerPlayer player, int slot, long gameplayTicks, ItemStack stack) {
		if (player == null || slot < 0 || slot >= SLOT_COUNT) {
			return false;
		}
		PetRule rule = resolvePetRule(stack);
		if (!isValidPlayerEntity(stack) || rule == null || !rule.canPerformReactiveRangedAttack()) {
			clearSlotCooldown(player.getUUID(), slot);
			return false;
		}
		return gameplayTicks >= slotCooldowns(player.getUUID())[slot];
	}

	private static void clearSlotCooldowns(UUID playerId) {
		if (playerId != null) {
			PLAYER_SLOT_COOLDOWNS.remove(playerId);
		}
	}

	private static void clearSlotCooldown(UUID playerId, int slot) {
		if (playerId == null || slot < 0 || slot >= SLOT_COUNT) {
			return;
		}
		long[] cooldowns = PLAYER_SLOT_COOLDOWNS.get(playerId);
		if (cooldowns != null) {
			cooldowns[slot] = 0L;
			if (!hasNonZeroCooldown(cooldowns)) {
				PLAYER_SLOT_COOLDOWNS.remove(playerId);
			}
		}
	}

	private static void setSlotCooldown(UUID playerId, int slot, long cooldownTick) {
		if (playerId == null || slot < 0 || slot >= SLOT_COUNT) {
			return;
		}
		slotCooldowns(playerId)[slot] = Math.max(0L, cooldownTick);
	}

	private static long[] slotCooldowns(UUID playerId) {
		return PLAYER_SLOT_COOLDOWNS.computeIfAbsent(playerId, ignored -> new long[SLOT_COUNT]);
	}

	private static void pruneCooldowns(UUID playerId, PlayerEntitiesInventory inventory) {
		if (playerId == null || inventory == null) {
			return;
		}
		long[] cooldowns = PLAYER_SLOT_COOLDOWNS.get(playerId);
		if (cooldowns == null) {
			return;
		}
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			if (!isValidPlayerEntity(inventory.getItem(slot))) {
				cooldowns[slot] = 0L;
			}
		}
		if (!hasNonZeroCooldown(cooldowns)) {
			PLAYER_SLOT_COOLDOWNS.remove(playerId);
		}
	}

	private static boolean hasAnyValidPet(ServerPlayer player) {
		return countPlayerEntities(player) > 0;
	}

	private static void copyToNewPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
		PlayerEntitiesInventory oldInventory = playerEntitiesInventory(oldPlayer);
		PlayerEntitiesInventory newInventory = playerEntitiesInventory(newPlayer);
		if (oldInventory == null || newInventory == null) {
			return;
		}

		newInventory.copyFrom(oldInventory);
	}

	private static void removeAllPets(MinecraftServer server, UUID playerId) {
		UUID[] petIds = PET_IDS_BY_PLAYER.remove(playerId);
		if (petIds == null) {
			return;
		}

		for (UUID petId : petIds) {
			removePet(server, petId);
		}
	}

	private static void removePet(MinecraftServer server, UUID petId) {
		Mob pet = findMob(server, petId);
		if (pet != null) {
			pet.discard();
		}
		if (petId != null) {
			ACTIVE_PET_IDS.remove(petId);
			NEXT_IDLE_MOVE_BY_PET.remove(petId);
		}
	}

	private static Mob findMob(MinecraftServer server, UUID entityId) {
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

	private static void preparePet(Mob pet) {
		if (pet == null) {
			return;
		}

		pet.removeAllGoals(goal -> true);
		pet.setTarget(null);
		pet.setAggressive(false);
		pet.getNavigation().setCanFloat(true);
	}

	private static void tagManagedPet(Mob pet, UUID ownerId) {
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
			for (Entity entity : level.getAllEntities()) {
				if (entity != null && entity.entityTags().contains(MANAGED_PET_TAG)) {
					entity.discard();
				}
			}
		}
	}

	private static void removeTaggedPets(MinecraftServer server, UUID ownerId) {
		if (server == null || ownerId == null) {
			return;
		}

		String ownerTag = ownerTag(ownerId);
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity != null && entity.entityTags().contains(MANAGED_PET_TAG) && entity.entityTags().contains(ownerTag)) {
					entity.discard();
				}
			}
		}
	}

	private static String ownerTag(UUID ownerId) {
		return MANAGED_PET_OWNER_PREFIX + ownerId;
	}

	private static PlayerEntitiesInventory playerEntitiesInventory(Player player) {
		return player instanceof PlayerEntitiesHolder holder ? holder.madokuCraft$getPlayerEntitiesInventory() : null;
	}

	private static PetRule resolvePetRule(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return null;
		}
		return petRulesByItemId.get(normalizeKey(itemId.toString()));
	}

	private static UUID parsePlayerOwner(MadokuScheduler.SchedulerOwner owner) {
		if (owner == null || !"player".equals(owner.getKind())) {
			return null;
		}
		return parseUuid(owner.getOwnerId());
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

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("schedulers", new JsonArray());
		root.add("slot_cooldowns", new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = createDefaultData();
		JsonArray schedulers = new JsonArray();
		for (Map.Entry<UUID, String> entry : PLAYER_SCHEDULER_IDS.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
				continue;
			}
			JsonObject scheduler = new JsonObject();
			scheduler.addProperty("uuid", entry.getKey().toString());
			scheduler.addProperty("scheduler_id", entry.getValue().trim());
			schedulers.add(scheduler);
		}
		root.add("schedulers", schedulers);

		JsonArray cooldowns = new JsonArray();
		for (Map.Entry<UUID, long[]> entry : PLAYER_SLOT_COOLDOWNS.entrySet()) {
			if (entry.getKey() == null || !hasNonZeroCooldown(entry.getValue())) {
				continue;
			}
			JsonObject playerCooldowns = new JsonObject();
			playerCooldowns.addProperty("uuid", entry.getKey().toString());
			JsonArray values = new JsonArray();
			long[] source = entry.getValue();
			for (int slot = 0; slot < SLOT_COUNT; slot++) {
				values.add(slot < source.length ? Math.max(0L, source[slot]) : 0L);
			}
			playerCooldowns.add("cooldowns", values);
			cooldowns.add(playerCooldowns);
		}
		root.add("slot_cooldowns", cooldowns);
		return root;
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_SCHEDULER_IDS.clear();
		PLAYER_SLOT_COOLDOWNS.clear();
		if (source == null) {
			return;
		}

		JsonArray schedulers = getArray(source, "schedulers");
		if (schedulers != null) {
			for (JsonElement element : schedulers) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject schedulerData = element.getAsJsonObject();
				UUID playerId = parseUuid(getString(schedulerData, "uuid", ""));
				String schedulerId = getString(schedulerData, "scheduler_id", "");
				if (playerId == null || schedulerId.isBlank()) {
					continue;
				}
				PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
			}
		}

		JsonArray slotCooldowns = getArray(source, "slot_cooldowns");
		if (slotCooldowns == null) {
			return;
		}
		for (JsonElement element : slotCooldowns) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject cooldownData = element.getAsJsonObject();
			UUID playerId = parseUuid(getString(cooldownData, "uuid", ""));
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

	private static Item resolveItem(String itemId) {
		Identifier identifier = Identifier.tryParse(itemId == null ? "" : itemId.trim());
		return identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
	}

	private static String resolvePetItemId(String fileKey, JsonObject sourceRoot) {
		String configured = getString(sourceRoot, "item_id", "");
		if (!configured.isBlank()) {
			return configured.trim();
		}
		String normalizedFileKey = normalizeFileKey(fileKey);
		if (normalizedFileKey.isEmpty()) {
			return null;
		}
		String itemId = normalizedFileKey.contains(":") ? normalizedFileKey : "minecraft:" + normalizedFileKey;
		Item item = resolveItem(itemId);
		return item instanceof SpawnEggItem ? BuiltInRegistries.ITEM.getKey(item).toString() : null;
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("JSON file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static String normalizeFileKey(String rawKey) {
		return rawKey == null ? "" : rawKey.trim().toLowerCase();
	}

	private static String normalizeKey(String rawKey) {
		return rawKey == null ? "" : rawKey.trim().toLowerCase();
	}

	private static JsonArray getArray(JsonObject source, String key) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonArray()) {
			return null;
		}
		return source.getAsJsonArray(key);
	}

	private static String getString(JsonObject source, String key, String fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsString();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static long getLong(JsonObject source, String key, long fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int getInt(JsonObject source, String key, int fallback) {
		long value = getLong(source, key, fallback);
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			return fallback;
		}
		return (int) value;
	}

	private static double getDouble(JsonObject source, String key, double fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean getBoolean(JsonObject source, String key, boolean fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private record SlotOffset(double side, double back) {
	}

	private static final class Settings {
		private final boolean enabled;
		private final long schedulerTickInterval;
		private final double petScale;
		private final double followSpeed;
		private final double idleMoveSpeed;
		private final double idleDistance;
		private final double teleportDistance;
		private final double idleWanderRadius;
		private final long idleMinIntervalTicks;
		private final long idleMaxIntervalTicks;
		private final int ambientSoundIntervalMultiplier;
		private final float soundVolumeMultiplier;
		private final double attackRearOffset;
		private final double attackRearSpread;
		private final double attackLateralRadius;
		private final double attackVerticalOffset;

		private Settings(
			boolean enabled,
			long schedulerTickInterval,
			double petScale,
			double followSpeed,
			double idleMoveSpeed,
			double idleDistance,
			double teleportDistance,
			double idleWanderRadius,
			long idleMinIntervalTicks,
			long idleMaxIntervalTicks,
			int ambientSoundIntervalMultiplier,
			float soundVolumeMultiplier,
			double attackRearOffset,
			double attackRearSpread,
			double attackLateralRadius,
			double attackVerticalOffset
		) {
			this.enabled = enabled;
			this.schedulerTickInterval = schedulerTickInterval;
			this.petScale = petScale;
			this.followSpeed = followSpeed;
			this.idleMoveSpeed = idleMoveSpeed;
			this.idleDistance = idleDistance;
			this.teleportDistance = teleportDistance;
			this.idleWanderRadius = idleWanderRadius;
			this.idleMinIntervalTicks = idleMinIntervalTicks;
			this.idleMaxIntervalTicks = idleMaxIntervalTicks;
			this.ambientSoundIntervalMultiplier = ambientSoundIntervalMultiplier;
			this.soundVolumeMultiplier = soundVolumeMultiplier;
			this.attackRearOffset = attackRearOffset;
			this.attackRearSpread = attackRearSpread;
			this.attackLateralRadius = attackLateralRadius;
			this.attackVerticalOffset = attackVerticalOffset;
		}

		private static Settings defaults() {
			return new Settings(
				true,
				5L,
				0.25D,
				1.25D,
				0.75D,
				4.0D,
				8.0D,
				2.0D,
				20L,
				60L,
				3,
				0.5F,
				0.58D,
				0.20D,
				0.45D,
				-0.34D
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			long schedulerTickInterval = clampLong(getLong(source, "scheduler_tick_interval", defaults.schedulerTickInterval), 1L, 20L);
			double petScale = clampDouble(getDouble(source, "pet_scale", defaults.petScale), 0.01D, 4.0D);
			double followSpeed = clampDouble(getDouble(source, "follow_speed", defaults.followSpeed), 0.05D, 4.0D);
			double idleMoveSpeed = clampDouble(getDouble(source, "idle_move_speed", defaults.idleMoveSpeed), 0.05D, 4.0D);
			double idleDistance = clampDouble(getDouble(source, "idle_distance", defaults.idleDistance), 0.5D, 32.0D);
			double teleportDistance = clampDouble(getDouble(source, "teleport_distance", defaults.teleportDistance), idleDistance, 64.0D);
			double idleWanderRadius = clampDouble(getDouble(source, "idle_wander_radius", defaults.idleWanderRadius), 0.0D, 16.0D);
			long idleMinIntervalTicks = clampLong(getLong(source, "idle_min_interval_ticks", defaults.idleMinIntervalTicks), 1L, 20L * 60L);
			long idleMaxIntervalTicks = clampLong(getLong(source, "idle_max_interval_ticks", defaults.idleMaxIntervalTicks), idleMinIntervalTicks, 20L * 60L);
			int ambientSoundIntervalMultiplier = (int) clampLong(getLong(source, "ambient_sound_interval_multiplier", defaults.ambientSoundIntervalMultiplier), 1L, 20L);
			float soundVolumeMultiplier = (float) clampDouble(getDouble(source, "sound_volume_multiplier", defaults.soundVolumeMultiplier), 0.0D, 4.0D);
			double attackRearOffset = clampDouble(getDouble(source, "attack_rear_offset", defaults.attackRearOffset), 0.0D, 4.0D);
			double attackRearSpread = clampDouble(getDouble(source, "attack_rear_spread", defaults.attackRearSpread), 0.0D, 4.0D);
			double attackLateralRadius = clampDouble(getDouble(source, "attack_lateral_radius", defaults.attackLateralRadius), 0.0D, 4.0D);
			double attackVerticalOffset = clampDouble(getDouble(source, "attack_vertical_offset", defaults.attackVerticalOffset), -4.0D, 4.0D);
			return new Settings(
				enabled,
				schedulerTickInterval,
				petScale,
				followSpeed,
				idleMoveSpeed,
				idleDistance,
				teleportDistance,
				idleWanderRadius,
				idleMinIntervalTicks,
				idleMaxIntervalTicks,
				ambientSoundIntervalMultiplier,
				soundVolumeMultiplier,
				attackRearOffset,
				attackRearSpread,
				attackLateralRadius,
				attackVerticalOffset
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("scheduler_tick_interval", schedulerTickInterval);
			root.addProperty("pet_scale", petScale);
			root.addProperty("follow_speed", followSpeed);
			root.addProperty("idle_move_speed", idleMoveSpeed);
			root.addProperty("idle_distance", idleDistance);
			root.addProperty("teleport_distance", teleportDistance);
			root.addProperty("idle_wander_radius", idleWanderRadius);
			root.addProperty("idle_min_interval_ticks", idleMinIntervalTicks);
			root.addProperty("idle_max_interval_ticks", idleMaxIntervalTicks);
			root.addProperty("ambient_sound_interval_multiplier", ambientSoundIntervalMultiplier);
			root.addProperty("sound_volume_multiplier", soundVolumeMultiplier);
			root.addProperty("attack_rear_offset", attackRearOffset);
			root.addProperty("attack_rear_spread", attackRearSpread);
			root.addProperty("attack_lateral_radius", attackLateralRadius);
			root.addProperty("attack_vertical_offset", attackVerticalOffset);
			return root;
		}

		private static long clampLong(long value, long min, long max) {
			return Math.max(min, Math.min(max, value));
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}

	private static final class PetRule {
		private final boolean enabled;
		private final String itemId;
		private final String abilityType;
		private final float attackDamage;
		private final float attackSpeed;
		private final long cooldownTicks;
		private final long shotDelayTicks;
		private final double attackArcStepDegrees;
		private final String soundEventId;

		private PetRule(
			boolean enabled,
			String itemId,
			String abilityType,
			float attackDamage,
			float attackSpeed,
			long cooldownTicks,
			long shotDelayTicks,
			double attackArcStepDegrees,
			String soundEventId
		) {
			this.enabled = enabled;
			this.itemId = itemId;
			this.abilityType = abilityType;
			this.attackDamage = attackDamage;
			this.attackSpeed = attackSpeed;
			this.cooldownTicks = cooldownTicks;
			this.shotDelayTicks = shotDelayTicks;
			this.attackArcStepDegrees = attackArcStepDegrees;
			this.soundEventId = soundEventId;
		}

		private static JsonObject defaultsForItem(String itemId, String abilityType) {
			JsonObject root = new JsonObject();
			String resolvedItemId = itemId == null ? "" : itemId.trim();
			root.addProperty("enabled", true);
			root.addProperty("item_id", resolvedItemId);
			root.addProperty("ability", abilityType);
			root.addProperty("attack_damage", PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType) ? 3.0D : 0.0D);
			root.addProperty("attack_speed", PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType) ? 1.0D : 0.0D);
			root.addProperty("cooldown_ticks", PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType) ? 15L * 20L : 0L);
			root.addProperty("shot_delay_ticks", PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType) ? 5L : 0L);
			root.addProperty("attack_arc_step_degrees", 18.0D);
			root.addProperty("sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString());
			return root;
		}

		private static PetRule fromJson(JsonObject source, String fileKey) {
			if (source == null) {
				return null;
			}

			String itemId = resolvePetItemId(fileKey, source);
			if (itemId == null || itemId.isBlank()) {
				return null;
			}
			String abilityType = normalizeKey(getString(source, "ability", PET_ABILITY_NONE));
			float attackDamage = (float) Settings.clampDouble(getDouble(source, "attack_damage", 0.0D), 0.0D, 1024.0D);
			float attackSpeed = (float) Settings.clampDouble(getDouble(source, "attack_speed", 0.0D), 0.05D, 8.0D);
			long cooldownTicks = Settings.clampLong(getLong(source, "cooldown_ticks", 0L), 0L, 20L * 60L * 60L);
			long shotDelayTicks = Settings.clampLong(getLong(source, "shot_delay_ticks", 0L), 0L, 20L * 60L);
			double attackArcStepDegrees = Settings.clampDouble(getDouble(source, "attack_arc_step_degrees", 18.0D), 0.0D, 90.0D);
			String soundEventId = getString(source, "sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString());
			return new PetRule(
				getBoolean(source, "enabled", true),
				itemId.trim(),
				abilityType.isBlank() ? PET_ABILITY_NONE : abilityType,
				attackDamage,
				attackSpeed,
				cooldownTicks,
				shotDelayTicks,
				attackArcStepDegrees,
				soundEventId
			);
		}

		private boolean canPerformReactiveRangedAttack() {
			return enabled
				&& PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)
				&& attackDamage > 0.0F
				&& attackSpeed > 0.0F
				&& cooldownTicks > 0L;
		}

		private SoundEvent resolveSoundEvent() {
			Identifier identifier = Identifier.tryParse(soundEventId == null ? "" : soundEventId.trim());
			if (identifier == null) {
				return SoundEvents.SKELETON_SHOOT;
			}
			SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(identifier);
			return soundEvent == null ? SoundEvents.SKELETON_SHOOT : soundEvent;
		}
	}
}
