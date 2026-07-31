package madoku.craft.entity;

import madoku.craft.pet.PetHagManager;
import madoku.craft.pet.PetConfigManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Random;

public class Hag extends Witch implements Merchant {
	private static final String PLAYER_TARGET_GOAL_NAME = "NearestAttackableWitchTargetGoal";
	private static final String PET_RARITY_COMMON = "common";
	private static final String PET_RARITY_RARE = "rare";
	private static final String PET_RARITY_EPIC = "epic";
	private static final String PET_RARITY_LEGENDARY = "legendary";
	private static final String PET_RARITY_MYTHIC = "mythic";
	private static final long TRADE_REFRESH_DAYS = 7L;
	private static final int AVAILABLE_TRADE_COUNT = 14;
	private static final int TRADE_MAX_USES = 999999;

	private MerchantOffers offers;
	private Player tradingPlayer;
	private int merchantXp;
	private long offerRefreshWeek = Long.MIN_VALUE;

	public Hag(EntityType<? extends Witch> entityType, Level level) {
		super(entityType, level);
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
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		InteractionResult result = super.mobInteract(player, hand);
		if (result.consumesAction()) {
			return result;
		}
		if (hand != InteractionHand.MAIN_HAND || !this.isAlive() || player.isSecondaryUseActive()) {
			return result;
		}
		if (this.getTarget() != null) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide()) {
			this.setTradingPlayer(player);
			this.openTradingScreen(player, this.getDisplayName(), 1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.removeAllGoals(Hag::isVanillaPlayerTargetGoal);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (MadokuEntities.shouldDespawnWanderingHag(this, level)) {
			this.discard();
			return;
		}
		if (this.tradingPlayer != null) {
			this.stopInPlace();
			this.setTarget(null);
		}
		super.customServerAiStep(level);
		if (this.tradingPlayer != null) {
			this.stopInPlace();
		}
	}

	@Override
	public void travel(Vec3 travelVector) {
		if (this.tradingPlayer != null) {
			this.stopInPlace();
			this.setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.travel(travelVector);
	}

	@Override
	public void setTradingPlayer(Player player) {
		this.tradingPlayer = player;
	}

	@Override
	public Player getTradingPlayer() {
		return this.tradingPlayer;
	}

	@Override
	public MerchantOffers getOffers() {
		long currentWeek = currentOfferWeek();
		if (this.offers == null || this.offerRefreshWeek != currentWeek) {
			this.offers = createPetOffers(currentWeek);
			this.offerRefreshWeek = currentWeek;
		}
		return this.offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
		this.offers = offers;
		this.offerRefreshWeek = currentOfferWeek();
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		this.ambientSoundTime = -this.getAmbientSoundInterval();
		this.playSound(this.getNotifyTradeSound(), 1.0F, this.getVoicePitch());
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return this.merchantXp;
	}

	@Override
	public void overrideXp(int xp) {
		this.merchantXp = xp;
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.WANDERING_TRADER_TRADE;
	}

	@Override
	public boolean isClientSide() {
		return this.level().isClientSide();
	}

	@Override
	public boolean stillValid(Player player) {
		return this.isAlive() && this.distanceToSqr(player) <= 16.0D;
	}

	private static boolean isVanillaPlayerTargetGoal(Goal goal) {
		return goal != null && PLAYER_TARGET_GOAL_NAME.equals(goal.getClass().getSimpleName());
	}

	private MerchantOffers createPetOffers(long week) {
		MerchantOffers offers = new MerchantOffers();
		Random random = new Random(
			this.getUUID().getMostSignificantBits()
				^ this.getUUID().getLeastSignificantBits()
				^ week
		);
		if (!PetConfigManager.isEnabled()) return offers;
		List<Item> petItems = buildPetItems(random);
		Set<String> usedTradeKeys = new HashSet<>();
		for (Item item : petItems) {
			int level = pickUniqueTradeLevel(item, random, usedTradeKeys);
			offers.add(createPetOffer(item, level));
		}
		return offers;
	}

	private long currentOfferWeek() {
		long absoluteDayTime;
		if (this.level() instanceof ServerLevel serverLevel) {
			absoluteDayTime = MadokuTimeManager.getCurrentAbsoluteDayTime(serverLevel);
		} else {
			absoluteDayTime = this.level().getOverworldClockTime();
		}
		long day = Math.max(0L, MadokuTimeManager.getDay(absoluteDayTime));
		return Math.floorDiv(day, TRADE_REFRESH_DAYS);
	}

	private List<Item> buildPetItems(Random random) {
		List<Item> pool = new ArrayList<>(PetHagManager.tradeItems());
		List<Item> selected = new ArrayList<>();
		Map<Item, Integer> selectedCounts = new HashMap<>();
		List<Item> selectionPool = new ArrayList<>(pool);
		while (selected.size() < AVAILABLE_TRADE_COUNT && !pool.isEmpty()) {
			if (selectionPool.isEmpty()) {
				for (Item item : pool) {
					if (selectedCounts.getOrDefault(item, 0) < 5) {
						selectionPool.add(item);
					}
				}
			}
			Item item = pickWeightedPet(selectionPool, random);
			if (item == null) {
				break;
			}
			selected.add(item);
			selectedCounts.merge(item, 1, Integer::sum);
			selectionPool.remove(item);
		}
		selected.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
		return selected;
	}

	private int pickUniqueTradeLevel(Item item, Random random, Set<String> usedTradeKeys) {
		int level = PetHagManager.randomTradeLevel(random);
		String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
		for (int attempts = 0; attempts < 5; attempts++) {
			if (!usedTradeKeys.contains(itemId + ":" + level)) {
				usedTradeKeys.add(itemId + ":" + level);
				return level;
			}
			level = level % 5 + 1;
		}
		return 1;
	}

	private Item pickWeightedPet(List<Item> pool, Random random) {
		if (pool == null || pool.isEmpty()) {
			return null;
		}

		int totalWeight = 0;
		for (Item item : pool) {
			totalWeight += rarityWeight(item);
		}
		if (totalWeight <= 0) {
			return pool.getFirst();
		}

		int roll = random.nextInt(totalWeight);
		int runningWeight = 0;
		for (Item item : pool) {
			runningWeight += rarityWeight(item);
			if (roll < runningWeight) {
				return item;
			}
		}
		return pool.getLast();
	}

	private MerchantOffer createPetOffer(Item item, int level) {
		int emeraldCost = emeraldCost(item);
		ItemStack resultStack = PetHagManager.tradeStack(item, level);
		return new MerchantOffer(
			PetHagManager.tradeIngredient(item, level),
			Optional.of(new ItemCost(Items.EMERALD, emeraldCost)),
			resultStack,
			TRADE_MAX_USES,
			0,
			0.0F
		);
	}

	private int rarityWeight(Item item) {
		return PetHagManager.rarityWeight(petRarity(item));
	}

	private int emeraldCost(Item item) {
		return switch (petRarity(item)) {
			case PET_RARITY_MYTHIC -> 128;
			case PET_RARITY_LEGENDARY -> 96;
			case PET_RARITY_EPIC -> 64;
			case PET_RARITY_RARE -> 48;
			case PET_RARITY_COMMON -> 32;
			default -> 32;
		};
	}

	private String petRarity(Item item) {
		return PetHagManager.rarity(new ItemStack(item));
	}
}

