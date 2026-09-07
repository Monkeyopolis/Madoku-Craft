package madoku.craft.java.ecosystem;

import java.util.concurrent.CopyOnWriteArrayList;

/** Internal listener registry for one shared Vanilla random-position source. */
final class EcosystemRandomPositionManager {
	private static final CopyOnWriteArrayList<EcosystemRandomPositionListener> LISTENERS = new CopyOnWriteArrayList<>();

	private EcosystemRandomPositionManager() {
	}

	static void initialize() {
	}

	static void reset() {
		LISTENERS.clear();
	}

	static void registerListener(EcosystemRandomPositionListener listener) {
		if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
	}

	static void unregisterListener(EcosystemRandomPositionListener listener) {
		if (listener != null) LISTENERS.remove(listener);
	}

	static void dispatch(EcosystemRandomPositionEvent event) {
		if (event == null) return;
		for (EcosystemRandomPositionListener listener : LISTENERS) {
			if (listener.accepts(event)) {
				listener.onRandomPosition(event);
			}
		}
	}
}
