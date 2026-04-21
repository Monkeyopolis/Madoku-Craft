package madoku.craft.scheduler;

import com.google.gson.JsonArray;
import net.minecraft.server.MinecraftServer;

public final class GlobalSchedulerSystem {
	private GlobalSchedulerSystem() {
	}

	static void load(MinecraftServer server, JsonArray ids, long currentDay) {
		SchedulerManagerSystem.loadTier(server, ids, SchedulerManagerSystem.SchedulerTier.GLOBAL, currentDay);
	}

	static void processDue(MinecraftServer server, long nowTick, long currentDay) {
		SchedulerManagerSystem.processTier(server, SchedulerManagerSystem.SchedulerTier.GLOBAL, null, nowTick, currentDay);
		WorldSchedulerSystem.processDue(server, nowTick, currentDay);
	}
}
