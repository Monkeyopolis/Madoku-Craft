package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Bat.class)
public abstract class BatPetBehaviorMixin {
	private static final Map<UUID, BlockPos> MANAGED_BAT_TARGETS = new ConcurrentHashMap<>();

	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void madokuCraft$keepManagedBatActive(ServerLevel level, CallbackInfo ci) {
		Bat self = (Bat) (Object) this;
		if (!PlayerEntitiesSystem.isManagedPet(self)) {
			MANAGED_BAT_TARGETS.remove(self.getUUID());
			return;
		}

		self.setResting(false);
	}

	@Inject(
		method = "customServerAiStep",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ambient/Bat;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"
		)
	)
	private void madokuCraft$applyManagedBatTarget(ServerLevel level, CallbackInfo ci) {
		Bat self = (Bat) (Object) this;
		if (!PlayerEntitiesSystem.isManagedPet(self)) {
			return;
		}

		long steeringInterval = Math.max(1L, PlayerEntitiesSystem.managedPetSteeringInterval());
		if (level.getGameTime() % steeringInterval == 0L) {
			Vec3 target = PlayerEntitiesSystem.managedPetMovementTarget(self);
			BlockPos targetPos = target == null
				? BlockPos.containing(self.position())
				: new BlockPos(
					Mth.floor(target.x),
					Math.max(level.getMinY(), Mth.floor(target.y + 0.4D)),
					Mth.floor(target.z)
				);
			MANAGED_BAT_TARGETS.put(self.getUUID(), targetPos);
		}

		BlockPos targetPos = MANAGED_BAT_TARGETS.get(self.getUUID());
		if (targetPos == null) {
			return;
		}

		((BatTargetAccessor) self).madokuCraft$setTargetPosition(targetPos);
	}
}

