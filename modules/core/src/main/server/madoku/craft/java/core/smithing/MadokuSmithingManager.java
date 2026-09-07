package madoku.craft.java.core.smithing;

import madoku.craft.java.core.enchant.EnchantAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager.Tier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/** Runtime implementation and orchestrator for Madoku Smithing. */
public final class MadokuSmithingManager {
	private MadokuSmithingManager() { }

	public static void initialize() { SmithingConfigAPIManager.initialize(); }
	public static void reset() { SmithingConfigAPIManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { SmithingConfigAPIManager.onServerStarted(server); }
	public static boolean acceptsPetItems() { return SmithingConfigAPIManager.isEnabled() && SmithingFeatureAPIManager.isPetsEnabled(); }
	public static boolean acceptsExtendedItems() { return SmithingConfigAPIManager.isEnabled() && (SmithingFeatureAPIManager.isItemsEnabled() || SmithingFeatureAPIManager.isPetsEnabled()); }
	public static boolean isTemplateItem(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.SmithingTemplateItem; }
	public static boolean isNetheriteUpgradeTemplate(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE); }
	public static boolean isBottleCatalyst(ItemStack stack) { return acceptsExtendedItems() && stack != null && stack.is(Items.EXPERIENCE_BOTTLE); }
	public static boolean isManagedBase(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return (SmithingFeatureAPIManager.isItemsEnabled() && SmithingFeatureAPIManager.isRarityCategoryItem(stack))
			|| (SmithingFeatureAPIManager.isPetsEnabled() && SmithingFeatureAPIManager.isPetItem(stack));
	}
	public static boolean isAllowedAdditional(SmithingMenu menu, ItemStack stack) {
		if (!acceptsExtendedItems() || stack == null || stack.isEmpty()) return false;
		if (stack.is(Items.NETHERITE_INGOT)) return true;
		ItemStack base = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		return isManagedBase(base) && base.getItem() == stack.getItem() && levelOf(base) == levelOf(stack) && rarityOf(base) == rarityOf(stack);
	}
	public static void applyCustomResult(SmithingMenu menu) {
		if (!acceptsExtendedItems()) return;
		ItemStack base = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		ItemStack template = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		ItemStack additional = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
		if (!isManagedBase(base)) return;
		boolean duplicateUpgrade = isBottleCatalyst(template) && !additional.isEmpty() && !additional.is(Items.NETHERITE_INGOT) && isAllowedAdditional(menu, additional);
		ItemStack vanillaResult = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
		boolean netheriteUpgrade = additional.is(Items.NETHERITE_INGOT) && isNetheriteUpgradeTemplate(template) && !vanillaResult.isEmpty();
		if (!duplicateUpgrade && !netheriteUpgrade) return;
		ItemStack result = vanillaResult;
		if (duplicateUpgrade) result = base.copy(); else copyManagedLevel(base, result);
		if (duplicateUpgrade) copyBestDurability(result, base, additional);
		if (duplicateUpgrade) EnchantAPIManager.mergeEnchantments(base, additional, result);
		else {
			EnchantAPIManager.copyEnchantments(base, result);
			if (SmithingFeatureAPIManager.isItemsEnabled()
				&& SmithingFeatureAPIManager.isRarityCategoryItem(result)
				&& SmithingFeatureAPIManager.areItemLevelsEnabled()) {
				SmithingFeatureAPIManager.setItemLevel(result, 1);
			}
		}
		if (duplicateUpgrade && !increaseLevel(result)) { menu.getSlot(SmithingMenu.RESULT_SLOT).set(ItemStack.EMPTY); return; }
		menu.getSlot(SmithingMenu.RESULT_SLOT).set(result);
	}
	public static void prepareSwappedRecipeResult(SmithingMenu menu, Level level) {
		if (!acceptsExtendedItems() || !(level instanceof ServerLevel serverLevel)) return;
		ItemStack template = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		ItemStack base = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		ItemStack addition = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
		SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
		ItemStack result = serverLevel.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, serverLevel).map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
		menu.getSlot(SmithingMenu.RESULT_SLOT).set(result);
	}
	private static void copyBestDurability(ItemStack result, ItemStack first, ItemStack duplicate) { if (result.isDamageableItem() && first.isDamageableItem() && duplicate.isDamageableItem()) result.setDamageValue(Math.min(first.getDamageValue(), duplicate.getDamageValue())); }
	private static boolean increaseLevel(ItemStack stack) {
		if (SmithingFeatureAPIManager.isItemsEnabled()
			&& SmithingFeatureAPIManager.isRarityCategoryItem(stack)
			&& SmithingFeatureAPIManager.areItemLevelsEnabled()) {
			int current = itemLevel(stack);
			if (current >= SmithingFeatureAPIManager.getItemMaximumLevel()) return false;
			SmithingFeatureAPIManager.setItemLevel(stack, current + 1);
			return true;
		}
		if (SmithingFeatureAPIManager.isPetsEnabled() && SmithingFeatureAPIManager.isPetItem(stack)) {
			int current = SmithingFeatureAPIManager.petLevel(stack);
			if (current >= SmithingFeatureAPIManager.maxPetLevel()) return false;
			SmithingFeatureAPIManager.setPetLevel(stack, current + 1);
			return true;
		}
		return false;
	}
	private static void copyManagedLevel(ItemStack source, ItemStack target) {
		if (SmithingFeatureAPIManager.isItemsEnabled()
			&& SmithingFeatureAPIManager.isRarityCategoryItem(target)
			&& SmithingFeatureAPIManager.areItemLevelsEnabled()) {
			SmithingFeatureAPIManager.setItemLevel(target, itemLevel(source));
			return;
		}
		if (SmithingFeatureAPIManager.isPetsEnabled() && SmithingFeatureAPIManager.isPetItem(target)) {
			SmithingFeatureAPIManager.setPetLevel(target, SmithingFeatureAPIManager.petLevel(source));
		}
	}
	private static int levelOf(ItemStack stack) {
		return SmithingFeatureAPIManager.isPetsEnabled() && SmithingFeatureAPIManager.isPetItem(stack)
			? SmithingFeatureAPIManager.petLevel(stack)
			: itemLevel(stack);
	}
	private static Tier rarityOf(ItemStack stack) { return RarityAPIManager.detectAppliedRarity(stack); }
	private static int itemLevel(ItemStack stack) {
		Integer level = SmithingFeatureAPIManager.getItemLevel(stack);
		return level == null ? SmithingFeatureAPIManager.getItemStartingLevel() : level;
	}
}
