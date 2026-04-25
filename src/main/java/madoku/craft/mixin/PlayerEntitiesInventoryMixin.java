package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesHolder;
import madoku.craft.pet.PlayerEntitiesInventory;
import madoku.craft.pet.PlayerEntitiesSystem;
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
public abstract class PlayerEntitiesInventoryMixin implements PlayerEntitiesHolder {
	@Unique
	private final PlayerEntitiesInventory madokuCraft$playerEntitiesInventory = madokuCraft$createPlayerEntitiesInventory();

	@Override
	public PlayerEntitiesInventory madokuCraft$getPlayerEntitiesInventory() {
		return madokuCraft$playerEntitiesInventory;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$savePlayerEntities(ValueOutput output, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$playerEntitiesInventory.getContainerSize(); slot++) {
			ItemStack stack = madokuCraft$playerEntitiesInventory.getItem(slot);
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
	private void madokuCraft$loadPlayerEntities(ValueInput input, CallbackInfo ci) {
		for (int slot = 0; slot < madokuCraft$playerEntitiesInventory.getContainerSize(); slot++) {
			String itemId = input.getStringOr(madokuCraft$slotKey(slot), "");
			if (itemId.isBlank()) {
				itemId = input.getStringOr(madokuCraft$legacySlotKey(slot), "");
			}
			if (itemId.isBlank()) {
				madokuCraft$playerEntitiesInventory.setItem(slot, ItemStack.EMPTY);
				continue;
			}

			Identifier identifier = Identifier.tryParse(itemId);
			Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
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

	@Unique
	private PlayerEntitiesInventory madokuCraft$createPlayerEntitiesInventory() {
		PlayerEntitiesInventory inventory = new PlayerEntitiesInventory();
		inventory.setChangeListener(() -> {
			if ((Object) this instanceof ServerPlayer serverPlayer) {
				PlayerEntitiesSystem.onPlayerEntitiesInventoryChanged(serverPlayer);
			}
		});
		return inventory;
	}
}
