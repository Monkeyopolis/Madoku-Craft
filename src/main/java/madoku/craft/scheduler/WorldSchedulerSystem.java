package madoku.craft.scheduler;

import com.google.gson.JsonArray;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class WorldSchedulerSystem {
	private WorldSchedulerSystem() {
	}

	static void load(MinecraftServer server, JsonArray ids, long currentDay) {
		SchedulerManagerSystem.loadTier(server, ids, SchedulerManagerSystem.SchedulerTier.WORLD, currentDay);
	}

	static void processDue(MinecraftServer server, long nowTick, long currentDay) {
		for (ServerLevel level : server.getAllLevels()) {
			String levelId = SchedulerManagerSystem.normalizeLevelIdentifier(level.dimension().toString());
			SchedulerManagerSystem.processTier(server, SchedulerManagerSystem.SchedulerTier.WORLD, levelId, nowTick, currentDay);
			SchedulerSystem.processDue(server, level, nowTick, currentDay);
		}
	}
}
