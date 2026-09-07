package madoku.craft.java.core.helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Shared runtime behavior for configured mob and pet projectiles. */
public final class HelperProjectileAPIManager {
	private static final double MIN_HOMING_SPEED = 0.75D;
	private static final int HOMING_LIFETIME_TICKS = 60;
	private static final int MANAGED_PROJECTILE_LIFETIME_TICKS = 15 * 20;
	private static final String HOMING_PROJECTILE_TAG = "madoku-craft.projectile.homing";

	private static final Map<UUID, HomingState> HOMING_PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FIXED_PROJECTILE_DAMAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MANAGED_PROJECTILES = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> INVULNERABILITY_BYPASS_PROJECTILES = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Entity> TRACKED_ENTITIES = new ConcurrentHashMap<>();

	private HelperProjectileAPIManager() {
	}

	public static void initialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> TRACKED_ENTITIES.put(entity.getUUID(), entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			TRACKED_ENTITIES.remove(entity.getUUID());
			onEntityCleanup(entity);
		});
	}

	public static void onServerStarted(MinecraftServer server) {
		clearRuntimeState();
		TRACKED_ENTITIES.clear();
	}

	public static void reset() {
		clearRuntimeState();
		TRACKED_ENTITIES.clear();
	}

	public static void onEntityCleanup(Entity entity) {
		if (entity instanceof AbstractArrow) {
			removeRuntimeState(entity.getUUID());
		}
	}

	public static float resolveProjectileDamageOverride(AbstractArrow projectile, float fallbackDamage) {
		if (projectile == null) {
			return fallbackDamage;
		}
		Float fixed = FIXED_PROJECTILE_DAMAGE.get(projectile.getUUID());
		return fixed == null ? fallbackDamage : Math.max(0.0F, fixed);
	}

	public static boolean hasProjectileDamageOverride(AbstractArrow projectile) {
		return projectile != null && FIXED_PROJECTILE_DAMAGE.containsKey(projectile.getUUID());
	}

	public static void setProjectileDamageOverride(AbstractArrow projectile, float damage) {
		if (projectile != null) {
			FIXED_PROJECTILE_DAMAGE.put(projectile.getUUID(), Math.max(0.0F, damage));
		}
	}

	public static void startProjectileHoming(AbstractArrow projectile, LivingEntity target) {
		if (projectile == null || target == null) {
			return;
		}
		double homingSpeed = Math.max(MIN_HOMING_SPEED, projectile.getDeltaMovement().length());
		projectile.setNoGravity(true);
		projectile.addTag(HOMING_PROJECTILE_TAG);
		HOMING_PROJECTILES.put(projectile.getUUID(), new HomingState(target.getUUID(), homingSpeed, HOMING_LIFETIME_TICKS));
	}

	public static void clearProjectileHoming(AbstractArrow projectile) {
		if (projectile == null) {
			return;
		}
		HOMING_PROJECTILES.remove(projectile.getUUID());
		if (projectile.isAlive()) {
			projectile.setNoGravity(false);
		}
	}

	public static boolean spawnManagedHomingArrow(
		LivingEntity shooter,
		LivingEntity target,
		Vec3 spawnPosition,
		float speed,
		float damage
	) {
		if (shooter == null || target == null || !target.isAlive() || spawnPosition == null || !(shooter.level() instanceof ServerLevel level)) {
			return false;
		}

		Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		arrow.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
		Vec3 desired = target.getEyePosition().subtract(spawnPosition);
		if (desired.lengthSqr() <= 1.0E-6D) {
			desired = shooter.getLookAngle();
		}
		arrow.shoot(desired.x, desired.y, desired.z, Math.max(0.1F, speed), 0.0F);
		arrow.setCritArrow(false);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		setProjectileDamageOverride(arrow, damage);
		INVULNERABILITY_BYPASS_PROJECTILES.add(arrow.getUUID());
		trackManagedProjectile(arrow);
		startProjectileHoming(arrow, target);
		level.addFreshEntity(arrow);
		return true;
	}

	public static boolean shouldBypassInvulnerability(AbstractArrow projectile) {
		return projectile != null && INVULNERABILITY_BYPASS_PROJECTILES.contains(projectile.getUUID());
	}

	public static boolean isManagedHomingProjectile(AbstractArrow projectile) {
		return projectile != null
			&& (HOMING_PROJECTILES.containsKey(projectile.getUUID()) || projectile.entityTags().contains(HOMING_PROJECTILE_TAG));
	}

	public static void clearInvulnerabilityBypass(AbstractArrow projectile) {
		if (projectile != null) {
			INVULNERABILITY_BYPASS_PROJECTILES.remove(projectile.getUUID());
		}
	}

	public static void trackManagedProjectile(AbstractArrow projectile) {
		if (projectile != null) {
			MANAGED_PROJECTILES.put(projectile.getUUID(), MANAGED_PROJECTILE_LIFETIME_TICKS);
		}
	}

	public static boolean hasActiveRuntimeState() {
		return !MANAGED_PROJECTILES.isEmpty() || !HOMING_PROJECTILES.isEmpty();
	}

	public static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickHomingProjectiles(HelperProjectileAPIManager::findEntity);
		tickManagedProjectiles(HelperProjectileAPIManager::findEntity);
	}

	private static Entity findEntity(UUID entityId) {
		Entity entity = TRACKED_ENTITIES.get(entityId);
		if (entity != null && entity.isAlive()) {
			return entity;
		}
		if (entity != null) {
			TRACKED_ENTITIES.remove(entityId);
		}
		return null;
	}

	private static void tickHomingProjectiles(Function<UUID, Entity> entityLookup) {
		for (Map.Entry<UUID, HomingState> entry : HOMING_PROJECTILES.entrySet()) {
			UUID projectileId = entry.getKey();
			HomingState state = entry.getValue();
			AbstractArrow projectile = resolveArrow(entityLookup, projectileId);
			if (state.remainingTicks <= 0) {
				releaseHoming(projectileId, projectile);
				continue;
			}
			if (projectile == null || !projectile.isAlive() || projectile.onGround()) {
				FIXED_PROJECTILE_DAMAGE.remove(projectileId);
				HOMING_PROJECTILES.remove(projectileId);
				continue;
			}
			Entity target = entityLookup.apply(state.targetUuid);
			if (!(target instanceof LivingEntity living) || !living.isAlive()) {
				releaseHoming(projectileId, projectile);
				continue;
			}
			Vec3 toTarget = new Vec3(target.getX() - projectile.getX(), target.getY(0.5D) - projectile.getY(), target.getZ() - projectile.getZ());
			if (toTarget.lengthSqr() <= 1.0E-6D) {
				releaseHoming(projectileId, projectile);
				continue;
			}
			double speed = Math.max(state.speed, projectile.getDeltaMovement().length());
			projectile.setNoGravity(true);
			projectile.setDeltaMovement(toTarget.normalize().scale(speed));
			HOMING_PROJECTILES.put(projectileId, new HomingState(state.targetUuid, speed, state.remainingTicks - 1));
		}
	}

	private static void tickManagedProjectiles(Function<UUID, Entity> entityLookup) {
		for (Map.Entry<UUID, Integer> entry : MANAGED_PROJECTILES.entrySet()) {
			UUID projectileId = entry.getKey();
			int remainingTicks = entry.getValue() == null ? 0 : entry.getValue();
			AbstractArrow projectile = resolveArrow(entityLookup, projectileId);
			if (projectile == null || !projectile.isAlive()) {
				removeRuntimeState(projectileId);
				continue;
			}
			if (remainingTicks <= 0) {
				projectile.discard();
				removeRuntimeState(projectileId);
				continue;
			}
			MANAGED_PROJECTILES.put(projectileId, remainingTicks - 1);
		}
	}

	private static AbstractArrow resolveArrow(Function<UUID, Entity> entityLookup, UUID projectileId) {
		Entity entity = entityLookup.apply(projectileId);
		return entity instanceof AbstractArrow projectile ? projectile : null;
	}

	private static void releaseHoming(UUID projectileId, AbstractArrow projectile) {
		HOMING_PROJECTILES.remove(projectileId);
		if (projectile != null && projectile.isAlive()) {
			projectile.setNoGravity(false);
		}
	}

	private static void removeRuntimeState(UUID projectileId) {
		if (projectileId == null) {
			return;
		}
		HOMING_PROJECTILES.remove(projectileId);
		FIXED_PROJECTILE_DAMAGE.remove(projectileId);
		MANAGED_PROJECTILES.remove(projectileId);
		INVULNERABILITY_BYPASS_PROJECTILES.remove(projectileId);
	}

	private static void clearRuntimeState() {
		HOMING_PROJECTILES.clear();
		FIXED_PROJECTILE_DAMAGE.clear();
		MANAGED_PROJECTILES.clear();
		INVULNERABILITY_BYPASS_PROJECTILES.clear();
	}

	private record HomingState(UUID targetUuid, double speed, int remainingTicks) {
	}
}

