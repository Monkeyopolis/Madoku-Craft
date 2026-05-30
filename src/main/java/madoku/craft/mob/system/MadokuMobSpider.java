package madoku.craft.mob.system;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MadokuMobSpider {
	private MadokuMobSpider() {
	}

	public static void applySpawnOverrides(
		Spider spider,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		MadokuMobManager.applySpiderSpawnOverrides(spider, world, difficulty, spawnReason);
	}
}

