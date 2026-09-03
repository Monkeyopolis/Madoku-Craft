package madoku.craft.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Built-in provider backed by the Madoku pet component implementation. */
public final class MadokuPetComponentsProvider implements PetComponentsProvider {
	@Override public void initialize() { PetComponentsManager.initialize(); }
	@Override public boolean isManaged(Entity entity) { return PetComponentsManager.isManaged(entity); }
	@Override public boolean isMob(Entity entity) { return PetComponentsManager.isMob(entity); }
	@Override public void dropAll(ServerPlayer player) { PetComponentsManager.dropAll(player); }
	@Override public int countPets(Player player) { return PetComponentsManager.countPets(player); }
}
