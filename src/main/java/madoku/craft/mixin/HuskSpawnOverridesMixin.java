package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobHusk;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Husk.class)
public abstract class HuskSpawnOverridesMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyHuskSpawnOverridesBeforeVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Husk husk = (Husk) (Object) this;
		if (MadokuMobHusk.shouldOverrideSpawnRules(husk)) {
			MadokuMobHusk.applySpawnOverrides(husk, world, difficulty, spawnReason);
			cir.setReturnValue(spawnGroupData);
		}
	}
}
