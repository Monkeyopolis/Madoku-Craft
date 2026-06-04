package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobDrowned;
import madoku.craft.mob.system.MadokuMobManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Drowned.class)
public abstract class DrownedSpawnOverridesMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyDrownedSpawnOverridesBeforeVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Drowned drowned = (Drowned) (Object) this;
		if (MadokuMobDrowned.shouldOverrideSpawnRules(drowned)) {
			MadokuMobManager.applyDrownedSpawnOverrides(drowned, world, difficulty, spawnReason);
			cir.setReturnValue(spawnGroupData);
		}
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void madokuCraft$applyDrownedSpawnOverrides(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Drowned drowned = (Drowned) (Object) this;
		MadokuMobDrowned.applySpawnOverrides(drowned, world, difficulty, spawnReason);
	}
}
