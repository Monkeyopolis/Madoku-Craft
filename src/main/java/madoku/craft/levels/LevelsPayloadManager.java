package madoku.craft.levels;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Owns Madoku Levels network payloads and builds synchronized player snapshots. */
public final class LevelsPayloadManager {
	private LevelsPayloadManager() { }

	public static void initialize() {
		ServerPlayNetworking.registerGlobalReceiver(LevelsPayloadAPIManager.LevelUpPayload.TYPE, (payload, context) ->
			MadokuLevelsManager.handleLevelUpRequest(context.player(), payload.statId()));
	}

	public static void reset() { }

	public static LevelsPayloadAPIManager.Payload createPayload(ServerPlayer player) {
		LevelsPlayerManager.PlayerState state = LevelsPlayerManager.state(player);
		return new LevelsPayloadAPIManager.Payload(
			player.getName().getString(),
			Math.min(state.level(), LevelsPlayerManager.maxPlayerLevel()),
			state.level() >= LevelsPlayerManager.maxPlayerLevel() ? 0 : state.currentXp(),
			LevelsPlayerManager.requiredXpForLevel(state.level()),
			state.availablePoints(),
			encodeMaxStatLevels(),
			MadokuLevelsManager.useAttributesContainer(),
			LevelStat.encodeVisibleStats(LevelStat.visibleStats()),
			LevelStat.encodeLevels(stateLevels(state))
		);
	}

	private static java.util.Map<LevelStat, Integer> stateLevels(LevelsPlayerManager.PlayerState state) {
		java.util.EnumMap<LevelStat, Integer> levels = LevelStat.createDefaultLevels();
		for (LevelStat stat : LevelStat.values()) levels.put(stat, state.statLevel(stat));
		return levels;
	}

	private static String encodeMaxStatLevels() {
		StringBuilder builder = new StringBuilder();
		for (LevelStat stat : LevelStat.values()) {
			if (builder.length() > 0) builder.append(';');
			builder.append(stat.id()).append('=').append(stat.maxLevel());
		}
		return builder.toString();
	}

}
