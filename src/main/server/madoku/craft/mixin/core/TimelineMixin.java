package madoku.craft.mixin.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import madoku.craft.java.core.time.TimeAPIManager;

import java.util.function.BiConsumer;

@Mixin(Timeline.class)
public abstract class TimelineMixin {
	@SuppressWarnings("unchecked")
	@Redirect(
		method = "registerTimeMarkers",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"
		)
	)
	private void madokuCraft$remapConfiguredTimeMarkers(
		BiConsumer<Object, Object> consumer,
		Object key,
		Object marker
	) {
		ResourceKey<ClockTimeMarker> markerKey = (ResourceKey<ClockTimeMarker>) key;
		ClockTimeMarker timeMarker = (ClockTimeMarker) marker;
		long resolvedTicks = TimeAPIManager.resolveConfiguredTimeMarkerTicks(markerKey);
		if (resolvedTicks >= 0L && resolvedTicks != timeMarker.ticks()) {
			timeMarker = new ClockTimeMarker(
				timeMarker.clock(),
				Math.toIntExact(resolvedTicks),
				timeMarker.periodTicks(),
				timeMarker.showInCommands()
			);
		}
		consumer.accept(markerKey, timeMarker);
	}
}
