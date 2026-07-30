package madoku.craft.pet;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Groups the payload types and client-side transient state used by Madoku Pets. */
public final class PetPayloadManager {
	public record PetAbilityHudPayload(int slot0RemainingTicks, int slot1RemainingTicks, int slot2RemainingTicks, int slot3RemainingTicks) implements CustomPacketPayload {
		public static final Type<PetAbilityHudPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_ability_hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PetAbilityHudPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot0RemainingTicks,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot1RemainingTicks,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot2RemainingTicks,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot3RemainingTicks,
			PetAbilityHudPayload::new
		);

		public static PetAbilityHudPayload fromArray(int[] values) {
			int[] safe = values == null ? new int[4] : values;
			return new PetAbilityHudPayload(valueAt(safe, 0), valueAt(safe, 1), valueAt(safe, 2), valueAt(safe, 3));
		}

		public int[] asArray() {
			return new int[] {slot0RemainingTicks, slot1RemainingTicks, slot2RemainingTicks, slot3RemainingTicks};
		}

		@Override public Type<PetAbilityHudPayload> type() { return TYPE; }
	}

	public record PetSoundStatePayload(String petUuid, String itemId) implements CustomPacketPayload {
		public static final Type<PetSoundStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_sound_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PetSoundStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PetSoundStatePayload::petUuid,
			ByteBufCodecs.STRING_UTF8, PetSoundStatePayload::itemId,
			PetSoundStatePayload::new
		);

		@Override public Type<PetSoundStatePayload> type() { return TYPE; }
	}

	private static final Map<UUID, String> SOUND_ITEM_IDS = new ConcurrentHashMap<>();

	private PetPayloadManager() {
	}

	public static void setSoundState(UUID petId, String itemId) {
		if (petId == null) return;
		String normalized = itemId == null ? "" : itemId.trim().toLowerCase();
		if (normalized.isEmpty()) SOUND_ITEM_IDS.remove(petId);
		else SOUND_ITEM_IDS.put(petId, normalized);
	}

	public static String soundItemId(UUID petId) {
		return petId == null ? "" : SOUND_ITEM_IDS.getOrDefault(petId, "");
	}

	public static void removeSoundState(UUID petId) {
		if (petId != null) SOUND_ITEM_IDS.remove(petId);
	}

	public static void clearSoundState() {
		SOUND_ITEM_IDS.clear();
	}

	private static int valueAt(int[] values, int index) {
		return index >= 0 && index < values.length ? Math.max(0, values[index]) : 0;
	}
}
