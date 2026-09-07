package madoku.craft.java.ecosystem;

/** Receives the one Vanilla environmental sample selected for a chunk tick. */
@FunctionalInterface
public interface EcosystemRandomPositionListener {
	/** Cheap relevance gate evaluated before the provider's hot handler runs. */
	default boolean accepts(EcosystemRandomPositionEvent event) {
		return true;
	}

	void onRandomPosition(EcosystemRandomPositionEvent event);
}
