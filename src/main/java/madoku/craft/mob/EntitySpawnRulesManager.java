package madoku.craft.mob;

import madoku.craft.entity.MadokuEntities;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.ServerLevelAccessor;

/** Runtime group for configured entity spawn rules. */
public final class EntitySpawnRulesManager {
	private EntitySpawnRulesManager() {
	}

	public static void initialize() {
	}

	public static boolean applyBeforeVanilla(Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason) {
		if (mob == null || world == null || difficulty == null || spawnReason == null || !MobEntityManager.isEnabled()) return false;
		if (mob instanceof ZombieVillager zombieVillager) {
			if (EntityBehaviorsManager.ZombieBehavior.shouldOverrideSpawnRules(zombieVillager)) {
				MobEntityManager.applyZombieSpawnOverrides(zombieVillager, world, difficulty, spawnReason);
				return true;
			}
			return false;
		}
		if (mob instanceof Drowned drowned) {
			if (EntityBehaviorsManager.DrownedBehavior.shouldOverrideSpawnRules(drowned)) {
				MobEntityManager.applyDrownedSpawnOverrides(drowned, world, difficulty, spawnReason);
				return true;
			}
			return false;
		}
		if (mob instanceof Husk husk) {
			if (EntityBehaviorsManager.HuskBehavior.shouldOverrideSpawnRules(husk)) {
				EntityBehaviorsManager.HuskBehavior.applySpawnOverrides(husk, world, difficulty, spawnReason);
				return true;
			}
			return false;
		}
		if (mob instanceof Creeper creeper) {
			if (EntityBehaviorsManager.CreeperBehavior.shouldOverrideSpawnRules(creeper)) {
				EntityBehaviorsManager.CreeperBehavior.applySpawnOverrides(creeper, world, difficulty);
				return true;
			}
			return false;
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON && EntityBehaviorsManager.WitherSkeletonBehavior.shouldOverrideSpawnRules(skeleton)) {
				EntityBehaviorsManager.WitherSkeletonBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
				return true;
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.BOGGED && EntityBehaviorsManager.BoggedBehavior.shouldOverrideSpawnRules(skeleton)) {
				EntityBehaviorsManager.BoggedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
				return true;
			}
			if (skeleton.getType() == madoku.craft.entity.MadokuEntityTypes.PARCHED && EntityBehaviorsManager.ParchedBehavior.shouldOverrideSpawnRules(skeleton)) {
				EntityBehaviorsManager.ParchedBehavior.applySpawnOverrides(skeleton, world, difficulty, spawnReason);
				return true;
			}
			if (EntityBehaviorsManager.SkeletonBehavior.shouldOverrideSpawnRules(skeleton)) {
				MobEntityManager.applySkeletonSpawnOverrides(skeleton, world, difficulty, spawnReason);
				return true;
			}
			return false;
		}
		if (mob instanceof Spider spider) {
			return EntityBehaviorsManager.SpiderBehavior.shouldOverrideSpawnRules(spider) && MobEntityManager.applySpiderSpawnOverrides(spider, world, difficulty, spawnReason);
		}
		if (mob instanceof Zombie zombie) {
			if (EntityBehaviorsManager.ZombieBehavior.shouldOverrideSpawnRules(zombie)) {
				MobEntityManager.applyZombieSpawnOverrides(zombie, world, difficulty, spawnReason);
				return true;
			}
		}
		return false;
	}

	public static void applyAfterVanilla(Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason) {
		if (mob == null || world == null || difficulty == null || spawnReason == null || !MobEntityManager.isEnabled()) return;
		EntityComponentsManager.applyConfiguredComponents(mob, MobEntityManager.resolveConfiguredEntityVariantForRuntime(mob));
		EntityComponentsManager.applyWorldDifficultyScaling(mob);
		if (mob.getType() == madoku.craft.entity.MadokuEntityTypes.BEE) {
			EntityBehaviorsManager.BeeBehavior.applySpawnOverrides(mob, world);
			return;
		}
		if (mob.getType() == MadokuEntities.HAG) {
			EntityBehaviorsManager.HagBehavior.applySpawnOverrides(mob);
			return;
		}
		if (mob instanceof Spider spider) {
			MobEntityManager.applySpiderSpawnOverrides(spider, world, difficulty, spawnReason);
		} else if (mob instanceof ZombieVillager zombieVillager) {
			MobEntityManager.applyZombieSpawnOverrides(zombieVillager, world, difficulty, spawnReason);
		} else if (mob instanceof Drowned drowned) {
			MobEntityManager.applyDrownedSpawnOverrides(drowned, world, difficulty, spawnReason);
		} else if (mob instanceof Husk husk) {
			EntityBehaviorsManager.HuskBehavior.applySpawnOverrides(husk, world, difficulty, spawnReason);
		} else if (mob instanceof AbstractSkeleton skeleton) {
			MobEntityManager.applySkeletonSpawnOverrides(skeleton, world, difficulty, spawnReason);
		} else if (mob instanceof Zombie zombie) {
			MobEntityManager.applyZombieSpawnOverrides(zombie, world, difficulty, spawnReason);
		}
	}
}
