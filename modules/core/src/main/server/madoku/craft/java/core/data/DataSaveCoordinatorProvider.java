package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Provider contract for coordinated persisted-state saves. */
public interface DataSaveCoordinatorProvider {
	default void initialize() { }
	default void reset() { }
	default void autosave(MinecraftServer server) { }
	default void saveAndWait(MinecraftServer server) { }
}
