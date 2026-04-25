package madoku.craft.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.mob.system.MadokuMob;
import madoku.craft.network.PetAbilityHudSync;
import madoku.craft.network.PetSoundStateSync;
import madoku.craft.player.PlayerTickSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
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
	private static final String PLAYER_SCHEDULER_KEY = "player_entities";
	private static final String LEGACY_SAVE_KEY = "PlayerPets";
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
	private static final int BAT_SCAN_VERTICAL_RADIUS_PER_EXTRA_BAT = 2;
	private static final int BAT_SCAN_BASE_CHUNK_RADIUS = 1;
	private static final int BAT_SCAN_ENHANCED_CHUNK_RADIUS = 2;
	private static final int BAT_SCAN_MIN_BATS_FOR_ENHANCED_RADIUS = 3;
	private static final long BAT_SCAN_GLOWING_DURATION_TICKS = 90L * 20L;
	private static final long BAT_SCAN_COOLDOWN_REDUCTION_PER_EXTRA_BAT = 30L * 20L;
	private static final Identifier PLAYER_DAMAGE_ABILITY_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_damage_bonus");
	private static final Identifier PLAYER_MAX_HEALTH_ABILITY_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_max_health_bonus");
	private static final Identifier PLAYER_ARMOR_ABILITY_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_pets_player_armor_bonus");
	private static final String FIELD_SLOT = "slot";
	private static final String FIELD_TARGET_UUID = "target-uuid";
	private static final String FIELD_SPAWN_X = "spawn-x";
	private static final String FIELD_SPAWN_Y = "spawn-y";
	private static final String FIELD_SPAWN_Z = "spawn-z";
	private static final Map<UUID, UUID[]> PET_IDS_BY_PLAYER = new ConcurrentHashMap<>();
	private static final Set<UUID> ACTIVE_PET_IDS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> NEXT_IDLE_MOVE_BY_PET = new ConcurrentHashMap<>();
	private static final Map<UUID, FollowCommand> FOLLOW_COMMANDS_BY_PET = new ConcurrentHashMap<>();
	private static final Map<UUID, String> PLAYER_SCHEDULER_IDS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_PROCESS_TICKS_BY_PLAYER = new HashMap<>();
	private static final Set<UUID> DIRTY_ABILITY_HUD_PLAYERS = new HashSet<>();
	private static final Map<UUID, long[]> PLAYER_SLOT_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, WebProjectileState> ACTIVE_WEB_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, ExplosiveProjectileState> ACTIVE_EXPLOSIVE_PROJECTILES = new ConcurrentHashMap<>();

	private static volatile PetSettings settings = PetSettings.defaults();
	private static volatile Map<String, PetRule> petRulesByItemId = Map.of();
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private PlayerEntitiesSystem() {
	}

	public static void initialize() {
		loadConfig();
		PlayerTickSystem.registerListener("pets", 40, PlayerEntitiesSystem::onPlayerTick);
		// Keep the legacy handler to consume any persisted pre-refactor pet_tick tasks.
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_PET_TICK, PlayerEntitiesSystem::runPetTick);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_PET_ATTACK, PlayerEntitiesSystem::runPetAttack);
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
			PetSoundState.remove(entityId);
		});
	}

	public static void reset() {
		PET_IDS_BY_PLAYER.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
		PLAYER_SCHEDULER_IDS.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		DIRTY_ABILITY_HUD_PLAYERS.clear();
		PLAYER_SLOT_COOLDOWNS.clear();
		ACTIVE_WEB_PROJECTILES.clear();
		ACTIVE_EXPLOSIVE_PROJECTILES.clear();
		PetSoundState.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadConfig();
		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(data);
		removeTaggedPets(server);
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
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

	public static void onServerTick(MinecraftServer server) {
		if (server == null) {
			return;
		}

		refreshPlayerPassiveAbilityBonuses(server);

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

		tickManagedWebProjectiles(server);
		tickManagedExplosiveProjectiles(server);
		flushAbilityHudSyncs(server);
	}

	public static void onPlayerEntitiesInventoryChanged(ServerPlayer player) {
		if (player == null) {
			return;
		}

		markAbilityHudDirty(player.getUUID());
		applyPlayerMaxHealthAbilityBonus(player);
		applyPlayerArmorAbilityBonus(player);
		applyPlayerDamageAbilityBonus(player);

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		if (!settings.enabled || !petEntitiesEnabled()) {
			removeAllPets(server, player.getUUID());
			return;
		}

		requestPetProcessing(server, player.getUUID(), 0L);
		onPlayerTick(server, player, MadokuTicks.getGameplayTicks());
	}

	public static boolean isValidPlayerEntity(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SpawnEggItem)) {
			return false;
		}
		PetRule rule = resolvePetRule(stack);
		return rule != null && rule.enabled;
	}

	public static boolean isManagedPet(Entity entity) {
		return entity != null
			&& (ACTIVE_PET_IDS.contains(entity.getUUID())
				|| entity.entityTags().contains(MANAGED_PET_TAG)
				|| PetSoundState.isManaged(entity));
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

	public static long managedPetSteeringInterval() {
		return activeSchedulerTickInterval();
	}

	public static Vec3 managedPetMovementTarget(Mob pet) {
		return PetMovementController.managedPetMovementTarget(pet, FOLLOW_COMMANDS_BY_PET);
	}

	public static double managedPetMovementSpeed(Mob pet, double fallbackSpeed) {
		return PetMovementController.managedPetMovementSpeed(pet, fallbackSpeed, FOLLOW_COMMANDS_BY_PET);
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

	public static int abilityCooldownTicks(Player player, int slot, ItemStack stack) {
		PetRule rule = resolvePetRule(stack);
		if (rule == null) {
			return 0;
		}
		if (!PET_ABILITY_MOB_SCAN.equals(rule.abilityType)) {
			return abilityCooldownTicks(stack);
		}
		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		int batCount = countSlotsWithAbility(inventory, PET_ABILITY_MOB_SCAN);
		return (int) Math.min(Integer.MAX_VALUE, effectiveBatScanCooldownTicks(Math.max(1, batCount), rule));
	}

	public static void applyAbilityLore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}

		PetRule rule = resolvePetRule(stack);
		if (rule == null) {
			return;
		}

		List<Component> lines = new ArrayList<>();
		String abilityText = rule.abilityDescription();
		if (!abilityText.isBlank()) {
			lines.add(Component.literal(abilityText).withStyle(ChatFormatting.GOLD));
		}
		String cooldownText = rule.cooldownDescription();
		if (!cooldownText.isBlank()) {
			lines.add(Component.literal(cooldownText).withStyle(ChatFormatting.GRAY));
		}
		if (lines.isEmpty()) {
			return;
		}

		stack.set(DataComponents.LORE, new ItemLore(lines));
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

	public static double playerFallDamageAbilityReduction(ServerPlayer player) {
		if (player == null || !settings.enabled) {
			return 0.0D;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null) {
			return 0.0D;
		}

		double totalReduction = 0.0D;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			PetRule rule = resolvePetRule(inventory.getItem(slot));
			if (rule != null) {
				totalReduction += rule.fallDamageReduction();
			}
		}
		return Math.min(1.0D, Math.max(0.0D, totalReduction));
	}

	public static double playerMaxHealthAbilityBonus(ServerPlayer player) {
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
				totalBonus += rule.maxHealthBonus();
			}
		}
		return Math.max(0.0D, totalBonus);
	}

	public static double playerArmorAbilityBonus(ServerPlayer player) {
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
				totalBonus += rule.armorBonus();
			}
		}
		return Math.max(0.0D, totalBonus);
	}

	public static float applyFallDamageAbilityReduction(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source == null || amount <= 0.0F || !source.is(DamageTypeTags.IS_FALL)) {
			return amount;
		}

		double reduction = playerFallDamageAbilityReduction(player);
		if (reduction <= 0.0D) {
			return amount;
		}

		return (float) Math.max(0.0D, amount * (1.0D - reduction));
	}

	public static float applyIncomingDamageBlockAbility(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !settings.enabled) {
			return amount;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
		if (inventory == null) {
			return amount;
		}

		long gameplayTicks = MadokuTicks.getGameplayTicks();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = resolvePetRule(stack);
			if (rule == null || !rule.canBlockIncomingDamage() || !isPetSlotOffCooldown(player, slot, gameplayTicks)) {
				continue;
			}

			setSlotCooldown(player.getUUID(), slot, gameplayTicks + rule.cooldownTicks);
			float blockedAmount = (float) Math.max(0.0D, amount - rule.damageBlockAmount());
			if (blockedAmount < amount) {
				player.level().playSound(
					null,
					player.getX(),
					player.getY(),
					player.getZ(),
					SoundEvents.SHIELD_BLOCK,
					SoundSource.PLAYERS,
					0.8F,
					1.0F
				);
			}
			return blockedAmount;
		}

		return amount;
	}

	public static void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		maxHealthAttribute.removeModifier(PLAYER_MAX_HEALTH_ABILITY_MODIFIER_ID);
		double bonus = playerMaxHealthAbilityBonus(player);
		if (bonus > 0.0D) {
			maxHealthAttribute.addOrUpdateTransientModifier(
				new AttributeModifier(
					PLAYER_MAX_HEALTH_ABILITY_MODIFIER_ID,
					bonus,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
				)
			);
		}

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	public static void applyPlayerArmorAbilityBonus(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance armorAttribute = player.getAttribute(Attributes.ARMOR);
		if (armorAttribute == null) {
			return;
		}

		armorAttribute.removeModifier(PLAYER_ARMOR_ABILITY_MODIFIER_ID);
		double bonus = playerArmorAbilityBonus(player);
		if (bonus <= 0.0D) {
			return;
		}

		armorAttribute.addOrUpdateTransientModifier(
			new AttributeModifier(
				PLAYER_ARMOR_ABILITY_MODIFIER_ID,
				bonus,
				AttributeModifier.Operation.ADD_VALUE
			)
		);
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
		if (player == null || playerEntitiesInventory == null) {
			return;
		}

		List<Integer> occupiedSlots = new ArrayList<>(playerEntitiesInventory.getContainerSize());
		for (int slot = 0; slot < playerEntitiesInventory.getContainerSize(); slot++) {
			if (!playerEntitiesInventory.getItem(slot).isEmpty()) {
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
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(PET_CONFIG_ROOT_FOLDER_NAME);
			Path configFile = resolveJsonFile(rootDirectory, PET_CONFIG_FILE_NAME);
			JsonObject defaults = PetSettings.defaults().toConfigJson();
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			PetSettings configured = PetSettings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured;
		} catch (IOException exception) {
			settings = PetSettings.defaults();
			LOGGER.error("Failed to load Madoku pet settings; using defaults.", exception);
		}
	}

	private static void loadPetRules() {
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(PET_CONFIG_ROOT_FOLDER_NAME);
			Path rulesDirectory = rootDirectory.resolve(PET_RULES_FOLDER_NAME);
			Map<String, JsonObject> normalizedFiles = DynamicStaticSystem.ensureManagedFolder(
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
				JsonObject normalized = DynamicStaticSystem.writeManagedFile(file, sourceRoot, abilityDefaults, null);
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
		defaults.put("bat", PetRule.defaultsForItem("minecraft:bat_spawn_egg", PET_ABILITY_MOB_SCAN));
		defaults.put("chicken", PetRule.defaultsForItem("minecraft:chicken_spawn_egg", PET_ABILITY_FALL_DAMAGE_REDUCTION));
		defaults.put("cow", PetRule.defaultsForItem("minecraft:cow_spawn_egg", PET_ABILITY_DAMAGE_BLOCK));
		defaults.put("creeper", PetRule.defaultsForItem("minecraft:creeper_spawn_egg", PET_ABILITY_EXPLOSIVE_PROJECTILE));
		defaults.put("pig", PetRule.defaultsForItem("minecraft:pig_spawn_egg", PET_ABILITY_MAX_HEALTH_BONUS));
		defaults.put("sheep", PetRule.defaultsForItem("minecraft:sheep_spawn_egg", PET_ABILITY_ARMOR_BONUS));
		defaults.put("skeleton", PetRule.defaultsForItem("minecraft:skeleton_spawn_egg", PET_ABILITY_RANGED_HOMING_ARROW));
		defaults.put("spider", PetRule.defaultsForItem("minecraft:spider_spawn_egg", PET_ABILITY_WEB_PROJECTILE));
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
		applyPlayerMaxHealthAbilityBonus(player);
		applyPlayerArmorAbilityBonus(player);
		applyPlayerDamageAbilityBonus(player);
		syncManagedPetSoundStateTo(player, server);
		if (petEntitiesEnabled()) {
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
				PetRule rule = resolvePetRule(pet);
				PetSoundStateSync.send(player, pet.getUUID(), rule == null ? "" : rule.itemId);
			}
		}
	}

	private static void broadcastManagedPetSoundState(MinecraftServer server, UUID petId, String itemId) {
		if (server == null || petId == null) {
			return;
		}
		for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
			PetSoundStateSync.send(onlinePlayer, petId, itemId);
		}
	}

	private static void handlePlayerLeave(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		removeAllPets(server, playerId);
		PLAYER_SCHEDULER_IDS.remove(playerId);
		NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);
		DIRTY_ABILITY_HUD_PLAYERS.remove(playerId);
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
			triggerReactivePetAttacks(playerAttacker, entity);
		}

		if (entity instanceof ServerPlayer playerVictim) {
			LivingEntity attackerTarget = resolveDamageSourceLivingEntity(source);
			if (attackerTarget != null) {
				triggerReactivePetAttacks(playerVictim, attackerTarget);
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

	private static void triggerReactivePetAttacks(ServerPlayer player, LivingEntity target) {
		if (!canReactiveAttackTarget(player, target)) {
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

	private static void triggerAutomaticPetAbilities(ServerPlayer player, long gameplayTicks) {
		triggerAutomaticBatMobScan(player, gameplayTicks);
	}

	private static void triggerAutomaticBatMobScan(ServerPlayer player, long gameplayTicks) {
		if (player == null || !player.isAlive()) {
			return;
		}

		PlayerEntitiesInventory inventory = playerEntitiesInventory(player);
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

		int horizontalRadius = (batCount >= BAT_SCAN_MIN_BATS_FOR_ENHANCED_RADIUS ? BAT_SCAN_ENHANCED_CHUNK_RADIUS : BAT_SCAN_BASE_CHUNK_RADIUS) * 16;
		int verticalRadius = BAT_SCAN_BASE_VERTICAL_RADIUS_BLOCKS + Math.max(0, batCount - 1) * BAT_SCAN_VERTICAL_RADIUS_PER_EXTRA_BAT;
		AABB area = new AABB(
			player.getX() - horizontalRadius,
			player.getY() - verticalRadius,
			player.getZ() - horizontalRadius,
			player.getX() + horizontalRadius,
			player.getY() + verticalRadius,
			player.getZ() + horizontalRadius
		);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, candidate ->
			candidate != null
				&& candidate.isAlive()
				&& !candidate.isRemoved()
				&& !isManagedPet(candidate)
		)) {
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) BAT_SCAN_GLOWING_DURATION_TICKS, 0, false, false, true));
		}
	}

	private static long effectiveBatScanCooldownTicks(int batCount, PetRule rule) {
		long baseCooldown = Math.max(0L, rule == null ? 0L : rule.cooldownTicks);
		int additionalBats = Math.max(0, batCount - 1);
		return Math.max(20L, baseCooldown - (additionalBats * BAT_SCAN_COOLDOWN_REDUCTION_PER_EXTRA_BAT));
	}

	private static int countSlotsWithAbility(PlayerEntitiesInventory inventory, String abilityType) {
		return collectSlotsWithAbility(inventory, abilityType, null, null);
	}

	private static int collectSlotsWithAbility(
		PlayerEntitiesInventory inventory,
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
			PetRule rule = resolvePetRule(stack);
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
			markAbilityHudDirty(playerId);
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
			markAbilityHudDirty(playerId);
		}
	}

	private static void onPlayerTick(MinecraftServer server, ServerPlayer player, long gameplayTick) {
		if (server == null || player == null) {
			return;
		}

		UUID playerId = player.getUUID();
		Long nextProcessTick = NEXT_PROCESS_TICKS_BY_PLAYER.get(playerId);
		if (nextProcessTick == null || gameplayTick < nextProcessTick) {
			return;
		}
		NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);

		if (!settings.enabled || !petEntitiesEnabled()) {
			debugPlayerIdEvent("pet.tick_stopped", playerId)
				.field("reason", !settings.enabled ? "system_disabled" : "entities_disabled")
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
			.field("inventory", inventorySummary(playerEntitiesInventory(player)))
			.log();

		long nextDelay = syncPlayerPets(player);
		LivingEntity ongoingReactiveTarget = resolveOngoingReactiveTarget(player);
		if (ongoingReactiveTarget != null) {
			triggerReactivePetAttacks(player, ongoingReactiveTarget);
			nextDelay = Math.min(nextDelay, activeSchedulerTickInterval());
		}
		triggerAutomaticPetAbilities(player, gameplayTick);
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

	private static void runPetTick(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (server == null || context == null) {
			return;
		}

		SchedulerManagerSystem.SchedulerBinding binding = context.getBinding();
		UUID playerId = binding == null ? null : binding.getEntityUuid();
		if (playerId == null) {
			return;
		}

		PLAYER_SCHEDULER_IDS.put(playerId, context.getSchedulerId());
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) {
			NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);
			removeAllPets(server, playerId);
			return;
		}

		long nowTick = context.getNowTick();
		Long existingTick = NEXT_PROCESS_TICKS_BY_PLAYER.get(playerId);
		if (existingTick == null || nowTick < existingTick) {
			NEXT_PROCESS_TICKS_BY_PLAYER.put(playerId, nowTick);
		}
		onPlayerTick(server, player, nowTick);
	}

	private static void runPetAttack(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (server == null || context == null || payload == null) {
			return;
		}

		SchedulerManagerSystem.SchedulerBinding binding = context.getBinding();
		UUID playerId = binding == null ? null : binding.getEntityUuid();
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
		if (rule == null || !rule.canPerformReactiveAttack() || !canPetSlotShoot(player, slot, context.getNowTick(), stack)) {
			return;
		}

		UUID targetId = parseUuid(getString(payload, FIELD_TARGET_UUID, ""));
		LivingEntity target = findLivingEntity(server, targetId);
		if (!canReactiveAttackTarget(player, target)) {
			return;
		}

		Vec3 spawnPosition = new Vec3(
			getDouble(payload, FIELD_SPAWN_X, player.getX()),
			getDouble(payload, FIELD_SPAWN_Y, player.getEyeY()),
			getDouble(payload, FIELD_SPAWN_Z, player.getZ())
		);
		if (spawnPetReactiveAttack(player, target, spawnPosition, rule)) {
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
				nextDelay = Math.min(nextDelay, PetMovementController.updatePetPosition(
					player,
					pet,
					slot,
					rule,
					settings,
					NEXT_IDLE_MOVE_BY_PET,
					FOLLOW_COMMANDS_BY_PET
				));
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

		Vec3 desiredPosition = PetMovementController.resolveDesiredPosition(owner, slot, pet);
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
		broadcastManagedPetSoundState(owner.level().getServer(), pet.getUUID(), rule == null ? "" : rule.itemId);
		debugPetEvent("pet.spawned", pet)
			.field("owner_uuid", owner.getUUID())
			.field("slot", slot)
			.field("item-id", rule == null ? "" : rule.itemId)
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
		boolean itemIdChanged = setManagedPetItemId(pet, rule == null ? null : rule.itemId);
		AttributeInstance scale = pet.getAttribute(Attributes.SCALE);
		if (scale != null) {
			double desiredScale = rule == null ? 0.25D : rule.petScale;
			if (Math.abs(scale.getBaseValue() - desiredScale) > 1.0E-4D) {
				scale.setBaseValue(desiredScale);
			}
		}
		if (itemIdChanged) {
			broadcastManagedPetSoundState(pet.level().getServer(), pet.getUUID(), rule == null ? "" : rule.itemId);
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

	private static EntityType<?> resolvePetType(ItemStack stack) {
		if (!(stack.getItem() instanceof SpawnEggItem)) {
			return null;
		}
		return SpawnEggItem.getType(stack);
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
			spawned = MadokuMob.spawnManagedHomingArrow(player, target, spawnPosition, rule.attackSpeed, rule.attackDamage);
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

		String created = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId));
		PLAYER_SCHEDULER_IDS.put(playerId, created);
		if (!enqueuePetAttackTask(created, slot, target, spawnPosition, delayTicks)) {
			LOGGER.error("Failed to enqueue delayed pet attack for player={} slot={}", playerId, slot);
		}
	}

	private static void requestPetProcessing(MinecraftServer server, UUID playerId, long delayTicks) {
		if (server == null || playerId == null || !petEntitiesEnabled()) {
			return;
		}

		long targetTick = MadokuTicks.getGameplayTicks() + Math.max(0L, delayTicks);
		Long existingTick = NEXT_PROCESS_TICKS_BY_PLAYER.get(playerId);
		if (existingTick == null || targetTick < existingTick) {
			NEXT_PROCESS_TICKS_BY_PLAYER.put(playerId, targetTick);
		}
		debugPlayerIdEvent("pet.tick_requested", playerId)
			.field("delay", Math.max(0L, delayTicks))
			.field("target_tick", targetTick)
			.log();
	}

	private static String ensureSchedulerExists(UUID playerId) {
		String schedulerId = PLAYER_SCHEDULER_IDS.get(playerId);
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.player(PLAYER_SCHEDULER_KEY, playerId));
			PLAYER_SCHEDULER_IDS.put(playerId, schedulerId);
		}
		return schedulerId;
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
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_PET_ATTACK,
			payload,
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED;
	}

	private static boolean canPetSlotShoot(ServerPlayer player, int slot, long gameplayTicks, ItemStack stack) {
		if (player == null || slot < 0 || slot >= SLOT_COUNT) {
			return false;
		}
		PetRule rule = resolvePetRule(stack);
		if (!isValidPlayerEntity(stack) || rule == null || !rule.canPerformReactiveAttack()) {
			return false;
		}
		return isPetSlotOffCooldown(player, slot, gameplayTicks);
	}

	private static boolean isPetSlotOffCooldown(ServerPlayer player, int slot, long gameplayTicks) {
		if (player == null || slot < 0 || slot >= SLOT_COUNT) {
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

	private static void refreshPlayerPassiveAbilityBonuses(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyPlayerMaxHealthAbilityBonus(player);
			applyPlayerArmorAbilityBonus(player);
			applyPlayerDamageAbilityBonus(player);
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
		long gameplayTicks = MadokuTicks.getGameplayTicks();
		for (int slot = 0; slot < Math.min(SLOT_COUNT, inventory.getContainerSize()); slot++) {
			if (cooldowns[slot] > 0L && cooldowns[slot] <= gameplayTicks) {
				cooldowns[slot] = 0L;
			}
		}
		if (!hasNonZeroCooldown(cooldowns)) {
			PLAYER_SLOT_COOLDOWNS.remove(playerId);
		}
		markAbilityHudDirty(playerId);
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
			PetSoundState.remove(petId);
			broadcastManagedPetSoundState(server, petId, "");
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
		if (pet instanceof Bat bat) {
			bat.setResting(false);
		}
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
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		ACTIVE_WEB_PROJECTILES.clear();
		ACTIVE_EXPLOSIVE_PROJECTILES.clear();
		PetSoundState.clear();
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
		String syncedItemId = normalizeKey(PetSoundState.getItemId(entity));
		if (!syncedItemId.isBlank()) {
			return resolvePetRule(syncedItemId);
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
		root.add("slot-cooldowns", new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = createDefaultData();

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
		root.add("slot-cooldowns", cooldowns);
		return root;
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
		if ("minecraft:bat_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_MOB_SCAN;
		}
		if ("minecraft:chicken_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_FALL_DAMAGE_REDUCTION;
		}
		if ("minecraft:cow_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_DAMAGE_BLOCK;
		}
		if ("minecraft:pig_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_MAX_HEALTH_BONUS;
		}
		if ("minecraft:sheep_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_ARMOR_BONUS;
		}
		if ("minecraft:skeleton_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_RANGED_HOMING_ARROW;
		}
		if ("minecraft:spider_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_WEB_PROJECTILE;
		}
		if ("minecraft:creeper_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_EXPLOSIVE_PROJECTILE;
		}
		if ("minecraft:zombie_spawn_egg".equals(normalizedItemId)) {
			return PET_ABILITY_PLAYER_DAMAGE_BONUS;
		}
		return PET_ABILITY_NONE;
	}

	static double defaultPetScaleForItem(String itemId) {
		String normalizedItemId = normalizeKey(itemId);
		if ("minecraft:bat_spawn_egg".equals(normalizedItemId)) {
			return 0.4D;
		}
		if ("minecraft:chicken_spawn_egg".equals(normalizedItemId)) {
			return 0.3D;
		}
		return 0.25D;
	}

	static String defaultRarityForItem(String itemId) {
		String normalizedItemId = normalizeKey(itemId);
		if ("minecraft:bat_spawn_egg".equals(normalizedItemId)
			|| "minecraft:cow_spawn_egg".equals(normalizedItemId)
			|| "minecraft:creeper_spawn_egg".equals(normalizedItemId)
			|| "minecraft:skeleton_spawn_egg".equals(normalizedItemId)
			|| "minecraft:spider_spawn_egg".equals(normalizedItemId)) {
			return PET_RARITY_RARE;
		}
		return PET_RARITY_COMMON;
	}

	static String resolvePetItemId(String fileKey, JsonObject sourceRoot) {
		String configured = getString(sourceRoot, "item-id", "");
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

	private static boolean setManagedPetItemId(Mob pet, String itemId) {
		if (pet == null) {
			return false;
		}
		String normalizedItemId = normalizeKey(itemId);
		PetSoundState.set(pet.getUUID(), normalizedItemId);
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

	static String normalizeKey(String rawKey) {
		return rawKey == null ? "" : rawKey.trim().toLowerCase();
	}

	static String normalizePetRarity(String rawRarity) {
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

	static String getString(JsonObject source, String key, String fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsString();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static long getLong(JsonObject source, String key, long fallback) {
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

	static double getDouble(JsonObject source, String key, double fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static boolean getBoolean(JsonObject source, String key, boolean fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return source.get(key).getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
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

}

