package madoku.craft.java.core.loot;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/** Provider contract for managed loot tables. */
public interface LootTableProvider {
	default void initialize() { }
	default void reset() { }
	default boolean applyManagedLootTable(Container container, ResourceKey<LootTable> lootTableKey, long lootTableSeed, ServerLevel level, ServerPlayer player) { return false; }
	default List<ItemStack> generateManagedLootForContext(LootContext lootContext) { return List.of(); }
}
