package madoku.craft.mixin;

import madoku.craft.entity.MadokuEntities;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeTabs.class)
public abstract class CreativeModeTabsSpawnEggsMixin {
	@Inject(
		method = "lambda$bootstrap$28(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V",
		at = @At("TAIL")
	)
	private static void madoku$addHagSpawnEgg(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output, CallbackInfo callbackInfo) {
		output.accept(MadokuEntities.HAG_SPAWN_EGG);
	}
}
