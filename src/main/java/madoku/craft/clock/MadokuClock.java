package madoku.craft.clock;

import net.minecraft.server.level.ServerLevel;

public final class MadokuClock {
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
		observeWorldTime(world.getOverworldClockTime());
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
}
