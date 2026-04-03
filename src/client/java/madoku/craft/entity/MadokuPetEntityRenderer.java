package madoku.craft.entity;

import madoku.craft.pet.MadokuPetEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuPetEntityRenderer extends EntityRenderer<MadokuPetEntity, MadokuPetEntityRenderer.PetRenderState> {
	private static final float PET_SCALE = 0.35F;
	private final Map<UUID, Mob> renderCopies = new HashMap<>();

	public MadokuPetEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public PetRenderState createRenderState() {
		return new PetRenderState();
	}

	@Override
	public void extractRenderState(MadokuPetEntity entity, PetRenderState renderState, float tickDelta) {
		super.extractRenderState(entity, renderState, tickDelta);
		renderState.delegateState = null;
		if (!(entity.level() instanceof ClientLevel clientLevel)) {
			return;
		}

		EntityType<?> sourceType = entity.getSourceType();
		Mob renderCopy = resolveRenderCopy(entity.getUUID(), clientLevel, sourceType);
		if (renderCopy == null) {
			return;
		}

		syncRenderCopy(entity, renderCopy);
		EntityRenderState delegateState = this.entityRenderDispatcher.extractEntity(renderCopy, tickDelta);
		if (delegateState == null) {
			return;
		}

		delegateState.shadowRadius *= PET_SCALE;
		delegateState.boundingBoxWidth *= PET_SCALE;
		delegateState.boundingBoxHeight *= PET_SCALE;
		delegateState.eyeHeight *= PET_SCALE;
		if (delegateState instanceof LivingEntityRenderState livingState) {
			livingState.scale *= PET_SCALE;
			livingState.ageScale *= PET_SCALE;
		}

		renderState.delegateState = delegateState;
	}

	@Override
	public void submit(PetRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (renderState.delegateState == null) {
			return;
		}
		submitDelegate(this.entityRenderDispatcher, renderState.delegateState, poseStack, submitNodeCollector, cameraRenderState);
	}

	private Mob resolveRenderCopy(UUID petId, ClientLevel level, EntityType<?> sourceType) {
		if (petId == null || sourceType == null) {
			if (petId != null) {
				this.renderCopies.remove(petId);
			}
			return null;
		}

		Mob cached = this.renderCopies.get(petId);
		if (cached != null && cached.getType() == sourceType) {
			return cached;
		}

		Entity created = sourceType.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof Mob mob)) {
			this.renderCopies.remove(petId);
			return null;
		}

		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.noPhysics = true;
		mob.setSilent(true);
		mob.setInvulnerable(true);
		this.renderCopies.put(petId, mob);
		return mob;
	}

	private static void syncRenderCopy(MadokuPetEntity pet, Mob renderCopy) {
		renderCopy.copyPosition(pet);
		renderCopy.setOldPosAndRot(pet.oldPosition(), pet.yRotO, pet.xRotO);
		renderCopy.setYRot(pet.getYRot());
		renderCopy.setXRot(pet.getXRot());
		renderCopy.setYBodyRot(pet.yBodyRot);
		renderCopy.yBodyRotO = pet.yBodyRotO;
		renderCopy.setYHeadRot(pet.getYHeadRot());
		renderCopy.yHeadRotO = pet.yHeadRotO;
		renderCopy.tickCount = pet.tickCount;
		renderCopy.setInvisible(pet.isInvisible());
		renderCopy.setCustomName(pet.getCustomName());
		renderCopy.setCustomNameVisible(pet.isCustomNameVisible());
		renderCopy.setPose(pet.getPose());
		renderCopy.setDeltaMovement(pet.getDeltaMovement());
	}

	private static <S extends EntityRenderState> void submitDelegate(
		EntityRenderDispatcher dispatcher,
		S delegateState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState
	) {
		dispatcher.getRenderer(delegateState).submit(delegateState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public static final class PetRenderState extends EntityRenderState {
		private EntityRenderState delegateState;
	}
}
