package madoku.craft.mixin.attributes;

import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootPoolSingletonContainer.class)
public interface LootPoolSingletonContainerAccessor {
	@Accessor("weight")
	int madokuCraft$getWeight();

	@Accessor("quality")
	int madokuCraft$getQuality();
}
