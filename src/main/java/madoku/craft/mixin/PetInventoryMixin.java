package madoku.craft.mixin;

import madoku.craft.pet.PetComponentsManager.PetHolder;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetEntitiesManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
public abstract class PetInventoryMixin implements PetHolder {
	@Unique
	private final PetInventory madokuCraft$petInventory = madokuCraft$createPetInventory();

	@Override
	public PetInventory madokuCraft$getPetInventory() {
		return madokuCraft$petInventory;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$savePets(ValueOutput output, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$petInventory.getContainerSize(); slot++) {
			ItemStack stack = madokuCraft$petInventory.getItem(slot);
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
	private void madokuCraft$loadPets(ValueInput input, CallbackInfo ci) {
		madokuCraft$petInventory.runBulkUpdate(() -> {
			for (int slot = 0; slot < madokuCraft$petInventory.getContainerSize(); slot++) {
				String itemId = input.getStringOr(madokuCraft$slotKey(slot), "");
				if (itemId.isBlank()) {
					madokuCraft$petInventory.setItem(slot, ItemStack.EMPTY);
					continue;
				}

				Identifier identifier = Identifier.tryParse(itemId);
				Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
				if (item == null) {
					madokuCraft$petInventory.setItem(slot, ItemStack.EMPTY);
					continue;
				}

				ItemStack stack = new ItemStack(item);
				madokuCraft$petInventory.setItem(slot, PetEntitiesManager.isValid(stack) ? stack : ItemStack.EMPTY);
			}
		});
	}

	@Unique
	private static String madokuCraft$slotKey(int slot) {
		return "MadokuPets." + slot;
	}

	@Unique
	private PetInventory madokuCraft$createPetInventory() {
		PetInventory inventory = new PetInventory();
		inventory.setChangeListener(() -> {
			if ((Object) this instanceof ServerPlayer serverPlayer) {
				PetEntitiesManager.onInventoryChanged(serverPlayer);
			}
		});
		return inventory;
	}
}

