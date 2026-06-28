package madoku.craft.pet;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PetSoundState {
	private static final Map<UUID, String> ITEM_ID_BY_PET_ID = new ConcurrentHashMap<>();

	private PetSoundState() {
	}

	public static void set(UUID petId, String itemId) {
		if (petId == null) {
			return;
		}
		String normalizedItemId = itemId == null ? "" : itemId.trim().toLowerCase();
		if (normalizedItemId.isEmpty()) {
			ITEM_ID_BY_PET_ID.remove(petId);
			return;
		}
		ITEM_ID_BY_PET_ID.put(petId, normalizedItemId);
	}

	public static void remove(UUID petId) {
		if (petId != null) {
			ITEM_ID_BY_PET_ID.remove(petId);
		}
	}

	public static String getItemId(Entity entity) {
		if (entity == null) {
			return "";
		}
		return getItemId(entity.getUUID());
	}

	public static String getItemId(UUID petId) {
		if (petId == null) {
			return "";
		}
		return ITEM_ID_BY_PET_ID.getOrDefault(petId, "");
	}

	public static boolean isManaged(Entity entity) {
		return entity != null && ITEM_ID_BY_PET_ID.containsKey(entity.getUUID());
	}

	public static void clear() {
		ITEM_ID_BY_PET_ID.clear();
	}
}

