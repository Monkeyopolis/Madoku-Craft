package madoku.craft.java.core.sync;

import net.minecraft.server.level.ServerPlayer;

/** Transport abstraction used by Core to deliver a configuration snapshot. */
@FunctionalInterface
public interface SyncConfigTransport {
	boolean send(ServerPlayer player, String configId, String snapshot);
}
