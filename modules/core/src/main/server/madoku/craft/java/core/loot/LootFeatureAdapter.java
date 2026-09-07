package madoku.craft.java.core.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

/** Optional feature behavior used by Core's loot-table implementations. */
public interface LootFeatureAdapter {
	default boolean isLuckEnabled() { return false; }
	default boolean isActiveDropPlayerPlacedBlock() { return false; }
	default ServerPlayer resolveLootPlayer(LootContext lootContext) { return null; }
	default ServerPlayer resolveActiveDropPlayer() { return null; }
	default double resolveLootLuckStat(ServerPlayer player) { return 0.0d; }
	default void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) { }

	default boolean isFarmingEnabled() { return false; }

	default boolean isMobEnabled() { return false; }
	default boolean isBeeCustomMobDropsEnabled(LivingEntity entity) { return false; }
	default String resolveBeeMobDropsConfigReference(LivingEntity entity) { return ""; }
	default boolean isZombieCustomMobDropsEnabled(LivingEntity entity) { return false; }
	default String resolveZombieMobDropsConfigReference(LivingEntity entity) { return ""; }

	default void applyGeneratedItemLevel(ItemStack stack, RandomSource random) { }
	default boolean isRarityCategoryItem(ItemStack stack) { return false; }
	default void applyPetLore(ItemStack stack) { }

	default boolean isPetsEnabled() { return false; }
}
