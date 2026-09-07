package madoku.craft.java.pet;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Registry-ID helpers for Pet-owned behavior that targets root-registered entities. */
public final class PetEntityTypeAPIManager {
	private static final Identifier HAG_ENTITY_ID = Identifier.fromNamespaceAndPath("madoku-craft", "hag");

	private PetEntityTypeAPIManager() {
	}

	public static boolean isHag(Entity entity) {
		return entity != null && HAG_ENTITY_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}
}
