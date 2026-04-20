package madoku.craft.mixin;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(ArmorItem.class)
public interface ArmorItemDefaultModifiersAccessor {
	@Mutable
	@Accessor("defaultModifiers")
	void madokuCraft$setDefaultModifiers(Supplier<ItemAttributeModifiers> defaultModifiers);
}
