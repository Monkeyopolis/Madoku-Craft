package madoku.craft.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

final class PetMovementController {
	private static final SlotOffset[] SLOT_OFFSETS = {
		new SlotOffset(-0.90D, 0.85D),
		new SlotOffset(-0.30D, 1.35D),
		new SlotOffset(0.30D, 1.35D),
		new SlotOffset(0.90D, 0.85D)
	};

	private PetMovementController() {
	}

	static long updatePetPosition(
		ServerPlayer owner,
		Mob pet,
		int slot,
		PetRule rule,
		PetSettings settings,
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
			return activeSchedulerTickInterval(settings);
		}

		double idleDistance = (rule == null ? 4.0D : rule.idleDistance) * creativeDistanceMultiplier;
		double idleDistanceSqr = idleDistance * idleDistance;
		if (ownerDistanceSqr <= idleDistanceSqr) {
			clearFollowCommand(pet.getUUID(), followCommandsByPet);
			return updatePetIdleMovement(owner, pet, slot, idleDistance, idleDistanceSqr, rule, settings, nextIdleMoveByPet, followCommandsByPet);
		}

		double followSpeed = rule == null ? 1.25D : rule.followSpeed;
		issueFollowCommandIfNeeded(pet, desiredPosition, followSpeed, followCommandsByPet);
		pet.getLookControl().setLookAt(desiredPosition.x, desiredPosition.y, desiredPosition.z, 30.0F, 30.0F);
		return activeSchedulerTickInterval(settings);
	}

	static Vec3 resolveDesiredPosition(ServerPlayer owner, int slot, Mob pet) {
		SlotOffset offset = SLOT_OFFSETS[Math.max(0, Math.min(SLOT_OFFSETS.length - 1, slot))];
		Vec3 horizontalForward = resolveFollowDirection(owner);
		Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
		double verticalOffset = 0.10D;
		if (pet instanceof Bat) {
			verticalOffset = owner.getBbHeight() * 0.75D + batSlotVerticalOffset(slot);
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
		PetSettings settings,
		Map<UUID, Long> nextIdleMoveByPet,
		Map<UUID, FollowCommand> followCommandsByPet
	) {
		if (pet == null || !pet.getNavigation().isDone()) {
			return activeSchedulerTickInterval(settings);
		}

		long gameTime = pet.level().getGameTime();
		long nextMoveTick = nextIdleMoveByPet.getOrDefault(pet.getUUID(), Long.MIN_VALUE);
		if (gameTime < nextMoveTick) {
			return clampScheduledDelay(nextMoveTick - gameTime, settings);
		}

		Vec3 idleTarget = resolveIdleTarget(owner, pet, slot, idleDistance, idleDistanceSqr, rule);
		if (idleTarget == null) {
			scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
			return nextIdleMoveDelay(pet, settings, nextIdleMoveByPet);
		}

		pet.getNavigation().moveTo(idleTarget.x, idleTarget.y, idleTarget.z, rule == null ? 0.75D : rule.idleMoveSpeed);
		pet.getLookControl().setLookAt(idleTarget.x, idleTarget.y, idleTarget.z, 20.0F, 20.0F);
		scheduleNextIdleMove(pet, rule, nextIdleMoveByPet);
		return activeSchedulerTickInterval(settings);
	}

	private static Vec3 resolveIdleTarget(ServerPlayer owner, Mob pet, int slot, double idleDistance, double idleDistanceSqr, PetRule rule) {
		double idleWanderRadius = rule == null ? 2.0D : rule.idleWanderRadius;
		if (pet instanceof Bat) {
			Vec3 hoverAnchor = resolveDesiredPosition(owner, slot, pet);
			Vec3 offset = new Vec3(
				(pet.getRandom().nextDouble() - 0.5D) * 2.0D * idleWanderRadius,
				(pet.getRandom().nextDouble() - 0.5D) * 1.2D,
				(pet.getRandom().nextDouble() - 0.5D) * 2.0D * idleWanderRadius
			);
			Vec3 candidate = hoverAnchor.add(offset);
			double minHoverY = owner.getY() + 0.9D;
			double maxHoverY = owner.getY() + Math.max(1.8D, owner.getBbHeight() + 0.8D);
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

	private static long activeSchedulerTickInterval(PetSettings settings) {
		long interval = settings == null ? 1L : settings.schedulerTickInterval;
		return Math.max(1L, interval);
	}

	private static long idleSchedulerTickInterval(PetSettings settings) {
		long activeInterval = activeSchedulerTickInterval(settings);
		return Math.max(activeInterval, Math.min(20L, activeInterval * 3L));
	}

	private static long clampScheduledDelay(long delay, PetSettings settings) {
		return Math.max(activeSchedulerTickInterval(settings), Math.min(idleSchedulerTickInterval(settings), delay));
	}

	private static long nextIdleMoveDelay(Mob pet, PetSettings settings, Map<UUID, Long> nextIdleMoveByPet) {
		if (pet == null) {
			return idleSchedulerTickInterval(settings);
		}
		long gameTime = pet.level().getGameTime();
		long nextMoveTick = nextIdleMoveByPet.getOrDefault(pet.getUUID(), gameTime + idleSchedulerTickInterval(settings));
		return clampScheduledDelay(nextMoveTick - gameTime, settings);
	}

	private static double batSlotVerticalOffset(int slot) {
		return switch (Math.max(0, Math.min(PlayerEntitiesSystem.SLOT_COUNT - 1, slot))) {
			case 0 -> 0.45D;
			case 1 -> 0.15D;
			case 2 -> 0.35D;
			case 3 -> 0.05D;
			default -> 0.25D;
		};
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
