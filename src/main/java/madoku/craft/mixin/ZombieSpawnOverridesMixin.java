package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobZombie;
import madoku.craft.mob.system.MadokuMobManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieSpawnOverridesMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyZombieSpawnOverridesBeforeVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Zombie zombie = (Zombie) (Object) this;
		if (zombie.getType() == EntityType.HUSK) {
			return;
		}
		if (MadokuMobZombie.shouldOverrideSpawnRules(zombie)) {
			MadokuMobManager.applyZombieSpawnOverrides(zombie, world, difficulty, spawnReason);
			cir.setReturnValue(spawnGroupData);
		}
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void madokuCraft$applyZombieSpawnOverrides(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Zombie zombie = (Zombie) (Object) this;
		if (zombie.getType() == EntityType.HUSK) {
			return;
		}
		MadokuMobZombie.applySpawnOverrides(zombie, world, difficulty, spawnReason);
	}
}

