package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Provider contract for Madoku's vanilla SavedData integration. */
public interface SavedDataProvider {
	default void initialize() { }
	default void reset() { }
	default MadokuSavedData world(MinecraftServer server) { return null; }
	default MadokuSavedData jsonWorld(MinecraftServer server) { return null; }
	default JsonObject toJson(CompoundTag tag) { return new JsonObject(); }
	default CompoundTag toNbt(JsonObject object) { return new CompoundTag(); }
}
