package madoku.craft.mixin.core;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import madoku.craft.java.core.time.TimeAPIManager;

@Mixin(ServerClockManager.class)
public abstract class ServerClockManagerMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@Shadow
	public abstract void setRate(Holder<WorldClock> clock, float rate);

	@Inject(method = "tick", at = @At("HEAD"))
	private void madokuCraft$scaleOverworldClock(CallbackInfo ci) {
		if (server == null || server.overworld() == null) {
			return;
		}

		Holder<WorldClock> overworldClock = server.overworld()
			.registryAccess()
			.lookupOrThrow(Registries.WORLD_CLOCK)
			.getOrThrow(WorldClocks.OVERWORLD);
		setRate(overworldClock, TimeAPIManager.resolveWorldClockRate(server));
	}
}
