package madoku.craft.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.DynamicJsonSystem;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.data.MadokuData;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.mob.system.MadokuMob;
import madoku.craft.network.PetAbilityHudSync;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
	private static final String MANAGED_PET_ITEM_PREFIX = "madoku-craft.pet.item:";
	private static final String PET_ABILITY_NONE = "none";
	private static final String PET_ABILITY_RANGED_HOMING_ARROW = "ranged_homing_arrow";
	private static final String PET_ABILITY_PLAYER_DAMAGE_BONUS = "player_damage_bonus";
	private static final String PET_RARITY_COMMON = "common";
	private static final String PET_RARITY_RARE = "rare";
	private static final String PET_RARITY_EPIC = "epic";
	private static final String PET_RARITY_MYTHIC = "mythic";
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;
	private static final Identifier PLAYER_DAMAGE_ABILITY_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_damage_bonus");
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
	private static final Map<UUID, FollowCommand> FOLLOW_COMMANDS_BY_PET = new ConcurrentHashMap<>();
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Set<UUID> SCHEDULED_PLAYERS = new HashSet<>();
	private static final Set<UUID> DIRTY_ABILITY_HUD_PLAYERS = new HashSet<>();
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
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			UUID entityId = entity.getUUID();
			ACTIVE_PET_IDS.remove(entityId);
			NEXT_IDLE_MOVE_BY_PET.remove(entityId);
			FOLLOW_COMMANDS_BY_PET.remove(entityId);
		});
	}

	public static void reset() {
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
		PLAYER_SCHEDULER_IDS.clear();
		SCHEDULED_PLAYERS.clear();
		DIRTY_ABILITY_HUD_PLAYERS.clear();
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

		refreshPlayerDamageAbilityBonuses(server);

		if (!settings.enabled) {
			clearAllManagedPetState(server);
			flushAbilityHudSyncs(server);
			return;
		}

		if (!petEntitiesEnabled()) {
			clearAllManagedPetState(server);
			flushAbilityHudSyncs(server);
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || player.isSpectator()) {
				continue;
			}
			PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
			boolean hasAnyValidPet = hasAnyValidPet(player);
			if (!hasAnyValidPet && hasAnyConfiguredPetStack(inventory)) {
				debugPlayerEvent("pet.inventory_invalid", player)
					.field("inventory", inventorySummary(inventory))
					.log();
			}
			if (!hasAnyValidPet && !PET_IDS_BY_PLAYER.containsKey(player.getUUID())) {
				continue;
			}
			requestPetProcessing(server, player.getUUID(), 0L);
		}
		flushAbilityHudSyncs(server);
	}

	public static boolean isValidPlayerEntity(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SpawnEggItem)) {
			return false;
		}
		PetRule rule = resolvePetRule(stack);
		return rule != null && rule.enabled;
	}

	public static boolean isManagedPet(Entity entity) {
		return entity != null && (ACTIVE_PET_IDS.contains(entity.getUUID()) || entity.entityTags().contains(MANAGED_PET_TAG));
	}

	public static float soundVolume(Entity entity, float baseVolume) {
		PetRule rule = resolvePetRule(entity);
		float volumeMultiplier = rule == null ? 1.0F : rule.soundVolumeMultiplier;
		return Math.max(0.0F, baseVolume * volumeMultiplier);
	}

	public static int ambientSoundInterval(Entity entity, int baseInterval) {
		PetRule rule = resolvePetRule(entity);
		int intervalMultiplier = rule == null ? 1 : rule.ambientSoundIntervalMultiplier;
		return Math.max(20, baseInterval * Math.max(1, intervalMultiplier));
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static boolean areEntitiesEnabled() {
		return petEntitiesEnabled();
	}

	public static String petRarity(ItemStack stack) {
		PetRule rule = resolvePetRule(stack);
		return rule == null ? PET_RARITY_COMMON : rule.rarity;
	}

	public static String petRarity(Entity entity) {
		PetRule rule = resolvePetRule(entity);
		return rule == null ? PET_RARITY_COMMON : rule.rarity;
	}

	public static boolean hasAbility(ItemStack stack) {
		PetRule rule = resolvePetRule(stack);
		return rule != null && rule.hasAbility();
	}

	public static int abilityCooldownTicks(ItemStack stack) {
		PetRule rule = resolvePetRule(stack);
		return rule == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rule.cooldownTicks));
	}

	public static double playerDamageAbilityBonus(ServerPlayer player) {
		if (player == null || !settings.enabled) {
			return 0.0D;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null) {
			return 0.0D;
		}

		double totalBonus = 0.0D;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			PetRule rule = resolvePetRule(inventory.getItem(slot));
			if (rule != null) {
				totalBonus += rule.playerDamageBonus();
			}
		}
		return totalBonus;
	}

	public static void applyPlayerDamageAbilityBonus(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance attackDamageAttribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamageAttribute == null) {
			return;
		}

		attackDamageAttribute.removeModifier(PLAYER_DAMAGE_ABILITY_MODIFIER_ID);
		double bonus = playerDamageAbilityBonus(player);
		if (bonus <= 0.0D) {
			return;
		}

		attackDamageAttribute.addOrUpdateTransientModifier(
			new AttributeModifier(
				PLAYER_DAMAGE_ABILITY_MODIFIER_ID,
				bonus,
				AttributeModifier.Operation.ADD_VALUE
			)
		);
	}

	public static List<Item> tradeSpawnEggItems() {
		List<Item> items = new ArrayList<>();
		for (PetRule rule : petRulesByItemId.values()) {
			if (rule == null || !rule.enabled) {
				continue;
			}

			Item item = resolveItem(rule.itemId);
			if (!(item instanceof SpawnEggItem) || item == null) {
				continue;
			}
			items.add(item);
		}
		items.sort((left, right) -> BuiltInRegistries.ITEM.getKey(left).toString().compareTo(BuiltInRegistries.ITEM.getKey(right).toString()));
		return List.copyOf(items);
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

	private static MadokuDebug.EventBuilder debugPlayerEvent(String metricId, ServerPlayer player) {
		MadokuDebug.EventBuilder builder = MadokuDebug.event(metricId, MadokuDebug.Domain.PET)
			.side(MadokuDebug.Side.SERVER);
		if (player == null) {
			return builder;
		}
		return builder
			.tick(player.level().getGameTime())
			.world(player.level().dimension().toString())
			.subject("player:" + player.getScoreboardName())
			.field("player_uuid", player.getUUID());
	}

	private static MadokuDebug.EventBuilder debugPlayerIdEvent(String metricId, UUID playerId) {
		return MadokuDebug.event(metricId, MadokuDebug.Domain.PET)
			.side(MadokuDebug.Side.SERVER)
			.subject("player:" + (playerId == null ? "unknown" : playerId))
			.field("player_uuid", playerId);
	}

	private static MadokuDebug.EventBuilder debugPetEvent(String metricId, Mob pet) {
		MadokuDebug.EventBuilder builder = MadokuDebug.event(metricId, MadokuDebug.Domain.PET)
			.side(MadokuDebug.Side.SERVER);
		if (pet == null) {
			return builder;
		}
		return builder
			.tick(pet.level().getGameTime())
			.world(pet.level().dimension().toString())
			.subject("pet:" + pet.getUUID())
			.field("pet_uuid", pet.getUUID())
			.field("pet_type", entityTypeId(pet.getType()));
	}

	private static boolean hasAnyConfiguredPetStack(PlayerEntitiesInventory inventory) {
		if (inventory == null) {
			return false;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (!inventory.getItem(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static String inventorySummary(PlayerEntitiesInventory inventory) {
		if (inventory == null) {
			return "null";
		}
		StringBuilder builder = new StringBuilder(64);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (slot > 0) {
				builder.append(", ");
			}
			builder.append(slot).append('=').append(stackSummary(inventory.getItem(slot)));
		}
		return builder.toString();
	}

	private static String stackSummary(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "empty";
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return itemId == null ? "<unregistered>" : itemId.toString();
	}

	private static String entityTypeId(EntityType<?> entityType) {
		if (entityType == null) {
			return "null";
		}
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
		return typeId == null ? "<unregistered>" : typeId.toString();
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
			Map<String, JsonObject> abilityNormalizedFiles = new LinkedHashMap<>();
			for (Map.Entry<String, JsonObject> entry : normalizedFiles.entrySet()) {
				String fileKey = entry.getKey();
				JsonObject sourceRoot = entry.getValue();
				String itemId = resolvePetItemId(fileKey, sourceRoot);
				if (itemId == null || itemId.isBlank()) {
					continue;
				}

				String abilityType = normalizeKey(getString(sourceRoot, "ability", defaultAbilityForItem(itemId)));
				JsonObject abilityDefaults = PetRule.defaultsForItem(itemId, abilityType);
				Path file = resolveJsonFile(rulesDirectory, fileKey);
				JsonObject normalized = DynamicJsonSystem.writeManagedFile(file, sourceRoot, abilityDefaults, null);
				abilityNormalizedFiles.put(fileKey, normalized);
			}
			Map<String, PetRule> resolved = new LinkedHashMap<>();
			for (Map.Entry<String, JsonObject> entry : abilityNormalizedFiles.entrySet()) {
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
		defaults.put("skeleton", PetRule.defaultsForItem("minecraft:skeleton_spawn_egg", PET_ABILITY_RANGED_HOMING_ARROW));
		defaults.put("zombie", PetRule.defaultsForItem("minecraft:zombie_spawn_egg", PET_ABILITY_PLAYER_DAMAGE_BONUS));
		return defaults;
	}

	private static JsonObject buildDynamicPetRuleDefaults(String fileKey) {
		String itemId = resolvePetItemId(fileKey, null);
		if (itemId == null) {
			return new JsonObject();
		}
		return PetRule.defaultsForItem(itemId, defaultAbilityForItem(itemId));
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
		markAbilityHudDirty(player.getUUID());
		applyPlayerDamageAbilityBonus(player);
		if (petEntitiesEnabled()) {
			requestPetProcessing(server, player.getUUID(), 0L);
		}
	}

	private static void handlePlayerLeave(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		removeAllPets(server, playerId);
		SCHEDULED_PLAYERS.remove(playerId);
		DIRTY_ABILITY_HUD_PLAYERS.remove(playerId);
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
		if (entity != null && source.getEntity() instanceof ServerPlayer playerAttacker) {
			triggerReactiveRangedAttacks(playerAttacker, entity);
		}

		if (entity instanceof ServerPlayer playerVictim) {
			LivingEntity attackerTarget = resolveDamageSourceLivingEntity(source);
			if (attackerTarget != null) {
				triggerReactiveRangedAttacks(playerVictim, attackerTarget);
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

	private static void triggerReactiveRangedAttacks(ServerPlayer player, LivingEntity target) {
		if (player == null || target == null || !player.isAlive() || !target.isAlive() || player == target) {
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
		if (player == null || !settings.enabled || !petEntitiesEnabled()) {
			debugPlayerIdEvent("pet.tick_stopped", playerId)
				.field("reason", player == null ? "player_missing" : (!settings.enabled ? "system_disabled" : "entities_disabled"))
				.log();
			removeAllPets(server, playerId);
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			debugPlayerEvent("pet.tick_stopped", player)
				.field("reason", player.isSpectator() ? "spectator" : "dead")
				.log();
			removeAllPets(server, playerId);
			return;
		}

		debugPlayerEvent("pet.tick_started", player)
			.field("scheduler_id", context.getSchedulerId())
			.field("inventory", inventorySummary(playerEntitiesInventory(player)))
			.log();

		long nextDelay = syncPlayerPets(player);
		if (nextDelay >= 0L) {
			debugPlayerEvent("pet.tick_completed", player)
				.field("next_delay", nextDelay)
				.log();
			requestPetProcessing(server, playerId, nextDelay);
		} else {
			debugPlayerEvent("pet.tick_completed", player)
				.field("next_delay", -1)
				.field("reason", "no_active_pets")
				.log();
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

	private static long syncPlayerPets(ServerPlayer player) {
		MinecraftServer server = player == null ? null : player.level().getServer();
		if (server == null) {
			return -1L;
		}
		if (!petEntitiesEnabled()) {
			removeAllPets(server, player.getUUID());
			return -1L;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null) {
			removeAllPets(server, player.getUUID());
			return -1L;
		}

		boolean anyActive = false;
		long nextDelay = idleSchedulerTickInterval();
		UUID[] petIds = PET_IDS_BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> new UUID[SLOT_COUNT]);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = resolvePetRule(stack);
			EntityType<?> desiredType = resolvePetType(stack);
			Mob pet = findMob(server, petIds[slot]);

			if (desiredType == null || rule == null) {
				if (!stack.isEmpty()) {
					debugPlayerEvent("pet.slot_rejected", player)
						.field("slot", slot)
						.field("stack", stackSummary(stack))
						.field("desired_type", entityTypeId(desiredType))
						.field("rule_found", rule != null)
						.log();
				}
				removePet(server, petIds[slot]);
				petIds[slot] = null;
				clearSlotCooldown(player.getUUID(), slot);
				continue;
			}

			if (pet == null || pet.getType() != desiredType || pet.level() != player.level()) {
				debugPlayerEvent("pet.spawn_required", player)
					.field("slot", slot)
					.field("stack", stackSummary(stack))
					.field("desired_type", entityTypeId(desiredType))
					.field("existing_pet", pet == null ? "null" : pet.getUUID())
					.log();
				removePet(server, petIds[slot]);
				pet = spawnPet(player, desiredType, slot, rule);
				petIds[slot] = pet == null ? null : pet.getUUID();
			}

			if (pet != null) {
				ACTIVE_PET_IDS.add(pet.getUUID());
				ensurePetConfiguration(pet, rule);
				nextDelay = Math.min(nextDelay, updatePetPosition(player, pet, slot, rule));
				anyActive = true;
			}
		}

		if (!anyActive) {
			PET_IDS_BY_PLAYER.remove(player.getUUID());
			pruneCooldowns(player.getUUID(), inventory);
			return -1L;
		}

		return Math.max(1L, nextDelay);
	}

	private static Mob spawnPet(ServerPlayer owner, EntityType<?> entityType, int slot, PetRule rule) {
		if (owner == null || entityType == null || !(owner.level() instanceof ServerLevel level)) {
			return null;
		}

		debugPlayerEvent("pet.spawn_attempt", owner)
			.field("slot", slot)
			.field("entity_type", entityTypeId(entityType))
			.log();

		Entity entity = entityType.create(level, EntitySpawnReason.EVENT);
		if (!(entity instanceof Mob pet)) {
			debugPlayerEvent("pet.spawn_failed", owner)
				.field("slot", slot)
				.field("entity_type", entityTypeId(entityType))
				.field("reason", "entity_not_mob")
				.log();
			return null;
		}

		Vec3 desiredPosition = resolveDesiredPosition(owner, slot);
		pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
		preparePet(pet);
		configurePet(pet, rule);
		tagManagedPet(pet, owner.getUUID());
		if (!level.addFreshEntity(pet)) {
			debugPlayerEvent("pet.spawn_failed", owner)
				.field("slot", slot)
				.field("entity_type", entityTypeId(entityType))
				.field("reason", "add_fresh_entity_rejected")
				.log();
			return null;
		}

		ACTIVE_PET_IDS.add(pet.getUUID());
		debugPetEvent("pet.spawned", pet)
			.field("owner_uuid", owner.getUUID())
			.field("slot", slot)
			.field("item_id", rule == null ? "" : rule.itemId)
			.log();
		return pet;
	}

	private static void configurePet(Mob pet, PetRule rule) {
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
		setManagedPetItemId(pet, rule == null ? null : rule.itemId);
		AttributeInstance scale = pet.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(rule == null ? 0.25D : rule.petScale);
		}
	}

	private static void ensurePetConfiguration(Mob pet, PetRule rule) {
		if (pet == null) {
			return;
		}

		if (pet.isNoAi()) {
			pet.setNoAi(false);
		}
		if (!pet.isInvulnerable()) {
			pet.setInvulnerable(true);
		}
		if (pet.isSilent()) {
			pet.setSilent(false);
		}
		if (!pet.isPersistenceRequired()) {
			pet.setPersistenceRequired();
		}
		if (pet.noPhysics) {
			pet.noPhysics = false;
		}
		if (pet.isNoGravity()) {
			pet.setNoGravity(false);
		}
		if (pet.blocksBuilding) {
			pet.blocksBuilding = false;
		}
		if (pet.isOnFire()) {
			pet.clearFire();
		}
		if (pet.canPickUpLoot()) {
			pet.setCanPickUpLoot(false);
		}
		if (pet.getTarget() != null) {
			pet.setTarget(null);
		}
		if (pet.isAggressive()) {
			pet.setAggressive(false);
		}
		setManagedPetItemId(pet, rule == null ? null : rule.itemId);
		AttributeInstance scale = pet.getAttribute(Attributes.SCALE);
		if (scale != null) {
			double desiredScale = rule == null ? 0.25D : rule.petScale;
			if (Math.abs(scale.getBaseValue() - desiredScale) > 1.0E-4D) {
				scale.setBaseValue(desiredScale);
			}
		}
	}

	private static long updatePetPosition(ServerPlayer owner, Mob pet, int slot, PetRule rule) {
		Vec3 desiredPosition = resolveDesiredPosition(owner, slot);
		double ownerDistanceSqr = pet.distanceToSqr(owner);
		double creativeDistanceMultiplier = creativeDistanceMultiplier(owner);
		double teleportDistance = (rule == null ? 8.0D : rule.teleportDistance) * creativeDistanceMultiplier;
		double teleportDistanceSqr = teleportDistance * teleportDistance;
		if (ownerDistanceSqr > teleportDistanceSqr) {
			pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
			pet.getNavigation().stop();
			pet.setDeltaMovement(Vec3.ZERO);
			clearFollowCommand(pet.getUUID());
			scheduleNextIdleMove(pet, rule);
			return activeSchedulerTickInterval();
		}

		double idleDistance = (rule == null ? 4.0D : rule.idleDistance) * creativeDistanceMultiplier;
		double idleDistanceSqr = idleDistance * idleDistance;
		if (ownerDistanceSqr <= idleDistanceSqr) {
			clearFollowCommand(pet.getUUID());
			return updatePetIdleMovement(owner, pet, idleDistance, idleDistanceSqr, rule);
		}

		double followSpeed = rule == null ? 1.25D : rule.followSpeed;
		issueFollowCommandIfNeeded(pet, desiredPosition, followSpeed);
		pet.getLookControl().setLookAt(desiredPosition.x, desiredPosition.y, desiredPosition.z, 30.0F, 30.0F);
		return activeSchedulerTickInterval();
	}

	private static long updatePetIdleMovement(ServerPlayer owner, Mob pet, double idleDistance, double idleDistanceSqr, PetRule rule) {
		if (pet == null || !pet.getNavigation().isDone()) {
			return activeSchedulerTickInterval();
		}

		long gameTime = pet.level().getGameTime();
		long nextMoveTick = NEXT_IDLE_MOVE_BY_PET.getOrDefault(pet.getUUID(), Long.MIN_VALUE);
		if (gameTime < nextMoveTick) {
			return clampScheduledDelay(nextMoveTick - gameTime);
		}

		Vec3 idleTarget = resolveIdleTarget(owner, pet, idleDistance, idleDistanceSqr, rule);
		if (idleTarget == null) {
			scheduleNextIdleMove(pet, rule);
			return nextIdleMoveDelay(pet);
		}

		pet.getNavigation().moveTo(idleTarget.x, idleTarget.y, idleTarget.z, rule == null ? 0.75D : rule.idleMoveSpeed);
		pet.getLookControl().setLookAt(idleTarget.x, idleTarget.y, idleTarget.z, 20.0F, 20.0F);
		scheduleNextIdleMove(pet, rule);
		return activeSchedulerTickInterval();
	}

	private static Vec3 resolveIdleTarget(ServerPlayer owner, Mob pet, double idleDistance, double idleDistanceSqr, PetRule rule) {
		double idleWanderRadius = rule == null ? 2.0D : rule.idleWanderRadius;
		Vec3 current = pet.position();
		Vec3 offset = new Vec3(
			(pet.getRandom().nextDouble() - 0.5D) * 2.0D * idleWanderRadius,
			0.0D,
			(pet.getRandom().nextDouble() - 0.5D) * 2.0D * idleWanderRadius
		);
		Vec3 candidate = current.add(offset);
		if (candidate.distanceToSqr(owner.position()) > idleDistanceSqr) {
			Vec3 ownerDelta = candidate.subtract(owner.position());
			Vec3 horizontal = new Vec3(ownerDelta.x, 0.0D, ownerDelta.z);
			if (horizontal.lengthSqr() <= 1.0E-6D) {
				return null;
			}
			candidate = owner.position().add(horizontal.normalize().scale(Math.max(0.5D, idleDistance * 0.4D))).add(0.0D, 0.1D, 0.0D);
		}
		return candidate;
	}

	private static double creativeDistanceMultiplier(ServerPlayer owner) {
		return owner != null && owner.isCreative() ? 2.0D : 1.0D;
	}

	private static void scheduleNextIdleMove(Mob pet, PetRule rule) {
		if (pet == null) {
			return;
		}

		long minInterval = Math.max(1L, rule == null ? 20L : rule.idleMinIntervalTicks);
		long maxInterval = Math.max(minInterval, rule == null ? 60L : rule.idleMaxIntervalTicks);
		long delay = minInterval;
		if (maxInterval > minInterval) {
			delay += pet.getRandom().nextInt((int) (maxInterval - minInterval + 1L));
		}
		NEXT_IDLE_MOVE_BY_PET.put(pet.getUUID(), pet.level().getGameTime() + delay);
	}

	private static void issueFollowCommandIfNeeded(Mob pet, Vec3 desiredPosition, double followSpeed) {
		if (pet == null || desiredPosition == null) {
			return;
		}

		UUID petId = pet.getUUID();
		FollowCommand previous = FOLLOW_COMMANDS_BY_PET.get(petId);
		boolean shouldRepath = previous == null
			|| previous.target.distanceToSqr(desiredPosition) > 0.5625D
			|| Math.abs(previous.speed - followSpeed) > 1.0E-4D
			|| pet.getNavigation().isDone();
		if (!shouldRepath) {
			return;
		}

		pet.getNavigation().moveTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, followSpeed);
		FOLLOW_COMMANDS_BY_PET.put(petId, new FollowCommand(desiredPosition, followSpeed));
	}

	private static void clearFollowCommand(UUID petId) {
		if (petId != null) {
			FOLLOW_COMMANDS_BY_PET.remove(petId);
		}
	}

	private static long activeSchedulerTickInterval() {
		return Math.max(1L, settings.schedulerTickInterval);
	}

	private static boolean petEntitiesEnabled() {
		return settings.enabled && settings.entitiesEnabled;
	}

	private static long idleSchedulerTickInterval() {
		long activeInterval = activeSchedulerTickInterval();
		return Math.max(activeInterval, Math.min(20L, activeInterval * 3L));
	}

	private static long clampScheduledDelay(long delay) {
		return Math.max(activeSchedulerTickInterval(), Math.min(idleSchedulerTickInterval(), delay));
	}

	private static long nextIdleMoveDelay(Mob pet) {
		if (pet == null) {
			return idleSchedulerTickInterval();
		}
		long gameTime = pet.level().getGameTime();
		long nextMoveTick = NEXT_IDLE_MOVE_BY_PET.getOrDefault(pet.getUUID(), gameTime + idleSchedulerTickInterval());
		return clampScheduledDelay(nextMoveTick - gameTime);
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

		String created = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null));
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (!enqueuePetAttackTask(created, slot, target, spawnPosition, delayTicks)) {
			LOGGER.error("Failed to enqueue delayed pet attack for player={} slot={}", playerId, slot);
		}
	}

	private static void requestPetProcessing(MinecraftServer server, UUID playerId, long delayTicks) {
		if (server == null || playerId == null || !petEntitiesEnabled() || SCHEDULED_PLAYERS.contains(playerId)) {
			return;
		}

		String schedulerId = ensureSchedulerExists(playerId);
		if (enqueuePetTick(schedulerId, delayTicks)) {
			SCHEDULED_PLAYERS.add(playerId);
			debugPlayerIdEvent("pet.tick_requested", playerId)
				.field("scheduler_id", schedulerId)
				.field("delay", Math.max(0L, delayTicks))
				.log();
			return;
		}

		String created = MadokuScheduler.createOrGetScheduler(MadokuScheduler.SchedulerOwner.of("player", playerId.toString(), null));
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (enqueuePetTick(created, delayTicks)) {
			SCHEDULED_PLAYERS.add(playerId);
			debugPlayerIdEvent("pet.tick_requested", playerId)
				.field("scheduler_id", created)
				.field("delay", Math.max(0L, delayTicks))
				.field("scheduler_recreated", true)
				.log();
		} else {
			LOGGER.error("Failed to enqueue pet runtime task for player={}", playerId);
			debugPlayerIdEvent("pet.tick_request_failed", playerId)
				.field("scheduler_id", created)
				.field("delay", Math.max(0L, delayTicks))
				.log();
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
			markAbilityHudDirty(playerId);
		}
	}

	private static void refreshPlayerDamageAbilityBonuses(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyPlayerDamageAbilityBonus(player);
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
			markAbilityHudDirty(playerId);
		}
	}

	private static void setSlotCooldown(UUID playerId, int slot, long cooldownTick) {
		if (playerId == null || slot < 0 || slot >= SLOT_COUNT) {
			return;
		}
		slotCooldowns(playerId)[slot] = Math.max(0L, cooldownTick);
		markAbilityHudDirty(playerId);
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
		markAbilityHudDirty(playerId);
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
		markAbilityHudDirty(newPlayer.getUUID());
	}

	private static void markAbilityHudDirty(UUID playerId) {
		if (playerId != null) {
			DIRTY_ABILITY_HUD_PLAYERS.add(playerId);
		}
	}

	private static void flushAbilityHudSyncs(MinecraftServer server) {
		if (server == null || DIRTY_ABILITY_HUD_PLAYERS.isEmpty()) {
			return;
		}

		List<UUID> dirtyPlayers = new ArrayList<>(DIRTY_ABILITY_HUD_PLAYERS);
		DIRTY_ABILITY_HUD_PLAYERS.clear();
		for (UUID playerId : dirtyPlayers) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}
			PetAbilityHudSync.send(player, currentAbilityCooldowns(playerId));
		}
	}

	private static int[] currentAbilityCooldowns(UUID playerId) {
		int[] remaining = new int[SLOT_COUNT];
		if (playerId == null) {
			return remaining;
		}

		long[] cooldowns = PLAYER_SLOT_COOLDOWNS.get(playerId);
		if (cooldowns == null) {
			return remaining;
		}

		long now = MadokuTicks.getGameplayTicks();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, cooldowns.length); slot++) {
			remaining[slot] = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, cooldowns[slot] - now));
		}
		return remaining;
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
			debugPetEvent("pet.removed", pet).log();
			pet.discard();
		}
		if (petId != null) {
			ACTIVE_PET_IDS.remove(petId);
			NEXT_IDLE_MOVE_BY_PET.remove(petId);
			FOLLOW_COMMANDS_BY_PET.remove(petId);
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

	private static void clearAllManagedPetState(MinecraftServer server) {
		if (!ACTIVE_PET_IDS.isEmpty() || !PET_IDS_BY_PLAYER.isEmpty()) {
			removeTaggedPets(server);
		}
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
		SCHEDULED_PLAYERS.clear();
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
		return resolvePetRule(itemId.toString());
	}

	private static PetRule resolvePetRule(Entity entity) {
		if (entity == null) {
			return null;
		}
		String itemId = getManagedPetItemId(entity);
		return itemId.isBlank() ? null : resolvePetRule(itemId);
	}

	private static PetRule resolvePetRule(String itemId) {
		String normalizedItemId = normalizeKey(itemId);
		if (normalizedItemId.isEmpty()) {
			return null;
		}
		return petRulesByItemId.get(normalizedItemId);
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

	private static String defaultAbilityForItem(String itemId) {
		String normalizedItemId = normalizeKey(itemId);
		if ("minecraft:skeleton_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_RANGED_HOMING_ARROW;
		}
		if ("minecraft:zombie_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_PLAYER_DAMAGE_BONUS;
		}
		return PET_ABILITY_NONE;
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
		List<String> candidates = new ArrayList<>();
		if (normalizedFileKey.contains(":")) {
			candidates.add(normalizedFileKey);
		} else {
			candidates.add("minecraft:" + normalizedFileKey);
			candidates.add("minecraft:" + normalizedFileKey + "_spawn_egg");
		}
		for (String itemId : candidates) {
			Item item = resolveItem(itemId);
			if (item instanceof SpawnEggItem) {
				return BuiltInRegistries.ITEM.getKey(item).toString();
			}
		}
		return null;
	}

	private static void setManagedPetItemId(Mob pet, String itemId) {
		if (pet == null) {
			return;
		}
		String existingTag = null;
		for (String tag : pet.entityTags()) {
			if (tag != null && tag.startsWith(MANAGED_PET_ITEM_PREFIX)) {
				existingTag = tag;
				break;
			}
		}
		String normalizedItemId = normalizeKey(itemId);
		String desiredTag = normalizedItemId.isEmpty() ? "" : MANAGED_PET_ITEM_PREFIX + normalizedItemId;
		if (desiredTag.equals(existingTag)) {
			return;
		}
		if (existingTag != null) {
			pet.removeTag(existingTag);
		}
		if (!desiredTag.isEmpty()) {
			pet.addTag(desiredTag);
		}
	}

	private static String getManagedPetItemId(Entity entity) {
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

	private static String normalizePetRarity(String rawRarity) {
		String normalized = normalizeKey(rawRarity);
		return switch (normalized) {
			case PET_RARITY_COMMON, PET_RARITY_RARE, PET_RARITY_EPIC, PET_RARITY_MYTHIC -> normalized;
			default -> PET_RARITY_COMMON;
		};
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

	private record FollowCommand(Vec3 target, double speed) {
	}

	private static final class Settings {
		private final boolean enabled;
		private final boolean entitiesEnabled;
		private final long schedulerTickInterval;

		private Settings(
			boolean enabled,
			boolean entitiesEnabled,
			long schedulerTickInterval
		) {
			this.enabled = enabled;
			this.entitiesEnabled = entitiesEnabled;
			this.schedulerTickInterval = schedulerTickInterval;
		}

		private static Settings defaults() {
			return new Settings(
				true,
				true,
				5L
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			boolean entitiesEnabled = getBoolean(source, "entities_enabled", defaults.entitiesEnabled);
			long schedulerTickInterval = clampLong(getLong(source, "scheduler_tick_interval", defaults.schedulerTickInterval), 1L, 20L);
			return new Settings(
				enabled,
				entitiesEnabled,
				schedulerTickInterval
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("entities_enabled", entitiesEnabled);
			root.addProperty("scheduler_tick_interval", schedulerTickInterval);
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
		private final String rarity;
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
		private final String abilityType;
		private final float attackDamage;
		private final float attackSpeed;
		private final double playerDamageBonusAmount;
		private final long cooldownTicks;
		private final long shotDelayTicks;
		private final double attackArcStepDegrees;
		private final double attackRearOffset;
		private final double attackRearSpread;
		private final double attackLateralRadius;
		private final double attackVerticalOffset;
		private final String soundEventId;

		private PetRule(
			boolean enabled,
			String itemId,
			String rarity,
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
			String abilityType,
			float attackDamage,
			float attackSpeed,
			double playerDamageBonusAmount,
			long cooldownTicks,
			long shotDelayTicks,
			double attackArcStepDegrees,
			double attackRearOffset,
			double attackRearSpread,
			double attackLateralRadius,
			double attackVerticalOffset,
			String soundEventId
		) {
			this.enabled = enabled;
			this.itemId = itemId;
			this.rarity = rarity;
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
			this.abilityType = abilityType;
			this.attackDamage = attackDamage;
			this.attackSpeed = attackSpeed;
			this.playerDamageBonusAmount = playerDamageBonusAmount;
			this.cooldownTicks = cooldownTicks;
			this.shotDelayTicks = shotDelayTicks;
			this.attackArcStepDegrees = attackArcStepDegrees;
			this.attackRearOffset = attackRearOffset;
			this.attackRearSpread = attackRearSpread;
			this.attackLateralRadius = attackLateralRadius;
			this.attackVerticalOffset = attackVerticalOffset;
			this.soundEventId = soundEventId;
		}

		private static JsonObject defaultsForItem(String itemId, String abilityType) {
			JsonObject root = new JsonObject();
			String resolvedItemId = itemId == null ? "" : itemId.trim();
			String resolvedAbilityType = normalizeKey(abilityType);
			boolean usesRangedHomingArrow = PET_ABILITY_RANGED_HOMING_ARROW.equals(resolvedAbilityType);
			boolean usesPlayerDamageBonus = PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(resolvedAbilityType);
			root.addProperty("enabled", true);
			root.addProperty("item_id", resolvedItemId);
			root.addProperty("rarity", PET_RARITY_COMMON);
			root.addProperty("pet_scale", 0.25D);
			root.addProperty("follow_speed", 1.2D);
			root.addProperty("idle_move_speed", 0.8D);
			root.addProperty("idle_distance", 4.0D);
			root.addProperty("teleport_distance", 8.0D);
			root.addProperty("idle_wander_radius", 2.0D);
			root.addProperty("idle_min_interval_ticks", 20L);
			root.addProperty("idle_max_interval_ticks", 60L);
			root.addProperty("ambient_sound_interval_multiplier", 3);
			root.addProperty("sound_volume_multiplier", 0.5D);
			root.addProperty("ability", resolvedAbilityType.isBlank() ? PET_ABILITY_NONE : resolvedAbilityType);
			if (usesRangedHomingArrow) {
				root.addProperty("attack_damage", 5.0D);
				root.addProperty("attack_speed", 1.0D);
				root.addProperty("cooldown_ticks", 10L * 20L);
				root.addProperty("shot_delay_ticks", 10L);
				root.addProperty("attack_arc_step_degrees", 18.0D);
				root.addProperty("attack_rear_offset", 0.58D);
				root.addProperty("attack_rear_spread", 0.20D);
				root.addProperty("attack_lateral_radius", 0.45D);
				root.addProperty("attack_vertical_offset", -0.34D);
				root.addProperty("sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString());
			}
			if (usesPlayerDamageBonus) {
				root.addProperty("player_damage_bonus", 1.0D);
			}
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
			String rarity = normalizePetRarity(getString(source, "rarity", PET_RARITY_COMMON));
			double petScale = Settings.clampDouble(getDouble(source, "pet_scale", 0.25D), 0.01D, 4.0D);
			double followSpeed = Settings.clampDouble(getDouble(source, "follow_speed", 1.2D), 0.05D, 4.0D);
			double idleMoveSpeed = Settings.clampDouble(getDouble(source, "idle_move_speed", 0.8D), 0.05D, 4.0D);
			double idleDistance = Settings.clampDouble(getDouble(source, "idle_distance", 4.0D), 0.5D, 32.0D);
			double teleportDistance = Settings.clampDouble(getDouble(source, "teleport_distance", 8.0D), idleDistance, 64.0D);
			double idleWanderRadius = Settings.clampDouble(getDouble(source, "idle_wander_radius", 2.0D), 0.0D, 16.0D);
			long idleMinIntervalTicks = Settings.clampLong(getLong(source, "idle_min_interval_ticks", 20L), 1L, 20L * 60L);
			long idleMaxIntervalTicks = Settings.clampLong(getLong(source, "idle_max_interval_ticks", 60L), idleMinIntervalTicks, 20L * 60L);
			int ambientSoundIntervalMultiplier = (int) Settings.clampLong(getLong(source, "ambient_sound_interval_multiplier", 3L), 1L, 20L);
			float soundVolumeMultiplier = (float) Settings.clampDouble(getDouble(source, "sound_volume_multiplier", 0.5D), 0.0D, 4.0D);
			String abilityType = normalizeKey(getString(source, "ability", PET_ABILITY_NONE));
			float attackDamage = (float) Settings.clampDouble(getDouble(source, "attack_damage", 0.0D), 0.0D, 1024.0D);
			float attackSpeed = (float) Settings.clampDouble(getDouble(source, "attack_speed", 0.0D), 0.05D, 8.0D);
			double playerDamageBonusAmount = Settings.clampDouble(getDouble(source, "player_damage_bonus", 0.0D), 0.0D, 1024.0D);
			long cooldownTicks = Settings.clampLong(getLong(source, "cooldown_ticks", 0L), 0L, 20L * 60L * 60L);
			long shotDelayTicks = Settings.clampLong(getLong(source, "shot_delay_ticks", 0L), 0L, 20L * 60L);
			double attackArcStepDegrees = Settings.clampDouble(getDouble(source, "attack_arc_step_degrees", 18.0D), 0.0D, 90.0D);
			double attackRearOffset = Settings.clampDouble(getDouble(source, "attack_rear_offset", 0.58D), 0.0D, 4.0D);
			double attackRearSpread = Settings.clampDouble(getDouble(source, "attack_rear_spread", 0.20D), 0.0D, 4.0D);
			double attackLateralRadius = Settings.clampDouble(getDouble(source, "attack_lateral_radius", 0.45D), 0.0D, 4.0D);
			double attackVerticalOffset = Settings.clampDouble(getDouble(source, "attack_vertical_offset", -0.34D), -4.0D, 4.0D);
			String soundEventId = getString(source, "sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString());
			return new PetRule(
				getBoolean(source, "enabled", true),
				itemId.trim(),
				rarity,
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
				abilityType.isBlank() ? PET_ABILITY_NONE : abilityType,
				attackDamage,
				attackSpeed,
				playerDamageBonusAmount,
				cooldownTicks,
				shotDelayTicks,
				attackArcStepDegrees,
				attackRearOffset,
				attackRearSpread,
				attackLateralRadius,
				attackVerticalOffset,
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

		private double playerDamageBonus() {
			return enabled && PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType) ? playerDamageBonusAmount : 0.0D;
		}

		private boolean hasAbility() {
			return enabled && !PET_ABILITY_NONE.equals(abilityType);
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
