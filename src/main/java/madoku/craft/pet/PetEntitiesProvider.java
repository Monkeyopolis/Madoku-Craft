package madoku.craft.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Provider contract for Madoku pet items and entities. */
public interface PetEntitiesProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default void onInventoryChanged(ServerPlayer player) { }
	default boolean isPetItem(ItemStack stack) { return false; }
	default int petLevel(ItemStack stack) { return 1; }
	default void setPetLevel(ItemStack stack, int level) { }
	default boolean isManaged(Entity entity) { return false; }
	default boolean isValid(ItemStack stack) { return false; }
	default void dropAll(ServerPlayer player) { }
	default int count(Player player) { return 0; }
	default Vec3 movementTarget(Mob pet) { return pet == null ? Vec3.ZERO : pet.position(); }
	default double movementSpeed(Mob pet, double fallback) { return fallback; }
	default EntityType<MadokuPetEntity> petEntityType() { return null; }
}
