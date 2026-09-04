package madoku.craft.java.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Provider contract implemented by the module that owns Madoku Pets. */
public interface PetProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerTick(MinecraftServer server) { }
	default void loadPersistedData(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default boolean isManagedPet(Entity entity) { return false; }
	default long managedPetSteeringInterval(MinecraftServer server) { return 1L; }
	default Vec3 managedPetMovementTarget(Mob pet) { return pet == null ? Vec3.ZERO : pet.position(); }
	default double managedPetMovementSpeed(Mob pet, double fallbackSpeed) { return fallbackSpeed; }
	default boolean isEnabled() { return false; }
	default int maxPetLevel() { return 1; }
	default boolean areEntitiesEnabled() { return false; }
	default void handlePlayerTeleport(ServerPlayer player) { }
}
