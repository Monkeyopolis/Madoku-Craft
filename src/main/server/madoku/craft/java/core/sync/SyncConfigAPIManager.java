package madoku.craft.java.core.sync;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Central registry for server-owned configuration snapshots synchronized to clients.
 *
 * <p>Each system registers a stable identifier, a server snapshot provider, and client
 * callbacks. The transport remains generic, so adding another synchronized configuration
 * does not require another payload type or another join event.</p>
 */
public final class SyncConfigAPIManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(SyncConfigAPIManager.class);
	private static final Map<String, ConfigSync> CONFIGS = new LinkedHashMap<>();
	private static boolean initialized;

	private SyncConfigAPIManager() {
	}

	/** Registers the server-to-client configuration synchronization pass. */
	public static void initialize() {
		if (initialized) {
			return;
		}

		initialized = true;
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
	}

	/**
	 * Registers one configuration system with the centralized synchronization transport.
	 * Registration is idempotent for a given identifier and replaces the previous definition.
	 */
	public static void register(
		String configId,
		Supplier<String> serverSnapshot,
		Consumer<String> clientSnapshotApplier,
		Runnable clientStateResetter
	) {
		String normalizedId = normalizeId(configId);
		if (normalizedId.isBlank()) {
			throw new IllegalArgumentException("Configuration sync id must not be blank.");
		}
		Objects.requireNonNull(serverSnapshot, "serverSnapshot");
		Objects.requireNonNull(clientSnapshotApplier, "clientSnapshotApplier");
		Objects.requireNonNull(clientStateResetter, "clientStateResetter");
		synchronized (CONFIGS) {
			CONFIGS.put(normalizedId, new ConfigSync(serverSnapshot, clientSnapshotApplier, clientStateResetter));
		}
	}

	/** Sends every registered configuration snapshot to one player. */
	public static int syncPlayer(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		int sent = 0;
		synchronized (CONFIGS) {
			for (Map.Entry<String, ConfigSync> entry : CONFIGS.entrySet()) {
				String snapshot;
				try {
					snapshot = Objects.requireNonNullElse(entry.getValue().serverSnapshot.get(), "");
				} catch (RuntimeException exception) {
					LOGGER.warn("Failed to create synchronized configuration snapshot for {}.", entry.getKey(), exception);
					continue;
				}

				try {
					if (SyncPlayerAPIManager.send(player, new SyncPayloadAPIManager(entry.getKey(), snapshot))) {
						sent++;
					}
				} catch (RuntimeException exception) {
					LOGGER.warn("Failed to send synchronized configuration {}.", entry.getKey(), exception);
				}
			}
		}
		return sent;
	}

	/** Applies a received snapshot through the registered client handler. */
	public static void applyClientSnapshot(String configId, String snapshot) {
		String normalizedId = normalizeId(configId);
		if (normalizedId.isBlank()) {
			LOGGER.debug("Ignoring synchronized configuration with a blank id.");
			return;
		}

		ConfigSync config;
		synchronized (CONFIGS) {
			config = CONFIGS.get(normalizedId);
		}
		if (config == null) {
			LOGGER.debug("Ignoring synchronized configuration with unknown id {}.", normalizedId);
			return;
		}

		try {
			config.clientSnapshotApplier.accept(snapshot);
		} catch (RuntimeException exception) {
			LOGGER.warn("Failed to apply synchronized configuration {}.", normalizedId, exception);
		}
	}

	/** Clears every registered system's client-side synchronized state. */
	public static void resetClientSynchronizedState() {
		synchronized (CONFIGS) {
			for (Map.Entry<String, ConfigSync> entry : CONFIGS.entrySet()) {
				try {
					entry.getValue().clientStateResetter.run();
				} catch (RuntimeException exception) {
					LOGGER.warn("Failed to reset synchronized configuration {}.", entry.getKey(), exception);
				}
			}
		}
	}

	private static String normalizeId(String configId) {
		return configId == null ? "" : configId.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private record ConfigSync(
		Supplier<String> serverSnapshot,
		Consumer<String> clientSnapshotApplier,
		Runnable clientStateResetter
	) {
	}
}

