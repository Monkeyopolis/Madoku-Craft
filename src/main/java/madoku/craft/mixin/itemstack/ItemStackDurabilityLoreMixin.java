package madoku.craft.mixin.itemstack;

import madoku.craft.items.MadokuItemsManager;
import madoku.craft.pet.PetHudAPIManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackDurabilityLoreMixin {
	@Inject(method = "getTooltipLines", at = @At("HEAD"))
	private void madokuCraft$refreshPetLore(
		Item.TooltipContext context,
		net.minecraft.world.entity.player.Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		PetHudAPIManager.applySupportedPetLore((ItemStack) (Object) this);
	}

	@Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$placeManagedLoreBeforeVanillaDetails(
		Item.TooltipContext context,
		net.minecraft.world.entity.player.Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		ItemStack stack = (ItemStack) (Object) this;
		if (!MadokuItemsManager.isEnabled() || !MadokuItemsManager.isRarityCategoryItem(stack)) {
			return;
		}

		List<Component> original = cir.getReturnValue();
		List<Component> managedLines = new ArrayList<>();
		List<Component> remaining = new ArrayList<>(original.size());
		for (Component line : original) {
			String text = line.getString();
			if (text.startsWith("Level:") || text.startsWith("Durability:")) {
				managedLines.add(line);
			} else {
				remaining.add(line);
			}
		}
		if (managedLines.isEmpty()) {
			return;
		}

		// Vanilla adds trim/enchantment providers before DataComponents.LORE.
		// Keep Madoku's identity and state lines immediately below the item name.
		remaining.addAll(Math.min(1, remaining.size()), managedLines);
		cir.setReturnValue(remaining);
	}

	@Inject(method = "setDamageValue", at = @At("TAIL"))
	private void madokuCraft$updateDurabilityLore(int damage, CallbackInfo ci) {
		MadokuItemsManager.updateDurabilityLore((ItemStack) (Object) this);
	}
}
