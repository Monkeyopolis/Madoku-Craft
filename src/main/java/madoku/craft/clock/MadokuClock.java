package madoku.craft.clock;

import net.minecraft.server.level.ServerLevel;

public final class MadokuClock {
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;

	private static boolean hasObservedWorldTime = false;
	private static long lastObservedWorldDayTime = 0L;
	private static long lastWorldTimeDelta = 0L;

	private MadokuClock() {
	}

	public static void reset() {
		hasObservedWorldTime = false;
		lastObservedWorldDayTime = 0L;
		lastWorldTimeDelta = 0L;
	}

	public static void observeWorldTime(ServerLevel world) {
		if (world == null) {
			return;
		}
		observeWorldTime(world.getDayTime());
	}

	public static void observeWorldTime(long observedWorldDayTime) {
		if (hasObservedWorldTime) {
			lastWorldTimeDelta = observedWorldDayTime - lastObservedWorldDayTime;
		} else {
			lastWorldTimeDelta = 0L;
			hasObservedWorldTime = true;
		}
		lastObservedWorldDayTime = observedWorldDayTime;
	}

	public static long getLastObservedWorldDayTime() {
		return hasObservedWorldTime ? lastObservedWorldDayTime : 0L;
	}

	public static long getLastWorldTimeDelta() {
		return lastWorldTimeDelta;
	}

	public static void tickGameplay() {
		MadokuTicks.tickGameplay();
	}

	public static void tickTime() {
		tickTime(1L);
	}

	public static void tickTime(long amount) {
		if (amount <= 0L) {
			return;
		}
	}

	public static long getGameplayTicks() {
		return MadokuTicks.getGameplayTicks();
	}

	public static long getTimeTicks() {
		return MadokuTicks.getGameplayTicks();
	}

	public static void setGameplayTicks(long value) {
		MadokuTicks.setGameplayTicks(value);
	}

	public static void setTimeTicks(long value) {
		MadokuTicks.setGameplayTicks(value);
	}
}
