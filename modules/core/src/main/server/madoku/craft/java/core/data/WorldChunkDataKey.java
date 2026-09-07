package madoku.craft.java.core.data;

/** Identifies a chunk-data entry by dimension and chunk coordinates. */
public record WorldChunkDataKey(String dimensionId, int chunkX, int chunkZ) {
	public WorldChunkDataKey {
		dimensionId = dimensionId == null ? "" : dimensionId.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
