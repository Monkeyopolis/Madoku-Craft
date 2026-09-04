package madoku.craft.java.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Built-in SavedData provider used by Madoku's data APIs. */
public final class MadokuSavedDataProvider implements SavedDataProvider {
	private static final SavedDataType<MadokuSavedData> WORLD_TYPE = MadokuSavedData.type(id("world"));
	private static final SavedDataType<MadokuSavedData> JSON_WORLD_TYPE = MadokuSavedData.type(id("json-world"));
	private static final SavedDataType<MadokuSavedData> PLAYER_TYPE = MadokuSavedData.type(id("players"));
	private static final SavedDataType<MadokuSavedData> CHUNK_TYPE = MadokuSavedData.type(id("chunks"));
	private final Set<SavedDataStorage> storages = Collections.newSetFromMap(new IdentityHashMap<>());

	@Override public void reset() { storages.clear(); }
	@Override public MadokuSavedData world(MinecraftServer server) { return server == null ? null : get(server.overworld().getDataStorage(), WORLD_TYPE); }
	@Override public MadokuSavedData jsonWorld(MinecraftServer server) { return server == null ? null : get(server.overworld().getDataStorage(), JSON_WORLD_TYPE); }
	@Override public MadokuSavedData players(MinecraftServer server) { return server == null ? null : get(server.overworld().getDataStorage(), PLAYER_TYPE); }
	@Override public MadokuSavedData chunks(ServerLevel level) { return level == null ? null : get(level.getDataStorage(), CHUNK_TYPE); }

	@Override public Set<SavedDataStorage> storages() {
		Set<SavedDataStorage> copy = Collections.newSetFromMap(new IdentityHashMap<>());
		copy.addAll(storages);
		return Collections.unmodifiableSet(copy);
	}

	@Override public JsonObject toJson(CompoundTag tag) {
		if (tag == null) return new JsonObject();
		JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
		return json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
	}

	@Override public CompoundTag toNbt(JsonObject object) {
		if (object == null) return new CompoundTag();
		var tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, object);
		return tag instanceof CompoundTag compound ? compound : new CompoundTag();
	}

	private MadokuSavedData get(SavedDataStorage storage, SavedDataType<MadokuSavedData> type) {
		storages.add(storage);
		return storage.computeIfAbsent(type);
	}

	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("madoku-craft", path); }
}
