package madoku.craft.mixin;

import madoku.craft.loot.system.MadokuLootTableSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainer.class)
public interface RandomizableContainerLootMixin {
	@Inject(
		method = "unpackLootTable(Lnet/minecraft/world/entity/player/Player;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$applyManagedLootTable(Player player, CallbackInfo ci) {
		RandomizableContainer container = (RandomizableContainer) (Object) this;
		ResourceKey<LootTable> lootTableKey = container.getLootTable();
		if (lootTableKey == null) {
			return;
		}
		if (!(container.getLevel() instanceof ServerLevel level)) {
			return;
		}

		ServerPlayer serverPlayer = player instanceof ServerPlayer resolved ? resolved : null;
		long lootTableSeed = container.getLootTableSeed();
		container.setLootTable((ResourceKey<LootTable>) null);
		container.setLootTableSeed(0L);

		boolean handled = false;
		try {
			handled = MadokuLootTableSystem.applyManagedLootTable(
				container,
				lootTableKey,
				lootTableSeed,
				level,
				serverPlayer
			);
		} finally {
			if (!handled) {
				container.setLootTable(lootTableKey);
				container.setLootTableSeed(lootTableSeed);
			}
		}
		if (!handled) {
			return;
		}

		ci.cancel();
	}
}
