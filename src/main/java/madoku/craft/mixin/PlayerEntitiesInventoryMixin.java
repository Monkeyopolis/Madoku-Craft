package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesHolder;
import madoku.craft.pet.PlayerEntitiesInventory;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerEntitiesInventoryMixin implements PlayerEntitiesHolder {
	@Unique
	private final PlayerEntitiesInventory madokuCraft$playerEntitiesInventory = new PlayerEntitiesInventory();

	@Override
	public PlayerEntitiesInventory madokuCraft$getPlayerEntitiesInventory() {
		return madokuCraft$playerEntitiesInventory;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$savePlayerEntities(CompoundTag output, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$playerEntitiesInventory.getContainerSize(); slot++) {
			ItemStack stack = madokuCraft$playerEntitiesInventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (itemId == null) {
				continue;
			}
			output.putString(madokuCraft$slotKey(slot), itemId.toString());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$loadPlayerEntities(CompoundTag input, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$playerEntitiesInventory.getContainerSize(); slot++) {
			String itemId = input.getString(madokuCraft$slotKey(slot));
			if (itemId.isBlank() && input.contains(madokuCraft$legacySlotKey(slot))) {
				itemId = input.getString(madokuCraft$legacySlotKey(slot));
			}
			if (itemId.isBlank()) {
				madokuCraft$playerEntitiesInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			ResourceLocation identifier = ResourceLocation.tryParse(itemId);
			Item item = identifier == null ? null : BuiltInRegistries.ITEM.get(identifier);
			if (item == null) {
				madokuCraft$playerEntitiesInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			ItemStack stack = new ItemStack(item);
			madokuCraft$playerEntitiesInventory.setItem(slot, PlayerEntitiesSystem.isValidPlayerEntity(stack) ? stack : ItemStack.EMPTY);
		}
		madokuCraft$playerEntitiesInventory.setChanged();
	}

	@Unique
	private static String madokuCraft$slotKey(int slot) {
		return PlayerEntitiesSystem.SAVE_KEY + "." + slot;
	}

	@Unique
	private static String madokuCraft$legacySlotKey(int slot) {
		return PlayerEntitiesSystem.legacySaveKey() + "." + slot;
	}
}


