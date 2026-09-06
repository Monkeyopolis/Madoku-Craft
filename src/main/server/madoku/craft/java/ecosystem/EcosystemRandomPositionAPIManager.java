package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Public API for the shared ecosystem random-position source. */
public final class EcosystemRandomPositionAPIManager {
	private static final EcosystemRandomPositionProvider UNAVAILABLE_PROVIDER = new EcosystemRandomPositionProvider() { };
	private static volatile EcosystemRandomPositionProvider provider = UNAVAILABLE_PROVIDER;

	private EcosystemRandomPositionAPIManager() {
	}

	public static void registerProvider(EcosystemRandomPositionProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Ecosystem random-position provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() {
		provider = UNAVAILABLE_PROVIDER;
	}

	public static void initialize() { provider.initialize(); }

	public static void reset() { provider.reset(); }

	public static void registerListener(EcosystemRandomPositionListener listener) { provider.registerListener(listener); }

	public static void unregisterListener(EcosystemRandomPositionListener listener) { provider.unregisterListener(listener); }

	public static void dispatch(ServerLevel level, BlockPos position, RandomSource random) {
		if (level != null && position != null && random != null) {
			provider.dispatch(new EcosystemRandomPositionEvent(
				level,
				position,
				level.getBlockState(position),
				random
			));
		}
	}
}
