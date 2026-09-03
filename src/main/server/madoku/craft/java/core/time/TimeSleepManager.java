package madoku.craft.java.core.time;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class TimeSleepManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeSleepManager.class);
	private static final double SLEEP_SPEED_MULTIPLIER = 100.0D;
	private static final long MINECRAFT_TICKS_PER_CYCLE = 24000L;
	private static final int MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES = 6 * 60;
	private static final int MINUTES_PER_DAY = 24 * 60;

	private static double fractionalCarry = 0.0D;
	private static volatile long cachedTickIncrement = 1L;
	private static volatile long wakeTargetWorldTime = -1L;

	private TimeSleepManager() {
	}

	public static void reset() {
		fractionalCarry = 0.0D;
		cachedTickIncrement = 1L;
		wakeTargetWorldTime = -1L;
	}

	public static boolean isEnabled() {
		return TimeConfigManager.isSleepEnabled();
	}

	public static boolean shouldAllowResettingTime(Player player) {
		boolean allowed = !isForwardTimeActive();
		return allowed;
	}

	public static long refreshTickIncrement(MinecraftServer server) {
		long tickIncrement = resolveTickIncrement(server);
		cachedTickIncrement = tickIncrement;
		return tickIncrement;
	}

	public static long getCachedTickIncrement() {
		return Math.max(1L, cachedTickIncrement);
	}

	public static long getTickIncrement(MinecraftServer server) {
		return refreshTickIncrement(server);
	}

	private static long resolveTickIncrement(MinecraftServer server) {
		if (server == null || !isForwardTimeActive()) {
			resetCarry();
			return 1L;
		}

		int totalPlayers = server.getPlayerList().getPlayerCount();
		if (totalPlayers <= 0) {
			resetCarry();
			return 1L;
		}

		int sleepingPlayers = countSleepingPlayers(server);
		if (sleepingPlayers <= 0) {
			resetCarry();
			return 1L;
		}

		double speedMultiplier = (SLEEP_SPEED_MULTIPLIER * sleepingPlayers) / totalPlayers;
		if (speedMultiplier < 1.0D) {
			speedMultiplier = 1.0D;
		}
		double resolvedSpeedMultiplier = speedMultiplier;

		double totalTicks = resolvedSpeedMultiplier + fractionalCarry;
		long wholeTicks = (long) Math.floor(totalTicks);
		fractionalCarry = totalTicks - wholeTicks;
		long result = Math.max(1L, wholeTicks);
		return result;
	}

	public static boolean canStartSleeping(Player player) {
		if (player == null) {
			return false;
		}
		if (!isSleepTransitionActive()) {
			return true;
		}
		return MadokuTimeManager.isSleepTime(player.level().getOverworldClockTime());
	}

	public static boolean shouldAllowBedSleepByTime(BedRule bedRule, Level level, Player player) {
		if (bedRule == null || level == null) {
			return false;
		}
		if (!isSleepTransitionActive()) {
			return bedRule.canSleep(level);
		}

		if (TimeConfigManager.isThunderstormBypassEnabled() && level.isThundering()) {
			return true;
		}

		if (!bedRule.canSetSpawn(level)) {
			return bedRule.canSleep(level);
		}
		return canStartSleeping(player);
	}

	public static boolean shouldKeepSleepingWhileForwarding(BedRule bedRule, Level level, Player player) {
		if (bedRule == null || level == null) {
			return false;
		}
		if (!isForwardTimeActive()) {
			return bedRule.canSleep(level);
		}
		if (player != null && player.isSleeping()) {
			return true;
		}
		return bedRule.canSleep(level);
	}

	public static void onSleepStarted(ServerPlayer player) {
		if (player == null || !isForwardTimeActive()) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		long currentTime = level.getOverworldClockTime();
		long targetWakeTime = resolveNextMorningWakeTime(currentTime);
		if (wakeTargetWorldTime < 0L || currentTime < wakeTargetWorldTime) {
			wakeTargetWorldTime = targetWakeTime;
		} else {
			wakeTargetWorldTime = Math.max(wakeTargetWorldTime, targetWakeTime);
		}

		LOGGER.info(
			"Madoku sleep-forward sleep started: worldTime={}, wakeTarget={}",
			currentTime,
			wakeTargetWorldTime
		);
	}

	public static void onWorldTimeAdvanced(MinecraftServer server, long absoluteDayTime) {
		if (server == null || !isForwardTimeActive()) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		if (MadokuTimeManager.getWorldTimeDelta() < 0L) {
			wakeTargetWorldTime = resolveNextMorningWakeTime(absoluteDayTime);
		}
		long wakeTarget = wakeTargetWorldTime;
		if (wakeTarget < 0L) {
			wakeTarget = resolveNextMorningWakeTime(absoluteDayTime);
			wakeTargetWorldTime = wakeTarget;
		}
		if (absoluteDayTime < wakeTarget) {
			return;
		}

		boolean wokeSleepingPlayer = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != null && player.isSleeping()) {
				player.stopSleepInBed(true, true);
				wokeSleepingPlayer = true;
			}
		}

		if (wokeSleepingPlayer && TimeConfigManager.shouldClearWeather()) {
			clearWeather(overworld);
		}

		if (wokeSleepingPlayer) {
			wakeTargetWorldTime = -1L;
			final long finalWakeTarget = wakeTarget;
			LOGGER.info(
				"Madoku sleep-forward wake: worldTime={}, wakeTarget={}, clearWeather={}",
				absoluteDayTime,
				finalWakeTarget,
				TimeConfigManager.shouldClearWeather()
			);
		}
	}

	private static int countSleepingPlayers(MinecraftServer server) {
		int sleepingPlayers = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != null && player.isSleeping()) {
				sleepingPlayers++;
			}
		}
		return sleepingPlayers;
	}

	private static void clearWeather(ServerLevel level) {
		try {
			MinecraftServer server = level.getServer();
			invokeWeatherSetter(
				server,
				"setWeatherParameters",
				new Class<?>[] { int.class, int.class, boolean.class, boolean.class },
				new Object[] { 0, 0, false, false }
			);
			WeatherData weatherData = level.getWeatherData();
			if (weatherData != null) {
				weatherData.setClearWeatherTime(0);
				weatherData.setThundering(false);
				weatherData.setThunderTime(0);
				weatherData.setRaining(false);
				weatherData.setRainTime(0);
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Failed to clear weather after sleep forward-time.", exception);
		}
	}

	private static long resolveNextMorningWakeTime(long absoluteDayTime) {
		long currentTime = Math.max(0L, absoluteDayTime);
		int morningMinutes = Math.floorMod(
			TimeConfigManager.getMorningMinutes() * 60 - MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES,
			MINUTES_PER_DAY);
		long morningTick = (morningMinutes * MINECRAFT_TICKS_PER_CYCLE) / MINUTES_PER_DAY;
		long cycleStart = Math.floorDiv(currentTime, MINECRAFT_TICKS_PER_CYCLE) * MINECRAFT_TICKS_PER_CYCLE;
		long wakeTarget = cycleStart + morningTick;
		return wakeTarget > currentTime ? wakeTarget : wakeTarget + MINECRAFT_TICKS_PER_CYCLE;
	}

	private static boolean invokeWeatherSetter(Object target, String methodName, Class<?>[] parameterTypes, Object[] values) {
		if (target == null || methodName == null || methodName.isBlank()) {
			return false;
		}
		Method method = findWeatherSetter(target.getClass(), methodName, parameterTypes);
		if (method == null) {
			return false;
		}
		try {
			if (!method.canAccess(target)) {
				method.setAccessible(true);
			}
			method.invoke(target, values);
			return true;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// If a setter is unavailable on this mapping/runtime, we keep going.
			return false;
		}
	}

	private static Method findWeatherSetter(Class<?> type, String methodName, Class<?>... parameterTypes) {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredMethod(methodName, parameterTypes);
			} catch (NoSuchMethodException ignored) {
				try {
					return current.getMethod(methodName, parameterTypes);
				} catch (NoSuchMethodException ignoredAgain) {
					current = current.getSuperclass();
				}
			}
		}
		return null;
	}

	private static void resetCarry() {
		fractionalCarry = 0.0D;
	}

	private static boolean isForwardTimeActive() {
		return MadokuTimeManager.isEnabled()
			&& TimeConfigManager.isSleepEnabled()
			&& TimeConfigManager.isForwardTimeEnabled();
	}

	private static boolean isSleepTransitionActive() {
		return MadokuTimeManager.isEnabled()
			&& TimeConfigManager.isSleepEnabled()
			&& TimeConfigManager.isSleepTimeTransitionsEnabled();
	}

}
