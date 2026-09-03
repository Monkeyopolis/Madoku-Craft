package madoku.craft.java.pet;

import madoku.craft.java.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Public payload contracts used by the Madoku Pets network integration. */
public final class PetPayloadAPIManager {
	public record LeftClickAirPayload() implements CustomPacketPayload {
		public static final Type<LeftClickAirPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_left_click_air"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LeftClickAirPayload> CODEC = StreamCodec.unit(new LeftClickAirPayload());
		@Override public Type<LeftClickAirPayload> type() { return TYPE; }
	}

	public record PetAbilityHudPayload(
		int slot0Cooldown0, int slot0Cooldown1, int slot0Cooldown2,
		int slot1Cooldown0, int slot1Cooldown1, int slot1Cooldown2,
		int slot2Cooldown0, int slot2Cooldown1, int slot2Cooldown2,
		int slot3Cooldown0, int slot3Cooldown1, int slot3Cooldown2
	) implements CustomPacketPayload {
		public static final Type<PetAbilityHudPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_ability_hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PetAbilityHudPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot0Cooldown0,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot0Cooldown1,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot0Cooldown2,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot1Cooldown0,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot1Cooldown1,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot1Cooldown2,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot2Cooldown0,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot2Cooldown1,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot2Cooldown2,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot3Cooldown0,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot3Cooldown1,
			ByteBufCodecs.VAR_INT, PetAbilityHudPayload::slot3Cooldown2,
			PetAbilityHudPayload::new
		);
		public static PetAbilityHudPayload fromArray(int[] values) {
			int[] safe = values == null ? new int[PetAPIManager.SLOT_COUNT * PetAPIManager.MAX_ABILITY_COOLDOWNS_PER_PET] : values;
			return new PetAbilityHudPayload(
				valueAt(safe, 0), valueAt(safe, 1), valueAt(safe, 2),
				valueAt(safe, 3), valueAt(safe, 4), valueAt(safe, 5),
				valueAt(safe, 6), valueAt(safe, 7), valueAt(safe, 8),
				valueAt(safe, 9), valueAt(safe, 10), valueAt(safe, 11)
			);
		}
		public int[] asArray() {
			return new int[] {
				slot0Cooldown0, slot0Cooldown1, slot0Cooldown2,
				slot1Cooldown0, slot1Cooldown1, slot1Cooldown2,
				slot2Cooldown0, slot2Cooldown1, slot2Cooldown2,
				slot3Cooldown0, slot3Cooldown1, slot3Cooldown2
			};
		}
		@Override public Type<PetAbilityHudPayload> type() { return TYPE; }
	}

	private PetPayloadAPIManager() { }
	private static int valueAt(int[] values, int index) { return index >= 0 && index < values.length ? Math.max(0, values[index]) : 0; }
}
