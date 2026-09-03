package madoku.craft.mixin.core;

import madoku.craft.MadokuCraft;
import madoku.craft.core.smithing.SmithingAPIManager;
import madoku.craft.core.smithing.SmithingConfigAPIManager;
import madoku.craft.mixin.inventory.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingScreen.class)
public abstract class SmithingScreenMixin {
	private static final Identifier MADOKU_BOTTLE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/bottle-slot.png");
	private static final Identifier MADOKU_HELMET_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/helmet-slot.png");
	private static final Identifier MADOKU_CHESTPLATE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/chestplate-slot.png");
	private static final Identifier MADOKU_LEGGINGS_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/leggings-slot.png");
	private static final Identifier MADOKU_BOOTS_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/boots-slot.png");
	private static final Identifier MADOKU_PET_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/pet-slot.png");
	private static final Identifier MADOKU_TEMPLATE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/template-upgrade-slot.png");
	private static final Identifier MADOKU_TRIM_TEMPLATE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/template-trim-slot.png");
	private static final Identifier MADOKU_AXE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/axe-slot.png");
	private static final Identifier MADOKU_SWORD_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/sword-slot.png");
	private static final Identifier MADOKU_PICKAXE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/pickaxe-slot.png");
	private static final Identifier MADOKU_SHOVEL_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/shovel-slot.png");
	private static final Identifier MADOKU_SPEAR_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/spear-slot.png");
	private static final Identifier MADOKU_INGOT_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/ingot-slot.png");
	@Unique private int madokuCraft$slotIconTick;
	@Shadow @Final private CyclingSlotBackground baseIcon;
	@Shadow @Final private CyclingSlotBackground templateIcon;
	@Shadow @Final private CyclingSlotBackground additionalIcon;

	@Inject(method = "containerTick()V", at = @At("TAIL"))
	private void madokuCraft$tickSlotIcons(CallbackInfo ci) {
		if (SmithingConfigAPIManager.isEnabled()) madokuCraft$slotIconTick++;
	}

	@Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
	private void madokuCraft$drawSlotIcons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!SmithingConfigAPIManager.isEnabled()) return;
		SmithingScreen screen = (SmithingScreen) (Object) this;
		SmithingMenu menu = screen.getMenu();
		int cycle = madokuCraft$slotIconTick / 40;

		if (SmithingAPIManager.acceptsExtendedItems()
			&& menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()) {
			Identifier[] baseIcons = SmithingAPIManager.acceptsPetItems()
				? new Identifier[] {
					MADOKU_HELMET_SLOT, MADOKU_CHESTPLATE_SLOT, MADOKU_LEGGINGS_SLOT,
					MADOKU_BOOTS_SLOT, MADOKU_AXE_SLOT, MADOKU_PICKAXE_SLOT,
					MADOKU_SHOVEL_SLOT, MADOKU_SPEAR_SLOT, MADOKU_SWORD_SLOT, MADOKU_PET_SLOT
				}
				: new Identifier[] {
					MADOKU_HELMET_SLOT, MADOKU_CHESTPLATE_SLOT, MADOKU_LEGGINGS_SLOT,
					MADOKU_BOOTS_SLOT, MADOKU_AXE_SLOT, MADOKU_PICKAXE_SLOT,
					MADOKU_SHOVEL_SLOT, MADOKU_SPEAR_SLOT, MADOKU_SWORD_SLOT
				};
			draw(graphics, baseIcons[cycle % baseIcons.length], SmithingMenu.TEMPLATE_SLOT_X_PLACEMENT);
		}
		if (SmithingAPIManager.acceptsExtendedItems()
			&& menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()) {
			Identifier[] templateIcons = {
				MADOKU_TRIM_TEMPLATE_SLOT, MADOKU_TEMPLATE_SLOT, MADOKU_BOTTLE_SLOT
			};
			draw(graphics, templateIcons[cycle % templateIcons.length], SmithingMenu.BASE_SLOT_X_PLACEMENT);
		}
		if (SmithingAPIManager.acceptsExtendedItems()
			&& menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty()) {
			Identifier[] additionalIcons = SmithingAPIManager.acceptsPetItems()
				? new Identifier[] {
					MADOKU_INGOT_SLOT, MADOKU_HELMET_SLOT, MADOKU_CHESTPLATE_SLOT,
					MADOKU_LEGGINGS_SLOT, MADOKU_BOOTS_SLOT, MADOKU_AXE_SLOT,
					MADOKU_PICKAXE_SLOT, MADOKU_SHOVEL_SLOT, MADOKU_SPEAR_SLOT,
					MADOKU_SWORD_SLOT, MADOKU_PET_SLOT
				}
				: new Identifier[] {
					MADOKU_INGOT_SLOT, MADOKU_HELMET_SLOT, MADOKU_CHESTPLATE_SLOT,
					MADOKU_LEGGINGS_SLOT, MADOKU_BOOTS_SLOT, MADOKU_AXE_SLOT,
					MADOKU_PICKAXE_SLOT, MADOKU_SHOVEL_SLOT, MADOKU_SPEAR_SLOT,
					MADOKU_SWORD_SLOT
				};
			draw(graphics, additionalIcons[cycle % additionalIcons.length], SmithingMenu.ADDITIONAL_SLOT_X_PLACEMENT);
		}
	}

	@Redirect(
		method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/CyclingSlotBackground;extractRenderState(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/client/gui/GuiGraphicsExtractor;FII)V"
		)
	)
	private void madokuCraft$hideReplacedSlotIcons(
		CyclingSlotBackground background,
		AbstractContainerMenu menu,
		GuiGraphicsExtractor graphics,
		float partialTick,
		int left,
		int top
	) {
		if (SmithingAPIManager.acceptsExtendedItems()
			&& (background == baseIcon || background == templateIcon || background == additionalIcon)) {
			return;
		}
		background.extractRenderState(menu, graphics, partialTick, left, top);
	}

	@Inject(method = "extractOnboardingTooltips(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$hideVanillaSmithingTooltips(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		CallbackInfo ci
	) {
		if (SmithingAPIManager.acceptsExtendedItems()) ci.cancel();
	}

	@Unique
	private void draw(GuiGraphicsExtractor graphics, Identifier texture, int x) {
		AbstractContainerScreenAccessor screenAccess = (AbstractContainerScreenAccessor) (Object) this;
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			texture,
			screenAccess.madokuCraft$getLeftPos() + x,
			screenAccess.madokuCraft$getTopPos() + SmithingMenu.SLOT_Y_PLACEMENT,
			0.0F,
			0.0F,
			16,
			16,
			16,
			16
		);
	}
}

