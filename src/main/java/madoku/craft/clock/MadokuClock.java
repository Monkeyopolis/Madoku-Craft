package madoku.craft.clock;

public final class MadokuClock {
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;

	private static long gameplayTicks = 0L;
	private static long timeTicks = 0L;

	private MadokuClock() {
	}

	public static void tickGameplay() {
		gameplayTicks++;
	}

	public static void tickTime() {
		tickTime(1L);
	}

	public static void tickTime(long amount) {
		if (amount <= 0L) {
			return;
		}
		timeTicks += amount;
	}

	public static void reset() {
		gameplayTicks = 0L;
		timeTicks = 0L;
	}

	public static long getGameplayTicks() {
		return gameplayTicks;
	}

	public static long getTimeTicks() {
		return timeTicks;
	}

	public static void setGameplayTicks(long value) {
		gameplayTicks = Math.max(0L, value);
	}

	public static void setTimeTicks(long value) {
		timeTicks = Math.max(0L, value);
	}
}
