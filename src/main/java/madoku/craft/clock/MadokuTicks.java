package madoku.craft.clock;

import madoku.craft.scheduler.MadokuScheduler;
import net.minecraft.server.MinecraftServer;

public final class MadokuTicks {
	private MadokuTicks() {
	}

	public static void advance(MinecraftServer server, long amount) {
		if (server == null) {
			return;
		}

		long timeSteps = Math.max(1L, amount);
		MadokuClock.tickGameplay();
		MadokuClock.tickTime();
		MadokuScheduler.tick(server, true, true);
		for (long step = 1L; step < timeSteps; step++) {
			MadokuClock.tickTime();
			MadokuScheduler.tick(server, false, true);
		}
	}
}
