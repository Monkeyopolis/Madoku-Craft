package madoku.craft.java.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Built-in provider backed by the Madoku Pets runtime orchestrator. */
public final class MadokuPetProvider implements PetProvider {
	@Override public void initialize() { MadokuPetManager.initialize(); }
	@Override public void reset() { MadokuPetManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuPetManager.onServerStarted(server); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuPetManager.loadPersistedData(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { MadokuPetManager.autosavePersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { MadokuPetManager.savePersistedData(server); }
	@Override public boolean isManagedPet(Entity entity) { return MadokuPetManager.isManagedPet(entity); }
	@Override public long managedPetSteeringInterval(MinecraftServer server) { return MadokuPetManager.managedPetSteeringInterval(server); }
	@Override public Vec3 managedPetMovementTarget(Mob pet) { return MadokuPetManager.managedPetMovementTarget(pet); }
	@Override public double managedPetMovementSpeed(Mob pet, double fallbackSpeed) { return MadokuPetManager.managedPetMovementSpeed(pet, fallbackSpeed); }
	@Override public boolean isEnabled() { return MadokuPetManager.isEnabled(); }
	@Override public int maxPetLevel() { return MadokuPetManager.maxPetLevel(); }
	@Override public boolean areEntitiesEnabled() { return MadokuPetManager.areEntitiesEnabled(); }
	@Override public void handlePlayerTeleport(ServerPlayer player) { MadokuPetManager.handlePlayerTeleport(player); }
}
