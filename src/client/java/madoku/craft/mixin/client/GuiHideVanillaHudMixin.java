package madoku.craft.mixin.client;

import madoku.craft.MadokuHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiHideVanillaHudMixin {
	@Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$hideHearts(
		GuiGraphics context,
		Player player,
		int x,
		int y,
		int lines,
		int regen,
		float maxHealth,
		int health,
		int displayHealth,
		int absorption,
		boolean blinking,
		CallbackInfo ci
	) {
		if (MadokuHud.isHealthHudEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$hideFood(GuiGraphics context, Player player, int top, int right, CallbackInfo ci) {
		if (MadokuHud.isHungerHudEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$hideArmor(GuiGraphics context, Player player, int x, int y, int width, int armor, CallbackInfo ci) {
		if (MadokuHud.isArmorHudEnabled()) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderAirBubbles(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;III)V",
		at = @At("HEAD"),
		cancellable = true,
		require = 0
	)
	private void madokuCraft$hideAirBubbles(GuiGraphics context, Player player, int top, int left, int air, CallbackInfo ci) {
		if (MadokuHud.isOxygenHudEnabled()) {
			ci.cancel();
		}
	}

	@Redirect(
		method = "renderPlayerHealth",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;getAirSupply()I"
		)
	)
	private int madokuCraft$hideAirSupply(Player player) {
		if (MadokuHud.isOxygenHudEnabled()) {
			return player.getMaxAirSupply();
		}
		return player.getAirSupply();
	}

	@Redirect(
		method = "renderPlayerHealth",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"
		)
	)
	private boolean madokuCraft$hideAirFluidCheck(Player player, TagKey<Fluid> fluidTag) {
		if (MadokuHud.isOxygenHudEnabled()) {
			return false;
		}
		return player.isEyeInFluid(fluidTag);
	}
}
