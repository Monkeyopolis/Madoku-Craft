package madoku.craft.java.pet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Built-in provider backed by the Madoku pet entity implementation. */
public final class MadokuPetEntitiesProvider implements PetEntitiesProvider {
	@Override public void initialize() { PetEntitiesManager.initialize(); }
	@Override public void reset() { PetEntitiesManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { PetEntitiesManager.onServerStarted(server); }
	@Override public void onInventoryChanged(ServerPlayer player) { PetEntitiesManager.onInventoryChanged(player); }
	@Override public boolean isPetItem(ItemStack stack) { return PetEntitiesManager.isPetItem(stack); }
	@Override public int petLevel(ItemStack stack) { return PetEntitiesManager.petLevel(stack); }
	@Override public void setPetLevel(ItemStack stack, int level) { PetEntitiesManager.setPetLevel(stack, level); }
	@Override public boolean isManaged(Entity entity) { return PetEntitiesManager.isManaged(entity); }
	@Override public boolean isValid(ItemStack stack) { return PetEntitiesManager.isValid(stack); }
	@Override public void dropAll(ServerPlayer player) { PetEntitiesManager.dropAll(player); }
	@Override public int count(Player player) { return PetEntitiesManager.count(player); }
	@Override public Vec3 movementTarget(Mob pet) { return PetEntitiesManager.movementTarget(pet); }
	@Override public double movementSpeed(Mob pet, double fallback) { return PetEntitiesManager.movementSpeed(pet, fallback); }
	@Override public EntityType<MadokuPetEntity> petEntityType() { return PetEntitiesManager.PET_ENTITY; }
}
