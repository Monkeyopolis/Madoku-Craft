package madoku.craft.mixin.debug;

import madoku.craft.java.debug.MadokuMsptDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary timing hooks for block-entity ticking, including per-ticker costs. */
@Mixin(Level.class)
public abstract class LevelMsptDebugMixin {
	@Inject(method = "tickBlockEntities", at = @At("HEAD"))
	private void madokuCraft$beginBlockEntityTick(CallbackInfo ci) {
		if ((Object) this instanceof ServerLevel level) {
			MadokuMsptDebug.beginLevelSection(level, "block_entities");
		}
	}

	@Inject(method = "tickBlockEntities", at = @At("RETURN"))
	private void madokuCraft$endBlockEntityTick(CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Redirect(
		method = "tickBlockEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"
		)
	)
	private void madokuCraft$profileIndividualBlockEntity(TickingBlockEntity ticker) {
		if ((Object) this instanceof ServerLevel level) {
			MadokuMsptDebug.beginBlockEntity(level, ticker);
			try {
				ticker.tick();
			} finally {
				MadokuMsptDebug.endSection();
			}
			return;
		}
		ticker.tick();
	}
}
