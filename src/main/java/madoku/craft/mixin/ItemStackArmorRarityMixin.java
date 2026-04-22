package madoku.craft.mixin;

import madoku.craft.rarity.MadokuRarity;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(ItemStack.class)
public class ItemStackArmorRarityMixin {
	@Inject(
		method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$applyScaledArmorModifiers(
		EquipmentSlot slot,
		BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
		CallbackInfo ci
	) {
		ItemAttributeModifiers modifiers = MadokuRarity.getScaledArmorAttributeModifiers((ItemStack) (Object) this);
		if (modifiers == null) {
			return;
		}

		modifiers.forEach(slot, consumer);
		ci.cancel();
	}

	@Inject(
		method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$applyScaledArmorGroupModifiers(
		EquipmentSlotGroup slotGroup,
		BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
		CallbackInfo ci
	) {
		ItemAttributeModifiers modifiers = MadokuRarity.getScaledArmorAttributeModifiers((ItemStack) (Object) this);
		if (modifiers == null) {
			return;
		}

		modifiers.forEach(slotGroup, consumer);
		ci.cancel();
	}
}
