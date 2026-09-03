package madoku.craft.java.core.enchant;

import madoku.craft.java.items.ItemsAPIManager;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Orchestrates the enchantment subsystem through its public API contract. */
public final class MadokuEnchantManager {
	private MadokuEnchantManager() {
	}

	public static void initialize() {
		EnchantConfigAPIManager.initialize();
		BooksConfigAPIManager.initialize();
		SyncConfigAPIManager.register(
			"enchant",
			BooksConfigAPIManager::createClientSyncSnapshot,
			MadokuEnchantManager::applyClientSynchronizedSnapshot,
			MadokuEnchantManager::resetClientSynchronizedState
		);
		EnchantTableAPIManager.initialize();
	}

	public static void reset() {
		EnchantBooksAPIManager.reset();
		EnchantTableAPIManager.reset();
		BooksConfigAPIManager.reset();
		EnchantConfigAPIManager.reset();
	}

	public static void onServerTick(MinecraftServer server) { EnchantBooksAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) {
		EnchantConfigAPIManager.initialize();
		BooksConfigAPIManager.onServerStarted(server);
		EnchantTableAPIManager.onServerStarted(server);
	}
	public static boolean isEnabled() { return EnchantConfigAPIManager.isEnabled(); }
	public static void resetClientSynchronizedState() { BooksConfigAPIManager.resetClientSynchronizedState(); }
	public static void applyClientSynchronizedSnapshot(String snapshot) { BooksConfigAPIManager.applyClientSynchronizedSnapshot(snapshot); }

	public static void copyEnchantments(ItemStack source, ItemStack target) {
		if (source == null || target == null || source.isEmpty() || target.isEmpty()) return;
		copyManagedItemData(source, target);
		EnchantmentHelper.setEnchantments(target, EnchantmentHelper.getEnchantmentsForCrafting(source));
		copyManagedItemData(source, target);
		ItemsAPIManager.updateDurabilityLore(target);
	}

	public static void mergeEnchantments(ItemStack primary, ItemStack duplicate, ItemStack target) {
		if (primary == null || duplicate == null || target == null || primary.isEmpty() || duplicate.isEmpty() || target.isEmpty()) return;
		ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(primary));
		ItemEnchantments duplicateEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(duplicate);
		for (var entry : duplicateEnchantments.entrySet()) {
			var enchantment = entry.getKey();
			int duplicateLevel = entry.getIntValue();
			int primaryLevel = merged.getLevel(enchantment);
			int combinedLevel = primaryLevel == 0 ? duplicateLevel
				: primaryLevel == duplicateLevel ? Math.min(enchantment.value().getMaxLevel(), primaryLevel + 1)
				: Math.max(primaryLevel, duplicateLevel);
			merged.set(enchantment, combinedLevel);
		}
		copyManagedItemData(primary, target);
		EnchantmentHelper.setEnchantments(target, merged.toImmutable());
		copyManagedItemData(primary, target);
		ItemsAPIManager.updateDurabilityLore(target);
	}

	private static void copyManagedItemData(ItemStack source, ItemStack target) {
		CustomData sourceData = source.get(DataComponents.CUSTOM_DATA);
		if (sourceData != null) {
			CustomData targetData = target.get(DataComponents.CUSTOM_DATA);
			CompoundTag merged = targetData == null ? new CompoundTag() : targetData.copyTag();
			merged.merge(sourceData.copyTag());
			target.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
		}
		Component customName = source.get(DataComponents.CUSTOM_NAME);
		if (customName != null) target.set(DataComponents.CUSTOM_NAME, customName);
		ItemLore lore = source.get(DataComponents.LORE);
		if (lore != null) target.set(DataComponents.LORE, lore);
	}
}
