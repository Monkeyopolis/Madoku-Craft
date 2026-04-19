package madoku.craft.entity;

import madoku.craft.MadokuCraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;

public class HagRenderer extends WitchRenderer {
	private static final ResourceLocation HAG_TEXTURE = ResourceLocation.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/entities/hag.png");

	public HagRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Witch witch) {
		return HAG_TEXTURE;
	}
}
