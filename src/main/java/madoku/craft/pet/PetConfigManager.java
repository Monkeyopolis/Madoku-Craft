package madoku.craft.pet;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

/** Owns the configured Madoku Pet definitions and configuration lifecycle. */
public final class PetConfigManager {
	public static final String ROOT_FOLDER = "madoku-craft";
	public static final String PET_FOLDER = "madoku-craft-pets";
	public static final String ENTITY_FOLDER = "madoku-entities";
	public static final String ABILITY_FOLDER = "madoku-abilities";

	private PetConfigManager() {
	}

	public static void initialize() {
		MadokuPetManager.reloadConfig();
	}

	public static boolean isEnabled() {
		return MadokuPetManager.isEnabled();
	}

	public static boolean areEntitiesEnabled() {
		return MadokuPetManager.areEntitiesEnabled();
	}

	public static boolean isValidPet(ItemStack stack) {
		return MadokuPetManager.isValidPlayerEntity(stack);
	}

	public static String petRarity(ItemStack stack) {
		return MadokuPetManager.petRarity(stack);
	}

	public static String normalizeKey(String value) {
		return MadokuPetManager.normalizeKey(value);
	}

	static JsonObject readObject(JsonObject source) {
		return source == null ? new JsonObject() : source.deepCopy();
	}
}
