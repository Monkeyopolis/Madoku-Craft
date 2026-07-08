package madoku.craft.api.time;

import madoku.craft.api.debug.MadokuDebugManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.lang.reflect.Method;

public final class SleepManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(SleepManager.class);
	private static final String DEBUG_SUB_SYSTEM = "sleep-manager";
	private static final double SLEEP_SPEED_MULTIPLIER = 100.0D;

	private static double fractionalCarry = 0.0D;

	private SleepManager() {
	}

	public static void reset() {
		fractionalCarry = 0.0D;
		emitSleepDebug("reset", builder -> builder
			.subject("reset")
			.field("carry", 0.0D));
	}

	public static boolean isEnabled() {
		return TimeConfigManager.isSleepEnabled();
	}

	public static boolean shouldAllowResettingTime(Player player) {
		boolean allowed = !isForwardTimeActive();
		emitSleepDebug("shouldAllowResettingTime", builder -> builder
			.subject(player == null ? "unknown" : "player")
			.field("allowed", allowed)
			.field("day-cycle-enabled", MadokuTimeManager.isEnabled())
			.field("sleep-enabled", TimeConfigManager.isSleepEnabled())
			.field("forward-time-enabled", TimeConfigManager.isForwardTimeEnabled()));
		return allowed;
	}

	public static long getTickIncrement(MinecraftServer server) {
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
		emitSleepDebug("getTickIncrement", builder -> builder
			.subject("forward-time")
			.field("players", totalPlayers)
			.field("sleeping", sleepingPlayers)
			.field("speed-multiplier", resolvedSpeedMultiplier)
			.field("tick-increment", result));
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

	public static void onWorldTimeAdvanced(MinecraftServer server, long absoluteDayTime) {
		if (server == null || !isForwardTimeActive()) {
			return;
		}
		if (!MadokuTimeManager.isDaytime(absoluteDayTime)) {
			return;
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
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
			emitSleepDebug("onWorldTimeAdvanced", builder -> builder
				.subject("wake")
				.field("daytime", absoluteDayTime)
				.field("cleared-weather", TimeConfigManager.shouldClearWeather()));
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
			Object levelData = level.getLevelData();
			if (levelData == null) {
				return;
			}
			invokeWeatherSetter(levelData, "setRaining", boolean.class, false);
			invokeWeatherSetter(levelData, "setThundering", boolean.class, false);
			invokeWeatherSetter(levelData, "setRainTime", int.class, 0);
			invokeWeatherSetter(levelData, "setThunderTime", int.class, 0);
		} catch (RuntimeException exception) {
			LOGGER.warn("Failed to clear weather after sleep forward-time.", exception);
		}
	}

	private static void invokeWeatherSetter(Object target, String methodName, Class<?> parameterType, Object value) {
		if (target == null || methodName == null || methodName.isBlank()) {
			return;
		}
		try {
			Method method = target.getClass().getMethod(methodName, parameterType);
			method.invoke(target, value);
		} catch (ReflectiveOperationException ignored) {
			// Some mappings expose weather state through different accessors.
		}
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

	private static void emitSleepDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		String entry = MadokuDebugManager.resolveCallerMethodName(1);
		if (!MadokuDebugManager.shouldEmit("api", "time-manager", DEBUG_SUB_SYSTEM, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "api", "time-manager", DEBUG_SUB_SYSTEM, entry)
			.side(MadokuDebugManager.Side.SERVER);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}
}
