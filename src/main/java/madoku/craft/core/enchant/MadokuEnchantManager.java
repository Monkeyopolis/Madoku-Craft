package madoku.craft.core.enchant;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.sync.SyncConfigManager;
import madoku.craft.items.MadokuItemsManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Orchestrates the Madoku Enchant API subsystem and exposes its shared lifecycle. */
public final class MadokuEnchantManager {
	public static final String ENCHANT_FOLDER_NAME = MadokuCoreManager.API_FOLDER_NAME + "/madoku-enchants";
	public static final String ENCHANTMENTS_FOLDER_NAME = "madoku-enchantments";

	private MadokuEnchantManager() {
	}

	public static void initialize() {
		EnchantConfigManager.initialize();
		BooksConfigManager.initialize();
		SyncConfigManager.register(
			"enchant",
			BooksConfigManager::createClientSyncSnapshot,
			MadokuEnchantManager::applyClientSynchronizedSnapshot,
			MadokuEnchantManager::resetClientSynchronizedState
		);
		EnchantTableManager.initialize();
	}

	public static void reset() {
		EnchantBooksManager.reset();
		EnchantTableManager.reset();
		BooksConfigManager.reset();
		EnchantConfigManager.reset();
	}

	public static void onServerTick(MinecraftServer server) {
		EnchantBooksManager.onServerTick(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		EnchantConfigManager.initialize();
		BooksConfigManager.onServerStarted(server);
		EnchantTableManager.onServerStarted(server);
	}

	public static boolean isEnabled() {
		return EnchantConfigManager.isEnabled();
	}

	public static void resetClientSynchronizedState() {
		BooksConfigManager.resetClientSynchronizedState();
	}

	public static void applyClientSynchronizedSnapshot(String snapshot) {
		BooksConfigManager.applyClientSynchronizedSnapshot(snapshot);
	}

	/** Shared component helper used when an enchant-preserving result is produced by another group. */
	public static void copyEnchantments(ItemStack source, ItemStack target) {
		if (source == null || target == null || source.isEmpty() || target.isEmpty()) return;
		copyManagedItemData(source, target);
		EnchantmentHelper.setEnchantments(target, EnchantmentHelper.getEnchantmentsForCrafting(source));
		// Some vanilla result builders replace the display components while writing
		// enchantments. Restore Madoku's presentation after that operation as well.
		copyManagedItemData(source, target);
		MadokuItemsManager.updateDurabilityLore(target);
	}

	/**
	 * Preserves enchantments from both items when a same-item upgrade consumes a duplicate.
	 * Matching enchantments use vanilla's anvil-style combination rule.
	 */
	public static void mergeEnchantments(ItemStack primary, ItemStack duplicate, ItemStack target) {
		if (primary == null || duplicate == null || target == null
			|| primary.isEmpty() || duplicate.isEmpty() || target.isEmpty()) return;

		ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(
			EnchantmentHelper.getEnchantmentsForCrafting(primary)
		);
		ItemEnchantments duplicateEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(duplicate);
		for (var entry : duplicateEnchantments.entrySet()) {
			var enchantment = entry.getKey();
			int duplicateLevel = entry.getIntValue();
			int primaryLevel = merged.getLevel(enchantment);
			int combinedLevel;
			if (primaryLevel == 0) {
				combinedLevel = duplicateLevel;
			} else if (primaryLevel == duplicateLevel) {
				combinedLevel = Math.min(enchantment.value().getMaxLevel(), primaryLevel + 1);
			} else {
				combinedLevel = Math.max(primaryLevel, duplicateLevel);
			}
			merged.set(enchantment, combinedLevel);
		}

		copyManagedItemData(primary, target);
		EnchantmentHelper.setEnchantments(target, merged.toImmutable());
		copyManagedItemData(primary, target);
		MadokuItemsManager.updateDurabilityLore(target);
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
