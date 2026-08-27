package madoku.craft.api.enchant;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runtime group that owns the Madoku enchantment-table rules. */
public final class EnchantTableManager {
	private static final int MAX_ENCHANTMENTS_PER_BOOK = 3;
	private static final int LEVELS_PER_ENCHANTMENT = 10;
	private static final int MAX_BOTTLE_LEVEL_COST = 10;
	private static final int SHIFT_BUTTON_OFFSET = 3;

	private EnchantTableManager() {
	}

	public static void initialize() {
	}

	public static void reset() {
	}

	public static void onServerStarted(MinecraftServer server) {
	}

	public static boolean isAcceptedInput(ItemStack stack) {
		return stack != null && !stack.isEmpty()
			&& (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.GLASS_BOTTLE));
	}

	public static int encodeShiftButton(int option) {
		return SHIFT_BUTTON_OFFSET + option;
	}

	public static void updateChoices(EnchantmentMenu menu) {
		clearChoices(menu);
		if (!EnchantConfigManager.isEnchantmentTableEnabled()) return;

		ItemStack input = menu.getSlot(0).getItem();
		if (input.isEmpty()) return;

		if (input.is(Items.GLASS_BOTTLE)) {
			menu.costs[0] = MAX_BOTTLE_LEVEL_COST;
			return;
		}

		for (int option = 0; option < menu.costs.length; option++) {
			int cost = (option + 1) * LEVELS_PER_ENCHANTMENT;
			if (input.is(Items.ENCHANTED_BOOK) && !canUpgrade(input)) {
				continue;
			}
			menu.costs[option] = cost;
		}
	}

	public static boolean handleButton(EnchantmentMenu menu, Player player, int button) {
		if (!EnchantConfigManager.isEnchantmentTableEnabled()) return false;
		boolean bulk = button >= SHIFT_BUTTON_OFFSET && button < SHIFT_BUTTON_OFFSET * 2;
		int option = bulk
			? button - SHIFT_BUTTON_OFFSET
			: button;
		if (option < 0 || option >= menu.costs.length) return false;
		if (bulk) {
			boolean crafted = false;
			while (craftOne(menu, player, option, true)) {
				crafted = true;
			}
			if (crafted) {
				updateChoices(menu);
				menu.broadcastChanges();
			}
			return crafted;
		}

		boolean crafted = craftOne(menu, player, option, false);
		if (crafted) {
			updateChoices(menu);
			menu.broadcastChanges();
		}
		return crafted;
}

	private static boolean craftOne(EnchantmentMenu menu, Player player, int option, boolean bulk) {

		ItemStack input = menu.getSlot(0).getItem();
		ItemStack inputPrototype = input.copyWithCount(1);
		int cost = menu.costs[option];
		if (input.isEmpty() || cost <= 0 || player.experienceLevel < cost) return false;

		int lapisCost = option + 1;
		ItemStack lapis = menu.getSlot(1).getItem();
		if (lapis.getCount() < lapisCost) return false;

		ItemStack result;
		if (input.is(Items.GLASS_BOTTLE)) {
			if (option != 0) return false;
			result = new ItemStack(Items.EXPERIENCE_BOTTLE);
		} else if (input.is(Items.BOOK)) {
			result = createEnchantedBook(player, cost / LEVELS_PER_ENCHANTMENT);
			if (result.isEmpty()) return false;
		} else if (input.is(Items.ENCHANTED_BOOK)) {
			result = upgradeEnchantedBook(input, cost / LEVELS_PER_ENCHANTMENT);
			if (result.isEmpty()) return false;
		} else {
			return false;
		}

		if (bulk && !player.getInventory().add(result.copy())) {
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			player.giveExperienceLevels(-cost);
			lapis.shrink(lapisCost);
		}
		input.shrink(1);
		if (bulk) {
			if (input.isEmpty()) {
				menu.getSlot(0).set(ItemStack.EMPTY);
				pullNextInput(player, menu, inputPrototype);
			} else {
				menu.getSlot(0).set(input);
			}
		} else if (player.getInventory().add(result.copy())) {
			if (input.isEmpty()) {
				menu.getSlot(0).set(ItemStack.EMPTY);
				pullNextInput(player, menu, inputPrototype);
			} else {
				menu.getSlot(0).set(input);
			}
		} else {
			if (!input.isEmpty() && !player.getInventory().add(input.copy())) {
				player.drop(input.copy(), false);
			}
			menu.getSlot(0).set(result);
		}
		menu.getSlot(1).set(lapis);
		return true;
	}

	private static void pullNextInput(Player player, EnchantmentMenu menu, ItemStack prototype) {
		for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
			ItemStack candidate = player.getInventory().getItem(inventorySlot);
			if (!ItemStack.isSameItemSameComponents(candidate, prototype)) continue;

			ItemStack nextInput = candidate.copyWithCount(1);
			candidate.shrink(1);
			player.getInventory().setItem(inventorySlot, candidate);
			menu.getSlot(0).set(nextInput);
			return;
		}
	}

	private static ItemStack createEnchantedBook(Player player, int enchantmentCount) {
		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> available = availableEnchantments(menuRandom(player), registry, enchantmentCount);
		if (available.isEmpty()) return ItemStack.EMPTY;

		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Holder<Enchantment> enchantment : available) enchantments.set(enchantment, 1);
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
		return result;
	}

	private static List<Holder<Enchantment>> availableEnchantments(RandomSource random, Registry<Enchantment> registry, int count) {
		List<Holder<Enchantment>> candidates = new ArrayList<>();
		for (Holder<Enchantment> holder : registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE)) {
			candidates.add(holder);
		}
		if (candidates.isEmpty()) {
			registry.stream().forEach(enchantment -> candidates.add(registry.wrapAsHolder(enchantment)));
		}
		Collections.shuffle(candidates, new RandomSourceAdapter(random));

		List<Holder<Enchantment>> selected = new ArrayList<>();
		for (Holder<Enchantment> candidate : candidates) {
			if (selected.size() >= Math.min(MAX_ENCHANTMENTS_PER_BOOK, count)) break;
			if (EnchantmentHelper.isEnchantmentCompatible(selected, candidate)) selected.add(candidate);
		}
		return selected;
	}

	private static boolean canUpgrade(ItemStack input) {
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			int current = enchantments.getLevel(enchantment);
			if (current < enchantment.value().getMaxLevel()) return true;
		}
		return false;
	}

	private static ItemStack upgradeEnchantedBook(ItemStack input, int levels) {
		ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(existing);
		boolean changed = false;
		for (Holder<Enchantment> enchantment : existing.keySet()) {
			int current = existing.getLevel(enchantment);
			int target = Math.min(enchantment.value().getMaxLevel(), current + levels);
			if (target > current) {
				upgraded.set(enchantment, target);
				changed = true;
			}
		}
		if (!changed) return ItemStack.EMPTY;
		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		EnchantmentHelper.setEnchantments(result, upgraded.toImmutable());
		return result;
	}

	private static void clearChoices(EnchantmentMenu menu) {
		for (int i = 0; i < menu.costs.length; i++) {
			menu.costs[i] = 0;
			menu.enchantClue[i] = -1;
			menu.levelClue[i] = -1;
		}
	}

	private static RandomSource menuRandom(Player player) {
		return player.getRandom();
	}

	private static final class RandomSourceAdapter extends java.util.Random {
		private final RandomSource source;

		private RandomSourceAdapter(RandomSource source) {
			this.source = source;
		}

		@Override
		protected int next(int bits) {
			return source.nextInt() >>> (Integer.SIZE - bits);
		}
	}
}
