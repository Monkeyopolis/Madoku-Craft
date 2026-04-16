package madoku.craft.entity;

import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.MadokuData;
import madoku.craft.time.MadokuTime;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

public final class MadokuEntities {
	public static final Identifier HAG_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "hag");
	public static final Identifier HAG_SPAWN_EGG_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "hag_spawn_egg");
	private static final String ENTITY_DATA_FOLDER_NAME = "madoku-craft-entities";
	private static final String ENTITY_DATA_FILE_NAME = "madoku-entities";
	private static final String DATA_NEXT_HAG_SPAWN_DAY = "next_hag_spawn_day";
	private static final String DATA_LAST_HAG_CHECK_DAY = "last_hag_check_day";
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;
	private static final long DAYS_PER_WEEK = 7L;
	private static final long MIN_HAG_SPAWN_WEEKS = 1L;
	private static final long MAX_HAG_SPAWN_WEEKS = 2L;
	private static final int MIN_HAG_SPAWN_DISTANCE = 24;
	private static final int MAX_HAG_SPAWN_DISTANCE = 48;
	private static final int HAG_SPAWN_ATTEMPTS_PER_PLAYER = 8;
	private static final String WANDERING_HAG_TAG = "madoku-craft.hag.wandering";
	private static final String WANDERING_HAG_DESPAWN_TIME_PREFIX = "madoku-craft.hag.despawn_time:";

	private static long nextWanderingHagSpawnDay = -1L;
	private static long lastProcessedWanderingHagDay = Long.MIN_VALUE;
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	public static final EntityType<Hag> HAG = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		HAG_ID,
		EntityType.Builder.of(Hag::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F)
			.eyeHeight(1.62F)
			.clientTrackingRange(8)
			.notInPeaceful()
			.build(ResourceKey.create(Registries.ENTITY_TYPE, HAG_ID))
	);
	public static final Item HAG_SPAWN_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		HAG_SPAWN_EGG_ID,
		new SpawnEggItem(
			new Item.Properties()
				.spawnEgg(HAG)
				.setId(ResourceKey.create(Registries.ITEM, HAG_SPAWN_EGG_ID))
		)
	);

	private MadokuEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HAG, Witch.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(HAG_SPAWN_EGG));
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof Witch witch) || witch.getType() != EntityType.WITCH || !(world instanceof ServerLevel serverLevel)) {
				return;
			}
			if (!isSwampHutSpawn(serverLevel, witch)) {
				return;
			}

			replaceWitchWithHag(serverLevel, witch);
		});
	}

	public static void reset() {
		nextWanderingHagSpawnDay = -1L;
		lastProcessedWanderingHagDay = Long.MIN_VALUE;
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		JsonObject defaults = createDefaultData();
		MadokuData.createWorldData(server, ENTITY_DATA_FOLDER_NAME, ENTITY_DATA_FILE_NAME, defaults);
		JsonObject data = MadokuData.loadWorldData(server, ENTITY_DATA_FOLDER_NAME, ENTITY_DATA_FILE_NAME);
		nextWanderingHagSpawnDay = Math.max(-1L, getLong(data, DATA_NEXT_HAG_SPAWN_DAY, -1L));
		lastProcessedWanderingHagDay = getLong(data, DATA_LAST_HAG_CHECK_DAY, Long.MIN_VALUE);
		long currentDay = currentAbsoluteDay(server);
		if (nextWanderingHagSpawnDay < 0L) {
			nextWanderingHagSpawnDay = currentDay + randomSpawnIntervalDays(server);
		}
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
		MadokuData.saveWorldData(server, ENTITY_DATA_FOLDER_NAME, ENTITY_DATA_FILE_NAME, toPersistedData());
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null || server.overworld() == null) {
			return;
		}
		long currentDay = currentAbsoluteDay(server);
		if (currentDay == lastProcessedWanderingHagDay) {
			return;
		}
		lastProcessedWanderingHagDay = currentDay;
		if (nextWanderingHagSpawnDay < 0L) {
			nextWanderingHagSpawnDay = currentDay + randomSpawnIntervalDays(server);
			return;
		}
		if (currentDay < nextWanderingHagSpawnDay || hasActiveWanderingHag(server)) {
			return;
		}
		if (trySpawnWanderingHag(server, currentDay)) {
			nextWanderingHagSpawnDay = currentDay + randomSpawnIntervalDays(server);
		} else {
			nextWanderingHagSpawnDay = currentDay + 1L;
		}
	}

	private static boolean isSwampHutSpawn(ServerLevel level, Witch witch) {
		StructureStart structureStart = level.structureManager().getStructureWithPieceAt(
			witch.blockPosition(),
			holder -> holder.unwrapKey().map(ResourceKey::identifier).filter(identifier -> "swamp_hut".equals(identifier.getPath())).isPresent()
		);
		return structureStart != null && structureStart != StructureStart.INVALID_START && structureStart.isValid() && !witch.hasActiveRaid();
	}

	private static void replaceWitchWithHag(ServerLevel level, Witch witch) {
		Hag hag = HAG.create(level, EntitySpawnReason.STRUCTURE);
		if (hag == null) {
			return;
		}

		hag.snapTo(witch.getX(), witch.getY(), witch.getZ(), witch.getYRot(), witch.getXRot());
		hag.setYBodyRot(witch.yBodyRot);
		hag.yHeadRot = witch.yHeadRot;
		hag.yHeadRotO = witch.yHeadRotO;
		hag.setHealth(Math.min(witch.getHealth(), hag.getMaxHealth()));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			hag.setItemSlot(slot, witch.getItemBySlot(slot).copy());
		}

		DifficultyInstance difficulty = level.getCurrentDifficultyAt(witch.blockPosition());
		hag.finalizeSpawn(level, difficulty, EntitySpawnReason.STRUCTURE, null);
		hag.setHealth(Math.min(witch.getHealth(), hag.getMaxHealth()));
		if (witch.hasCustomName()) {
			hag.setCustomName(witch.getCustomName());
			hag.setCustomNameVisible(witch.isCustomNameVisible());
		}
		hag.setInvulnerable(witch.isInvulnerable());
		hag.setSilent(witch.isSilent());
		hag.setNoAi(witch.isNoAi());
		if (witch.isPersistenceRequired()) {
			hag.setPersistenceRequired();
		}

		if (level.addFreshEntity(hag)) {
			witch.discard();
		}
	}

	private static boolean trySpawnWanderingHag(MinecraftServer server, long currentDay) {
		ServerLevel level = server == null ? null : server.overworld();
		if (level == null) {
			return false;
		}

		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level) {
				continue;
			}
			candidates.add(player);
		}
		if (candidates.isEmpty()) {
			return false;
		}

		ServerPlayer player = candidates.get(level.getRandom().nextInt(candidates.size()));
		return trySpawnWanderingHagNearPlayer(level, player) != null;
	}

	private static Hag trySpawnWanderingHagNearPlayer(ServerLevel level, ServerPlayer player) {
		if (level == null || player == null) {
			return null;
		}

		for (int attempt = 0; attempt < HAG_SPAWN_ATTEMPTS_PER_PLAYER; attempt++) {
			double angle = level.getRandom().nextDouble() * (Math.PI * 2.0D);
			int distance = MIN_HAG_SPAWN_DISTANCE + level.getRandom().nextInt(MAX_HAG_SPAWN_DISTANCE - MIN_HAG_SPAWN_DISTANCE + 1);
			int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos spawnPos = new BlockPos(x, y, z);
			BlockPos belowPos = spawnPos.below();
			if (level.getBlockState(belowPos).getCollisionShape(level, belowPos).isEmpty()) {
				continue;
			}
			if (!level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()
				|| !level.getBlockState(spawnPos.above()).getCollisionShape(level, spawnPos.above()).isEmpty()) {
				continue;
			}

			Hag hag = HAG.create(level, EntitySpawnReason.NATURAL);
			if (hag == null) {
				return null;
			}
			hag.snapTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
			hag.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.NATURAL, null);
			tagAsWanderingHag(hag, currentAbsoluteDayTime(level));
			if (!level.noCollision(hag) || !hag.checkSpawnObstruction(level)) {
				hag.discard();
				continue;
			}
			if (level.addFreshEntity(hag)) {
				return hag;
			}
			hag.discard();
		}
		return null;
	}

	private static net.minecraft.world.phys.AABB hagSearchBounds(ServerLevel level) {
		return level == null ? new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0) : new net.minecraft.world.phys.AABB(
			level.getWorldBorder().getMinX(),
			level.getMinY(),
			level.getWorldBorder().getMinZ(),
			level.getWorldBorder().getMaxX(),
			level.getMaxY(),
			level.getWorldBorder().getMaxZ()
		);
	}

	private static boolean hasActiveWanderingHag(MinecraftServer server) {
		if (server == null) {
			return false;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Hag hag : level.getEntitiesOfClass(Hag.class, hagSearchBounds(level))) {
				if (hag.isAlive() && isWanderingHag(hag)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void tagAsWanderingHag(Hag hag, long currentAbsoluteDayTime) {
		if (hag == null) {
			return;
		}
		hag.addTag(WANDERING_HAG_TAG);
		String existingDespawnTag = null;
		for (String tag : hag.getTags()) {
			if (tag != null && tag.startsWith(WANDERING_HAG_DESPAWN_TIME_PREFIX)) {
				existingDespawnTag = tag;
				break;
			}
		}
		if (existingDespawnTag != null) {
			hag.removeTag(existingDespawnTag);
		}
		hag.addTag(WANDERING_HAG_DESPAWN_TIME_PREFIX + Math.max(0L, currentAbsoluteDayTime + 24000L));
	}

	static boolean isWanderingHag(Hag hag) {
		return hag != null && hag.getTags().contains(WANDERING_HAG_TAG);
	}

	static boolean shouldDespawnWanderingHag(Hag hag, ServerLevel level) {
		Long despawnTime = wanderingHagDespawnTime(hag);
		return despawnTime != null && currentAbsoluteDayTime(level) >= despawnTime;
	}

	private static Long wanderingHagDespawnTime(Hag hag) {
		if (hag == null) {
			return null;
		}
		for (String tag : hag.getTags()) {
			if (tag == null || !tag.startsWith(WANDERING_HAG_DESPAWN_TIME_PREFIX)) {
				continue;
			}
			String suffix = tag.substring(WANDERING_HAG_DESPAWN_TIME_PREFIX.length()).trim();
			try {
				return Long.parseLong(suffix);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static long currentAbsoluteDay(MinecraftServer server) {
		ServerLevel level = server == null ? null : server.overworld();
		if (level == null) {
			return 0L;
		}
		if (MadokuTime.isEnabled()) {
			return Math.max(0L, MadokuTime.getDay(MadokuTime.getCurrentAbsoluteDayTime(level)));
		}
		return Math.max(0L, Math.floorDiv(level.getDayTime(), 24000L));
	}

	private static long currentAbsoluteDayTime(ServerLevel level) {
		if (level == null) {
			return 0L;
		}
		if (MadokuTime.isEnabled()) {
			return Math.max(0L, MadokuTime.getCurrentAbsoluteDayTime(level));
		}
		return Math.max(0L, level.getDayTime());
	}

	private static long randomSpawnIntervalDays(MinecraftServer server) {
		ServerLevel level = server == null ? null : server.overworld();
		if (level == null) {
			return DAYS_PER_WEEK * MIN_HAG_SPAWN_WEEKS;
		}
		long minDays = DAYS_PER_WEEK * MIN_HAG_SPAWN_WEEKS;
		long maxDays = DAYS_PER_WEEK * MAX_HAG_SPAWN_WEEKS;
		return minDays + level.getRandom().nextInt((int) (maxDays - minDays + 1L));
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.addProperty(DATA_NEXT_HAG_SPAWN_DAY, -1L);
		root.addProperty(DATA_LAST_HAG_CHECK_DAY, Long.MIN_VALUE);
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		root.addProperty(DATA_NEXT_HAG_SPAWN_DAY, nextWanderingHagSpawnDay);
		root.addProperty(DATA_LAST_HAG_CHECK_DAY, lastProcessedWanderingHagDay);
		return root;
	}

	private static long getLong(JsonObject root, String key, long fallback) {
		if (root == null || key == null || !root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return root.get(key).getAsLong();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}
}
