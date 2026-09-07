package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** A feature-owned participant in the coordinated persistence cycle. */
public interface DataSaveParticipant {
	/** Stable identifier used to replace duplicate registrations safely. */
	String id();

	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
}
