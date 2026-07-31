package madoku.craft.pet;

import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;
import java.io.IOException;
import net.minecraft.server.level.ServerLevel;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetConfigManager.PetRule;

/** Owns the runtime pet-entity lifecycle and equipped pet slots. */
public final class PetEntitiesManager {
	public static final int SLOT_COUNT = MadokuPetManager.SLOT_COUNT;
	public static final int FIRST_SLOT_INDEX = MadokuPetManager.FIRST_SLOT_INDEX;
	public static final int SLOT_X = MadokuPetManager.SLOT_X;
	public static final int[] SLOT_YS = MadokuPetManager.SLOT_YS;
	public static final String PET_ITEM_NAMESPACE = "madoku-craft";
	private static final String PET_LEVEL_TAG = "madoku-pet-level";
	private static final int DEFAULT_PET_LEVEL = 1;
	private static final Map<String, Item> PET_ITEMS_BY_ID = new LinkedHashMap<>();
	private static final Map<Item, String> PET_IDS_BY_ITEM = new LinkedHashMap<>();
	private static boolean itemsRegistered;
	public static final Identifier PET_ENTITY_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet");
	public static final EntityType<MadokuPetEntity> PET_ENTITY = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		PET_ENTITY_ID,
		EntityType.Builder.of(MadokuPetEntity::new, MobCategory.CREATURE)
			.sized(0.8F, 1.8F)
			.clientTrackingRange(10)
			// ServerEntityPetUpdateIntervalMixin adapts this between 1 and 5 at runtime.
			.updateInterval(5)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, PET_ENTITY_ID))
	);
	static final Map<UUID, UUID[]> PET_IDS_BY_PLAYER = new ConcurrentHashMap<>();
	/** The authoritative owner/slot index; full entity scans are recovery-only. */
	private static final Set<UUID> PET_INDEX_RECOVERED_OWNERS = ConcurrentHashMap.newKeySet();
	/** Runtime UUID lookup avoids walking every server level for indexed pets. */
	private static final Map<UUID, MadokuPetEntity> PET_ENTITIES_BY_ID = new ConcurrentHashMap<>();
	static final Set<UUID> ACTIVE_PET_IDS = ConcurrentHashMap.newKeySet();
	static final Map<UUID, Long> NEXT_IDLE_MOVE_BY_PET = new ConcurrentHashMap<>();
	static final Map<UUID, FollowCommand> FOLLOW_COMMANDS_BY_PET = new ConcurrentHashMap<>();

	private PetEntitiesManager() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(PET_ENTITY, MadokuPetEntity.createAttributes());
		registerPetItems();
	}

	private static void registerPetItems() {
		if (itemsRegistered) return;
		String[] petIds = {
			"minecraft:bat", "minecraft:bee", "minecraft:chicken", "minecraft:cow", "minecraft:creeper",
			"minecraft:pig", "minecraft:sheep", "minecraft:skeleton", "minecraft:spider", "minecraft:zombie"
		};
		for (String petId : petIds) {
			String itemPath = PetConfigManager.petItemPath(petId);
			Identifier itemId = Identifier.fromNamespaceAndPath(PET_ITEM_NAMESPACE, itemPath);
			Item item = Registry.register(
				BuiltInRegistries.ITEM,
				itemId,
				new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, itemId)))
			);
			PET_ITEMS_BY_ID.put(PetConfigManager.normalizePetId(petId), item);
			PET_IDS_BY_ITEM.put(item, PetConfigManager.normalizePetId(petId));
		}
		itemsRegistered = true;
	}

	public static boolean isPetItem(ItemStack stack) {
		return stack != null && !stack.isEmpty() && PET_IDS_BY_ITEM.containsKey(stack.getItem());
	}

	static String petId(ItemStack stack) {
		return stack == null ? "" : PET_IDS_BY_ITEM.getOrDefault(stack.getItem(), "");
	}

	static Item petItem(String petId) {
		return PET_ITEMS_BY_ID.get(PetConfigManager.normalizePetId(petId));
	}

	public static int petLevel(ItemStack stack) {
		if (!isPetItem(stack)) return 1;
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) return DEFAULT_PET_LEVEL;
		int level = customData.copyTag().getInt(PET_LEVEL_TAG).orElse(DEFAULT_PET_LEVEL);
		return Math.max(DEFAULT_PET_LEVEL, Math.min(PetConfigManager.maxPetLevel(), level));
	}

	public static void setPetLevel(ItemStack stack, int level) {
		if (!isPetItem(stack)) return;
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		net.minecraft.nbt.CompoundTag tag = customData == null ? new net.minecraft.nbt.CompoundTag() : customData.copyTag();
		tag.putInt(PET_LEVEL_TAG, Math.max(DEFAULT_PET_LEVEL, Math.min(PetConfigManager.maxPetLevel(), level)));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** Owns the dynamic pet entity JSON definitions under madoku-entities. */
	public static final class EntitiesConfigManager {
		private static volatile Map<String, PetRule> rules = Map.of();

		private EntitiesConfigManager() {
		}

		static void reload() {
			try {
				Path rulesDirectory = PetConfigManager.petDirectory().resolve(PetConfigManager.ENTITY_FOLDER);
				Map<String, JsonObject> normalizedFiles = JSONFormatManager.ensureManagedFolder(
					rulesDirectory,
					buildDefaultPetRuleFiles(),
					EntitiesConfigManager::buildDynamicPetRuleDefaults,
					EntitiesConfigManager::isSupportedPetRuleFile,
					null
				);
				Map<String, JsonObject> abilityDefinitions = PetAbilitiesManager.AbilitiesConfigManager.definitions();
				Map<String, PetRule> resolved = new LinkedHashMap<>();
				for (Map.Entry<String, JsonObject> entry : normalizedFiles.entrySet()) {
					String fileKey = entry.getKey();
					JsonObject sourceRoot = entry.getValue();
					String petId = PetConfigManager.resolvePetId(fileKey, sourceRoot);
					if (petId == null || petId.isBlank()) continue;

					List<String> abilityTypes = PetConfigManager.resolveAbilityTypes(sourceRoot);
					String abilityType = abilityTypes.isEmpty() ? MadokuPetManager.PET_ABILITY_NONE : abilityTypes.get(0);
					Path file = PetConfigManager.resolveJsonFile(rulesDirectory, fileKey);
					JsonObject normalized = JSONFormatManager.writeManagedFile(
						file,
						sourceRoot,
						PetConfigManager.PetRule.defaultsForEntity(petId, abilityType),
						null
					);
					PetRule rule = PetConfigManager.PetRule.fromJson(normalized, fileKey, abilityDefinitions);
					if (rule != null && !rule.itemId.isBlank()) {
						resolved.put(PetConfigManager.normalizePetId(rule.petId), rule);
						resolved.put(PetConfigManager.normalizeKey(rule.itemId), rule);
					}
				}
				rules = Map.copyOf(resolved);
			} catch (IOException | RuntimeException exception) {
				rules = Map.of();
				PetConfigManager.logFailure("Failed to load Madoku pet entity definitions; using defaults.", exception);
			}
		}

		static Map<String, PetRule> rules() {
			return rules;
		}

		static PetRule resolve(String itemId) {
			String normalizedItemId = PetConfigManager.normalizeKey(itemId);
			if (normalizedItemId.isEmpty()) return null;
			PetRule direct = rules.get(normalizedItemId);
			if (direct != null) return direct;
			return rules.get(PetConfigManager.normalizePetId(normalizedItemId));
		}

		private static Map<String, JsonObject> buildDefaultPetRuleFiles() {
			Map<String, JsonObject> defaults = new LinkedHashMap<>();
			defaults.put("bat", PetRule.defaultsForEntity("minecraft:bat", MadokuPetManager.PET_ABILITY_MOB_SCAN));
			defaults.put("bee", PetRule.defaultsForEntity("minecraft:bee", MadokuPetManager.PET_ABILITY_BEE_SWARM));
			defaults.put("chicken", PetRule.defaultsForEntity(
				"minecraft:chicken",
				MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION,
				MadokuPetManager.PET_ABILITY_EGG_PROJECTILE
			));
			defaults.put("cow", PetRule.defaultsForEntity("minecraft:cow", MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK));
			defaults.put("creeper", PetRule.defaultsForEntity("minecraft:creeper", MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE));
			defaults.put("pig", PetRule.defaultsForEntity("minecraft:pig", MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS));
			defaults.put("sheep", PetRule.defaultsForEntity("minecraft:sheep", MadokuPetManager.PET_ABILITY_ARMOR_BONUS));
			defaults.put("skeleton", PetRule.defaultsForEntity("minecraft:skeleton", MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW));
			defaults.put("spider", PetRule.defaultsForEntity("minecraft:spider", MadokuPetManager.PET_ABILITY_WEB_PROJECTILE));
			defaults.put("zombie", PetRule.defaultsForEntity("minecraft:zombie", MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS));
			return defaults;
		}

		private static JsonObject buildDynamicPetRuleDefaults(String fileKey) {
			String petId = PetConfigManager.resolvePetId(fileKey, null);
			return petId == null
				? JSONFormatManager.object().build()
				: PetRule.defaultsForEntity(petId, PetConfigManager.defaultAbilityForItem(petId));
		}

		private static boolean isSupportedPetRuleFile(String fileKey, JsonObject sourceRoot) {
			String petId = PetConfigManager.resolvePetId(fileKey, sourceRoot);
			return PetEntitiesManager.petItem(petId) != null;
		}
	}

	public static void reset() {
		PET_IDS_BY_PLAYER.clear();
		PET_INDEX_RECOVERED_OWNERS.clear();
		PET_ENTITIES_BY_ID.clear();
		ACTIVE_PET_IDS.clear();
		NEXT_IDLE_MOVE_BY_PET.clear();
		FOLLOW_COMMANDS_BY_PET.clear();
	}

	static void clearAllManagedPetState(MinecraftServer server) {
		if (!ACTIVE_PET_IDS.isEmpty() || !PET_IDS_BY_PLAYER.isEmpty()) {
			removeAllPetEntities(server);
		}
		reset();
	}

	static void clearManagedPetEntityState(MinecraftServer server) {
		if (!ACTIVE_PET_IDS.isEmpty() || !PET_IDS_BY_PLAYER.isEmpty()) {
			removeAllPetEntities(server);
		}
		reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		MadokuPetManager.onServerStarted(server);
	}

	public static void onInventoryChanged(ServerPlayer player) {
		if (player == null) return;
		PetHudManager.markAbilityHudDirty(player.getUUID());
		PetAbilitiesManager.applyPlayerMaxHealthAbilityBonus(player);
		PetAbilitiesManager.applyPlayerArmorAbilityBonus(player);
		PetAbilitiesManager.applyPlayerDamageAbilityBonus(player);
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		if (!PetConfigManager.isEnabled() || !PetConfigManager.areEntitiesEnabled()) {
			removeAllPets(server, player.getUUID());
			return;
		}
		MadokuPetManager.requestPetProcessing(server, player.getUUID(), 0L);
		MadokuPetManager.onPlayerTick(server, player, MadokuTimeManager.getGameplayTicks());
	}

	public static boolean isManaged(Entity entity) {
		return entity instanceof MadokuPetEntity;
	}

	public static boolean isValid(ItemStack stack) {
		return PetConfigManager.isValidPet(stack);
	}

	public static void dropAll(ServerPlayer player) {
		PetComponentsManager.dropAll(player);
	}

	public static int count(Player player) {
		return PetComponentsManager.countPets(player);
	}

	public static Vec3 movementTarget(Mob pet) {
		return MovementController.managedPetMovementTarget(pet, FOLLOW_COMMANDS_BY_PET);
	}

	public static double movementSpeed(Mob pet, double fallback) {
		return MovementController.managedPetMovementSpeed(pet, fallback, FOLLOW_COMMANDS_BY_PET);
	}

	static long managedPetSteeringInterval(MinecraftServer server) {
		return activeSchedulerTickInterval(server);
	}

	static void removeAllPets(MinecraftServer server, UUID playerId) {
		PetAbilitiesManager.stopBeeSwarmsForOwner(playerId);
		UUID[] petIds = PET_IDS_BY_PLAYER.remove(playerId);
		boolean hadIndexedState = petIds != null || PET_INDEX_RECOVERED_OWNERS.contains(playerId);
		PET_INDEX_RECOVERED_OWNERS.remove(playerId);
		if (petIds != null) {
			for (UUID petId : petIds) {
				removePet(server, petId);
			}
		}
		if (server != null && playerId != null && hadIndexedState) {
			for (ServerLevel level : server.getAllLevels()) {
				List<Entity> entities = new ArrayList<>();
				for (Entity entity : level.getAllEntities()) {
					entities.add(entity);
				}
				for (Entity entity : entities) {
					if (entity instanceof MadokuPetEntity pet && playerId.equals(pet.ownerUuid())) {
						removePet(server, pet.getUUID());
					}
				}
			}
		}
	}

	static void removePet(MinecraftServer server, UUID petId) {
		Mob pet = findMob(server, petId);
		if (pet != null) {
			pet.discard();
		}
		if (petId != null) {
			PET_ENTITIES_BY_ID.remove(petId);
			ACTIVE_PET_IDS.remove(petId);
			NEXT_IDLE_MOVE_BY_PET.remove(petId);
			FOLLOW_COMMANDS_BY_PET.remove(petId);
		}
	}

	static Mob findMob(MinecraftServer server, UUID entityId) {
		if (server == null || entityId == null) {
			return null;
		}
		MadokuPetEntity indexedPet = PET_ENTITIES_BY_ID.get(entityId);
		if (indexedPet != null) {
			if (indexedPet.isAlive() && !indexedPet.isRemoved()) {
				return indexedPet;
			}
			PET_ENTITIES_BY_ID.remove(entityId, indexedPet);
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(entityId) instanceof MadokuPetEntity pet && pet.isAlive()) {
				PET_ENTITIES_BY_ID.put(entityId, pet);
				return pet;
			}
		}
		return null;
	}

	static MadokuPetEntity findPet(MinecraftServer server, UUID entityId) {
		return findMob(server, entityId) instanceof MadokuPetEntity pet ? pet : null;
	}

	private static MadokuPetEntity findPetForOwnerAndSlot(MinecraftServer server, UUID ownerId, int slot) {
		if (server == null || ownerId == null || slot < 0 || slot >= SLOT_COUNT) {
			return null;
		}

		UUID[] petIds = PET_IDS_BY_PLAYER.computeIfAbsent(ownerId, ignored -> new UUID[SLOT_COUNT]);
		UUID indexedPetId = petIds[slot];
		MadokuPetEntity indexedPet = indexedPetId == null ? null : PET_ENTITIES_BY_ID.get(indexedPetId);
		if (isIndexedPet(indexedPet, ownerId, slot)) {
			return indexedPet;
		}
		petIds[slot] = null;

		if (!PET_INDEX_RECOVERED_OWNERS.contains(ownerId)) {
			recoverPetIndex(server, ownerId, petIds);
			indexedPet = petIds[slot] == null ? null : PET_ENTITIES_BY_ID.get(petIds[slot]);
			return isIndexedPet(indexedPet, ownerId, slot) ? indexedPet : null;
		}
		return null;
	}

	private static boolean isIndexedPet(MadokuPetEntity pet, UUID ownerId, int slot) {
		return pet != null
			&& pet.isAlive()
			&& ownerId.equals(pet.ownerUuid())
			&& slot == pet.petSlot();
	}

	/** Rebuilds the owner/slot index once, then removes duplicate or malformed managed pets. */
	private static void recoverPetIndex(MinecraftServer server, UUID ownerId, UUID[] petIds) {
		if (server == null || ownerId == null || petIds == null || petIds.length < SLOT_COUNT) {
			return;
		}
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			petIds[slot] = null;
		}

		List<MadokuPetEntity> toDiscard = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof MadokuPetEntity pet) || !ownerId.equals(pet.ownerUuid())) {
					continue;
				}
				int slot = pet.petSlot();
				if (!pet.isAlive() || slot < 0 || slot >= SLOT_COUNT || petIds[slot] != null) {
					toDiscard.add(pet);
					continue;
				}
				petIds[slot] = pet.getUUID();
				PET_ENTITIES_BY_ID.put(pet.getUUID(), pet);
			}
		}
		PET_INDEX_RECOVERED_OWNERS.add(ownerId);
		for (MadokuPetEntity pet : toDiscard) {
			removePet(server, pet.getUUID());
		}
	}

	static void preparePet(MadokuPetEntity pet) {
		if (pet == null) {
			return;
		}
		pet.getNavigation().setCanFloat(true);
	}

	private static void removeAllPetEntities(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<MadokuPetEntity> toDiscard = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof MadokuPetEntity pet) {
					toDiscard.add(pet);
				}
			}
			for (MadokuPetEntity pet : toDiscard) {
				removePet(server, pet.getUUID());
			}
		}
	}

	static void removeAllPetEntitiesOnServerStart(MinecraftServer server) {
		removeAllPetEntities(server);
	}

	static String getManagedPetItemId(Entity entity) {
		return entity instanceof MadokuPetEntity pet ? pet.petId() : "";
	}
	static long syncPlayerPets(ServerPlayer player) {
		MinecraftServer server = player == null ? null : player.level().getServer();
		if (server == null) {
			return -1L;
		}
		if (!PetConfigManager.areEntitiesEnabled()) {
			removeAllPets(server, player.getUUID());
			return -1L;
		}

		PetInventory inventory = PetComponentsManager.petInventory(player);
		if (inventory == null) {
			removeAllPets(server, player.getUUID());
			return -1L;
		}

		boolean anyActive = false;
		long nextDelay = idleSchedulerTickInterval(server);
		UUID[] petIds = PET_IDS_BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> new UUID[SLOT_COUNT]);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = inventory.getItem(slot);
			PetRule rule = PetConfigManager.resolvePetRule(stack);
			MadokuPetEntity pet = findPetForOwnerAndSlot(server, player.getUUID(), slot);
			if (pet == null) {
				pet = findPet(server, petIds[slot]);
			}

			if (rule == null) {
				if (!stack.isEmpty()) {
				}
				removePet(server, petIds[slot]);
				petIds[slot] = null;
				continue;
			}

			if (pet == null || pet.level() != player.level() || !rule.petId.equals(pet.petId()) || pet.petSlot() != slot || !player.getUUID().equals(pet.ownerUuid())) {
				removePet(server, petIds[slot]);
				pet = spawnPet(player, slot, rule, PetEntitiesManager.petLevel(stack));
				petIds[slot] = pet == null ? null : pet.getUUID();
			}

			if (pet != null) {
				ACTIVE_PET_IDS.add(pet.getUUID());
				petIds[slot] = pet.getUUID();
				ensurePetConfiguration(pet, rule, PetEntitiesManager.petLevel(stack));
					boolean beeSwarmActive = rule.hasAbility(MadokuPetManager.PET_ABILITY_BEE_SWARM) && PetAbilitiesManager.isBeeSwarmActive(player.getUUID(), slot);
				if (beeSwarmActive) {
					nextDelay = Math.min(nextDelay, activeSchedulerTickInterval(server));
				} else {
						nextDelay = Math.min(nextDelay, MovementController.updatePetPosition(
						player,
						pet,
						slot,
						rule,
						NEXT_IDLE_MOVE_BY_PET,
						FOLLOW_COMMANDS_BY_PET
					));
				}
				anyActive = true;
			}
		}

		if (!anyActive) {
			PET_IDS_BY_PLAYER.remove(player.getUUID());
			PET_INDEX_RECOVERED_OWNERS.remove(player.getUUID());
			PetAbilitiesManager.pruneCooldowns(player.getUUID(), inventory);
			return -1L;
		}

		return Math.max(1L, nextDelay);
	}

	static MadokuPetEntity spawnPet(ServerPlayer owner, int slot, PetRule rule, int level) {
		if (owner == null || rule == null || !(owner.level() instanceof ServerLevel serverLevel)) {
			return null;
		}

		MadokuPetEntity pet = PET_ENTITY.create(serverLevel, EntitySpawnReason.EVENT);
		if (pet == null) {
			return null;
		}

		Vec3 desiredPosition = MovementController.resolveDesiredPosition(owner, slot, pet);
		pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
		pet.setOwnerUuid(owner.getUUID());
		pet.setPetSlot(slot);
		pet.setPetLevel(level);
		preparePet(pet);
		configurePet(pet, rule);
		if (!serverLevel.addFreshEntity(pet)) {
			return null;
		}

		PET_ENTITIES_BY_ID.put(pet.getUUID(), pet);
		ACTIVE_PET_IDS.add(pet.getUUID());
		indexPet(pet);
		return pet;
	}

	private static void indexPet(MadokuPetEntity pet) {
		if (pet == null || pet.ownerUuid() == null || pet.petSlot() < 0 || pet.petSlot() >= SLOT_COUNT) {
			return;
		}
		UUID[] petIds = PET_IDS_BY_PLAYER.computeIfAbsent(pet.ownerUuid(), ignored -> new UUID[SLOT_COUNT]);
		UUID indexedPetId = petIds[pet.petSlot()];
		if (indexedPetId == null || indexedPetId.equals(pet.getUUID())) {
			petIds[pet.petSlot()] = pet.getUUID();
		}
	}

	static void configurePet(MadokuPetEntity pet, PetRule rule) {
		if (pet == null) {
			return;
		}

		pet.setPersistenceRequired();
		pet.noPhysics = false;
		pet.setNoGravity(false);
		pet.blocksBuilding = false;
		pet.clearFire();
		if (rule != null) {
			pet.setPetId(rule.petId);
		}
		pet.setNoGravity(isAirbornePet(pet));
		AttributeInstance scale = pet.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(rule == null ? 0.25D : rule.petScale);
		}
	}

	static void ensurePetConfiguration(MadokuPetEntity pet, PetRule rule, int level) {
		if (pet == null) {
			return;
		}

		if (!pet.isPersistenceRequired()) {
			pet.setPersistenceRequired();
		}
		pet.setNoGravity(isAirbornePet(pet));
		if (pet.isOnFire()) {
			pet.clearFire();
		}
		pet.setPetLevel(level);
		AttributeInstance scale = pet.getAttribute(Attributes.SCALE);
		if (scale != null) {
			double desiredScale = rule == null ? 0.25D : rule.petScale;
			if (Math.abs(scale.getBaseValue() - desiredScale) > 1.0E-4D) {
				scale.setBaseValue(desiredScale);
			}
		}
	}

	private static boolean isAirbornePet(MadokuPetEntity pet) {
		if (pet == null) {
			return false;
		}
		String petId = PetConfigManager.normalizePetId(pet.petId());
		return "minecraft:bat".equals(petId) || "minecraft:bee".equals(petId);
	}

	static long activeSchedulerTickInterval(MinecraftServer server) {
		return MadokuPetManager.adaptiveSchedulerInterval(server);
	}

	static boolean petEntitiesEnabled() {
		return PetConfigManager.settings().enabled && PetConfigManager.settings().entitiesEnabled;
	}

	static long idleSchedulerTickInterval(MinecraftServer server) {
		long activeInterval = activeSchedulerTickInterval(server);
		return Math.max(activeInterval, Math.min(20L, activeInterval * 3L));
	}

	static record FollowCommand(Vec3 target, double speed) {

	}

	static final class MovementController {
		private static final SlotOffset[] SLOT_OFFSETS = {
				new SlotOffset(-0.90D, 0.85D),
				new SlotOffset(-0.30D, 1.35D),
				new SlotOffset(0.30D, 1.35D),
				new SlotOffset(0.90D, 0.85D)
			};

			private MovementController() {
			}

			static long updatePetPosition(
				ServerPlayer owner,
				Mob pet,
				int slot,
				PetRule rule,
				Map<UUID, Long> nextIdleMoveByPet,
				Map<UUID, FollowCommand> followCommandsByPet
			) {
				Vec3 desiredPosition = resolveDesiredPosition(owner, slot, pet);
				double ownerDistanceSqr = pet.distanceToSqr(owner);
				double creativeDistanceMultiplier = creativeDistanceMultiplier(owner);
				double teleportDistance = (rule == null ? 8.0D : rule.teleportDistance) * creativeDistanceMultiplier;
				double teleportDistanceSqr = teleportDistance * teleportDistance;
				if (ownerDistanceSqr > teleportDistanceSqr) {
					pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
					pet.getNavigation().stop();
					pet.setDeltaMovement(Vec3.ZERO);
					clearFollowCommand(pet.getUUID(), followCommandsByPet);
					scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
					return activeSchedulerTickInterval(pet);
				}

				if (isHoveringPet(pet)) {
					return updateHoveringPetPosition(owner, pet, slot, ownerDistanceSqr, rule, nextIdleMoveByPet, followCommandsByPet);
				}

				double idleDistance = (rule == null ? 4.0D : rule.idleDistance) * creativeDistanceMultiplier;
				double idleDistanceSqr = idleDistance * idleDistance;
				if (ownerDistanceSqr <= idleDistanceSqr) {
					clearFollowCommand(pet.getUUID(), followCommandsByPet);
					return updatePetIdleMovement(owner, pet, slot, idleDistance, idleDistanceSqr, rule, nextIdleMoveByPet, followCommandsByPet);
				}

				double followSpeed = rule == null ? 1.25D : rule.followSpeed;
				issueFollowCommandIfNeeded(pet, desiredPosition, followSpeed, followCommandsByPet);
				pet.getLookControl().setLookAt(desiredPosition.x, desiredPosition.y, desiredPosition.z, 30.0F, 30.0F);
				return activeSchedulerTickInterval(pet);
			}

			private static long updateHoveringPetPosition(
				ServerPlayer owner,
				Mob pet,
				int slot,
				double ownerDistanceSqr,
				PetRule rule,
				Map<UUID, Long> nextIdleMoveByPet,
				Map<UUID, FollowCommand> followCommandsByPet
			) {
				Vec3 desiredPosition = resolveDesiredPosition(owner, slot, pet);
				double idleDistance = (rule == null ? 4.0D : rule.idleDistance) * creativeDistanceMultiplier(owner);
				double idleDistanceSqr = idleDistance * idleDistance;
				long gameTime = pet.level().getGameTime();
				UUID petId = pet.getUUID();
				FollowCommand currentCommand = followCommandsByPet.get(petId);
				long nextIdleMove = nextIdleMoveByPet.getOrDefault(petId, Long.MIN_VALUE);
				boolean ownerIsClose = ownerDistanceSqr <= idleDistanceSqr;
				boolean targetNeedsRefresh = currentCommand == null
					|| currentCommand.target() == null
					|| (!ownerIsClose && currentCommand.target().distanceToSqr(desiredPosition) > 1.0D);
				Vec3 target;
				double configuredSpeed;
				if (!ownerIsClose || targetNeedsRefresh) {
					target = desiredPosition;
					configuredSpeed = rule == null ? 0.8D : rule.followSpeed;
					followCommandsByPet.put(petId, new FollowCommand(target, configuredSpeed));
					scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
				} else if (gameTime >= nextIdleMove) {
					target = resolveIdleTarget(owner, pet, slot, idleDistance, idleDistanceSqr, rule);
					configuredSpeed = rule == null ? 0.8D : rule.idleMoveSpeed;
					followCommandsByPet.put(petId, new FollowCommand(target, configuredSpeed));
					scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
				} else {
					target = currentCommand.target();
					configuredSpeed = currentCommand.speed();
				}

				pet.getNavigation().stop();
				Vec3 difference = target.subtract(pet.position());
				double distance = difference.length();
				if (distance <= 0.05D) {
					pet.setDeltaMovement(Vec3.ZERO);
				} else {
					double interval = Math.max(1L, activeSchedulerTickInterval(pet));
					double speed = Mth.clamp(configuredSpeed * 0.20D * interval, 0.06D * interval, 0.35D * interval);
					Vec3 movement = difference.scale(Math.min(speed, distance) / distance);
					pet.move(MoverType.SELF, movement);
					pet.setDeltaMovement(Vec3.ZERO);
					orientPetToMovement(pet, difference);
				}
				pet.getLookControl().setLookAt(target.x, target.y, target.z, 30.0F, 30.0F);
				return activeSchedulerTickInterval(pet);
			}

			private static void orientPetToMovement(Mob pet, Vec3 movement) {
				if (pet == null || movement == null || movement.lengthSqr() <= 1.0E-6D) {
					return;
				}
				double horizontalLength = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
				if (horizontalLength <= 1.0E-6D) {
					return;
				}
				float yaw = (float) (Math.atan2(movement.z, movement.x) * (180.0D / Math.PI)) - 90.0F;
				float pitch = (float) (-(Math.atan2(movement.y, horizontalLength) * (180.0D / Math.PI)));
				pet.setYRot(yaw);
				pet.setYHeadRot(yaw);
				pet.setXRot(pitch);
				pet.yBodyRot = yaw;
				pet.yBodyRotO = yaw;
				pet.yHeadRot = yaw;
				pet.yHeadRotO = yaw;
				pet.yRotO = yaw;
				pet.xRotO = pitch;
			}

			static Vec3 resolveDesiredPosition(ServerPlayer owner, int slot, Mob pet) {
				SlotOffset offset = SLOT_OFFSETS[Math.max(0, Math.min(SLOT_OFFSETS.length - 1, slot))];
				Vec3 horizontalForward = resolveFollowDirection(owner);
				Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
				double verticalOffset = 0.10D;
				if (isHoveringPet(pet)) {
					verticalOffset = owner.getBbHeight() * 0.75D + hoverPetSlotVerticalOffset(pet, slot);
				}
				Vec3 base = owner.position().add(0.0D, verticalOffset, 0.0D);
				return base.add(right.scale(offset.side())).subtract(horizontalForward.scale(offset.back()));
			}

			static Vec3 managedPetMovementTarget(Mob pet, Map<UUID, FollowCommand> followCommandsByPet) {
				if (pet == null) {
					return null;
				}

				FollowCommand followCommand = followCommandsByPet.get(pet.getUUID());
				if (followCommand != null && followCommand.target() != null) {
					return followCommand.target();
				}

				if (pet.getNavigation().isDone()) {
					return null;
				}

				BlockPos navigationTarget = pet.getNavigation().getTargetPos();
				return navigationTarget == null ? null : Vec3.atCenterOf(navigationTarget);
			}

			static double managedPetMovementSpeed(Mob pet, double fallbackSpeed, Map<UUID, FollowCommand> followCommandsByPet) {
				if (pet == null) {
					return fallbackSpeed;
				}

				FollowCommand followCommand = followCommandsByPet.get(pet.getUUID());
				if (followCommand != null && followCommand.speed() > 0.0D) {
					return followCommand.speed();
				}

				return fallbackSpeed;
			}

			private static long updatePetIdleMovement(
				ServerPlayer owner,
				Mob pet,
				int slot,
				double idleDistance,
				double idleDistanceSqr,
				PetRule rule,
				Map<UUID, Long> nextIdleMoveByPet,
				Map<UUID, FollowCommand> followCommandsByPet
			) {
				if (pet == null || !pet.getNavigation().isDone()) {
					return activeSchedulerTickInterval(pet);
				}

				long gameTime = pet.level().getGameTime();
				long nextMoveTick = nextIdleMoveByPet.getOrDefault(pet.getUUID(), Long.MIN_VALUE);
				if (gameTime < nextMoveTick) {
					return clampScheduledDelay(nextMoveTick - gameTime, pet);
				}

				Vec3 idleTarget = resolveIdleTarget(owner, pet, slot, idleDistance, idleDistanceSqr, rule);
				if (idleTarget == null) {
					scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
					return nextIdleMoveDelay(pet, nextIdleMoveByPet);
				}

				pet.getNavigation().moveTo(idleTarget.x, idleTarget.y, idleTarget.z, rule == null ? 0.75D : rule.idleMoveSpeed);
				pet.getLookControl().setLookAt(idleTarget.x, idleTarget.y, idleTarget.z, 20.0F, 20.0F);
				scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
				return activeSchedulerTickInterval(pet);
			}

			private static Vec3 resolveIdleTarget(ServerPlayer owner, Mob pet, int slot, double idleDistance, double idleDistanceSqr, PetRule rule) {
				double idleWanderRadius = rule == null ? 2.0D : rule.idleWanderRadius;
				if (isHoveringPet(pet)) {
					Vec3 hoverAnchor = resolveDesiredPosition(owner, slot, pet);
					Vec3 offset = new Vec3(
						(pet.getRandom().nextDouble() - 0.5D) * 2.0D * idleWanderRadius,
						(pet.getRandom().nextDouble() - 0.5D) * (isBeePet(pet) ? 1.6D : 1.2D),
						(pet.getRandom().nextDouble() - 0.5D) * 2.0D * idleWanderRadius
					);
					Vec3 candidate = hoverAnchor.add(offset);
					double minHoverY = owner.getY() + 0.9D;
					double maxHoverY = owner.getY() + Math.max(1.8D, owner.getBbHeight() + (isBeePet(pet) ? 1.1D : 0.8D));
					candidate = new Vec3(candidate.x, Mth.clamp(candidate.y, minHoverY, maxHoverY), candidate.z);
					if (candidate.distanceToSqr(owner.position()) > idleDistanceSqr) {
						Vec3 clampedOffset = candidate.subtract(owner.position());
						if (clampedOffset.lengthSqr() <= 1.0E-6D) {
							return hoverAnchor;
						}
						candidate = owner.position().add(clampedOffset.normalize().scale(Math.max(0.75D, idleDistance * 0.55D)));
						candidate = new Vec3(candidate.x, Mth.clamp(candidate.y, minHoverY, maxHoverY), candidate.z);
					}
					return candidate;
				}

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

			private static void scheduleNextIdleMove(Mob pet, PetRule rule, Map<UUID, Long> nextIdleMoveByPet) {
				if (pet == null) {
					return;
				}

				long minInterval = Math.max(1L, rule == null ? 20L : rule.idleMinIntervalTicks);
				long maxInterval = Math.max(minInterval, rule == null ? 60L : rule.idleMaxIntervalTicks);
				long delay = minInterval;
				if (maxInterval > minInterval) {
					delay += pet.getRandom().nextInt((int) (maxInterval - minInterval + 1L));
				}
				nextIdleMoveByPet.put(pet.getUUID(), pet.level().getGameTime() + delay);
			}

			private static void issueFollowCommandIfNeeded(Mob pet, Vec3 desiredPosition, double followSpeed, Map<UUID, FollowCommand> followCommandsByPet) {
				if (pet == null || desiredPosition == null) {
					return;
				}

				UUID petId = pet.getUUID();
				FollowCommand previous = followCommandsByPet.get(petId);
				boolean shouldRepath = previous == null
					|| previous.target().distanceToSqr(desiredPosition) > 0.5625D
					|| Math.abs(previous.speed() - followSpeed) > 1.0E-4D
					|| pet.getNavigation().isDone();
				if (!shouldRepath) {
					return;
				}

				pet.getNavigation().moveTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, followSpeed);
				followCommandsByPet.put(petId, new FollowCommand(desiredPosition, followSpeed));
			}

			private static void clearFollowCommand(UUID petId, Map<UUID, FollowCommand> followCommandsByPet) {
				if (petId != null) {
					followCommandsByPet.remove(petId);
				}
			}

			private static long activeSchedulerTickInterval(Mob pet) {
				return PetEntitiesManager.activeSchedulerTickInterval(pet == null ? null : pet.level().getServer());
			}

			private static long idleSchedulerTickInterval(Mob pet) {
				long activeInterval = activeSchedulerTickInterval(pet);
				return Math.max(activeInterval, Math.min(20L, activeInterval * 3L));
			}

			private static long clampScheduledDelay(long delay, Mob pet) {
				return Math.max(activeSchedulerTickInterval(pet), Math.min(idleSchedulerTickInterval(pet), delay));
			}

			private static long nextIdleMoveDelay(Mob pet, Map<UUID, Long> nextIdleMoveByPet) {
				if (pet == null) {
					return idleSchedulerTickInterval(null);
				}
				long gameTime = pet.level().getGameTime();
				long nextMoveTick = nextIdleMoveByPet.getOrDefault(pet.getUUID(), gameTime + idleSchedulerTickInterval(pet));
				return clampScheduledDelay(nextMoveTick - gameTime, pet);
			}

			private static double batSlotVerticalOffset(int slot) {
				return switch (Math.max(0, Math.min(PetEntitiesManager.SLOT_COUNT - 1, slot))) {
					case 0 -> 0.45D;
					case 1 -> 0.15D;
					case 2 -> 0.35D;
					case 3 -> 0.05D;
					default -> 0.25D;
				};
			}

			private static double beeSlotVerticalOffset(int slot) {
				return switch (Math.max(0, Math.min(PetEntitiesManager.SLOT_COUNT - 1, slot))) {
					case 0 -> 0.55D;
					case 1 -> 0.30D;
					case 2 -> 0.62D;
					case 3 -> 0.25D;
					default -> 0.45D;
				};
			}

			private static double hoverPetSlotVerticalOffset(Mob pet, int slot) {
				if (isBeePet(pet)) {
					return beeSlotVerticalOffset(slot);
				}
				return batSlotVerticalOffset(slot);
			}

			private static boolean isHoveringPet(Mob pet) {
				return isBatPet(pet) || isBeePet(pet);
			}

			private static boolean isBatPet(Mob pet) {
				return pet instanceof MadokuPetEntity madokuPet
					&& "minecraft:bat".equals(PetConfigManager.normalizePetId(madokuPet.petId()));
			}

			private static boolean isBeePet(Mob pet) {
				return pet instanceof MadokuPetEntity madokuPet
					&& "minecraft:bee".equals(PetConfigManager.normalizePetId(madokuPet.petId()));
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

			private record SlotOffset(double side, double back) {
			}
	}
}
