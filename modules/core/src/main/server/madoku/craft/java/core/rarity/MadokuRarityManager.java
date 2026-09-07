package madoku.craft.java.core.rarity;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import madoku.craft.java.core.rarity.RarityTierAPIManager.Tier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Orchestrates Core's generic rarity subsystem and its synchronized settings. */
public final class MadokuRarityManager {
	private static volatile Boolean clientSynchronizedEnabled;

	private MadokuRarityManager() {
	}

	public static void initialize() {
		RarityConfigManager.initialize();
		RarityRuntimeManager.initialize();
		SyncConfigAPIManager.register(
			"rarity",
			MadokuRarityManager::createClientSyncSnapshot,
			MadokuRarityManager::applyClientSyncSnapshot,
			MadokuRarityManager::resetClientSyncState
		);
	}

	public static void reset() {
		RarityRuntimeManager.reset();
		RarityConfigManager.reset();
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? RarityConfigManager.isEnabled() : synchronizedEnabled;
	}

	public static String createClientSyncSnapshot() {
		return JSONFormatAPIManager.object().put("enabled", RarityConfigManager.isEnabled()).build().toString();
	}

	public static void applyClientSyncSnapshot(String snapshot) {
		try {
			var root = com.google.gson.JsonParser.parseString(snapshot).getAsJsonObject();
			clientSynchronizedEnabled = root.has("enabled") && root.get("enabled").getAsBoolean();
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid rarity configuration snapshot.", exception);
		}
	}

	public static void resetClientSyncState() {
		clientSynchronizedEnabled = null;
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) {
		RarityRuntimeManager.applyGeneratedRarity(stack, randomSource, null);
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) {
		RarityRuntimeManager.applyGeneratedRarity(stack, randomSource, luckPlayer);
	}

	public static void applyConfiguredRarity(ItemStack stack, Tier rarity) {
		RarityRuntimeManager.applyConfiguredRarity(stack, rarity);
	}

	public static void preserveRarityOnRename(ItemStack source, ItemStack target) {
		RarityRuntimeManager.preserveRarityOnRename(source, target);
	}

	public static Tier detectAppliedRarity(ItemStack stack) {
		return RarityRuntimeManager.detectAppliedRarity(stack);
	}

	public static boolean isRarityItem(ItemStack stack) {
		return detectAppliedRarity(stack) != null;
	}

	public static double resolveWeight(Tier tier, double luckStat, boolean useMadokuLuck) {
		if (!RarityConfigManager.isTierEnabled(tier) || RarityConfigManager.getTierWeight(tier) <= 0) {
			return 0.0D;
		}

		double luckWeight = 0.0D;
		if (useMadokuLuck && RarityConfigManager.useMadokuLuck()
			&& madoku.craft.java.core.loot.LootFeatureAPIManager.isLuckEnabled()) {
			luckWeight = Double.isFinite(luckStat)
				? Math.max(0.0D, luckStat) * Math.max(0.0D, RarityConfigManager.getTierWeightAdjustment(tier))
				: 0.0D;
		}
		return Math.max(0.0D, RarityConfigManager.getTierWeight(tier) + luckWeight);
	}

	public static double resolveWeight(Tier tier, ServerPlayer player, boolean useMadokuLuck) {
		return resolveWeight(
			tier,
			player == null ? 0.0D : madoku.craft.java.core.loot.LootFeatureAPIManager.resolveLootLuckStat(player),
			useMadokuLuck && player != null
		);
	}

	public static double resolveWeightMultiplier(Tier tier, double luckStat, boolean useMadokuLuck) {
		if (!RarityConfigManager.isTierEnabled(tier) || RarityConfigManager.getTierWeight(tier) <= 0) {
			return 0.0D;
		}
		return resolveWeight(tier, luckStat, useMadokuLuck) / RarityConfigManager.getTierWeight(tier);
	}
}
