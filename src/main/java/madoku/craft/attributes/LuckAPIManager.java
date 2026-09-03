package madoku.craft.attributes;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;

/** Public contract for the luck attribute subsystem. */
public final class LuckAPIManager {
	private LuckAPIManager() {
	}

	public static void initialize() { MadokuLuckManager.initialize(); }
	public static boolean isEnabled() { return MadokuLuckManager.isEnabled(); }
	public static void applyClientSynchronizedEnabled(boolean enabled) { MadokuLuckManager.applyClientSynchronizedEnabled(enabled); }
	public static void resetClientSynchronizedSettings() { MadokuLuckManager.resetClientSynchronizedSettings(); }
	public static void handlePlayerEffectsChanged(ServerPlayer player) { MadokuLuckManager.handlePlayerEffectsChanged(player); }
	public static boolean shouldOverrideVanillaLuckEffect(LivingEntity entity, MobEffect effect) {
		return MadokuLuckManager.shouldOverrideVanillaLuckEffect(entity, effect);
	}
	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		return MadokuLuckManager.shouldOverrideVanillaEffectAttributes(entity, effect);
	}
	public static double reduceCreeperGriefChanceForTarget(LivingEntity target, double chance) {
		return MadokuLuckManager.reduceCreeperGriefChanceForTarget(target, chance);
	}
	public static double reduceHostileRangedAccuracyForTarget(LivingEntity target, double accuracy) {
		return MadokuLuckManager.reduceHostileRangedAccuracyForTarget(target, accuracy);
	}
	public static boolean shouldApplyPlayerMeleeCrit(Player player, Entity target) {
		return MadokuLuckManager.shouldApplyPlayerMeleeCrit(player, target);
	}
	public static float playerCritDamageMultiplier() { return MadokuLuckManager.playerCritDamageMultiplier(); }
	public static double resolveLootLuckStat(ServerPlayer player) { return MadokuLuckManager.resolveLootLuckStat(player); }
	public static ServerPlayer resolveLootPlayer(LootContext lootContext) { return MadokuLuckManager.resolveLootPlayer(lootContext); }
	public static ServerPlayer resolveActiveDropPlayer() { return MadokuLuckManager.resolveActiveDropPlayer(); }
	public static boolean isActiveDropPlayerPlacedBlock() { return MadokuLuckManager.isActiveDropPlayerPlacedBlock(); }
	public static void applyGeneratedLoot(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		MadokuLuckManager.applyGeneratedLoot(lootContext, stacks);
	}
	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks) {
		MadokuLuckManager.applyManagedCropDrops(random, stacks);
	}
	public static void applyManagedCropDrops(RandomSource random, ObjectArrayList<ItemStack> stacks, ItemStack tool) {
		MadokuLuckManager.applyManagedCropDrops(random, stacks, tool);
	}
	public static void applyManagedCropDrops(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		MadokuLuckManager.applyManagedCropDrops(lootContext, stacks);
	}
	public static void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) {
		MadokuLuckManager.applyManagedMobDrops(player, random, stacks);
	}
	public static Consumer<ItemStack> wrapMobDeathLootConsumer(
		ServerLevel level,
		LivingEntity target,
		DamageSource damageSource,
		boolean causedByPlayer,
		Consumer<ItemStack> downstream
	) {
		return MadokuLuckManager.wrapMobDeathLootConsumer(level, target, damageSource, causedByPlayer, downstream);
	}
	public static double resolvePlayerCriticalDamageMultiplier(Player player, Entity target) {
		return MadokuLuckManager.resolvePlayerCriticalDamageMultiplier(player, target);
	}
	public static void beginBlockDropContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		MadokuLuckManager.beginBlockDropContext(level, player, pos, state);
	}
	public static void endBlockDropContext() { MadokuLuckManager.endBlockDropContext(); }
}
