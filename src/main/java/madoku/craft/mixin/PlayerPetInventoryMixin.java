package madoku.craft.mixin;

import madoku.craft.trinket.PlayerPetHolder;
import madoku.craft.trinket.PlayerPetInventory;
import madoku.craft.trinket.PlayerPetSystem;
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
public abstract class PlayerPetInventoryMixin implements PlayerPetHolder {
	@Unique
	private final PlayerPetInventory madokuCraft$playerPetInventory = new PlayerPetInventory();

	@Override
	public PlayerPetInventory madokuCraft$getPlayerPetInventory() {
		return madokuCraft$playerPetInventory;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$savePlayerPets(ValueOutput output, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$playerPetInventory.getContainerSize(); slot++) {
			ItemStack stack = madokuCraft$playerPetInventory.getItem(slot);
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
	private void madokuCraft$loadPlayerPets(ValueInput input, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$playerPetInventory.getContainerSize(); slot++) {
			String itemId = input.getStringOr(madokuCraft$slotKey(slot), "");
			if (itemId.isBlank()) {
				madokuCraft$playerPetInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			Identifier identifier = Identifier.tryParse(itemId);
			Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
			if (item == null) {
				madokuCraft$playerPetInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			ItemStack stack = new ItemStack(item);
			madokuCraft$playerPetInventory.setItem(slot, PlayerPetSystem.isValidPlayerPet(stack) ? stack : ItemStack.EMPTY);
		}
		madokuCraft$playerPetInventory.setChanged();
	}

	@Unique
	private static String madokuCraft$slotKey(int slot) {
		return PlayerPetSystem.SAVE_KEY + "." + slot;
	}
}
