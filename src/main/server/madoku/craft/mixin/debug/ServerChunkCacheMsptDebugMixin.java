package madoku.craft.mixin.debug;

import madoku.craft.java.debug.MadokuMsptDebug;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Temporary timing hook for chunk loading, ticking, unloading, and broadcasts. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMsptDebugMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void madokuCraft$beginChunkSourceTick(BooleanSupplier haveTime, boolean tickChunks, CallbackInfo ci) {
		if (((ServerChunkCache) (Object) this).getLevel() instanceof ServerLevel level) {
			MadokuMsptDebug.beginLevelSection(level, "chunk_source");
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void madokuCraft$endChunkSourceTick(BooleanSupplier haveTime, boolean tickChunks, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}
}
