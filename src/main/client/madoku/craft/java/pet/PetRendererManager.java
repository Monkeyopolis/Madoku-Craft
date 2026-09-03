package madoku.craft.java.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.animal.sheep.SheepFurModel;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Map;

/** Registers the client-side visual profiles for the neutral Madoku pet entity. */
public final class PetRendererManager {
	private PetRendererManager() {
	}

	@SuppressWarnings("deprecation")
	public static void initialize() {
		EntityRendererRegistry.register(PetEntitiesManager.PET_ENTITY, MadokuPetRenderer::new);
	}

	private static final class MadokuPetRenderer extends EntityRenderer<MadokuPetEntity, PetRenderState> {
		private static final Identifier BAT_TEXTURE = texture("textures/entity/bat/bat.png");
		private static final Identifier BEE_TEXTURE = texture("textures/entity/bee/bee.png");
		private static final Identifier CHICKEN_TEXTURE = texture("textures/entity/chicken/chicken_temperate.png");
		private static final Identifier COW_TEXTURE = texture("textures/entity/cow/cow_temperate.png");
		private static final Identifier CREEPER_TEXTURE = texture("textures/entity/creeper/creeper.png");
		private static final Identifier PIG_TEXTURE = texture("textures/entity/pig/pig_temperate.png");
		private static final Identifier SHEEP_TEXTURE = texture("textures/entity/sheep/sheep.png");
		private static final Identifier SKELETON_TEXTURE = texture("textures/entity/skeleton/skeleton.png");
		private static final Identifier SPIDER_TEXTURE = texture("textures/entity/spider/spider.png");
		private static final Identifier ZOMBIE_TEXTURE = texture("textures/entity/zombie/zombie.png");

		private final Map<String, PetModelProfile> profiles;

		MadokuPetRenderer(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.35F;
			this.profiles = Map.of(
				"minecraft:bat", new BatProfile(new BatModel(context.bakeLayer(ModelLayers.BAT)), BAT_TEXTURE),
				"minecraft:bee", new BeeProfile(new AdultBeeModel(context.bakeLayer(ModelLayers.BEE)), BEE_TEXTURE),
				"minecraft:chicken", new ChickenProfile(new AdultChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), CHICKEN_TEXTURE),
				"minecraft:cow", new LivingProfile(new CowModel(context.bakeLayer(ModelLayers.COW)), COW_TEXTURE),
				"minecraft:creeper", new CreeperProfile(new CreeperModel(context.bakeLayer(ModelLayers.CREEPER)), CREEPER_TEXTURE),
				"minecraft:pig", new LivingProfile(new PigModel(context.bakeLayer(ModelLayers.PIG)), PIG_TEXTURE),
				"minecraft:sheep", new SheepProfile(
					new SheepModel(context.bakeLayer(ModelLayers.SHEEP)),
					new SheepFurModel(context.bakeLayer(ModelLayers.SHEEP_WOOL)),
					SHEEP_TEXTURE
				),
				"minecraft:skeleton", new SkeletonProfile(new SkeletonModel<>(context.bakeLayer(ModelLayers.SKELETON)), SKELETON_TEXTURE),
				"minecraft:spider", new LivingProfile(new SpiderModel(context.bakeLayer(ModelLayers.SPIDER)), SPIDER_TEXTURE),
				"minecraft:zombie", new ZombieProfile(new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), ZOMBIE_TEXTURE)
			);
		}

		@Override
		public PetRenderState createRenderState() {
			return new PetRenderState();
		}

		@Override
		public void extractRenderState(MadokuPetEntity entity, PetRenderState state, float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			state.petId = PetConfigManager.normalizePetId(entity.petId());
			state.bodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
			float headRotation = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
			state.yRot = Mth.wrapDegrees(headRotation - state.bodyRot);
			state.xRot = entity.getXRot(partialTick);
			state.walkAnimationPos = entity.walkAnimation.position(partialTick);
			state.walkAnimationSpeed = entity.walkAnimation.speed(partialTick);
			state.scale = entity.getScale();
			state.ageScale = entity.getAgeScale();
			state.isBaby = entity.isBaby();
			state.isInWater = entity.isInWater();
			state.deathTime = entity.deathTime;
			state.pose = entity.getPose();
			state.eyeHeight = entity.getEyeHeight();
		}

		@Override
		public void submit(PetRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
			PetModelProfile profile = profiles.get(state.petId);
			if (profile == null) {
				super.submit(state, poseStack, collector, cameraState);
				return;
			}

			poseStack.pushPose();
			poseStack.scale(state.scale, state.scale, state.scale);
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.bodyRot));
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			profile.render(state, poseStack, collector);
			poseStack.popPose();
			super.submit(state, poseStack, collector, cameraState);
		}

		private static Identifier texture(String path) {
			return Identifier.fromNamespaceAndPath("minecraft", path);
		}
	}

	private static final class PetRenderState extends LivingEntityRenderState {
		private String petId = "";
	}

	private interface PetModelProfile {
		void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector);
	}

	private static final class LivingProfile implements PetModelProfile {
		private final EntityModel<LivingEntityRenderState> model;
		private final Identifier texture;

		private LivingProfile(EntityModel<LivingEntityRenderState> model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			model.setupAnim(source);
			collector.submitModel(model, source, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class BatProfile implements PetModelProfile {
		private final BatModel model;
		private final Identifier texture;

		private BatProfile(BatModel model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			BatRenderState state = copyLiving(source, new BatRenderState());
			state.isResting = false;
			state.flyAnimationState.start(0);
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class BeeProfile implements PetModelProfile {
		private final AdultBeeModel model;
		private final Identifier texture;

		private BeeProfile(AdultBeeModel model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			BeeRenderState state = copyLiving(source, new BeeRenderState());
			state.isOnGround = false;
			state.isAngry = false;
			state.hasNectar = false;
			state.hasStinger = false;
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class ChickenProfile implements PetModelProfile {
		private final AdultChickenModel model;
		private final Identifier texture;

		private ChickenProfile(AdultChickenModel model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			ChickenRenderState state = copyLiving(source, new ChickenRenderState());
			state.flap = 0.0F;
			state.flapSpeed = 0.0F;
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class CreeperProfile implements PetModelProfile {
		private final CreeperModel model;
		private final Identifier texture;

		private CreeperProfile(CreeperModel model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			CreeperRenderState state = copyLiving(source, new CreeperRenderState());
			state.swelling = 0.0F;
			state.isPowered = false;
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class SheepProfile implements PetModelProfile {
		private final SheepModel model;
		private final SheepFurModel woolModel;
		private final Identifier texture;

		private SheepProfile(SheepModel model, SheepFurModel woolModel, Identifier texture) {
			this.model = model;
			this.woolModel = woolModel;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			SheepRenderState state = copyLiving(source, new SheepRenderState());
			state.isSheared = false;
			state.woolColor = net.minecraft.world.item.DyeColor.WHITE;
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
			woolModel.setupAnim(state);
			collector.submitModel(woolModel, state, poseStack, MadokuPetRenderer.texture("textures/entity/sheep/sheep_wool.png"), source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class SkeletonProfile implements PetModelProfile {
		private final SkeletonModel<SkeletonRenderState> model;
		private final Identifier texture;

		private SkeletonProfile(SkeletonModel<SkeletonRenderState> model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			SkeletonRenderState state = copyLiving(source, new SkeletonRenderState());
			state.isAggressive = false;
			state.isShaking = false;
			state.isHoldingBow = false;
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static final class ZombieProfile implements PetModelProfile {
		private final ZombieModel<ZombieRenderState> model;
		private final Identifier texture;

		private ZombieProfile(ZombieModel<ZombieRenderState> model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}

		@Override
		public void render(PetRenderState source, PoseStack poseStack, SubmitNodeCollector collector) {
			ZombieRenderState state = copyLiving(source, new ZombieRenderState());
			state.isAggressive = false;
			state.isConverting = false;
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, texture, source.lightCoords, OverlayTexture.NO_OVERLAY, source.outlineColor, null);
		}
	}

	private static <S extends LivingEntityRenderState> S copyLiving(PetRenderState source, S target) {
		target.entityType = source.entityType;
		target.x = source.x;
		target.y = source.y;
		target.z = source.z;
		target.ageInTicks = source.ageInTicks;
		target.boundingBoxWidth = source.boundingBoxWidth;
		target.boundingBoxHeight = source.boundingBoxHeight;
		target.eyeHeight = source.eyeHeight;
		target.distanceToCameraSq = source.distanceToCameraSq;
		target.isInvisible = source.isInvisible;
		target.isDiscrete = source.isDiscrete;
		target.displayFireAnimation = source.displayFireAnimation;
		target.lightCoords = source.lightCoords;
		target.outlineColor = source.outlineColor;
		target.passengerOffset = source.passengerOffset;
		target.nameTag = source.nameTag;
		target.scoreText = source.scoreText;
		target.nameTagAttachment = source.nameTagAttachment;
		target.shadowRadius = source.shadowRadius;
		target.bodyRot = source.bodyRot;
		target.yRot = source.yRot;
		target.xRot = source.xRot;
		target.deathTime = source.deathTime;
		target.walkAnimationPos = source.walkAnimationPos;
		target.walkAnimationSpeed = source.walkAnimationSpeed;
		target.scale = source.scale;
		target.ageScale = source.ageScale;
		target.isUpsideDown = source.isUpsideDown;
		target.isFullyFrozen = source.isFullyFrozen;
		target.isBaby = source.isBaby;
		target.isInWater = source.isInWater;
		target.isAutoSpinAttack = source.isAutoSpinAttack;
		target.hasRedOverlay = source.hasRedOverlay;
		target.isInvisibleToPlayer = source.isInvisibleToPlayer;
		target.bedOrientation = source.bedOrientation;
		target.pose = source.pose;
		return target;
	}
}
