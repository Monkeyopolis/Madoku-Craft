package madoku.craft.java.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Built-in SavedData provider used by Madoku's data APIs. */
public final class MadokuSavedDataProvider implements SavedDataProvider {
	private static final SavedDataType<MadokuSavedData> WORLD_TYPE = MadokuSavedData.type(id("world"));
	private static final SavedDataType<MadokuSavedData> JSON_WORLD_TYPE = MadokuSavedData.type(id("json-world"));

	@Override public void reset() { }
	@Override public MadokuSavedData world(MinecraftServer server) { return server == null ? null : get(server.overworld().getDataStorage(), WORLD_TYPE); }
	@Override public MadokuSavedData jsonWorld(MinecraftServer server) { return server == null ? null : get(server.overworld().getDataStorage(), JSON_WORLD_TYPE); }

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
		return storage.computeIfAbsent(type);
	}

	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("madoku-craft", path); }
}
