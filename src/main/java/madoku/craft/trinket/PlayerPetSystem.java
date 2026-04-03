package madoku.craft.trinket;

import madoku.craft.clock.MadokuTicks;
import madoku.craft.mob.system.MadokuMob;
import madoku.craft.pet.PlayerEntitiesHolder;
import madoku.craft.pet.PlayerEntitiesInventory;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerPetSystem {
	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "PlayerPets";
	private static final Item SKELETON_PET_ITEM = Items.SKELETON_SPAWN_EGG;
	private static final long SKELETON_PET_COOLDOWN_TICKS = 15L * 20L;
	private static final long SKELETON_PET_SHOT_DELAY_TICKS = 5L;
	private static final float SKELETON_PET_ARROW_DAMAGE = 5.0F;
	private static final float SKELETON_PET_ARROW_SPEED = 1.6F;
	private static final Map<UUID, long[]> SKELETON_PET_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Map<UUID, Queue<PendingSkeletonArrowShot>> PENDING_SKELETON_ARROW_SHOTS = new ConcurrentHashMap<>();

	private PlayerPetSystem() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
				return;
			}

			dropAll(player);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PlayerPetSystem::handleAfterDamage);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> copyToNewPlayer(oldPlayer, newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(PlayerPetSystem::processPendingSkeletonArrowShots);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SKELETON_PET_COOLDOWNS.clear());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PENDING_SKELETON_ARROW_SHOTS.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SKELETON_PET_COOLDOWNS.clear();
			PENDING_SKELETON_ARROW_SHOTS.clear();
		});
	}

	public static boolean isValidPlayerPet(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpawnEggItem;
	}

	public static boolean hasSkeletonPet(Player player) {
		return hasPet(player, SKELETON_PET_ITEM);
	}

	public static int countSkeletonPets(Player player) {
		return countPets(player, SKELETON_PET_ITEM);
	}

	public static boolean hasPet(Player player, Item item) {
		return countPets(player, item) > 0;
	}

	public static int countPets(Player player, Item item) {
		PlayerEntitiesInventory playerEntitiesInventory = playerEntitiesInventory(player);
		if (playerEntitiesInventory == null || item == null) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < playerEntitiesInventory.getContainerSize(); slot++) {
			if (playerEntitiesInventory.getItem(slot).is(item)) {
				count++;
			}
		}
		return count;
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

	private static void copyToNewPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
		PlayerEntitiesInventory oldInventory = playerEntitiesInventory(oldPlayer);
		PlayerEntitiesInventory newInventory = playerEntitiesInventory(newPlayer);
		if (oldInventory == null || newInventory == null) {
			return;
		}

		newInventory.copyFrom(oldInventory);
	}

	private static void handleAfterDamage(
		LivingEntity entity,
		DamageSource source,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked
	) {
		if (!(entity instanceof LivingEntity target) || damageTaken <= 0.0F || blocked) {
			return;
		}
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!player.isAlive() || !target.isAlive() || player == target || !hasSkeletonPet(player)) {
			return;
		}
		long gameplayTicks = MadokuTicks.getGameplayTicks();
		PlayerEntitiesInventory playerEntitiesInventory = playerEntitiesInventory(player);
		if (playerEntitiesInventory == null) {
			return;
		}

		long[] slotCooldowns = SKELETON_PET_COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new long[SLOT_COUNT]);
		int[] readySkeletonSlots = new int[SLOT_COUNT];
		int readySkeletonCount = 0;
		for (int slot = 0; slot < Math.min(SLOT_COUNT, playerEntitiesInventory.getContainerSize()); slot++) {
			ItemStack stack = playerEntitiesInventory.getItem(slot);
			if (!stack.is(SKELETON_PET_ITEM)) {
				slotCooldowns[slot] = 0L;
				continue;
			}
			if (gameplayTicks < slotCooldowns[slot]) {
				continue;
			}

			readySkeletonSlots[readySkeletonCount++] = slot;
		}
		if (readySkeletonCount <= 0) {
			return;
		}

		int queuedShots = 0;
		for (int index = 0; index < readySkeletonCount; index++) {
			int slot = readySkeletonSlots[index];
			Vec3 spawnPosition = resolveSkeletonArrowSpawn(player, index, readySkeletonCount);
			if (index == 0) {
				if (spawnSkeletonPetArrow(player, target, spawnPosition)) {
					slotCooldowns[slot] = gameplayTicks + SKELETON_PET_COOLDOWN_TICKS;
					queuedShots++;
				}
				continue;
			}

			enqueuePendingSkeletonArrowShot(
				player,
				slot,
				target,
				spawnPosition,
				gameplayTicks + (index * SKELETON_PET_SHOT_DELAY_TICKS)
			);
			queuedShots++;
		}
		if (queuedShots <= 0) {
			return;
		}
	}

	private static Vec3 resolveSkeletonArrowSpawn(ServerPlayer player, int index, int count) {
		Vec3 forward = player.getLookAngle();
		forward = new Vec3(forward.x, 0.0D, forward.z);
		if (forward.lengthSqr() <= 1.0E-6D) {
			forward = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			forward = forward.normalize();
		}

		Vec3 back = forward.scale(-1.0D);
		Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
		double angleRadians = resolveSkeletonArcAngle(index, count);
		double lateralOffset = Math.sin(angleRadians) * 0.45D;
		double rearOffset = 0.58D + Math.cos(angleRadians) * 0.20D;
		return player.getEyePosition()
			.add(back.scale(rearOffset))
			.add(right.scale(lateralOffset))
			.add(0.0D, -0.34D, 0.0D);
	}

	private static double resolveSkeletonArcAngle(int index, int count) {
		int clampedCount = Math.max(1, Math.min(SLOT_COUNT, count));
		if (clampedCount == 1) {
			return 0.0D;
		}

		double step = Math.toRadians(18.0D);
		double centeringOffset = (clampedCount - 1) * 0.5D;
		return (index - centeringOffset) * step;
	}

	private static boolean spawnSkeletonPetArrow(ServerPlayer player, LivingEntity target, Vec3 spawnPosition) {
		if (player == null || target == null || spawnPosition == null || !player.isAlive() || !target.isAlive()) {
			return false;
		}
		if (!MadokuMob.spawnManagedHomingArrow(player, target, spawnPosition, SKELETON_PET_ARROW_SPEED, SKELETON_PET_ARROW_DAMAGE)) {
			return false;
		}

		player.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
	}

	private static void enqueuePendingSkeletonArrowShot(ServerPlayer player, int skeletonSlot, LivingEntity target, Vec3 spawnPosition, long dueTick) {
		if (player == null || target == null || spawnPosition == null) {
			return;
		}

		PENDING_SKELETON_ARROW_SHOTS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>())
			.add(new PendingSkeletonArrowShot(skeletonSlot, target.getUUID(), spawnPosition, Math.max(0L, dueTick)));
	}

	private static void processPendingSkeletonArrowShots(MinecraftServer server) {
		if (server == null || PENDING_SKELETON_ARROW_SHOTS.isEmpty()) {
			return;
		}

		long gameplayTicks = MadokuTicks.getGameplayTicks();
		Iterator<Map.Entry<UUID, Queue<PendingSkeletonArrowShot>>> iterator = PENDING_SKELETON_ARROW_SHOTS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Queue<PendingSkeletonArrowShot>> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Queue<PendingSkeletonArrowShot> queue = entry.getValue();
			if (player == null || queue == null || queue.isEmpty()) {
				iterator.remove();
				continue;
			}

			while (!queue.isEmpty() && queue.peek().dueTick() <= gameplayTicks) {
				PendingSkeletonArrowShot shot = queue.remove();
				LivingEntity target = findLivingEntity(server, shot.targetId());
				if (target == null || !player.isAlive() || !target.isAlive() || !canSkeletonSlotShoot(player, shot.skeletonSlot(), gameplayTicks)) {
					continue;
				}
				if (spawnSkeletonPetArrow(player, target, shot.spawnPosition())) {
					skeletonPetCooldowns(player)[shot.skeletonSlot()] = gameplayTicks + SKELETON_PET_COOLDOWN_TICKS;
				}
			}

			if (queue.isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static LivingEntity findLivingEntity(MinecraftServer server, UUID entityId) {
		if (server == null || entityId == null) {
			return null;
		}

		for (var level : server.getAllLevels()) {
			if (level == null) {
				continue;
			}
			if (level.getEntity(entityId) instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
				return livingEntity;
			}
		}
		return null;
	}

	private static boolean canSkeletonSlotShoot(ServerPlayer player, int slot, long gameplayTicks) {
		if (slot < 0 || slot >= SLOT_COUNT) {
			return false;
		}

		PlayerEntitiesInventory playerEntitiesInventory = playerEntitiesInventory(player);
		if (playerEntitiesInventory == null || slot >= playerEntitiesInventory.getContainerSize()) {
			return false;
		}
		if (!playerEntitiesInventory.getItem(slot).is(SKELETON_PET_ITEM)) {
			skeletonPetCooldowns(player)[slot] = 0L;
			return false;
		}
		return gameplayTicks >= skeletonPetCooldowns(player)[slot];
	}

	private static long[] skeletonPetCooldowns(ServerPlayer player) {
		return SKELETON_PET_COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new long[SLOT_COUNT]);
	}

	private static PlayerEntitiesInventory playerEntitiesInventory(Player player) {
		return player instanceof PlayerEntitiesHolder holder ? holder.madokuCraft$getPlayerEntitiesInventory() : null;
	}

	private record PendingSkeletonArrowShot(int skeletonSlot, UUID targetId, Vec3 spawnPosition, long dueTick) {
	}
}
