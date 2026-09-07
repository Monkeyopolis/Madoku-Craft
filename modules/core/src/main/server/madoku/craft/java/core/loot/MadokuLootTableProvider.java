package madoku.craft.java.core.loot;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/** Built-in provider backed by the Madoku loot-table implementation. */
public final class MadokuLootTableProvider implements LootTableProvider {
	@Override public void initialize() { MadokuLootTableManager.initialize(); }
	@Override public void reset() { MadokuLootTableManager.reset(); }
	@Override public boolean applyManagedLootTable(Container container, ResourceKey<LootTable> lootTableKey, long lootTableSeed, ServerLevel level, ServerPlayer player) { return MadokuLootTableManager.applyManagedLootTable(container, lootTableKey, lootTableSeed, level, player); }
	@Override public List<ItemStack> generateManagedLootForContext(LootContext lootContext) { return MadokuLootTableManager.generateManagedLootForContext(lootContext); }
}
