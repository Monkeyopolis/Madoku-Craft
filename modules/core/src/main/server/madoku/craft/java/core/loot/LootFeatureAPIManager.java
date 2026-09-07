package madoku.craft.java.core.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

/** Access point for optional feature behavior required by the Core loot subsystem. */
public final class LootFeatureAPIManager {
	private static final LootFeatureAdapter NO_ADAPTER = new LootFeatureAdapter() { };
	private static volatile LootFeatureAdapter adapter = NO_ADAPTER;

	private LootFeatureAPIManager() {
	}

	public static void registerAdapter(LootFeatureAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Loot feature adapter must not be null.");
		}
		adapter = candidate;
	}

	public static void unregisterAdapter() {
		adapter = NO_ADAPTER;
	}

	public static boolean isLuckEnabled() { return adapter.isLuckEnabled(); }
	public static boolean isActiveDropPlayerPlacedBlock() { return adapter.isActiveDropPlayerPlacedBlock(); }
	public static ServerPlayer resolveLootPlayer(LootContext lootContext) { return adapter.resolveLootPlayer(lootContext); }
	public static ServerPlayer resolveActiveDropPlayer() { return adapter.resolveActiveDropPlayer(); }
	public static double resolveLootLuckStat(ServerPlayer player) { return adapter.resolveLootLuckStat(player); }
	public static void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) {
		adapter.applyManagedMobDrops(player, random, stacks);
	}
	public static boolean isFarmingEnabled() { return adapter.isFarmingEnabled(); }
	public static boolean isMobEnabled() { return adapter.isMobEnabled(); }
	public static boolean isBeeCustomMobDropsEnabled(LivingEntity entity) { return adapter.isBeeCustomMobDropsEnabled(entity); }
	public static String resolveBeeMobDropsConfigReference(LivingEntity entity) { return adapter.resolveBeeMobDropsConfigReference(entity); }
	public static boolean isZombieCustomMobDropsEnabled(LivingEntity entity) { return adapter.isZombieCustomMobDropsEnabled(entity); }
	public static String resolveZombieMobDropsConfigReference(LivingEntity entity) { return adapter.resolveZombieMobDropsConfigReference(entity); }
	public static void applyGeneratedItemLevel(ItemStack stack, RandomSource random) { adapter.applyGeneratedItemLevel(stack, random); }
	public static boolean isRarityCategoryItem(ItemStack stack) { return adapter.isRarityCategoryItem(stack); }
	public static void applyPetLore(ItemStack stack) { adapter.applyPetLore(stack); }
	public static boolean isPetsEnabled() { return adapter.isPetsEnabled(); }
}
