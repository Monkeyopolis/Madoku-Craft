package madoku.craft.mixin;

import madoku.craft.trinket.MadokuTrinketHolder;
import madoku.craft.trinket.MadokuTrinketInventory;
import madoku.craft.trinket.MadokuTrinkets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerTrinketInventoryMixin implements MadokuTrinketHolder {
	@Unique
	private final MadokuTrinketInventory madokuCraft$trinketInventory = new MadokuTrinketInventory();

	@Override
	public MadokuTrinketInventory madokuCraft$getTrinketInventory() {
		return madokuCraft$trinketInventory;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$saveTrinkets(ValueOutput output, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$trinketInventory.getContainerSize(); slot++) {
			ItemStack stack = madokuCraft$trinketInventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (itemId == null) {
				continue;
			}
			output.putString(madokuCraft$slotKey(slot), itemId.toString());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$loadTrinkets(ValueInput input, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$trinketInventory.getContainerSize(); slot++) {
			String itemId = input.getStringOr(madokuCraft$slotKey(slot), "");
			if (itemId.isBlank()) {
				madokuCraft$trinketInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			Identifier identifier = Identifier.tryParse(itemId);
			Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
			if (item == null) {
				madokuCraft$trinketInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			ItemStack stack = new ItemStack(item);
			madokuCraft$trinketInventory.setItem(slot, MadokuTrinkets.isValidTrinket(stack) ? stack : ItemStack.EMPTY);
		}
		madokuCraft$trinketInventory.setChanged();
	}

	@Unique
	private static String madokuCraft$slotKey(int slot) {
		return MadokuTrinkets.SAVE_KEY + "." + slot;
	}
}
