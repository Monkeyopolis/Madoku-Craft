package madoku.craft.luck;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.MadokuCraft;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.farming.system.MadokuFarming;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

public final class MadokuLuckManager {
	private static final Identifier BASE_LUCK_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_luck");
	private static final Identifier EFFECT_LUCK_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_luck_effect");

	private static volatile LuckConfigManager.Settings settings = LuckConfigManager.Settings.defaults();
	private static final ThreadLocal<ActiveDropContext> ACTIVE_DROP_CONTEXT = new ThreadLocal<>();

	private MadokuLuckManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		ServerPlayerEvents.JOIN.register(MadokuLuckManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshPlayerLuck(newPlayer));
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		refreshPlayerLuck(player);
	}

	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return effect == MobEffects.LUCK.value();
	}

	public static double reduceCreeperGriefChanceForTarget(LivingEntity target, double chance) {
		return reduceChanceByTargetLuck(
			target,
			chance,
			settings.creeperGriefReductionMultiplier,
			"luck.creeper_grief_reduction_applied",
			"luck.creeper_grief_reduction_skipped",
			"creeper_grief_chance"
		);
	}

	public static double reduceHostileRangedAccuracyForTarget(LivingEntity target, double accuracy) {
		return reduceChanceByTargetLuck(
			target,
			accuracy,
			settings.rangedAccuracyReductionMultiplier,
			"luck.ranged_accuracy_reduction_applied",
			"luck.ranged_accuracy_reduction_skipped",
			"ranged_accuracy"
		);
	}

	public static boolean shouldApplyPlayerMeleeCrit(Player player, Entity target) {
		if (!settings.enabled || !(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		ServerLevel level = serverPlayer.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		double critChance = resolveLuckProcChance(serverPlayer);
		if (critChance <= 0.0d) {
			emitLuckDebug(
				"luck.player_crit_skipped",
				level,
				target == null ? player.blockPosition() : target.blockPosition(),
				subjectForCombatTarget(target),
				Map.of(
					"reason", "non_positive_chance",
					"player", serverPlayer.getScoreboardName(),
					"chance", Double.toString(critChance),
					"multiplier", Float.toString(settings.playerCritDamageMultiplier)
				)
			);
			return false;
		}

		RandomSource random = serverPlayer.getRandom();
		double roll = random == null ? 1.0d : random.nextDouble();
		boolean crit = roll < critChance;
		emitLuckDebug(
			crit ? "luck.player_crit_applied" : "luck.player_crit_skipped",
			level,
			target == null ? player.blockPosition() : target.blockPosition(),
			subjectForCombatTarget(target),
			Map.of(
				"reason", crit ? "chance_succeeded" : "chance_failed",
				"player", serverPlayer.getScoreboardName(),
				"chance", Double.toString(critChance),
				"roll", Double.toString(roll),
				"multiplier", Float.toString(settings.playerCritDamageMultiplier)
			)
		);
		return crit;
	}

	public static float playerCritDamageMultiplier() {
		return settings.playerCritDamageMultiplier;
	}

	public static double resolveLootLuckStat(ServerPlayer player) {
		if (!settings.enabled || player == null) {
			return 0.0d;
		}
		return resolveLuckProcChance(player) * 100.0d;
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		refreshPlayerLuck(player);
	}

	private static void refreshPlayerLuck(ServerPlayer player) {
		applyBaseLuck(player);
		applyLuckEffect(player);
	}

	private static void applyBaseLuck(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance luckAttribute = player.getAttribute(Attributes.LUCK);
		if (luckAttribute == null) {
			return;
		}

		luckAttribute.removeModifier(BASE_LUCK_MODIFIER_ID);
		if (settings.baseLuck > 0.0d) {
			luckAttribute.addOrUpdateTransientModifier(
				new AttributeModifier(BASE_LUCK_MODIFIER_ID, settings.baseLuck, AttributeModifier.Operation.ADD_VALUE)
			);
		}
	}

	private static void applyLuckEffect(ServerPlayer player) {
		if (player == null) {
			return;
		}

		AttributeInstance luckAttribute = player.getAttribute(Attributes.LUCK);
		if (luckAttribute == null) {
			return;
		}

		luckAttribute.removeModifier(EFFECT_LUCK_MODIFIER_ID);
		int effectLevel = getLuckEffectLevel(player);
		double bonus = settings.enabled ? effectLevel * 0.05d : 0.0d;
		if (bonus > 0.0d) {
			luckAttribute.addOrUpdateTransientModifier(
				new AttributeModifier(EFFECT_LUCK_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE)
			);
		}
	}

	private static int getLuckEffectLevel(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		MobEffectInstance effectInstance = player.getEffect(MobEffects.LUCK);
		return effectInstance == null ? 0 : effectInstance.getAmplifier() + 1;
	}

	public static void applyGeneratedLoot(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (!settings.enabled || lootContext == null || stacks == null || stacks.isEmpty()) {
			return;
		}

		ActiveDropContext context = ACTIVE_DROP_CONTEXT.get();
		if (context != null) {
			applyGeneratedBlockDrops(lootContext, stacks, context);
			return;
		}

		applyGeneratedMobDrops(lootContext, stacks);
	}

	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) {
		if (!settings.enabled || stacks == null || stacks.isEmpty()) {
			return;
		}

		ActiveDropContext context = ACTIVE_DROP_CONTEXT.get();
		if (context == null) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				null,
				null,
				"block:managed_crop",
				Map.of(
					"reason", "missing_block_context",
					"managed_crop", Boolean.TRUE.toString()
				)
			);
			return;
		}

		boolean managedCrop = MadokuFarming.isManagedCrop(context.level, context.pos, context.state)
			&& MadokuFarming.isCropHarvestReady(context.level, context.pos, context.state);
		if (!managedCrop) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "managed_crop_not_detected",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.FALSE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}
		if (context.player.isCreative()) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "creative",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		double dropBonusMultiplier = resolveDropBonusMultiplier(true);
		double luckValue = resolveLuckValue(context.player);
		double dropChance = resolveLuckProcChance(context.player);
		if (dropBonusMultiplier <= 0.0d || dropChance <= 0.0d) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", dropBonusMultiplier <= 0.0d ? "non_positive_multiplier" : "non_positive_chance",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		double roll = random == null ? 1.0d : random.nextDouble();
		if (roll >= dropChance) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "chance_failed",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"chance", Double.toString(dropChance),
					"roll", Double.toString(roll),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		DropScalingResult scaling = scaleStacks(stacks, dropBonusMultiplier, random);
		emitLuckDebug(
			"luck.drop_scale_applied",
			context.level,
			context.pos,
			subjectForPos("block", context.pos),
			Map.ofEntries(
				Map.entry("player", context.player.getScoreboardName()),
				Map.entry("state", context.state.getBlock().toString()),
				Map.entry("luck", Double.toString(luckValue)),
				Map.entry("chance", Double.toString(dropChance)),
				Map.entry("roll", Double.toString(roll)),
				Map.entry("managed_crop", Boolean.TRUE.toString()),
				Map.entry("drop_multiplier", Double.toString(settings.dropMultiplier)),
				Map.entry("effective_drop_multiplier", Double.toString(dropBonusMultiplier)),
				Map.entry("bonus_multiplier", Double.toString(dropBonusMultiplier)),
				Map.entry("stacks", Integer.toString(scaling.scaledStacks())),
				Map.entry("original_total", Integer.toString(scaling.originalTotal())),
				Map.entry("extra_total", Integer.toString(scaling.extraTotal()))
			)
		);
	}

	public static void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (lootContext == null) {
			applyManagedCropDrops((RandomSource) null, stacks);
			return;
		}
		applyManagedCropDrops(lootContext.getRandom(), stacks);
	}

	public static Consumer<ItemStack> wrapMobDeathLootConsumer(
		ServerLevel level,
		LivingEntity target,
		DamageSource damageSource,
		boolean causedByPlayer,
		Consumer<ItemStack> downstream
	) {
		if (!settings.enabled || level == null || target == null || downstream == null || ACTIVE_DROP_CONTEXT.get() != null) {
			return downstream;
		}

		if (!(target instanceof Mob mob)) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				null,
				Map.of(
					"reason", "non_mob_target",
					"entity", target.getType().toShortString(),
					"entity_class", target.getClass().getSimpleName()
				)
			);
			return downstream;
		}

		ServerPlayer player = resolveMobLootPlayer(target, damageSource);
		if (!causedByPlayer && player == null) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				mob,
				Map.of("reason", "not_player_kill", "mob", mob.getType().toShortString())
			);
			return downstream;
		}
		if (player == null) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				mob,
				Map.of("reason", "missing_player")
			);
			return downstream;
		}
		if (player.isCreative()) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				mob,
				Map.of(
					"reason", "creative",
					"player", player.getScoreboardName(),
					"mob", mob.getType().toShortString()
				)
			);
			return downstream;
		}

		double mobDropBonusMultiplier = resolveMobDropBonusMultiplier();
		if (mobDropBonusMultiplier <= 0.0d) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				mob,
				Map.of(
					"reason", "non_positive_multiplier",
					"player", player.getScoreboardName(),
					"multiplier", Double.toString(mobDropBonusMultiplier),
					"mob", mob.getType().toShortString()
				)
			);
			return downstream;
		}

		double luckValue = resolveLuckValue(player);
		double mobDropChance = resolveLuckProcChance(player);
		if (mobDropChance <= 0.0d) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				mob,
				Map.of(
					"reason", "non_positive_chance",
					"player", player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"mob", mob.getType().toShortString()
				)
			);
			return downstream;
		}

		RandomSource random = level.getRandom();
		double roll = random == null ? 1.0d : random.nextDouble();
		if (roll >= mobDropChance) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				level,
				mob,
				Map.of(
					"reason", "chance_failed",
					"player", player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"chance", Double.toString(mobDropChance),
					"roll", Double.toString(roll),
					"mob", mob.getType().toShortString()
				)
			);
			return downstream;
		}

		emitMobLuckDebug(
			"luck.mob_drop_scale_applied",
			level,
			mob,
			Map.of(
				"player", player.getScoreboardName(),
				"mob", mob.getType().toShortString(),
				"luck", Double.toString(luckValue),
				"chance", Double.toString(mobDropChance),
				"roll", Double.toString(roll),
				"mob_drop_multiplier", Double.toString(settings.mobDropMultiplier),
				"bonus_multiplier", Double.toString(mobDropBonusMultiplier)
			)
		);

		return stack -> {
			if (stack != null && !stack.isEmpty()) {
				int extraCount = calculateExtraCount(stack.getCount(), mobDropBonusMultiplier, random);
				if (extraCount > 0) {
					stack.grow(extraCount);
				}
			}
			downstream.accept(stack);
		};
	}

	private static void applyGeneratedBlockDrops(
		LootContext lootContext,
		ObjectArrayList<ItemStack> stacks,
		ActiveDropContext context
	) {
		boolean creative = context.player.isCreative();
		boolean playerPlaced = MadokuPlacedBlocks.isPlayerPlacedBlock(context.level, context.pos);
		boolean managedCrop = MadokuFarming.isManagedCrop(context.level, context.pos, context.state)
			&& MadokuFarming.isCropHarvestReady(context.level, context.pos, context.state);
		emitLuckDebug(
			"luck.place_check",
			context.level,
			context.pos,
			subjectForPos("block", context.pos),
			Map.of(
				"player", context.player.getScoreboardName(),
				"creative", Boolean.toString(creative),
				"player_placed", Boolean.toString(playerPlaced),
				"managed_crop", Boolean.toString(managedCrop),
				"state", context.state.getBlock().toString()
			)
		);
		if (creative || (playerPlaced && !managedCrop)) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", creative ? "creative" : "player_placed",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.toString(managedCrop),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}
		if (managedCrop) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "managed_crop_delegate",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.TRUE.toString(),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		double dropBonusMultiplier = resolveDropBonusMultiplier(managedCrop);
		if (dropBonusMultiplier <= 0.0d) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "non_positive_multiplier",
					"player", context.player.getScoreboardName(),
					"managed_crop", Boolean.toString(managedCrop),
					"multiplier", Double.toString(dropBonusMultiplier)
				)
			);
			return;
		}

		double luckValue = resolveLuckValue(context.player);
		double dropChance = resolveLuckProcChance(context.player);
		if (dropChance <= 0.0d) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "non_positive_chance",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"managed_crop", Boolean.toString(managedCrop),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		RandomSource random = lootContext.getRandom();
		double roll = random == null ? 1.0d : random.nextDouble();
		if (roll >= dropChance) {
			emitLuckDebug(
				"luck.drop_scale_skipped",
				context.level,
				context.pos,
				subjectForPos("block", context.pos),
				Map.of(
					"reason", "chance_failed",
					"player", context.player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"chance", Double.toString(dropChance),
					"roll", Double.toString(roll),
					"managed_crop", Boolean.toString(managedCrop),
					"state", context.state.getBlock().toString()
				)
			);
			return;
		}

		DropScalingResult scaling = scaleStacks(stacks, dropBonusMultiplier, random);
		emitLuckDebug(
			"luck.drop_scale_applied",
			context.level,
			context.pos,
			subjectForPos("block", context.pos),
			Map.ofEntries(
				Map.entry("player", context.player.getScoreboardName()),
				Map.entry("state", context.state.getBlock().toString()),
				Map.entry("luck", Double.toString(luckValue)),
				Map.entry("chance", Double.toString(dropChance)),
				Map.entry("roll", Double.toString(roll)),
				Map.entry("managed_crop", Boolean.toString(managedCrop)),
				Map.entry("drop_multiplier", Double.toString(settings.dropMultiplier)),
				Map.entry("effective_drop_multiplier", Double.toString(dropBonusMultiplier)),
				Map.entry("bonus_multiplier", Double.toString(dropBonusMultiplier)),
				Map.entry("stacks", Integer.toString(scaling.scaledStacks())),
				Map.entry("original_total", Integer.toString(scaling.originalTotal())),
				Map.entry("extra_total", Integer.toString(scaling.extraTotal()))
			)
		);
	}

	private static double resolveDropBonusMultiplier(boolean managedCrop) {
		return Math.max(0.0d, managedCrop ? 0.5d : settings.dropMultiplier);
	}

	private static void applyGeneratedMobDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		Entity entity = resolveLootContextParameter(lootContext, "THIS_ENTITY", Entity.class);
		if (entity == null) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext == null ? null : lootContext.getLevel(),
				null,
				Map.of("reason", "missing_this_entity")
			);
			return;
		}
		if (!(entity instanceof Mob mob)) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				null,
				Map.of(
					"reason", "non_mob_entity",
					"entity", entity.getType().toShortString(),
					"entity_class", entity.getClass().getSimpleName()
				)
			);
			return;
		}

		ServerPlayer player = resolveMobLootPlayer(lootContext);
		if (player == null) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of("reason", "missing_player", "state", mob.getType().toShortString())
			);
			return;
		}
		if (player.isCreative()) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "creative",
					"player", player.getScoreboardName(),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		double mobDropBonusMultiplier = resolveMobDropBonusMultiplier();
		if (mobDropBonusMultiplier <= 0.0d) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "non_positive_multiplier",
					"player", player.getScoreboardName(),
					"multiplier", Double.toString(mobDropBonusMultiplier),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		double luckValue = resolveLuckValue(player);
		double mobDropChance = resolveLuckProcChance(player);
		if (mobDropChance <= 0.0d) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "non_positive_chance",
					"player", player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		RandomSource random = lootContext.getRandom();
		double roll = random == null ? 1.0d : random.nextDouble();
		if (roll >= mobDropChance) {
			emitMobLuckDebug(
				"luck.mob_drop_scale_skipped",
				lootContext.getLevel(),
				mob,
				Map.of(
					"reason", "chance_failed",
					"player", player.getScoreboardName(),
					"luck", Double.toString(luckValue),
					"chance", Double.toString(mobDropChance),
					"roll", Double.toString(roll),
					"mob", mob.getType().toShortString()
				)
			);
			return;
		}

		DropScalingResult scaling = scaleStacks(stacks, mobDropBonusMultiplier, random);
		emitMobLuckDebug(
			"luck.mob_drop_scale_applied",
			lootContext.getLevel(),
			mob,
			Map.of(
				"player", player.getScoreboardName(),
				"mob", mob.getType().toShortString(),
				"luck", Double.toString(luckValue),
				"chance", Double.toString(mobDropChance),
				"roll", Double.toString(roll),
				"mob_drop_multiplier", Double.toString(settings.mobDropMultiplier),
				"bonus_multiplier", Double.toString(mobDropBonusMultiplier),
				"stacks", Integer.toString(scaling.scaledStacks()),
				"original_total", Integer.toString(scaling.originalTotal()),
				"extra_total", Integer.toString(scaling.extraTotal())
			)
		);
	}

	private static double resolveMobDropBonusMultiplier() {
		return Math.max(0.0d, settings.mobDropMultiplier);
	}

	private static double reduceChanceByTargetLuck(
		LivingEntity target,
		double baseValue,
		double reductionMultiplier,
		String appliedMetricId,
		String skippedMetricId,
		String valueField
	) {
		ServerLevel world = target != null && target.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		String subject = target instanceof ServerPlayer player
			? "player:" + player.getScoreboardName()
			: target == null
				? "player:unknown"
				: "entity:" + target.getType().toShortString();
		if (!settings.enabled) {
			double clampedBaseValue = clampDouble(baseValue, 0.0d, 1.0d);
			emitLuckDebug(
				skippedMetricId,
				world,
				target == null ? null : target.blockPosition(),
				subject,
				Map.of(
					"reason", "disabled",
					valueField, Double.toString(clampedBaseValue)
				)
			);
			return clampedBaseValue;
		}
		double clampedBaseValue = clampDouble(baseValue, 0.0d, 1.0d);
		if (target == null) {
			emitLuckDebug(
				skippedMetricId,
				world,
				null,
				subject,
				Map.of(
					"reason", "missing_target",
					valueField, Double.toString(clampedBaseValue),
					"reduction_multiplier", Double.toString(Math.max(0.0d, reductionMultiplier))
				)
			);
			return clampedBaseValue;
		}
		if (reductionMultiplier <= 0.0d) {
			emitLuckDebug(
				skippedMetricId,
				world,
				target.blockPosition(),
				subject,
				Map.of(
					"reason", "non_positive_multiplier",
					"target", target.getScoreboardName(),
					valueField, Double.toString(clampedBaseValue),
					"reduction_multiplier", Double.toString(reductionMultiplier)
				)
			);
			return clampedBaseValue;
		}

		double luckChance = resolveTargetLuckReductionChance(target);
		double sanitizedReductionMultiplier = Math.max(0.0d, reductionMultiplier);
		double reduction = luckChance * sanitizedReductionMultiplier;
		double finalValue = clampDouble(clampedBaseValue - reduction, 0.0d, 1.0d);
		if (reduction <= 0.0d) {
			emitLuckDebug(
				skippedMetricId,
				world,
				target.blockPosition(),
				subject,
				Map.of(
					"reason", "non_positive_luck",
					"target", target.getScoreboardName(),
					"value", Double.toString(clampedBaseValue),
					valueField, Double.toString(clampedBaseValue),
					"luck", Double.toString(luckChance),
					"reduction_multiplier", Double.toString(sanitizedReductionMultiplier)
				)
			);
			return finalValue;
		}

		emitLuckDebug(
			appliedMetricId,
			world,
			target.blockPosition(),
			subject,
			Map.of(
				"target", target.getScoreboardName(),
				"value", Double.toString(clampedBaseValue),
				valueField, Double.toString(clampedBaseValue),
				"final_value", Double.toString(finalValue),
				"luck", Double.toString(luckChance),
				"reduction_multiplier", Double.toString(sanitizedReductionMultiplier),
				"reduction", Double.toString(reduction)
			)
		);
		return finalValue;
	}

	private static double resolveTargetLuckReductionChance(LivingEntity target) {
		AttributeInstance luckAttribute = target == null ? null : target.getAttribute(Attributes.LUCK);
		double luckValue = luckAttribute == null ? 0.0d : luckAttribute.getValue();
		if (!Double.isFinite(luckValue)) {
			return 0.0d;
		}
		return clampDouble(luckValue / 100.0d, 0.0d, 1.0d);
	}

	private static double resolveLuckProcChance(ServerPlayer player) {
		return clampDouble(resolveLuckValue(player), 0.0d, 1.0d);
	}

	private static double resolveLuckValue(ServerPlayer player) {
		AttributeInstance luckAttribute = player == null ? null : player.getAttribute(Attributes.LUCK);
		double luckValue = luckAttribute == null ? settings.baseLuck : luckAttribute.getValue();
		if (!Double.isFinite(luckValue)) {
			return settings.baseLuck;
		}
		return luckValue;
	}

	private static int calculateExtraCount(int originalCount, double dropBonusMultiplier, RandomSource random) {
		if (originalCount <= 0 || dropBonusMultiplier <= 0.0d) {
			return 0;
		}

		double rawExtraCount = originalCount * dropBonusMultiplier;
		if (!Double.isFinite(rawExtraCount) || rawExtraCount <= 0.0d) {
			return 0;
		}

		int guaranteedExtraCount = (int) Math.floor(rawExtraCount);
		double fractionalExtraCount = rawExtraCount - guaranteedExtraCount;
		if (fractionalExtraCount > 0.0d && random != null && random.nextDouble() < fractionalExtraCount) {
			guaranteedExtraCount++;
		}
		return guaranteedExtraCount;
	}

	private static DropScalingResult scaleStacks(
		ObjectArrayList<ItemStack> stacks,
		double dropBonusMultiplier,
		RandomSource random
	) {
		int scaledStacks = 0;
		int originalTotal = 0;
		int extraTotal = 0;
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}

			int originalCount = stack.getCount();
			int extraCount = calculateExtraCount(originalCount, dropBonusMultiplier, random);
			originalTotal += originalCount;
			if (extraCount <= 0) {
				continue;
			}

			stack.grow(extraCount);
			scaledStacks++;
			extraTotal += extraCount;
		}
		return new DropScalingResult(scaledStacks, originalTotal, extraTotal);
	}

	private static void loadStaticConfig() {
		settings = LuckConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public static void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (level == null || player == null || pos == null || state == null) {
			ACTIVE_DROP_CONTEXT.remove();
			return;
		}

		ACTIVE_DROP_CONTEXT.set(new ActiveDropContext(level, player, pos.immutable(), state));
		emitLuckDebug(
			"luck.drop_context_begin",
			level,
			pos,
			subjectForPos("block", pos),
			Map.of(
				"player", player.getScoreboardName(),
				"state", state.getBlock().toString(),
				"luck", Double.toString(resolveLuckValue(player))
			)
		);
	}

	public static void endBlockDropContext() {
		ActiveDropContext context = ACTIVE_DROP_CONTEXT.get();
		if (context != null) {
			MadokuPlacedBlocks.consumePlacedBlock(context.level, context.pos);
		}
		ACTIVE_DROP_CONTEXT.remove();
	}

	static void emitLuckDebug(String metricId, ServerLevel world, BlockPos pos, String subject, Map<String, String> fields) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.LUCK, metricId)) {
			return;
		}

		MadokuDebug.EventBuilder builder = MadokuDebug.event(metricId, MadokuDebug.Domain.LUCK)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(subject == null || subject.isBlank() ? "global" : subject);

		if (pos != null) {
			builder.field("pos", pos.toShortString());
		}
		if (fields != null) {
			for (Map.Entry<String, String> entry : fields.entrySet()) {
				if (entry != null) {
					builder.field(entry.getKey(), entry.getValue());
				}
			}
		}
		builder.log();
	}

	private static void emitMobLuckDebug(String metricId, ServerLevel world, Mob mob, Map<String, String> fields) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.LUCK, metricId)) {
			return;
		}

		MadokuDebug.EventBuilder builder = MadokuDebug.event(metricId, MadokuDebug.Domain.LUCK)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(mob == null ? "mob:unknown" : "mob:" + mob.getType().toShortString());

		if (mob != null) {
			builder.field("pos", mob.blockPosition().toShortString());
		}
		if (fields != null) {
			for (Map.Entry<String, String> entry : fields.entrySet()) {
				if (entry != null) {
					builder.field(entry.getKey(), entry.getValue());
				}
			}
		}
		builder.log();
	}

	private static ServerPlayer resolveMobLootPlayer(LootContext lootContext) {
		ServerPlayer player = resolveLootContextParameter(lootContext, "LAST_DAMAGE_PLAYER", ServerPlayer.class);
		if (player != null) {
			return player;
		}

		player = resolveLootContextParameter(lootContext, "ATTACKING_ENTITY", ServerPlayer.class);
		if (player != null) {
			return player;
		}

		return resolveLootContextParameter(lootContext, "KILLER_ENTITY", ServerPlayer.class);
	}

	private static ServerPlayer resolveMobLootPlayer(LivingEntity target, DamageSource damageSource) {
		if (damageSource != null) {
			Entity attacker = damageSource.getEntity();
			if (attacker instanceof ServerPlayer player) {
				return player;
			}
		}

		return target.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
	}

	private static <T> T resolveLootContextParameter(LootContext lootContext, String fieldName, Class<T> targetType) {
		if (lootContext == null || fieldName == null || fieldName.isBlank() || targetType == null) {
			return null;
		}

		Object parameter = resolveLootContextParameterKey(fieldName);
		if (parameter == null) {
			return null;
		}

		try {
			Method hasParameter = findLootContextMethod(lootContext.getClass(), "hasParameter", parameter.getClass());
			if (hasParameter != null) {
				Object present = hasParameter.invoke(lootContext, parameter);
				if (!(present instanceof Boolean) || !((Boolean) present)) {
					return null;
				}
			}

			for (String methodName : new String[] { "getParameter", "getParam", "getOptionalParameter", "getParamOrNull", "get" }) {
				Method method = findLootContextMethod(lootContext.getClass(), methodName, parameter.getClass());
				if (method == null) {
					continue;
				}

				Object value = method.invoke(lootContext, parameter);
				if (targetType.isInstance(value)) {
					return targetType.cast(value);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}

		return null;
	}

	private static Object resolveLootContextParameterKey(String fieldName) {
		try {
			Field field = LootContextParams.class.getField(fieldName);
			return field.get(null);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}
	}

	private static Method findLootContextMethod(Class<?> type, String name, Class<?> parameterType) {
		if (type == null || name == null || parameterType == null) {
			return null;
		}

		for (Method method : type.getMethods()) {
			if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> candidateType = method.getParameterTypes()[0];
			if (candidateType.isAssignableFrom(parameterType) || parameterType.isAssignableFrom(candidateType)) {
				return method;
			}
		}
		return null;
	}

	private static String subjectForPos(String prefix, BlockPos pos) {
		if (pos == null) {
			return prefix == null || prefix.isBlank() ? "global" : prefix;
		}
		String safePrefix = prefix == null || prefix.isBlank() ? "block" : prefix;
		return safePrefix + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String subjectForCombatTarget(Entity target) {
		if (target == null) {
			return "combat:unknown";
		}
		return "combat:" + target.getType().toShortString();
	}

	private record DropScalingResult(int scaledStacks, int originalTotal, int extraTotal) {
	}

	private record ActiveDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
	}

}
