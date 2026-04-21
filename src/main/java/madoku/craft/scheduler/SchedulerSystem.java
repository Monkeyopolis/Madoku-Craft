package madoku.craft.scheduler;

import com.google.gson.JsonArray;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class SchedulerSystem {
	private SchedulerSystem() {
	}

	static void load(MinecraftServer server, JsonArray ids, long currentDay) {
		SchedulerManagerSystem.loadTier(server, ids, SchedulerManagerSystem.SchedulerTier.LOCAL, currentDay);
	}

	static void processDue(MinecraftServer server, ServerLevel level, long nowTick, long currentDay) {
		if (level == null) {
			return;
		}
		String levelId = SchedulerManagerSystem.normalizeLevelIdentifier(level.dimension().toString());
		SchedulerManagerSystem.processTier(server, SchedulerManagerSystem.SchedulerTier.LOCAL, levelId, nowTick, currentDay);
	}
}
