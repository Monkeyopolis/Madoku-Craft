package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Public API for accessing Madoku's vanilla SavedData stores. */
public final class MadokuSavedDataManager {
	private static final SavedDataProvider UNAVAILABLE_PROVIDER = new SavedDataProvider() { };
	private static volatile SavedDataProvider provider = UNAVAILABLE_PROVIDER;

	private MadokuSavedDataManager() { }

	public static void registerProvider(SavedDataProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("SavedData provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static MadokuSavedData world(MinecraftServer server) { return provider.world(server); }
	public static MadokuSavedData jsonWorld(MinecraftServer server) { return provider.jsonWorld(server); }
	public static JsonObject toJson(CompoundTag tag) { return provider.toJson(tag); }
	public static CompoundTag toNbt(JsonObject object) { return provider.toNbt(object); }
}
