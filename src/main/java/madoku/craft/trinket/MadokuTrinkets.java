package madoku.craft.trinket;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.gamerules.GameRules;

public final class MadokuTrinkets {
	public static final int SLOT_COUNT = 4;
	public static final int FIRST_SLOT_INDEX = 46;
	public static final int SLOT_X = 77;
	public static final int[] SLOT_YS = {8, 26, 44, 62};
	public static final String SAVE_KEY = "MadokuTrinkets";
	public static final String ITEMS_KEY = "Items";
	public static final String SLOT_KEY = "Slot";
	public static final String STACK_KEY = "Stack";

	private MadokuTrinkets() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
				return;
			}

			dropAll(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> copyToNewPlayer(oldPlayer, newPlayer));
	}

	public static boolean isValidTrinket(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpawnEggItem;
	}

	public static void dropAll(ServerPlayer player) {
		if (!(player instanceof MadokuTrinketHolder holder)) {
			return;
		}

		MadokuTrinketInventory trinketInventory = holder.madokuCraft$getTrinketInventory();
		for (int slot = 0; slot < trinketInventory.getContainerSize(); slot++) {
			ItemStack stack = trinketInventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			player.drop(stack, true, false);
			trinketInventory.setItem(slot, ItemStack.EMPTY);
		}
		trinketInventory.setChanged();
	}

	private static void copyToNewPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
		if (!(oldPlayer instanceof MadokuTrinketHolder oldHolder) || !(newPlayer instanceof MadokuTrinketHolder newHolder)) {
			return;
		}

		newHolder.madokuCraft$getTrinketInventory().copyFrom(oldHolder.madokuCraft$getTrinketInventory());
	}
}
