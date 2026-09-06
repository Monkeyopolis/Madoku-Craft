package madoku.craft.java.ecosystem;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the ecosystem subsystem through its public API contract. */
public final class MadokuEcosystemManager {
	private static final EcosystemRandomPositionListener GROWTH_RANDOM_POSITION_LISTENER =
		new EcosystemRandomPositionListener() {
			@Override
			public boolean accepts(EcosystemRandomPositionEvent event) {
				return NaturalGrowthAPIManager.acceptsRandomPosition(event);
			}

			@Override
			public void onRandomPosition(EcosystemRandomPositionEvent event) {
				NaturalGrowthAPIManager.onRandomPosition(event);
			}
		};
	private static final EcosystemRandomPositionListener EROSION_RANDOM_POSITION_LISTENER =
		new EcosystemRandomPositionListener() {
			@Override
			public boolean accepts(EcosystemRandomPositionEvent event) {
				return NaturalErosionAPIManager.acceptsRandomPosition(event);
			}

			@Override
			public void onRandomPosition(EcosystemRandomPositionEvent event) {
				NaturalErosionAPIManager.onRandomPosition(event);
			}
		};
	private static final EcosystemRandomPositionListener DECAY_RANDOM_POSITION_LISTENER =
		new EcosystemRandomPositionListener() {
			@Override
			public boolean accepts(EcosystemRandomPositionEvent event) {
				return NaturalDecayAPIManager.acceptsRandomPosition(event);
			}

			@Override
			public void onRandomPosition(EcosystemRandomPositionEvent event) {
				NaturalDecayAPIManager.onRandomPosition(event);
			}
		};

	private MadokuEcosystemManager() {
	}

	/** Initializes the shared ecosystem runtime and each ecosystem subsystem. */
	public static void initialize() {
		EcosystemAPIManager.initialize();
		EcosystemRandomPositionAPIManager.registerProvider(new MadokuEcosystemRandomPositionProvider());
		EcosystemRandomPositionAPIManager.initialize();
		NaturalGrowthAPIManager.registerProvider(new MadokuNaturalGrowthProvider());
		NaturalErosionAPIManager.registerProvider(new MadokuNaturalErosionProvider());
		NaturalDecayAPIManager.registerProvider(new MadokuNaturalDecayProvider());
		NaturalGrowthAPIManager.initialize();
		NaturalErosionAPIManager.initialize();
		NaturalDecayAPIManager.initialize();
		registerRandomPositionListeners();
		EcosystemAPIManager.refreshSettings();
	}

	private static void registerRandomPositionListeners() {
		EcosystemRandomPositionAPIManager.registerListener(GROWTH_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.registerListener(EROSION_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.registerListener(DECAY_RANDOM_POSITION_LISTENER);
	}

	/** Resets each ecosystem subsystem and the shared ecosystem runtime. */
	public static void reset() {
		EcosystemRandomPositionAPIManager.unregisterListener(GROWTH_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.unregisterListener(EROSION_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.unregisterListener(DECAY_RANDOM_POSITION_LISTENER);
		NaturalGrowthAPIManager.reset();
		NaturalErosionAPIManager.reset();
		NaturalDecayAPIManager.reset();
		EcosystemRandomPositionAPIManager.reset();
		EcosystemAPIManager.reset();
	}

	public static void onServerTick(MinecraftServer server) { EcosystemAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) {
		registerRandomPositionListeners();
		EcosystemAPIManager.onServerStarted(server);
	}
	public static void loadPersistedData(MinecraftServer server) { EcosystemAPIManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { EcosystemAPIManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { EcosystemAPIManager.savePersistedData(server); }
}
