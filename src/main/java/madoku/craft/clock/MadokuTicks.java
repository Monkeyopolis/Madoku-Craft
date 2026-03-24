package madoku.craft.clock;

import madoku.craft.scheduler.MadokuScheduler;
import madoku.craft.time.MadokuTime;
import net.minecraft.server.MinecraftServer;

public final class MadokuTicks {
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;

	private static long gameplayTicks = 0L;

	private MadokuTicks() {
	}

	public static void tickGameplay() {
		gameplayTicks++;
	}

	public static void reset() {
		gameplayTicks = 0L;
	}

	public static long getGameplayTicks() {
		return gameplayTicks;
	}

	public static void setGameplayTicks(long value) {
		gameplayTicks = Math.max(0L, value);
	}

	public static void advance(MinecraftServer server, long amount) {
		if (server == null) {
			return;
		}

		tickGameplay();
		MadokuTime.advanceSkippedTimeTicks(Math.max(1L, amount));
		MadokuScheduler.tick(server, true, true);
	}
}
