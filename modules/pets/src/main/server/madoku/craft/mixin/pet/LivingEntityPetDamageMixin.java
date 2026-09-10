package madoku.craft.mixin.pet;

import madoku.craft.java.pet.PetAbilitiesAPIManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the standalone Pets damage-block ability after vanilla armor absorption. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPetDamageMixin {
	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$applyPetDamageBlock(
		DamageSource source,
		float amount,
		CallbackInfoReturnable<Float> cir
	) {
		if (isHandledByUnifiedOrCompat()) {
			return;
		}

		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity instanceof ServerPlayer) {
			cir.setReturnValue(PetAbilitiesAPIManager.applyDamageBlock(entity, source, cir.getReturnValue()));
		}
	}

	private static boolean isHandledByUnifiedOrCompat() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("madoku-craft")) {
			return true;
		}
		return loader.isModLoaded("madoku-craft-compat")
			&& loader.isModLoaded("madoku-craft-core")
			&& loader.isModLoaded("madoku-craft-attributes")
			&& loader.isModLoaded("madoku-craft-mobs")
			&& loader.isModLoaded("madoku-craft-pets");
	}
}
