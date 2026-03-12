package madoku.craft.clock;

public final class MadokuClock {
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;

	private static long ticks = 0L;
	private static volatile boolean enabled = true;

	private MadokuClock() {
	}

	public static void tick() {
		tick(1L);
	}

	public static void tick(long amount) {
		if (!enabled || amount <= 0L) {
			return;
		}
		ticks += amount;
	}

	public static void reset() {
		ticks = 0L;
	}

	public static long getTicks() {
		if (!enabled) {
			return MadokuGameplayClock.getTicks();
		}
		return ticks;
	}

	public static void setTicks(long value) {
		if (!enabled) {
			return;
		}
		ticks = value;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isEnabled() {
		return enabled;
	}
}
