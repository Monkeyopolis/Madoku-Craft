package madoku.craft.pet;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerEntitiesSystem {
	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "PlayerEntities";
	private static final String MANAGED_PET_TAG = "madoku-craft.pet";
	private static final String MANAGED_PET_OWNER_PREFIX = "madoku-craft.pet.owner:";
	private static final int PET_AMBIENT_INTERVAL_MULTIPLIER = 3;
	private static final float PET_SOUND_VOLUME_MULTIPLIER = 0.5F;
	private static final double PET_SCALE = 0.25D;
	private static final double PET_FOLLOW_SPEED = 1.25D;
	private static final double PET_IDLE_MOVE_SPEED = 0.75D;
	private static final double PET_IDLE_DISTANCE_SQR = 16.0D;
	private static final double PET_TELEPORT_DISTANCE_SQR = 64.0D;
	private static final double PET_IDLE_WANDER_RADIUS = 2.0D;
	private static final long PET_IDLE_MIN_INTERVAL_TICKS = 20L;
	private static final long PET_IDLE_MAX_INTERVAL_TICKS = 60L;
	private static final SlotOffset[] SLOT_OFFSETS = {
		new SlotOffset(-0.90D, 0.85D),
		new SlotOffset(-0.30D, 1.35D),
		new SlotOffset(0.30D, 1.35D),
		new SlotOffset(0.90D, 0.85D)
	};
	private static final Map<UUID, UUID[]> PET_IDS_BY_PLAYER = new ConcurrentHashMap<>();
	private static final Set<UUID> ACTIVE_PET_IDS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> NEXT_IDLE_MOVE_BY_PET = new ConcurrentHashMap<>();

	private PlayerEntitiesSystem() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			removeAllPets(player.level().getServer(), player.getUUID());
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
				return;
			}

			dropAll(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			copyToNewPlayer(oldPlayer, newPlayer);
			removeAllPets(newPlayer.level().getServer(), oldPlayer.getUUID());
		});
		ServerPlayerEvents.JOIN.register(PlayerEntitiesSystem::handlePlayerJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler == null || handler.player == null) {
				return;
			}
			handlePlayerLeave(server, handler.player.getUUID());
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> ACTIVE_PET_IDS.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(PlayerEntitiesSystem::tickPets);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PET_IDS_BY_PLAYER.clear();
			ACTIVE_PET_IDS.clear();
			NEXT_IDLE_MOVE_BY_PET.clear();
			removeTaggedPets(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PET_IDS_BY_PLAYER.clear();
			ACTIVE_PET_IDS.clear();
			NEXT_IDLE_MOVE_BY_PET.clear();
		});
	}

	public static boolean isValidPlayerEntity(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpawnEggItem;
	}

	public static boolean isManagedPet(Entity entity) {
		return entity != null && ACTIVE_PET_IDS.contains(entity.getUUID());
	}

	public static int ambientSoundInterval(int baseInterval) {
		return Math.max(20, baseInterval * PET_AMBIENT_INTERVAL_MULTIPLIER);
	}

	public static float soundVolume(float baseVolume) {
		return Math.max(0.0F, baseVolume * PET_SOUND_VOLUME_MULTIPLIER);
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

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}

		handlePlayerLeave(server, player.getUUID());
		syncPlayerPets(player);
	}

	private static void handlePlayerLeave(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		removeAllPets(server, playerId);
		removeTaggedPets(server, playerId);
	}

	private static void tickPets(MinecraftServer server) {
		if (server == null) {
			return;
		}

		Set<UUID> activePlayers = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			activePlayers.add(player.getUUID());
			syncPlayerPets(player);
		}

		PET_IDS_BY_PLAYER.keySet().removeIf(playerId -> {
			if (activePlayers.contains(playerId)) {
				return false;
			}
			removeAllPets(server, playerId);
			return true;
		});
	}

	private static void syncPlayerPets(ServerPlayer player) {
		MinecraftServer server = player == null ? null : player.level().getServer();
		if (server == null) {
			return;
		}
		PlayerEntitiesInventory playerEntitiesInventory = playerEntitiesInventory(player);
		if (playerEntitiesInventory == null || player.isSpectator()) {
			removeAllPets(server, player.getUUID());
			return;
		}

		UUID[] petIds = PET_IDS_BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> new UUID[SLOT_COUNT]);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = playerEntitiesInventory.getItem(slot);
			EntityType<?> desiredType = resolvePetType(stack);
			Mob pet = findMob(server, petIds[slot]);

			if (desiredType == null) {
				removePet(server, petIds[slot]);
				petIds[slot] = null;
				continue;
			}

			if (pet == null || pet.getType() != desiredType || pet.level() != player.level()) {
				removePet(server, petIds[slot]);
				pet = spawnPet(player, desiredType, slot);
				petIds[slot] = pet == null ? null : pet.getUUID();
			}

			if (pet != null) {
				configurePet(pet);
				updatePetPosition(player, pet, slot);
			}
		}

		if (Arrays.stream(petIds).allMatch(uuid -> uuid == null)) {
			PET_IDS_BY_PLAYER.remove(player.getUUID());
		}
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
			scale.setBaseValue(PET_SCALE);
		}
	}

	private static void updatePetPosition(ServerPlayer owner, Mob pet, int slot) {
		Vec3 desiredPosition = resolveDesiredPosition(owner, slot);
		double ownerDistanceSqr = pet.distanceToSqr(owner);
		if (ownerDistanceSqr > PET_TELEPORT_DISTANCE_SQR) {
			pet.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
			pet.getNavigation().stop();
			pet.setDeltaMovement(Vec3.ZERO);
			scheduleNextIdleMove(pet);
			return;
		}

		if (ownerDistanceSqr <= PET_IDLE_DISTANCE_SQR) {
			updatePetIdleMovement(owner, pet);
			return;
		}

		pet.getNavigation().moveTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, PET_FOLLOW_SPEED);
		pet.getLookControl().setLookAt(desiredPosition.x, desiredPosition.y, desiredPosition.z, 30.0F, 30.0F);
	}

	private static void updatePetIdleMovement(ServerPlayer owner, Mob pet) {
		if (pet == null) {
			return;
		}

		if (!pet.getNavigation().isDone()) {
			return;
		}

		long gameTime = pet.level().getGameTime();
		long nextMoveTick = NEXT_IDLE_MOVE_BY_PET.getOrDefault(pet.getUUID(), Long.MIN_VALUE);
		if (gameTime < nextMoveTick) {
			return;
		}

		Vec3 idleTarget = resolveIdleTarget(owner, pet);
		if (idleTarget == null) {
			scheduleNextIdleMove(pet);
			return;
		}

		pet.getNavigation().moveTo(idleTarget.x, idleTarget.y, idleTarget.z, PET_IDLE_MOVE_SPEED);
		pet.getLookControl().setLookAt(idleTarget.x, idleTarget.y, idleTarget.z, 20.0F, 20.0F);
		scheduleNextIdleMove(pet);
	}

	private static Vec3 resolveIdleTarget(ServerPlayer owner, Mob pet) {
		Vec3 current = pet.position();
		Vec3 offset = new Vec3(
			(pet.getRandom().nextDouble() - 0.5D) * 2.0D * PET_IDLE_WANDER_RADIUS,
			0.0D,
			(pet.getRandom().nextDouble() - 0.5D) * 2.0D * PET_IDLE_WANDER_RADIUS
		);
		Vec3 candidate = current.add(offset);
		if (candidate.distanceToSqr(owner.position()) > PET_IDLE_DISTANCE_SQR) {
			Vec3 ownerDelta = candidate.subtract(owner.position());
			Vec3 horizontal = new Vec3(ownerDelta.x, 0.0D, ownerDelta.z);
			if (horizontal.lengthSqr() <= 1.0E-6D) {
				return null;
			}
			candidate = owner.position().add(horizontal.normalize().scale(1.6D)).add(0.0D, 0.1D, 0.0D);
		}
		return candidate;
	}

	private static void scheduleNextIdleMove(Mob pet) {
		if (pet == null) {
			return;
		}

		long delay = PET_IDLE_MIN_INTERVAL_TICKS + pet.getRandom().nextInt((int) (PET_IDLE_MAX_INTERVAL_TICKS - PET_IDLE_MIN_INTERVAL_TICKS + 1L));
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

	private record SlotOffset(double side, double back) {
	}
}
