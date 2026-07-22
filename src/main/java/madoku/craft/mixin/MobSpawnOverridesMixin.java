package madoku.craft.mixin;

import madoku.craft.mob.system.MobEntityManager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({
	Mob.class,
	Zombie.class,
	ZombieVillager.class,
	Drowned.class,
	Husk.class,
	AbstractSkeleton.class,
	Spider.class
})
public abstract class MobSpawnOverridesMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applySpawnOverridesBeforeVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Mob mob = (Mob) (Object) this;
		if (MobEntityManager.applyMobSpawnOverridesBeforeVanilla(mob, world, difficulty, spawnReason)) {
			cir.setReturnValue(spawnGroupData);
		}
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void madokuCraft$applySpawnOverridesAfterVanilla(
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		SpawnGroupData spawnGroupData,
		CallbackInfoReturnable<SpawnGroupData> cir
	) {
		MobEntityManager.applyMobSpawnOverridesAfterVanilla((Mob) (Object) this, world, difficulty, spawnReason);
	}
}


