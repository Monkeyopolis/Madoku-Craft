package madoku.craft.pet;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Owns the managed-pet component boundary: identity, sound state, and entity checks. */
public final class PetComponentsManager {
	private PetComponentsManager() {
	}

	public static void initialize() {
	}

	public static boolean isManaged(Entity entity) {
		return MadokuPetManager.isManagedPet(entity) || (entity != null && PetPayloadManager.hasSoundState(entity.getUUID()));
	}

	public static float soundVolume(Entity entity, float baseVolume) {
		return MadokuPetManager.soundVolume(entity, baseVolume);
	}

	public static int ambientSoundInterval(Entity entity, int baseInterval) {
		return MadokuPetManager.ambientSoundInterval(entity, baseInterval);
	}

	public static void clear() {
		PetPayloadManager.clearSoundState();
	}

	public static boolean isMob(Entity entity) {
		return entity instanceof Mob && isManaged(entity);
	}
}
