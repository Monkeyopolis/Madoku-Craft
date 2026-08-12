package madoku.craft.mixin.inventory;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int madokuCraft$getLeftPos();

	@Accessor("topPos")
	int madokuCraft$getTopPos();

	@Accessor("menu")
	AbstractContainerMenu madokuCraft$getMenu();
}
