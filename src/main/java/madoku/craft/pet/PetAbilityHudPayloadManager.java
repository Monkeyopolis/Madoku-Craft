package madoku.craft.pet;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PetAbilityHudPayloadManager(
	int slot0RemainingTicks,
	int slot1RemainingTicks,
	int slot2RemainingTicks,
	int slot3RemainingTicks
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PetAbilityHudPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_ability_hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PetAbilityHudPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			PetAbilityHudPayloadManager::slot0RemainingTicks,
			ByteBufCodecs.VAR_INT,
			PetAbilityHudPayloadManager::slot1RemainingTicks,
			ByteBufCodecs.VAR_INT,
			PetAbilityHudPayloadManager::slot2RemainingTicks,
			ByteBufCodecs.VAR_INT,
			PetAbilityHudPayloadManager::slot3RemainingTicks,
			PetAbilityHudPayloadManager::new
		);

	public static PetAbilityHudPayloadManager fromArray(int[] remainingTicks) {
		int[] values = remainingTicks == null ? new int[4] : remainingTicks;
		return new PetAbilityHudPayloadManager(valueAt(values, 0), valueAt(values, 1), valueAt(values, 2), valueAt(values, 3));
	}

	public int[] asArray() {
		return new int[] {slot0RemainingTicks, slot1RemainingTicks, slot2RemainingTicks, slot3RemainingTicks};
	}

	@Override
	public Type<PetAbilityHudPayloadManager> type() {
		return TYPE;
	}

	private static int valueAt(int[] values, int index) {
		return index >= 0 && index < values.length ? Math.max(0, values[index]) : 0;
	}
}
