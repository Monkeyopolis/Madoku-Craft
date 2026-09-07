package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Core registry for feature-owned persistence callbacks. */
public final class DataSaveParticipantAPIManager {
	private static final Map<String, DataSaveParticipant> PARTICIPANTS = new LinkedHashMap<>();

	private DataSaveParticipantAPIManager() { }

	public static synchronized void register(DataSaveParticipant candidate) {
		if (candidate == null || candidate.id() == null || candidate.id().isBlank()) {
			throw new IllegalArgumentException("Data-save participant and id must not be null or blank.");
		}
		PARTICIPANTS.put(candidate.id(), candidate);
	}

	public static synchronized void unregister(String id) {
		if (id != null) {
			PARTICIPANTS.remove(id);
		}
	}

	public static synchronized List<DataSaveParticipant> participants() {
		return List.copyOf(new ArrayList<>(PARTICIPANTS.values()));
	}

	public static void autosavePersistedData(MinecraftServer server) {
		for (DataSaveParticipant participant : participants()) {
			participant.autosavePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		for (DataSaveParticipant participant : participants()) {
			participant.savePersistedData(server);
		}
	}
}
