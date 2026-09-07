package madoku.craft.java.levels;

import net.minecraft.server.level.ServerPlayer;

/** Built-in provider for Madoku Levels network payloads. */
public final class MadokuLevelsPayloadProvider implements LevelsPayloadProvider {
	@Override public void initialize() { LevelsPayloadManager.initialize(); }
	@Override public void reset() { LevelsPayloadManager.reset(); }
	@Override public LevelsPayloadAPIManager.Payload createPayload(ServerPlayer player) { return LevelsPayloadManager.createPayload(player); }
}
