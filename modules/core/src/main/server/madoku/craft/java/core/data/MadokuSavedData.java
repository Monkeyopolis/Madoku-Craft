package madoku.craft.java.core.data;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Raw NBT-backed SavedData used by Madoku's data APIs. */
public final class MadokuSavedData extends SavedData {
	public static final Codec<MadokuSavedData> CODEC = CompoundTag.CODEC.xmap(
		MadokuSavedData::new,
		MadokuSavedData::copyData
	);

	private CompoundTag data;

	public MadokuSavedData() {
		this(new CompoundTag());
	}

	private MadokuSavedData(CompoundTag data) {
		this.data = data == null ? new CompoundTag() : data.copy();
	}

	public CompoundTag copyData() {
		return data.copy();
	}

	public void replaceData(CompoundTag data) {
		CompoundTag replacement = data == null ? new CompoundTag() : data.copy();
		if (this.data.equals(replacement)) return;
		this.data = replacement;
		setDirty();
	}

	public static net.minecraft.world.level.saveddata.SavedDataType<MadokuSavedData> type(
		net.minecraft.resources.Identifier id) {
		return new net.minecraft.world.level.saveddata.SavedDataType<>(
			id,
			MadokuSavedData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
		);
	}
}
