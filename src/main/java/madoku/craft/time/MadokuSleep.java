package madoku.craft.time;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class MadokuSleep {
	private static final double SLEEP_SPEED_MULTIPLIER = 100.0D;
	private static double fractionalCarry = 0.0D;

	private MadokuSleep() {
	}

	public static void reset() {
		fractionalCarry = 0.0D;
	}

	public static long getTickIncrement(MinecraftServer server) {
		if (!MadokuTime.isEnabled()) {
			reset();
			return 1L;
		}

		wakeSleepingPlayers(server);

		int totalPlayers = server.getPlayerList().getPlayerCount();
		if (totalPlayers <= 0) {
			reset();
			return 1L;
		}

		int sleepingPlayers = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSleeping()) {
				sleepingPlayers++;
			}
		}

		if (sleepingPlayers <= 0) {
			reset();
			return 1L;
		}

		double speedMultiplier = (SLEEP_SPEED_MULTIPLIER * sleepingPlayers) / totalPlayers;
		if (speedMultiplier < 1.0D) {
			speedMultiplier = 1.0D;
		}

		double totalTicks = speedMultiplier + fractionalCarry;
		long wholeTicks = (long) Math.floor(totalTicks);
		fractionalCarry = totalTicks - wholeTicks;
		return Math.max(1L, wholeTicks);
	}

	public static boolean canStartSleeping(Player player) {
		if (player == null) {
			return false;
		}
		if (!MadokuTime.isEnabled()) {
			return true;
		}
		return MadokuTime.isSleepTime(player.level().getOverworldClockTime());
	}

	public static boolean shouldAllowBedSleepByTime(BedRule bedRule, Level level, Player player) {
		if (!MadokuTime.isEnabled()) {
			return bedRule.canSleep(level);
		}
		if (!bedRule.canSetSpawn(level)) {
			return bedRule.canSleep(level);
		}
		return canStartSleeping(player);
	}

	private static void wakeSleepingPlayers(MinecraftServer server) {
		if (!MadokuTime.isDaytime(server.overworld().getOverworldClockTime())) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSleeping()) {
				player.stopSleepInBed(true, true);
			}
		}
	}
}
