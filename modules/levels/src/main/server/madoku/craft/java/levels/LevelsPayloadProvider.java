package madoku.craft.java.levels;

import net.minecraft.server.level.ServerPlayer;

/** Provider contract for Madoku Levels network payloads. */
public interface LevelsPayloadProvider {
	default void initialize() { }
	default void reset() { }
	default LevelsPayloadAPIManager.Payload createPayload(ServerPlayer player) { return null; }
}
