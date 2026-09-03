package madoku.craft.mixin.inventory;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
	@Invoker("isInventoryOpen")
	boolean madokuCraft$isInventoryOpen();
}
