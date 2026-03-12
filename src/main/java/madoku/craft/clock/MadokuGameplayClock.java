package madoku.craft.clock;

public final class MadokuGameplayClock {
	private static long ticks = 0L;

	private MadokuGameplayClock() {
	}

	public static void tick() {
		ticks++;
	}

	public static void reset() {
		ticks = 0L;
	}

	public static long getTicks() {
		return ticks;
	}

	public static void setTicks(long value) {
		ticks = Math.max(0L, value);
	}
}
