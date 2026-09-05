package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemBlockPropertiesAccess;
import madoku.craft.java.ecosystem.EcosystemBlockStateManager;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Blocks.class)
public abstract class BlocksEcosystemPropertiesMixin {
	@ModifyArgs(
		method = "<clinit>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Blocks;register(Lnet/minecraft/references/BlockItemId;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)Lnet/minecraft/world/level/block/Block;"
		)
	)
	private static void madokuCraft$markDirectBlockProperties(Args args) {
		mark(args, 0, 1);
	}

	@ModifyArgs(
		method = "<clinit>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Blocks;register(Lnet/minecraft/references/BlockItemId;Ljava/util/function/Function;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)Lnet/minecraft/world/level/block/Block;"
		)
	)
	private static void madokuCraft$markFactoryBlockProperties(Args args) {
		mark(args, 0, 2);
	}

	private static void mark(Args args, int idIndex, int propertiesIndex) {
		Object id = args.get(idIndex);
		Object properties = args.get(propertiesIndex);
		if (EcosystemBlockStateManager.shouldAddStateProperties((net.minecraft.references.BlockItemId) id)
			&& properties instanceof EcosystemBlockPropertiesAccess access) {
			access.madokuCraft$setEcosystemStateProperties(true);
		}
	}
}
