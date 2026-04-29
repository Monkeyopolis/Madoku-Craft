package madoku.craft.levels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.attributes.MadokuAttributes;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.luck.MadokuLuck;
import madoku.craft.network.MadokuLevelUpPayload;
import madoku.craft.network.MadokuLevelsPayload;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MadokuLevels {
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-levels";
	private static final String CONFIG_FILE_NAME = "madoku-levels";
	private static final String DATA_FOLDER_NAME = "madoku-craft-levels";
	private static final String DATA_FILE_NAME = "madoku-levels";
	private static final double DEFAULT_BASE_XP_REQUIREMENT = 5.0d;
	private static final double DEFAULT_BASE_XP_MULTIPLIER = 0.10d;
	private static final int DEFAULT_MAX_PLAYER_LEVEL_ATTRIBUTES = 60;
	private static final int DEFAULT_MAX_PLAYER_LEVEL_ATTRIBUTES_PARTIAL = 50;
	private static final int DEFAULT_MAX_PLAYER_LEVEL_VANILLA = 40;
	private static final int DEFAULT_MAX_STAT_LEVEL_ATTRIBUTES = 10;
	private static final int DEFAULT_MAX_STAT_LEVEL_VANILLA = 10;
	private static final double DEFAULT_HEALTH_PER_LEVEL = 1.0d;
	private static final double DEFAULT_PLAYER_DAMAGE_PER_LEVEL = 0.2d;
	private static final double DEFAULT_PLAYER_ARMOR_PER_LEVEL_ATTRIBUTES = 0.4d;
	private static final double DEFAULT_PLAYER_ARMOR_PER_LEVEL_VANILLA = 0.2d;
	private static final double DEFAULT_PLAYER_LUCK_PER_LEVEL = 0.02d;
	private static final double DEFAULT_PLAYER_HUNGER_PER_LEVEL = 2.0d;
	private static final double DEFAULT_PLAYER_MOVEMENT_SPEED_PER_LEVEL = 0.001d;
	private static final Identifier HEALTH_BONUS_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels_health_bonus");
	private static final Identifier DAMAGE_BONUS_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels_damage_bonus");
	private static final Identifier ARMOR_BONUS_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels_armor_bonus");
	private static final Identifier LUCK_BONUS_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels_luck_bonus");
	private static final Identifier MOVEMENT_SPEED_BONUS_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels_movement_speed_bonus");

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static final Set<UUID> DIRTY_PLAYERS = new HashSet<>();
	private static volatile Settings settings = Settings.defaults();
	private static boolean initialized = false;
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private MadokuLevels() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		loadStaticConfig();
		PayloadTypeRegistry.clientboundPlay().register(MadokuLevelsPayload.TYPE, MadokuLevelsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MadokuLevelUpPayload.TYPE, MadokuLevelUpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MadokuLevelUpPayload.TYPE, (payload, context) ->
			handleLevelUpRequest(context.player(), payload.statId())
		);
		ServerPlayerEvents.JOIN.register(MadokuLevels::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuLevels::handlePlayerRespawn);
		initialized = true;
	}

	public static void reset() {
		PLAYER_STATES.clear();
		DIRTY_PLAYERS.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static int maxStatLevel() {
		return Math.max(1, MadokuAttributes.isEnabled() ? settings.maxStatLevelAttributes : settings.maxStatLevelVanilla);
	}

	public static double healthPerLevel() {
		return settings.healthPerLevel;
	}

	public static double playerDamagePerLevel() {
		return settings.playerDamagePerLevel;
	}

	public static double playerArmorPerLevelAttributes() {
		return settings.playerArmorPerLevelAttributes;
	}

	public static double playerArmorPerLevelVanilla() {
		return settings.playerArmorPerLevelVanilla;
	}

	public static double playerHungerPerLevel() {
		return settings.playerHungerPerLevel;
	}

	public static double playerLuckPerLevel() {
		return settings.playerLuckPerLevel;
	}

	public static double playerMovementSpeedPerLevel() {
		return settings.playerMovementSpeedPerLevel;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(data);
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ensurePlayerState(player);
			applyPlayerAttributes(player);
			markDirty(player.getUUID());
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
	}

	public static void flushDirtySyncs(MinecraftServer server) {
		if (server == null || DIRTY_PLAYERS.isEmpty()) {
			return;
		}

		Set<UUID> syncedPlayers = new HashSet<>();
		for (UUID playerId : DIRTY_PLAYERS) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null || !ServerPlayNetworking.canSend(player, MadokuLevelsPayload.TYPE)) {
				continue;
			}
			ServerPlayNetworking.send(player, createPayload(player));
			syncedPlayers.add(playerId);
		}

		DIRTY_PLAYERS.removeAll(syncedPlayers);
	}

	public static void addXp(ServerPlayer player, int xpAmount) {
		if (player == null || xpAmount <= 0) {
			return;
		}

		PlayerState state = ensurePlayerState(player);
		int maxPlayerLevel = maxPlayerLevel();
		if (state.level >= maxPlayerLevel) {
			return;
		}

		state.currentXp += xpAmount;

		while (state.currentXp >= state.requiredXp && state.level < maxPlayerLevel) {
			state.currentXp -= state.requiredXp;
			state.level++;
			state.availablePoints++;
			if (state.level >= maxPlayerLevel) {
				state.level = maxPlayerLevel;
				state.currentXp = 0;
			}
			state.requiredXp = requiredXpForLevel(state.level);
		}

		applyPlayerAttributes(player);
		markDirty(player.getUUID());
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}

		ensurePlayerState(player);
		applyPlayerAttributes(player);
		markDirty(player.getUUID());
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null) {
			return;
		}

		PlayerState state = ensurePlayerState(newPlayer);
		if (!alive) {
			state.currentXp = 0;
			state.requiredXp = requiredXpForLevel(state.level);
		}
		applyPlayerAttributes(newPlayer);
		markDirty(newPlayer.getUUID());
	}

	private static void handleLevelUpRequest(ServerPlayer player, String statId) {
		if (player == null) {
			return;
		}

		MadokuLevelStat stat = MadokuLevelStat.fromId(statId);
		if (stat == null) {
			return;
		}

		PlayerState state = ensurePlayerState(player);
		if (state.availablePoints <= 0) {
			return;
		}

		int currentStatLevel = state.statLevels.getOrDefault(stat, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		if (currentStatLevel >= MadokuLevelStat.maxStatLevel()) {
			return;
		}

		state.statLevels.put(stat, MadokuLevelStat.clampLevel(currentStatLevel + 1));
		state.availablePoints--;
		applyPlayerAttributes(player);
		MadokuHunger.handleMaximumHungerChanged(player);
		markDirty(player.getUUID());
	}

	private static PlayerState ensurePlayerState(ServerPlayer player) {
		return PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> PlayerState.createDefault());
	}

	private static void applyPlayerAttributes(ServerPlayer player) {
		if (player == null) {
			return;
		}

		PlayerState state = ensurePlayerState(player);
		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance attackDamageAttribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
		AttributeInstance armorAttribute = player.getAttribute(Attributes.ARMOR);
		AttributeInstance luckAttribute = player.getAttribute(Attributes.LUCK);
		AttributeInstance movementSpeedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);

		int healthStatLevel = state.statLevels.getOrDefault(MadokuLevelStat.HEALTH, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		int damageStatLevel = state.statLevels.getOrDefault(MadokuLevelStat.PLAYER_DAMAGE, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		int armorStatLevel = state.statLevels.getOrDefault(MadokuLevelStat.PLAYER_ARMOR, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		int luckStatLevel = state.statLevels.getOrDefault(MadokuLevelStat.PLAYER_LUCK, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		int movementSpeedStatLevel = state.statLevels.getOrDefault(MadokuLevelStat.PLAYER_MOVEMENT_SPEED, MadokuLevelStat.DEFAULT_STAT_LEVEL);

		if (maxHealthAttribute != null) {
			applyAttributeModifier(maxHealthAttribute, HEALTH_BONUS_MODIFIER_ID, MadokuLevelStat.HEALTH.valueAtLevel(healthStatLevel));
		}
		PlayerEntitiesSystem.applyPlayerMaxHealthAbilityBonus(player);
		if (attackDamageAttribute != null) {
			applyAttributeModifier(attackDamageAttribute, DAMAGE_BONUS_MODIFIER_ID, MadokuLevelStat.PLAYER_DAMAGE.valueAtLevel(damageStatLevel));
		}
		PlayerEntitiesSystem.applyPlayerDamageAbilityBonus(player);
		if (armorAttribute != null) {
			applyAttributeModifier(armorAttribute, ARMOR_BONUS_MODIFIER_ID, MadokuLevelStat.PLAYER_ARMOR.valueAtLevel(armorStatLevel));
		}
		PlayerEntitiesSystem.applyPlayerArmorAbilityBonus(player);
		if (luckAttribute != null) {
			applyAttributeModifier(luckAttribute, LUCK_BONUS_MODIFIER_ID, MadokuLevelStat.PLAYER_LUCK.valueAtLevel(luckStatLevel));
		}
		if (movementSpeedAttribute != null) {
			applyAttributeModifier(
				movementSpeedAttribute,
				MOVEMENT_SPEED_BONUS_MODIFIER_ID,
				MadokuLevelStat.PLAYER_MOVEMENT_SPEED.valueAtLevel(movementSpeedStatLevel)
			);
		}

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	public static int getPlayerHungerBonusPoints(ServerPlayer player) {
		if (player == null || !MadokuHunger.isEnabled()) {
			return 0;
		}

		PlayerState state = ensurePlayerState(player);
		int hungerStatLevel = state.statLevels.getOrDefault(MadokuLevelStat.PLAYER_HUNGER, MadokuLevelStat.DEFAULT_STAT_LEVEL);
		return (int) Math.round(MadokuLevelStat.PLAYER_HUNGER.valueAtLevel(hungerStatLevel));
	}

	private static MadokuLevelsPayload createPayload(ServerPlayer player) {
		PlayerState state = ensurePlayerState(player);
		boolean useAttributesContainer = useAttributesContainer();
		List<MadokuLevelStat> visibleStats = visibleStats();
		int effectiveLevel = Math.min(state.level, maxPlayerLevel());
		int effectiveCurrentXp = state.level >= maxPlayerLevel() ? 0 : state.currentXp;
		int effectiveRequiredXp = requiredXpForLevel(effectiveLevel);
		return new MadokuLevelsPayload(
			player.getName().getString(),
			effectiveLevel,
			effectiveCurrentXp,
			effectiveRequiredXp,
			state.availablePoints,
			MadokuLevelStat.maxStatLevel(),
			useAttributesContainer,
			MadokuLevelStat.encodeVisibleStats(visibleStats),
			MadokuLevelStat.encodeLevels(state.statLevels)
		);
	}

	private static boolean useAttributesContainer() {
		return MadokuAttributes.isEnabled() && hasExtendedAttributeStats();
	}

	private static List<MadokuLevelStat> visibleStats() {
		if (!MadokuAttributes.isEnabled()) {
			return MadokuLevelStat.vanillaVisibleStats();
		}
		if (!hasExtendedAttributeStats()) {
			return MadokuLevelStat.vanillaVisibleStats();
		}
		if (!MadokuHunger.isEnabled()) {
			return MadokuLevelStat.attributeVisibleStatsWithoutHunger();
		}
		if (!MadokuLuck.isEnabled()) {
			return MadokuLevelStat.attributeVisibleStatsWithoutLuck();
		}
		// Health remains visible even if the custom health subsystem is disabled.
		return MadokuLevelStat.attributeVisibleStats();
	}

	private static boolean hasExtendedAttributeStats() {
		return MadokuHunger.isEnabled() || MadokuLuck.isEnabled();
	}

	private static int activeExtendedAttributeStatCount() {
		int count = 0;
		if (MadokuHunger.isEnabled()) {
			count++;
		}
		if (MadokuLuck.isEnabled()) {
			count++;
		}
		return count;
	}

	private static void markDirty(UUID playerId) {
		if (playerId != null) {
			DIRTY_PLAYERS.add(playerId);
		}
	}

	private static void applyAttributeModifier(AttributeInstance attribute, Identifier modifierId, double amount) {
		if (attribute == null || modifierId == null) {
			return;
		}

		attribute.removeModifier(modifierId);
		if (amount > 0.0d) {
			attribute.addOrUpdateTransientModifier(
				new AttributeModifier(
					modifierId,
					amount,
					AttributeModifier.Operation.ADD_VALUE
				)
			);
		}
	}

	private static int requiredXpForLevel(int level) {
		int normalizedLevel = Math.max(1, level);
		double scaledBase = settings.baseXpRequirement * normalizedLevel;
		double scaledMultiplier = 1.0d + (settings.baseXpMultiplier * (normalizedLevel - 1));
		return Math.max(1, (int) Math.round(scaledBase * scaledMultiplier));
	}

	private static int maxPlayerLevel() {
		if (!MadokuAttributes.isEnabled()) {
			return Math.max(1, settings.maxPlayerLevelVanilla);
		}

		int activeExtendedStats = activeExtendedAttributeStatCount();
		if (activeExtendedStats <= 0) {
			return Math.max(1, settings.maxPlayerLevelVanilla);
		}
		if (activeExtendedStats == 1) {
			return Math.max(1, settings.maxPlayerLevelAttributesPartial);
		}
		return Math.max(1, settings.maxPlayerLevelAttributes);
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path directory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
		}
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

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add("players", new JsonArray());
		return root;
	}

	private static void applyPersistedData(JsonObject data) {
		PLAYER_STATES.clear();
		if (data == null) {
			return;
		}

		JsonArray players = data.has("players") && data.get("players").isJsonArray() ? data.getAsJsonArray("players") : new JsonArray();
		for (JsonElement element : players) {
			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject playerData = element.getAsJsonObject();
			String uuidText = getString(playerData, "uuid", "");
			if (uuidText.isBlank()) {
				continue;
			}

			try {
				UUID playerId = UUID.fromString(uuidText);
				PlayerState state = PlayerState.createDefault();
				state.level = Math.max(1, getInt(playerData, "level", 1));
				state.currentXp = Math.max(0, getInt(playerData, "currentXp", 0));
				state.requiredXp = Math.max(1, getInt(playerData, "requiredXp", requiredXpForLevel(state.level)));
				state.availablePoints = Math.max(0, getInt(playerData, "availablePoints", 1));
				if (state.level <= 1 && state.availablePoints < 1) {
					state.availablePoints = 1;
				}
				if (playerData.has("stats") && playerData.get("stats").isJsonObject()) {
					JsonObject statsObject = playerData.getAsJsonObject("stats");
					for (MadokuLevelStat stat : MadokuLevelStat.values()) {
						state.statLevels.put(
							stat,
							MadokuLevelStat.clampLevel(getInt(statsObject, stat.id(), MadokuLevelStat.DEFAULT_STAT_LEVEL))
						);
					}
				}
				PLAYER_STATES.put(playerId, state);
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		JsonArray players = new JsonArray();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			JsonObject playerData = new JsonObject();
			playerData.addProperty("uuid", entry.getKey().toString());
			playerData.addProperty("level", state.level);
			playerData.addProperty("currentXp", state.currentXp);
			playerData.addProperty("requiredXp", state.requiredXp);
			playerData.addProperty("availablePoints", state.availablePoints);
			JsonObject statsObject = new JsonObject();
			for (MadokuLevelStat stat : MadokuLevelStat.values()) {
				statsObject.addProperty(stat.id(), state.statLevels.getOrDefault(stat, MadokuLevelStat.DEFAULT_STAT_LEVEL));
			}
			playerData.add("stats", statsObject);
			players.add(playerData);
		}
		root.add("players", players);
		return root;
	}

	private static int getInt(JsonObject object, String memberName, int fallback) {
		if (object == null || memberName == null || !object.has(memberName)) {
			return fallback;
		}
		JsonElement element = object.get(memberName);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : fallback;
	}

	private static String getString(JsonObject object, String memberName, String fallback) {
		if (object == null || memberName == null || !object.has(memberName)) {
			return fallback;
		}
		JsonElement element = object.get(memberName);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : fallback;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int clampInt(long value, int min, int max) {
		return (int) Math.max(min, Math.min(max, value));
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class PlayerState {
		private int level;
		private int currentXp;
		private int requiredXp;
		private int availablePoints;
		private final EnumMap<MadokuLevelStat, Integer> statLevels;

		private PlayerState(int level, int currentXp, int requiredXp, int availablePoints, EnumMap<MadokuLevelStat, Integer> statLevels) {
			this.level = level;
			this.currentXp = currentXp;
			this.requiredXp = requiredXp;
			this.availablePoints = availablePoints;
			this.statLevels = statLevels;
		}

		private static PlayerState createDefault() {
			return new PlayerState(1, 0, requiredXpForLevel(1), 1, MadokuLevelStat.createDefaultLevels());
		}
	}

	private static final class Settings {
		private final double baseXpRequirement;
		private final double baseXpMultiplier;
		private final int maxPlayerLevelAttributes;
		private final int maxPlayerLevelAttributesPartial;
		private final int maxPlayerLevelVanilla;
		private final int maxStatLevelAttributes;
		private final int maxStatLevelVanilla;
		private final double healthPerLevel;
		private final double playerDamagePerLevel;
		private final double playerArmorPerLevelAttributes;
		private final double playerArmorPerLevelVanilla;
		private final double playerLuckPerLevel;
		private final double playerHungerPerLevel;
		private final double playerMovementSpeedPerLevel;

		private Settings(
			double baseXpRequirement,
			double baseXpMultiplier,
			int maxPlayerLevelAttributes,
			int maxPlayerLevelAttributesPartial,
			int maxPlayerLevelVanilla,
			int maxStatLevelAttributes,
			int maxStatLevelVanilla,
			double healthPerLevel,
			double playerDamagePerLevel,
			double playerArmorPerLevelAttributes,
			double playerArmorPerLevelVanilla,
			double playerLuckPerLevel,
			double playerHungerPerLevel,
			double playerMovementSpeedPerLevel
		) {
			this.baseXpRequirement = baseXpRequirement;
			this.baseXpMultiplier = baseXpMultiplier;
			this.maxPlayerLevelAttributes = maxPlayerLevelAttributes;
			this.maxPlayerLevelAttributesPartial = maxPlayerLevelAttributesPartial;
			this.maxPlayerLevelVanilla = maxPlayerLevelVanilla;
			this.maxStatLevelAttributes = maxStatLevelAttributes;
			this.maxStatLevelVanilla = maxStatLevelVanilla;
			this.healthPerLevel = healthPerLevel;
			this.playerDamagePerLevel = playerDamagePerLevel;
			this.playerArmorPerLevelAttributes = playerArmorPerLevelAttributes;
			this.playerArmorPerLevelVanilla = playerArmorPerLevelVanilla;
			this.playerLuckPerLevel = playerLuckPerLevel;
			this.playerHungerPerLevel = playerHungerPerLevel;
			this.playerMovementSpeedPerLevel = playerMovementSpeedPerLevel;
		}

		private static Settings defaults() {
			return new Settings(
				DEFAULT_BASE_XP_REQUIREMENT,
				DEFAULT_BASE_XP_MULTIPLIER,
				DEFAULT_MAX_PLAYER_LEVEL_ATTRIBUTES,
				DEFAULT_MAX_PLAYER_LEVEL_ATTRIBUTES_PARTIAL,
				DEFAULT_MAX_PLAYER_LEVEL_VANILLA,
				DEFAULT_MAX_STAT_LEVEL_ATTRIBUTES,
				DEFAULT_MAX_STAT_LEVEL_VANILLA,
				DEFAULT_HEALTH_PER_LEVEL,
				DEFAULT_PLAYER_DAMAGE_PER_LEVEL,
				DEFAULT_PLAYER_ARMOR_PER_LEVEL_ATTRIBUTES,
				DEFAULT_PLAYER_ARMOR_PER_LEVEL_VANILLA,
				DEFAULT_PLAYER_LUCK_PER_LEVEL,
				DEFAULT_PLAYER_HUNGER_PER_LEVEL,
				DEFAULT_PLAYER_MOVEMENT_SPEED_PER_LEVEL
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				clampDouble(getDouble(source, "base-xp-requirement", defaults.baseXpRequirement), 0.1d, 1_000_000.0d),
				clampDouble(getDouble(source, "base-xp-multiplier", defaults.baseXpMultiplier), 0.0d, 1_000.0d),
				clampInt(getLong(source, "max-player-level-attributes", defaults.maxPlayerLevelAttributes), 1, 1000),
				clampInt(
					getLong(source, "max-player-level-attributes-partial", defaults.maxPlayerLevelAttributesPartial),
					1,
					1000
				),
				clampInt(getLong(source, "max-player-level-vanilla", defaults.maxPlayerLevelVanilla), 1, 1000),
				clampInt(getLong(source, "max-stat-level-attributes", defaults.maxStatLevelAttributes), 1, 1000),
				clampInt(getLong(source, "max-stat-level-vanilla", defaults.maxStatLevelVanilla), 1, 1000),
				clampDouble(getDouble(source, "health-per-level", defaults.healthPerLevel), 0.0d, 1000.0d),
				clampDouble(getDouble(source, "player-damage-per-level", defaults.playerDamagePerLevel), 0.0d, 1000.0d),
				clampDouble(
					getDouble(source, "player-armor-per-level-attributes", defaults.playerArmorPerLevelAttributes),
					0.0d,
					1000.0d
				),
				clampDouble(
					getDouble(source, "player-armor-per-level-vanilla", defaults.playerArmorPerLevelVanilla),
					0.0d,
					1000.0d
				),
				clampDouble(getDouble(source, "player-luck-per-level", defaults.playerLuckPerLevel), 0.0d, 1000.0d),
				clampDouble(getDouble(source, "player-hunger-per-level", defaults.playerHungerPerLevel), 0.0d, 1000.0d),
				clampDouble(
					getDouble(source, "player-movement-speed-per-level", defaults.playerMovementSpeedPerLevel),
					0.0d,
					1000.0d
				)
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("base-xp-requirement", baseXpRequirement);
			root.addProperty("base-xp-multiplier", baseXpMultiplier);
			root.addProperty("max-player-level-attributes", maxPlayerLevelAttributes);
			root.addProperty("max-player-level-attributes-partial", maxPlayerLevelAttributesPartial);
			root.addProperty("max-player-level-vanilla", maxPlayerLevelVanilla);
			root.addProperty("max-stat-level-attributes", maxStatLevelAttributes);
			root.addProperty("max-stat-level-vanilla", maxStatLevelVanilla);
			root.addProperty("health-per-level", healthPerLevel);
			root.addProperty("player-damage-per-level", playerDamagePerLevel);
			root.addProperty("player-armor-per-level-attributes", playerArmorPerLevelAttributes);
			root.addProperty("player-armor-per-level-vanilla", playerArmorPerLevelVanilla);
			root.addProperty("player-luck-per-level", playerLuckPerLevel);
			root.addProperty("player-hunger-per-level", playerHungerPerLevel);
			root.addProperty("player-movement-speed-per-level", playerMovementSpeedPerLevel);
			return root;
		}
	}
}
