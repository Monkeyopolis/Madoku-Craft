package madoku.craft.java.ecosystem;

import madoku.craft.java.core.data.DataSaveParticipant;
import madoku.craft.java.core.data.DataSaveParticipantAPIManager;
import net.minecraft.server.MinecraftServer;

/** Orchestrates the ecosystem subsystem through its public API contract. */
public final class MadokuEcosystemManager {
	private static final DataSaveParticipant DATA_SAVE_PARTICIPANT = new DataSaveParticipant() {
		@Override public String id() { return "ecosystem"; }
		@Override public void autosavePersistedData(MinecraftServer server) { MadokuEcosystemManager.autosavePersistedData(server); }
		@Override public void savePersistedData(MinecraftServer server) { MadokuEcosystemManager.savePersistedData(server); }
	};

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
	private static final EcosystemBlockChangeListener CANDIDATE_INVALIDATION_LISTENER =
		EcosystemAPIManager::invalidateCandidatesAt;

	private MadokuEcosystemManager() {
	}

	/** Initializes the shared ecosystem runtime and each ecosystem subsystem. */
	public static void initialize() {
		DataSaveParticipantAPIManager.register(DATA_SAVE_PARTICIPANT);
		EcosystemAPIManager.initialize();
		EcosystemRandomPositionAPIManager.registerProvider(new MadokuEcosystemRandomPositionProvider());
		EcosystemRandomPositionAPIManager.initialize();
		EcosystemBlockChangeAPIManager.registerProvider(new MadokuEcosystemBlockChangeProvider());
		EcosystemBlockChangeAPIManager.initialize();
		NaturalGrowthAPIManager.registerProvider(new MadokuNaturalGrowthProvider());
		NaturalErosionAPIManager.registerProvider(new MadokuNaturalErosionProvider());
		NaturalDecayAPIManager.registerProvider(new MadokuNaturalDecayProvider());
		NaturalGrowthAPIManager.initialize();
		NaturalErosionAPIManager.initialize();
		NaturalDecayAPIManager.initialize();
		registerRandomPositionListeners();
		registerBlockChangeListeners();
		EcosystemAPIManager.refreshSettings();
	}

	private static void registerRandomPositionListeners() {
		EcosystemRandomPositionAPIManager.registerListener(GROWTH_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.registerListener(EROSION_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.registerListener(DECAY_RANDOM_POSITION_LISTENER);
	}

	private static void registerBlockChangeListeners() {
		EcosystemBlockChangeAPIManager.registerListener(CANDIDATE_INVALIDATION_LISTENER);
	}

	/** Resets each ecosystem subsystem and the shared ecosystem runtime. */
	public static void reset() {
		EcosystemRandomPositionAPIManager.unregisterListener(GROWTH_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.unregisterListener(EROSION_RANDOM_POSITION_LISTENER);
		EcosystemRandomPositionAPIManager.unregisterListener(DECAY_RANDOM_POSITION_LISTENER);
		EcosystemBlockChangeAPIManager.unregisterListener(CANDIDATE_INVALIDATION_LISTENER);
		NaturalGrowthAPIManager.reset();
		NaturalErosionAPIManager.reset();
		NaturalDecayAPIManager.reset();
		EcosystemRandomPositionAPIManager.reset();
		EcosystemBlockChangeAPIManager.reset();
		EcosystemAPIManager.reset();
	}

	public static void onServerTick(MinecraftServer server) { EcosystemAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) {
		registerRandomPositionListeners();
		registerBlockChangeListeners();
		EcosystemAPIManager.onServerStarted(server);
	}
	public static void loadPersistedData(MinecraftServer server) { EcosystemAPIManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { EcosystemAPIManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { EcosystemAPIManager.savePersistedData(server); }
}
