package madoku.craft.java.core.sync;

import madoku.craft.java.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Clientbound payload carrying one compressed server configuration snapshot. */
public record SyncPayloadAPIManager(String configId, byte[] compressedSnapshot) implements CustomPacketPayload {
	private static final int MAX_COMPRESSED_BYTES = 1_048_576;
	private static final int MAX_UNCOMPRESSED_BYTES = 4_194_304;

	public static final CustomPacketPayload.Type<SyncPayloadAPIManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "config_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayloadAPIManager> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		SyncPayloadAPIManager::configId,
		ByteBufCodecs.byteArray(MAX_COMPRESSED_BYTES),
		SyncPayloadAPIManager::compressedSnapshot,
		SyncPayloadAPIManager::new
	);

	public SyncPayloadAPIManager {
		if (configId == null || configId.isBlank()) {
			throw new IllegalArgumentException("Configuration snapshot id cannot be blank.");
		}
		if (compressedSnapshot == null || compressedSnapshot.length > MAX_COMPRESSED_BYTES) {
			throw new IllegalArgumentException("Configuration snapshot is too large.");
		}
		compressedSnapshot = compressedSnapshot.clone();
	}

	public SyncPayloadAPIManager(String configId, String snapshot) {
		this(configId, compress(snapshot));
	}

	@Override
	public byte[] compressedSnapshot() {
		return compressedSnapshot.clone();
	}

	public String snapshot() {
		return decompress(compressedSnapshot);
	}

	private static byte[] compress(String snapshot) {
		if (snapshot == null) {
			throw new IllegalArgumentException("Configuration snapshot cannot be null.");
		}
		byte[] source = snapshot.getBytes(StandardCharsets.UTF_8);
		if (source.length > MAX_UNCOMPRESSED_BYTES) {
			throw new IllegalArgumentException("Configuration snapshot is too large.");
		}

		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
				deflater.write(source);
			}
			byte[] compressed = output.toByteArray();
			if (compressed.length > MAX_COMPRESSED_BYTES) {
				throw new IllegalArgumentException("Compressed configuration snapshot is too large.");
			}
			return compressed;
		} catch (IOException exception) {
			throw new IllegalArgumentException("Failed to compress configuration snapshot.", exception);
		}
	}

	private static String decompress(byte[] compressed) {
		if (compressed == null || compressed.length > MAX_COMPRESSED_BYTES) {
			throw new IllegalArgumentException("Compressed configuration snapshot is too large.");
		}

		try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = inflater.read(buffer)) != -1) {
				total += read;
				if (total > MAX_UNCOMPRESSED_BYTES) {
					throw new IllegalArgumentException("Configuration snapshot expands beyond the allowed size.");
				}
				output.write(buffer, 0, read);
			}
			return output.toString(StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalArgumentException("Failed to decompress configuration snapshot.", exception);
		}
	}

	@Override
	public Type<SyncPayloadAPIManager> type() {
		return TYPE;
	}
}

