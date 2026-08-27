package madoku.craft.api.enchant;

import madoku.craft.items.MadokuItemsManager;
import madoku.craft.pet.MadokuPetManager;
import madoku.craft.api.rarity.MadokuRarityManager;
import madoku.craft.api.rarity.RarityTierManager.Tier;
import madoku.craft.pet.PetEntitiesManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/** Runtime group that owns Madoku smithing-table extensions. */
public final class EnchantSmithingManager {
	private EnchantSmithingManager() {
	}

	public static void initialize() {
	}

	public static void reset() {
	}

	public static void onServerStarted(MinecraftServer server) {
	}

	public static boolean acceptsPetItems() {
		return EnchantConfigManager.isSmithingTableEnabled() && MadokuPetManager.isEnabled();
	}

	public static boolean acceptsExtendedItems() {
		return EnchantConfigManager.isSmithingTableEnabled()
			&& MadokuEnchantManager.isItemOrPetSystemEnabled();
	}

	public static boolean isTemplateItem(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.SmithingTemplateItem;
	}

	public static boolean isNetheriteUpgradeTemplate(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
	}

	public static boolean isBottleCatalyst(ItemStack stack) {
		return acceptsExtendedItems() && stack != null && stack.is(Items.EXPERIENCE_BOTTLE);
	}

	public static boolean isManagedBase(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return (MadokuItemsManager.isEnabled() && MadokuItemsManager.isRarityCategoryItem(stack))
			|| (MadokuPetManager.isEnabled() && PetEntitiesManager.isPetItem(stack));
	}

	public static boolean isAllowedAdditional(SmithingMenu menu, ItemStack stack) {
		if (!acceptsExtendedItems() || stack == null || stack.isEmpty()) return false;
		if (stack.is(Items.NETHERITE_INGOT)) {
			TemporarySmithingDebug.additionalCheck(menu, stack, true);
			return true;
		}
		ItemStack base = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		boolean accepted = isManagedBase(base)
			&& base.getItem() == stack.getItem()
			&& levelOf(base) == levelOf(stack)
			&& rarityOf(base) == rarityOf(stack);
		TemporarySmithingDebug.additionalCheck(menu, stack, accepted);
		return accepted;
	}

	public static void applyCustomResult(SmithingMenu menu) {
		if (!acceptsExtendedItems()) return;

		ItemStack base = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		ItemStack template = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		ItemStack additional = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
		TemporarySmithingDebug.resultInputs(menu);
		if (!isManagedBase(base)) return;

		boolean duplicateUpgrade = isBottleCatalyst(template)
			&& !additional.isEmpty()
			&& !additional.is(Items.NETHERITE_INGOT)
			&& isAllowedAdditional(menu, additional);
		ItemStack vanillaResult = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
		boolean netheriteUpgrade = additional.is(Items.NETHERITE_INGOT)
			&& isNetheriteUpgradeTemplate(template)
			&& !vanillaResult.isEmpty();
		if (!duplicateUpgrade && !netheriteUpgrade) return;

		ItemStack result = vanillaResult;
		if (duplicateUpgrade) {
			result = base.copy();
		} else {
			// Netherite upgrades retain the current Madoku level; they do not perform
			// the XP-bottle duplicate upgrade operation.
			copyManagedLevel(base, result);
		}
		if (duplicateUpgrade) copyBestDurability(result, base, additional);
		if (duplicateUpgrade) {
			MadokuEnchantManager.mergeEnchantments(base, additional, result);
		} else {
			MadokuEnchantManager.copyEnchantments(base, result);
		}
		if (duplicateUpgrade && !increaseLevel(result)) {
			menu.getSlot(SmithingMenu.RESULT_SLOT).set(ItemStack.EMPTY);
			return;
		}
		menu.getSlot(SmithingMenu.RESULT_SLOT).set(result);
	}

	/** Re-evaluates vanilla smithing recipes using Madoku's physical slot roles. */
	public static void prepareSwappedRecipeResult(SmithingMenu menu, Level level) {
		if (!acceptsExtendedItems() || !(level instanceof ServerLevel serverLevel)) return;

		ItemStack template = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		ItemStack base = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		ItemStack addition = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
		SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
		ItemStack result = serverLevel.recipeAccess()
			.getRecipeFor(RecipeType.SMITHING, input, serverLevel)
			.map(holder -> holder.value().assemble(input))
			.orElse(ItemStack.EMPTY);
		menu.getSlot(SmithingMenu.RESULT_SLOT).set(result);
	}

	private static void copyBestDurability(ItemStack result, ItemStack first, ItemStack duplicate) {
		if (!result.isDamageableItem() || !first.isDamageableItem() || !duplicate.isDamageableItem()) return;
		result.setDamageValue(Math.min(first.getDamageValue(), duplicate.getDamageValue()));
	}

	private static boolean increaseLevel(ItemStack stack) {
		if (MadokuItemsManager.isEnabled() && MadokuItemsManager.isRarityCategoryItem(stack)
			&& MadokuItemsManager.areItemLevelsEnabled()) {
			int current = itemLevel(stack);
			if (current >= MadokuItemsManager.getItemMaximumLevel()) return false;
			MadokuItemsManager.setItemLevel(stack, current + 1);
			return true;
		}
		if (MadokuPetManager.isEnabled() && PetEntitiesManager.isPetItem(stack)) {
			int current = PetEntitiesManager.petLevel(stack);
			if (current >= MadokuPetManager.maxPetLevel()) return false;
			PetEntitiesManager.setPetLevel(stack, current + 1);
			return true;
		}
		return false;
	}

	private static void copyManagedLevel(ItemStack source, ItemStack target) {
		if (MadokuItemsManager.isEnabled() && MadokuItemsManager.isRarityCategoryItem(target)
			&& MadokuItemsManager.areItemLevelsEnabled()) {
			MadokuItemsManager.setItemLevel(target, itemLevel(source));
			return;
		}
		if (MadokuPetManager.isEnabled() && PetEntitiesManager.isPetItem(target)) {
			PetEntitiesManager.setPetLevel(target, PetEntitiesManager.petLevel(source));
		}
	}

	private static int levelOf(ItemStack stack) {
		if (MadokuPetManager.isEnabled() && PetEntitiesManager.isPetItem(stack)) {
			return PetEntitiesManager.petLevel(stack);
		}
		return itemLevel(stack);
	}

	private static Tier rarityOf(ItemStack stack) {
		return MadokuRarityManager.detectAppliedRarity(stack);
	}

	private static int itemLevel(ItemStack stack) {
		Integer level = MadokuItemsManager.getItemLevel(stack);
		return level == null ? MadokuItemsManager.getItemStartingLevel() : level;
	}
}
