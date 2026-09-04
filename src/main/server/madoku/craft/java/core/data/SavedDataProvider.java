package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.Set;

/** Provider contract for Madoku's vanilla SavedData integration. */
public interface SavedDataProvider {
	default void initialize() { }
	default void reset() { }
	default MadokuSavedData world(MinecraftServer server) { return null; }
	default MadokuSavedData jsonWorld(MinecraftServer server) { return null; }
	default MadokuSavedData players(MinecraftServer server) { return null; }
	default MadokuSavedData chunks(ServerLevel level) { return null; }
	default Set<SavedDataStorage> storages() { return Set.of(); }
	default JsonObject toJson(CompoundTag tag) { return new JsonObject(); }
	default CompoundTag toNbt(JsonObject object) { return new CompoundTag(); }
}
