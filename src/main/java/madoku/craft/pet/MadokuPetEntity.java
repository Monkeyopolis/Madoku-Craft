package madoku.craft.pet;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/** A neutral, persistent world entity whose visual profile is supplied by the pet renderer. */
public final class MadokuPetEntity extends PathfinderMob {
	private static final EntityDataAccessor<String> PET_ID = SynchedEntityData.defineId(MadokuPetEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData.defineId(MadokuPetEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> PET_SLOT = SynchedEntityData.defineId(MadokuPetEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PET_LEVEL = SynchedEntityData.defineId(MadokuPetEntity.class, EntityDataSerializers.INT);

	MadokuPetEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
		setInvulnerable(true);
		setPersistenceRequired();
		setCanPickUpLoot(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0D)
			.add(Attributes.FLYING_SPEED, 0.60D)
			.add(Attributes.MOVEMENT_SPEED, 0.25D);
	}

	@Override
	protected void registerGoals() {
		// MadokuPetManager supplies all pet movement and abilities explicitly.
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PET_ID, "");
		builder.define(OWNER_UUID, "");
		builder.define(PET_SLOT, -1);
		builder.define(PET_LEVEL, 1);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("pet-id", petId());
		output.putString("owner-uuid", ownerUuid() == null ? "" : ownerUuid().toString());
		output.putInt("pet-slot", petSlot());
		output.putInt("pet-level", petLevel());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setPetId(input.getStringOr("pet-id", ""));
		setOwnerUuid(parseUuid(input.getStringOr("owner-uuid", "")));
		setPetSlot(input.getIntOr("pet-slot", -1));
		setPetLevel(input.getIntOr("pet-level", 1));
	}

	@Override
	public void tick() {
		super.tick();
	}

	public String petId() {
		return entityData.get(PET_ID);
	}

	public void setPetId(String petId) {
		entityData.set(PET_ID, petId == null ? "" : petId);
		refreshNavigation();
	}

	private void refreshNavigation() {
		if (navigation == null) {
			return;
		}
		boolean shouldUseFlyingNavigation = usesFlyingNavigation();
		if (shouldUseFlyingNavigation == (navigation instanceof FlyingPathNavigation)) {
			return;
		}

		navigation.stop();
		if (shouldUseFlyingNavigation) {
			moveControl = new FlyingMoveControl<>(this, 20, true);
			FlyingPathNavigation flyingNavigation = new FlyingPathNavigation(this, level());
			flyingNavigation.setCanOpenDoors(false);
			flyingNavigation.setCanFloat(false);
			flyingNavigation.setRequiredPathLength(48.0F);
			navigation = flyingNavigation;
		} else {
			moveControl = new MoveControl<>(this);
			navigation = super.createNavigation(level());
		}
	}

	private boolean usesFlyingNavigation() {
		String id = petId();
		return "minecraft:bat".equals(id) || "minecraft:bee".equals(id);
	}

	public UUID ownerUuid() {
		return parseUuid(entityData.get(OWNER_UUID));
	}

	public void setOwnerUuid(UUID ownerUuid) {
		entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
	}

	public int petSlot() {
		return entityData.get(PET_SLOT);
	}

	public void setPetSlot(int petSlot) {
		entityData.set(PET_SLOT, petSlot);
	}

	public int petLevel() {
		return Math.max(1, entityData.get(PET_LEVEL));
	}

	public void setPetLevel(int petLevel) {
		entityData.set(PET_LEVEL, Math.max(1, petLevel));
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
