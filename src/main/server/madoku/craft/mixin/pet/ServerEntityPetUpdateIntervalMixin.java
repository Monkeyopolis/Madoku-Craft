package madoku.craft.mixin.pet;

import madoku.craft.java.pet.MadokuPetEntity;
import madoku.craft.java.pet.PetAPIManager;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the shared adaptive pet runtime interval to entity tracking updates. */
@Mixin(ServerEntity.class)
public abstract class ServerEntityPetUpdateIntervalMixin {
	@Shadow
	@Final
	private Entity entity;

	@Shadow
	@Final
	private int updateInterval;

	@Redirect(
		method = "sendChanges",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/server/level/ServerEntity;updateInterval:I",
			opcode = Opcodes.GETFIELD
		)
	)
	private int madokuCraft$resolvePetUpdateInterval(ServerEntity tracker) {
		if (entity instanceof MadokuPetEntity pet && pet.level().getServer() != null) {
			long interval = PetAPIManager.managedPetSteeringInterval(pet.level().getServer());
			return (int) Math.max(1L, Math.min(5L, interval));
		}
		return updateInterval;
	}
}

