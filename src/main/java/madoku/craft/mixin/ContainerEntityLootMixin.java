package madoku.craft.mixin;

import madoku.craft.loot.system.MadokuLootTableManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerEntity.class)
public interface ContainerEntityLootMixin {
	@Inject(
		method = "unpackChestVehicleLootTable(Lnet/minecraft/world/entity/player/Player;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$applyManagedVehicleLootTable(Player player, CallbackInfo ci) {
		ContainerEntity container = (ContainerEntity) (Object) this;
		ResourceKey<LootTable> lootTableKey = container.getContainerLootTable();
		if (lootTableKey == null) {
			return;
		}
		if (!(container.level() instanceof ServerLevel level)) {
			return;
		}

		ServerPlayer serverPlayer = player instanceof ServerPlayer resolved ? resolved : null;
		long lootTableSeed = container.getContainerLootTableSeed();
		container.setContainerLootTable((ResourceKey<LootTable>) null);
		container.setContainerLootTableSeed(0L);

		boolean handled = false;
		try {
			handled = MadokuLootTableManager.applyManagedLootTable(
				container,
				lootTableKey,
				lootTableSeed,
				level,
				serverPlayer
			);
		} finally {
			if (!handled) {
				container.setContainerLootTable(lootTableKey);
				container.setContainerLootTableSeed(lootTableSeed);
			}
		}
		if (!handled) {
			return;
		}

		ci.cancel();
	}
}


