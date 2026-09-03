package madoku.craft.java.core.scheduler;

import net.minecraft.server.MinecraftServer;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import madoku.craft.java.core.time.TimeAPIManager;

public final class SchedulerAdaptiveIntervalAPIManager {
	private static final float LOW_LOAD_MSPT = 30.0f;
	private static final float HIGH_LOAD_MSPT = 55.0f;
	private static final float HEALTHY_MSPT = 42.0f;
	private static final float UNHEALTHY_MSPT = 50.0f;
	private static final float CRITICAL_MSPT = 58.0f;
	private static final double EMA_ALPHA = 0.20d;
	private static final long MIN_CHANGE_COOLDOWN_TICKS = 100L;
	private static final long PROBE_OBSERVE_TICKS = 200L;
	private static final long PROBE_FAIL_COOLDOWN_TICKS = 600L;
	private static final long PROBE_SUCCESS_COOLDOWN_TICKS = 200L;
	private static final long HEALTHY_STABLE_TICKS_TO_PROBE = 300L;
	private static final long UNHEALTHY_STABLE_TICKS_TO_SCALE_UP = 100L;
	private static final long SAFE_FLOOR_RELAX_TICKS = 1200L;
	private static final Map<String, IntervalState> STATES = new ConcurrentHashMap<>();

	private SchedulerAdaptiveIntervalAPIManager() {
	}

	public static long resolve(String systemId, MinecraftServer server, long minimumIntervalTicks, long maximumIntervalTicks) {
		long min = Math.max(1L, minimumIntervalTicks);
		long max = Math.max(min, maximumIntervalTicks);
		if (server == null || min == max) {
			return min;
		}
		String normalizedSystemId = normalizeSystemId(systemId);
		long nowTick = Math.max(0L, TimeAPIManager.getGameplayTicks());
		IntervalState state = STATES.computeIfAbsent(normalizedSystemId, ignored -> IntervalState.initial(nowTick, min));
		return state.resolve(server, nowTick, min, max);
	}

	public static void clearSystem(String systemId) {
		STATES.remove(normalizeSystemId(systemId));
	}

	public static void clearAll() {
		STATES.clear();
	}

	private static String normalizeSystemId(String systemId) {
		if (systemId == null) {
			return "default";
		}
		String normalized = systemId.trim().toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? "default" : normalized;
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class IntervalState {
		private long currentInterval;
		private long safeFloorInterval;
		private long nextAllowedChangeTick;
		private long nextAllowedDownscaleTick;
		private long lastHealthyTick = Long.MIN_VALUE;
		private long lastUnhealthyTick = Long.MIN_VALUE;
		private long lastSafeFloorRaiseTick = Long.MIN_VALUE;
		private long lastEvaluationTick = Long.MIN_VALUE;
		private boolean probing;
		private long probeStartTick = Long.MIN_VALUE;
		private long probeInterval;
		private double smoothedMspt = Double.NaN;

		private static IntervalState initial(long nowTick, long minInterval) {
			IntervalState state = new IntervalState();
			state.currentInterval = Math.max(1L, minInterval);
			state.safeFloorInterval = state.currentInterval;
			state.nextAllowedChangeTick = nowTick;
			state.nextAllowedDownscaleTick = nowTick;
			state.lastEvaluationTick = Long.MIN_VALUE;
			return state;
		}

		private long resolve(MinecraftServer server, long nowTick, long min, long max) {
			normalizeRange(nowTick, min, max);
			if (nowTick == lastEvaluationTick) {
				return clamp(currentInterval, safeFloorInterval, max);
			}
			if (nowTick < lastEvaluationTick) {
				resetForClockRollback(nowTick, min, max);
			}

			float rawMspt = Math.max(0.0f, resolveAverageMspt(server));
			if (Float.isFinite(rawMspt) && rawMspt > 0.0f) {
				if (!Double.isFinite(smoothedMspt) || smoothedMspt <= 0.0d) {
					smoothedMspt = rawMspt;
				} else {
					smoothedMspt = (EMA_ALPHA * rawMspt) + ((1.0d - EMA_ALPHA) * smoothedMspt);
				}
			}

			double mspt = smoothedMspt;
			boolean healthy = Double.isFinite(mspt) && mspt <= HEALTHY_MSPT;
			boolean unhealthy = Double.isFinite(mspt) && mspt >= UNHEALTHY_MSPT;
			boolean critical = Double.isFinite(mspt) && mspt >= CRITICAL_MSPT;
			updateStreakMarkers(nowTick, healthy, unhealthy);

			if (probing && unhealthy) {
				failProbe(nowTick, min, max);
			} else if (probing && nowTick - probeStartTick >= PROBE_OBSERVE_TICKS) {
				succeedProbe(nowTick);
			}

			if (safeFloorInterval > min
				&& healthy
				&& lastSafeFloorRaiseTick != Long.MIN_VALUE
				&& nowTick - lastSafeFloorRaiseTick >= SAFE_FLOOR_RELAX_TICKS
				&& nowTick >= nextAllowedDownscaleTick) {
				safeFloorInterval = Math.max(min, safeFloorInterval - 1L);
				lastSafeFloorRaiseTick = nowTick;
			}

			long targetInterval = currentInterval;
			if (Double.isFinite(mspt)) {
				targetInterval = calculateMsptTarget(mspt, min, max);
			}

			if (critical && currentInterval < max) {
				long increased = Math.min(max, Math.max(targetInterval, currentInterval + 2L));
				applyIntervalChange(increased, nowTick);
			} else if (unhealthy
				&& hasStayedUnhealthy(nowTick)
				&& nowTick >= nextAllowedChangeTick
				&& currentInterval < max) {
				long increased = Math.min(max, Math.max(targetInterval, currentInterval + 1L));
				applyIntervalChange(increased, nowTick);
			} else if (healthy
				&& !probing
				&& nowTick >= nextAllowedChangeTick
				&& nowTick >= nextAllowedDownscaleTick
				&& hasStayedHealthy(nowTick)
				&& currentInterval > safeFloorInterval) {
				long probeCandidate = Math.max(safeFloorInterval, currentInterval - 1L);
				startProbe(probeCandidate, nowTick);
			}

			lastEvaluationTick = nowTick;
			return clamp(currentInterval, safeFloorInterval, max);
		}

		private void normalizeRange(long nowTick, long min, long max) {
			currentInterval = clamp(currentInterval, min, max);
			safeFloorInterval = clamp(safeFloorInterval, min, max);
			if (nextAllowedChangeTick == Long.MIN_VALUE) {
				nextAllowedChangeTick = nowTick;
			}
			if (nextAllowedDownscaleTick == Long.MIN_VALUE) {
				nextAllowedDownscaleTick = nowTick;
			}
		}

		private void resetForClockRollback(long nowTick, long min, long max) {
			currentInterval = clamp(currentInterval, min, max);
			safeFloorInterval = clamp(safeFloorInterval, min, max);
			nextAllowedChangeTick = nowTick;
			nextAllowedDownscaleTick = nowTick;
			lastHealthyTick = Long.MIN_VALUE;
			lastUnhealthyTick = Long.MIN_VALUE;
			lastSafeFloorRaiseTick = Long.MIN_VALUE;
			probing = false;
			probeStartTick = Long.MIN_VALUE;
			probeInterval = currentInterval;
		}

		private void updateStreakMarkers(long nowTick, boolean healthy, boolean unhealthy) {
			if (healthy) {
				if (lastHealthyTick == Long.MIN_VALUE) {
					lastHealthyTick = nowTick;
				}
				lastUnhealthyTick = Long.MIN_VALUE;
				return;
			}
			if (unhealthy) {
				if (lastUnhealthyTick == Long.MIN_VALUE) {
					lastUnhealthyTick = nowTick;
				}
				lastHealthyTick = Long.MIN_VALUE;
				return;
			}
			lastHealthyTick = Long.MIN_VALUE;
			lastUnhealthyTick = Long.MIN_VALUE;
		}

		private boolean hasStayedHealthy(long nowTick) {
			return lastHealthyTick != Long.MIN_VALUE && nowTick - lastHealthyTick >= HEALTHY_STABLE_TICKS_TO_PROBE;
		}

		private boolean hasStayedUnhealthy(long nowTick) {
			return lastUnhealthyTick != Long.MIN_VALUE && nowTick - lastUnhealthyTick >= UNHEALTHY_STABLE_TICKS_TO_SCALE_UP;
		}

		private void startProbe(long probeCandidate, long nowTick) {
			probing = true;
			probeStartTick = nowTick;
			probeInterval = probeCandidate;
			currentInterval = probeCandidate;
			nextAllowedChangeTick = nowTick + MIN_CHANGE_COOLDOWN_TICKS;
		}

		private void failProbe(long nowTick, long min, long max) {
			probing = false;
			probeStartTick = Long.MIN_VALUE;
			long lockedFloor = Math.max(min, probeInterval + 1L);
			if (lockedFloor > safeFloorInterval) {
				safeFloorInterval = lockedFloor;
				lastSafeFloorRaiseTick = nowTick;
			}
			currentInterval = clamp(Math.max(currentInterval, safeFloorInterval), min, max);
			nextAllowedChangeTick = nowTick + MIN_CHANGE_COOLDOWN_TICKS;
			nextAllowedDownscaleTick = nowTick + PROBE_FAIL_COOLDOWN_TICKS;
		}

		private void succeedProbe(long nowTick) {
			probing = false;
			probeStartTick = Long.MIN_VALUE;
			currentInterval = Math.max(safeFloorInterval, probeInterval);
			nextAllowedChangeTick = nowTick + MIN_CHANGE_COOLDOWN_TICKS;
			nextAllowedDownscaleTick = nowTick + PROBE_SUCCESS_COOLDOWN_TICKS;
		}

		private void applyIntervalChange(long nextInterval, long nowTick) {
			currentInterval = Math.max(safeFloorInterval, nextInterval);
			nextAllowedChangeTick = nowTick + MIN_CHANGE_COOLDOWN_TICKS;
		}
	}

	private static long calculateMsptTarget(double mspt, long min, long max) {
		if (mspt <= LOW_LOAD_MSPT) {
			return min;
		}
		if (mspt >= HIGH_LOAD_MSPT) {
			return max;
		}
		double ratio = (mspt - LOW_LOAD_MSPT) / (HIGH_LOAD_MSPT - LOW_LOAD_MSPT);
		long span = max - min;
		return min + Math.round(span * ratio);
	}

	private static float resolveAverageMspt(MinecraftServer server) {
		double directMspt = invokeNumericNoArgs(server, "getAverageTickTime");
		if (Double.isFinite(directMspt) && directMspt > 0.0d) {
			return (float) directMspt;
		}

		double nanosPerTick = invokeNumericNoArgs(server, "getAverageNanosPerTick");
		if (Double.isFinite(nanosPerTick) && nanosPerTick > 0.0d) {
			return (float) (nanosPerTick / 1_000_000.0d);
		}

		double tickTimesAverage = averageOfLongArray(invokeLongArrayNoArgs(server, "getTickTimesNanos"));
		if (Double.isFinite(tickTimesAverage) && tickTimesAverage > 0.0d) {
			return (float) (tickTimesAverage / 1_000_000.0d);
		}

		double tickTimesMsAverage = averageOfLongArray(invokeLongArrayNoArgs(server, "getTickTimes"));
		if (Double.isFinite(tickTimesMsAverage) && tickTimesMsAverage > 0.0d) {
			return (float) tickTimesMsAverage;
		}

		return 0.0f;
	}

	private static double invokeNumericNoArgs(Object target, String methodName) {
		if (target == null || methodName == null || methodName.isBlank()) {
			return Double.NaN;
		}
		try {
			Method method = target.getClass().getMethod(methodName);
			Object value = method.invoke(target);
			if (value instanceof Number number) {
				return number.doubleValue();
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return Double.NaN;
	}

	private static long[] invokeLongArrayNoArgs(Object target, String methodName) {
		if (target == null || methodName == null || methodName.isBlank()) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(methodName);
			Object value = method.invoke(target);
			if (value instanceof long[] array) {
				return array;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return null;
	}

	private static double averageOfLongArray(long[] values) {
		if (values == null || values.length == 0) {
			return Double.NaN;
		}
		double sum = 0.0d;
		int count = 0;
		for (long value : values) {
			if (value <= 0L) {
				continue;
			}
			sum += value;
			count++;
		}
		if (count == 0) {
			return Double.NaN;
		}
		return sum / count;
	}
}


