package madoku.craft.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Owns the runtime pet-entity lifecycle and equipped pet slots. */
public final class PetEntitiesManager {
	public static final int SLOT_COUNT = MadokuPetManager.SLOT_COUNT;
	public static final int FIRST_SLOT_INDEX = MadokuPetManager.FIRST_SLOT_INDEX;
	public static final int SLOT_X = MadokuPetManager.SLOT_X;
	public static final int[] SLOT_YS = MadokuPetManager.SLOT_YS;

	private PetEntitiesManager() {
	}

	public static void initialize() {
	}

	public static void reset() {
		MadokuPetManager.reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		MadokuPetManager.onServerStarted(server);
	}

	public static void onInventoryChanged(ServerPlayer player) {
		MadokuPetManager.onPetInventoryChanged(player);
	}

	public static boolean isManaged(Entity entity) {
		return MadokuPetManager.isManagedPet(entity);
	}

	public static boolean isValid(ItemStack stack) {
		return MadokuPetManager.isValidPlayerEntity(stack);
	}

	public static void dropAll(ServerPlayer player) {
		MadokuPetManager.dropAll(player);
	}

	public static int count(Player player) {
		return MadokuPetManager.countPets(player);
	}

	public static Vec3 movementTarget(Mob pet) {
		return MadokuPetManager.managedPetMovementTarget(pet);
	}

	public static double movementSpeed(Mob pet, double fallback) {
		return MadokuPetManager.managedPetMovementSpeed(pet, fallback);
	}
}
