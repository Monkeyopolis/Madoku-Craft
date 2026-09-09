package madoku.craft.java.core.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Access point for optional feature behavior required by the Core loot subsystem. */
public final class LootFeatureAPIManager {
	private static final List<LootFeatureAdapter> adapters = new CopyOnWriteArrayList<>();

	private LootFeatureAPIManager() {
	}

	public static void registerAdapter(LootFeatureAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Loot feature adapter must not be null.");
		}
		if (!adapters.contains(candidate)) {
			adapters.add(candidate);
		}
	}

	public static void unregisterAdapter() {
		adapters.clear();
	}

	public static boolean isLuckEnabled() {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isLuckEnabled()) return true;
		}
		return false;
	}

	public static boolean isActiveDropPlayerPlacedBlock() {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isActiveDropPlayerPlacedBlock()) return true;
		}
		return false;
	}

	public static ServerPlayer resolveLootPlayer(LootContext lootContext) {
		for (LootFeatureAdapter adapter : adapters) {
			ServerPlayer player = adapter.resolveLootPlayer(lootContext);
			if (player != null) return player;
		}
		return null;
	}

	public static ServerPlayer resolveActiveDropPlayer() {
		for (LootFeatureAdapter adapter : adapters) {
			ServerPlayer player = adapter.resolveActiveDropPlayer();
			if (player != null) return player;
		}
		return null;
	}

	public static double resolveLootLuckStat(ServerPlayer player) {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isLuckEnabled()) return adapter.resolveLootLuckStat(player);
		}
		return 0.0D;
	}

	public static void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) {
		for (LootFeatureAdapter adapter : adapters) {
			adapter.applyManagedMobDrops(player, random, stacks);
		}
	}

	public static boolean isFarmingEnabled() {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isFarmingEnabled()) return true;
		}
		return false;
	}

	public static boolean isMobEnabled() {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isMobEnabled()) return true;
		}
		return false;
	}

	public static boolean isBeeCustomMobDropsEnabled(LivingEntity entity) {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isBeeCustomMobDropsEnabled(entity)) return true;
		}
		return false;
	}

	public static String resolveBeeMobDropsConfigReference(LivingEntity entity) {
		for (LootFeatureAdapter adapter : adapters) {
			String reference = adapter.resolveBeeMobDropsConfigReference(entity);
			if (reference != null && !reference.isBlank()) return reference;
		}
		return "";
	}

	public static boolean isZombieCustomMobDropsEnabled(LivingEntity entity) {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isZombieCustomMobDropsEnabled(entity)) return true;
		}
		return false;
	}

	public static String resolveZombieMobDropsConfigReference(LivingEntity entity) {
		for (LootFeatureAdapter adapter : adapters) {
			String reference = adapter.resolveZombieMobDropsConfigReference(entity);
			if (reference != null && !reference.isBlank()) return reference;
		}
		return "";
	}

	public static void applyGeneratedItemLevel(ItemStack stack, RandomSource random) {
		for (LootFeatureAdapter adapter : adapters) {
			adapter.applyGeneratedItemLevel(stack, random);
		}
	}

	public static boolean isRarityCategoryItem(ItemStack stack) {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isRarityCategoryItem(stack)) return true;
		}
		return false;
	}

	public static void applyPetLore(ItemStack stack) {
		for (LootFeatureAdapter adapter : adapters) {
			adapter.applyPetLore(stack);
		}
	}

	public static boolean isPetsEnabled() {
		for (LootFeatureAdapter adapter : adapters) {
			if (adapter.isPetsEnabled()) return true;
		}
		return false;
	}
}
