package madoku.craft;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/** Keeps the client-side native time tick in step with the server clock rate. */
public final class MadokuClientTimeManager {
	private static volatile float serverClockRate = 1.0F;
	private static volatile double worldTimeRemainder = 0.0D;

	private MadokuClientTimeManager() {
	}

	public static void setServerClockRate(float rate) {
		if (!Float.isFinite(rate) || rate <= 0.0F) {
			serverClockRate = 1.0F;
		} else {
			serverClockRate = rate;
		}
		worldTimeRemainder = 0.0D;
	}

	public static void reset() {
		serverClockRate = 1.0F;
		worldTimeRemainder = 0.0D;
	}

	public static long resolveNextWorldTime(ClientLevel level, long vanillaNextDayTime) {
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return vanillaNextDayTime;
		}

		float rate = serverClockRate;
		if (!Float.isFinite(rate) || rate <= 0.0F || rate == 1.0F) {
			return vanillaNextDayTime;
		}

		worldTimeRemainder += rate;
		long wholeTicks = (long) Math.floor(worldTimeRemainder);
		worldTimeRemainder -= wholeTicks;
		return wholeTicks <= 0L ? level.getDayTime() : safeAdd(level.getDayTime(), wholeTicks);
	}

	private static long safeAdd(long base, long delta) {
		try {
			return Math.addExact(base, delta);
		} catch (ArithmeticException exception) {
			return delta >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}
}
