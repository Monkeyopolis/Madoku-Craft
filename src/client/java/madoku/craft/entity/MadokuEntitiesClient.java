package madoku.craft.entity;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class MadokuEntitiesClient {
	private MadokuEntitiesClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(MadokuEntities.HAG, HagRenderer::new);
	}
}
