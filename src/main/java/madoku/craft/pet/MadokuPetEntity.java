package madoku.craft.pet;

import madoku.craft.entity.MadokuEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class MadokuPetEntity extends PathfinderMob {
	private static final EntityDataAccessor<String> DATA_SOURCE_TYPE_ID = SynchedEntityData.defineId(MadokuPetEntity.class, EntityDataSerializers.STRING);
	private static final double PET_SCALE = 0.25D;
	private static final double FOLLOW_DISTANCE_SQR = 4.0D;
	private static final double TELEPORT_DISTANCE_SQR = 16.0D;
	private static final double MOVE_SPEED = 0.28D;
	private static final double VERTICAL_MOVE_SPEED = 0.24D;
	private static final double DESIRED_POSITION_EPSILON_SQR = 0.04D;
	private static final SlotOffset[] SLOT_OFFSETS = {
		new SlotOffset(-0.90D, 0.85D),
		new SlotOffset(-0.30D, 1.35D),
		new SlotOffset(0.30D, 1.35D),
		new SlotOffset(0.90D, 0.85D)
	};

	private UUID ownerUuid;
	private int slotIndex;

	public MadokuPetEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
		this.setNoAi(true);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setSilent(true);
		this.setInvulnerable(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 1.0D)
			.add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
			.add(Attributes.FOLLOW_RANGE, 16.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SOURCE_TYPE_ID, "");
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public void tick() {
		super.tick();
		applyPetState();
		if (!(this.level() instanceof ServerLevel)) {
			return;
		}

		ServerPlayer owner = this.getOwner();
		if (owner == null || owner.isSpectator()) {
			return;
		}

		Vec3 desiredPosition = resolveDesiredPosition(owner, this.slotIndex);
		double ownerDistanceSqr = this.distanceToSqr(owner);
		if (ownerDistanceSqr > TELEPORT_DISTANCE_SQR) {
			this.snapTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, owner.getYRot(), 0.0F);
			this.setDeltaMovement(Vec3.ZERO);
			return;
		}

		Vec3 delta = desiredPosition.subtract(this.position());
		if (ownerDistanceSqr <= FOLLOW_DISTANCE_SQR && delta.lengthSqr() <= DESIRED_POSITION_EPSILON_SQR) {
			this.setDeltaMovement(Vec3.ZERO);
			return;
		}

		Vec3 horizontalDelta = new Vec3(delta.x, 0.0D, delta.z);
		double horizontalDistance = horizontalDelta.length();
		Vec3 horizontalStep = horizontalDistance <= 1.0E-6D
			? Vec3.ZERO
			: horizontalDelta.scale(Math.min(MOVE_SPEED, horizontalDistance) / horizontalDistance);
		double verticalStep = Mth.clamp(delta.y, -VERTICAL_MOVE_SPEED, VERTICAL_MOVE_SPEED);
		Vec3 movement = new Vec3(horizontalStep.x, verticalStep, horizontalStep.z);
		Vec3 nextPosition = this.position().add(movement);
		this.setPos(nextPosition.x, nextPosition.y, nextPosition.z);
		this.setDeltaMovement(movement);
		alignToMovement(owner, movement);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		EntityType<?> sourceType = this.getSourceType();
		if (sourceType == null) {
			return super.getDefaultDimensions(pose).scale((float) PET_SCALE);
		}
		return sourceType.getDimensions().scale((float) PET_SCALE);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_SOURCE_TYPE_ID.equals(accessor)) {
			this.refreshDimensions();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	public boolean canBeSeenByAnyone() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE;
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	public void bindToOwner(ServerPlayer owner, int slot) {
		if (owner == null) {
			return;
		}

		this.ownerUuid = owner.getUUID();
		this.slotIndex = Math.max(0, Math.min(SLOT_OFFSETS.length - 1, slot));
	}

	public ServerPlayer getOwner() {
		if (this.ownerUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getServer().getPlayerList().getPlayer(this.ownerUuid);
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public int getSlotIndex() {
		return this.slotIndex;
	}

	public EntityType<?> getSourceType() {
		return resolveSourceType(this.entityData.get(DATA_SOURCE_TYPE_ID));
	}

	public void setSourceType(EntityType<?> sourceType) {
		EntityType<?> normalized = normalizeSourceType(sourceType);
		String sourceId = normalized == null ? "" : EntityType.getKey(normalized).toString();
		if (!sourceId.equals(this.entityData.get(DATA_SOURCE_TYPE_ID))) {
			this.entityData.set(DATA_SOURCE_TYPE_ID, sourceId);
			this.refreshDimensions();
		}
	}

	private void applyPetState() {
		this.setNoAi(true);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setSilent(true);
		this.setInvulnerable(true);
		this.setPersistenceRequired();
		this.clearFire();
		this.setCanPickUpLoot(false);
		if (this.getHealth() != this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	private void alignToMovement(ServerPlayer owner, Vec3 movement) {
		if (movement.lengthSqr() > 1.0E-6D) {
			float targetYaw = (float) (Mth.atan2(movement.x, movement.z) * (180.0D / Math.PI));
			this.setYRot(targetYaw);
			this.setYBodyRot(targetYaw);
			this.setYHeadRot(targetYaw);
			this.yRotO = targetYaw;
			this.yBodyRotO = targetYaw;
			this.yHeadRotO = targetYaw;
			return;
		}

		float ownerYaw = owner.getYRot();
		this.setYRot(ownerYaw);
		this.setYBodyRot(ownerYaw);
		this.setYHeadRot(ownerYaw);
	}

	public static Vec3 resolveDesiredPosition(ServerPlayer owner, int slot) {
		SlotOffset offset = SLOT_OFFSETS[Math.max(0, Math.min(SLOT_OFFSETS.length - 1, slot))];
		Vec3 forward = owner.getLookAngle();
		Vec3 horizontalForward = new Vec3(forward.x, 0.0D, forward.z);
		if (horizontalForward.lengthSqr() <= 1.0E-6D) {
			horizontalForward = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			horizontalForward = horizontalForward.normalize();
		}
		Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
		Vec3 base = owner.position().add(0.0D, 0.10D, 0.0D);
		return base.add(right.scale(offset.side())).subtract(horizontalForward.scale(offset.back()));
	}

	private static EntityType<?> normalizeSourceType(EntityType<?> sourceType) {
		if (sourceType == null || sourceType == MadokuEntities.PET) {
			return null;
		}
		if (!Mob.class.isAssignableFrom(sourceType.getBaseClass())) {
			return null;
		}
		return sourceType;
	}

	private static EntityType<?> resolveSourceType(String sourceId) {
		if (sourceId == null || sourceId.isBlank()) {
			return null;
		}

		EntityType<?> sourceType = EntityType.byString(sourceId).orElse(null);
		return normalizeSourceType(sourceType);
	}

	private record SlotOffset(double side, double back) {
	}
}
