package madoku.craft.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Provider contract for managed pet components. */
public interface PetComponentsProvider {
	default void initialize() { }
	default boolean isManaged(Entity entity) { return false; }
	default boolean isMob(Entity entity) { return false; }
	default void dropAll(ServerPlayer player) { }
	default int countPets(Player player) { return 0; }
}
