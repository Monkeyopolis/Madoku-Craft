package madoku.craft.java.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Public contract for the Madoku Pets subsystem lifecycle and runtime orchestration. */
public final class PetAPIManager {
	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "MadokuPets";
	public static final int MAX_ABILITY_COOLDOWNS_PER_PET = 3;
	public static final String PET_ABILITY_NONE = "none";
	public static final String PET_ABILITY_RANGED_HOMING_ARROW = "ranged_homing_arrow";
	public static final String PET_ABILITY_WEB_PROJECTILE = "web_projectile";
	public static final String PET_ABILITY_EXPLOSIVE_PROJECTILE = "explosive_projectile";
	public static final String PET_ABILITY_EGG_PROJECTILE = "egg_projectile";
	public static final String PET_ABILITY_PLAYER_DAMAGE_BONUS = "player_damage_bonus";
	public static final String PET_ABILITY_FALL_DAMAGE_REDUCTION = "fall_damage_reduction";
	public static final String PET_ABILITY_MAX_HEALTH_BONUS = "max_health_bonus";
	public static final String PET_ABILITY_ARMOR_BONUS = "armor_bonus";
	public static final String PET_ABILITY_DAMAGE_BLOCK = "damage_block";
	public static final String PET_ABILITY_HEALTH_REGENERATION = "health_regeneration";
	public static final String PET_ABILITY_MOB_SCAN = "mob_scan";
	public static final String PET_ABILITY_BEE_SWARM = "bee_swarm";

	private static final PetProvider UNAVAILABLE_PROVIDER = new PetProvider() { };
	private static volatile PetProvider provider = UNAVAILABLE_PROVIDER;

	private PetAPIManager() { }

	public static void registerProvider(PetProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { provider.onServerTick(server); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static boolean isManagedPet(Entity entity) { return provider.isManagedPet(entity); }
	public static long managedPetSteeringInterval(MinecraftServer server) { return provider.managedPetSteeringInterval(server); }
	public static Vec3 managedPetMovementTarget(Mob pet) { return provider.managedPetMovementTarget(pet); }
	public static double managedPetMovementSpeed(Mob pet, double fallbackSpeed) { return provider.managedPetMovementSpeed(pet, fallbackSpeed); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static int maxPetLevel() { return provider.maxPetLevel(); }
	public static boolean areEntitiesEnabled() { return provider.areEntitiesEnabled(); }
	public static void handlePlayerTeleport(ServerPlayer player) { provider.handlePlayerTeleport(player); }
}
