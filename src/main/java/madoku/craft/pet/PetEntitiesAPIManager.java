package madoku.craft.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Public contract for Madoku pet items and managed pet entities. */
public final class PetEntitiesAPIManager {
	public static final int SLOT_COUNT = PetAPIManager.SLOT_COUNT;
	public static final int FIRST_SLOT_INDEX = PetAPIManager.FIRST_SLOT_INDEX;
	public static final int SLOT_X = PetAPIManager.SLOT_X;
	public static final int[] SLOT_YS = PetAPIManager.SLOT_YS;
	public static final String PET_ITEM_NAMESPACE = "madoku-craft";

	private static final PetEntitiesProvider UNAVAILABLE_PROVIDER = new PetEntitiesProvider() { };
	private static volatile PetEntitiesProvider provider = UNAVAILABLE_PROVIDER;

	private PetEntitiesAPIManager() { }

	public static void registerProvider(PetEntitiesProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet entities provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onInventoryChanged(ServerPlayer player) { provider.onInventoryChanged(player); }
	public static boolean isPetItem(ItemStack stack) { return provider.isPetItem(stack); }
	public static int petLevel(ItemStack stack) { return provider.petLevel(stack); }
	public static void setPetLevel(ItemStack stack, int level) { provider.setPetLevel(stack, level); }
	public static boolean isManaged(Entity entity) { return provider.isManaged(entity); }
	public static boolean isValid(ItemStack stack) { return provider.isValid(stack); }
	public static void dropAll(ServerPlayer player) { provider.dropAll(player); }
	public static int count(Player player) { return provider.count(player); }
	public static Vec3 movementTarget(Mob pet) { return provider.movementTarget(pet); }
	public static double movementSpeed(Mob pet, double fallback) { return provider.movementSpeed(pet, fallback); }
	public static EntityType<MadokuPetEntity> petEntityType() { return provider.petEntityType(); }
}
