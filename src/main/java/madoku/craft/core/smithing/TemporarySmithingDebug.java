package madoku.craft.core.smithing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary diagnostics for tracing smithing slot routing. */
public final class TemporarySmithingDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger("MadokuCraft/TemporarySmithingDebug");
	private static final boolean ENABLED = true;

	private TemporarySmithingDebug() {
	}

	public static void menuCreated(SmithingMenu menu) {
		if (!ENABLED || menu == null) return;
		for (int i = 0; i <= SmithingMenu.RESULT_SLOT; i++) {
			Slot slot = menu.getSlot(i);
			LOGGER.info("smithing menu slot={} containerSlot={} x={} y={} class={} item={}",
				i, slot.index, slot.x, slot.y, slot.getClass().getName(), itemId(slot.getItem()));
		}
		LOGGER.info("smithing flags config={} extended={} items={} pets={}",
			SmithingConfigManager.isEnabled(),
			MadokuSmithingManager.acceptsExtendedItems(),
			madoku.craft.items.MadokuItemsManager.isEnabled(),
			madoku.craft.pet.MadokuPetManager.isEnabled());
	}

	public static void placement(SmithingMenu menu, int slotIndex, ItemStack stack, boolean accepted) {
		if (!ENABLED) return;
		LOGGER.info("smithing mayPlace slot={} item={} count={} accepted={} left={} middle={} right={}",
			slotIndex, itemId(stack), stack == null ? 0 : stack.getCount(), accepted,
			menu == null ? "null" : itemId(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem()),
			menu == null ? "null" : itemId(menu.getSlot(SmithingMenu.BASE_SLOT).getItem()),
			menu == null ? "null" : itemId(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem()));
	}

	public static void additionalCheck(SmithingMenu menu, ItemStack stack, boolean accepted) {
		if (!ENABLED) return;
		ItemStack base = menu == null ? ItemStack.EMPTY : menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
		LOGGER.info("smithing additionalCheck item={} accepted={} base={} baseLevel={} additionalLevel={} extended={} sameItem={}",
			itemId(stack), accepted, itemId(base),
			level(base), level(stack), MadokuSmithingManager.acceptsExtendedItems(),
			base != null && stack != null && !base.isEmpty() && !stack.isEmpty() && base.getItem() == stack.getItem());
	}

	public static void resultInputs(SmithingMenu menu) {
		if (!ENABLED || menu == null) return;
		LOGGER.info("smithing result inputs left(base)={} middle(template)={} right(additional)={} result={}",
			itemId(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem()),
			itemId(menu.getSlot(SmithingMenu.BASE_SLOT).getItem()),
			itemId(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem()),
			itemId(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem()));
	}

	private static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "empty";
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String level(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "empty";
		Integer itemLevel = madoku.craft.items.MadokuItemsManager.getItemLevel(stack);
		if (itemLevel != null) return Integer.toString(itemLevel);
		if (madoku.craft.pet.PetEntitiesManager.isPetItem(stack)) {
			return Integer.toString(madoku.craft.pet.PetEntitiesManager.petLevel(stack));
		}
		return "none";
	}
}
