package madoku.craft.entity;

import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.time.MadokuTime;
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
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class Hag extends Witch implements Merchant {
	private static final String PLAYER_TARGET_GOAL_NAME = "NearestAttackableWitchTargetGoal";
	private static final String PET_RARITY_COMMON = "common";
	private static final String PET_RARITY_RARE = "rare";
	private static final String PET_RARITY_EPIC = "epic";
	private static final String PET_RARITY_MYTHIC = "mythic";
	private static final int DEFAULT_SPAWN_EGG_EGG_COST = 64;
	private static final int DEFAULT_SPAWN_EGG_EMERALD_COST = 16;
	private static final long TRADE_REFRESH_DAYS = 7L;
	private static final int AVAILABLE_TRADE_COUNT = 7;
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
			this.getNavigation().stop();
			this.setTarget(null);
		}
		super.customServerAiStep(level);
		if (this.tradingPlayer != null) {
			this.getNavigation().stop();
		}
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
			this.offers = createSpawnEggOffers(currentWeek);
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

	private MerchantOffers createSpawnEggOffers(long week) {
		MerchantOffers offers = new MerchantOffers();
		Random random = new Random(
			this.getUUID().getMostSignificantBits()
				^ this.getUUID().getLeastSignificantBits()
				^ week
		);
		boolean petSystemEnabled = PlayerEntitiesSystem.isEnabled();
		List<Item> spawnEggs = petSystemEnabled ? buildPetSystemSpawnEggs(random) : buildFallbackSpawnEggs(random);
		for (Item item : spawnEggs) {
			offers.add(createSpawnEggOffer(item, petSystemEnabled));
		}
		return offers;
	}

	private long currentOfferWeek() {
		long absoluteDayTime;
		if (this.level() instanceof ServerLevel serverLevel) {
			absoluteDayTime = MadokuTime.getCurrentAbsoluteDayTime(serverLevel);
		} else {
			absoluteDayTime = this.level().getOverworldClockTime();
		}
		long day = Math.max(0L, MadokuTime.getDay(absoluteDayTime));
		return Math.floorDiv(day, TRADE_REFRESH_DAYS);
	}

	private List<Item> buildPetSystemSpawnEggs(Random random) {
		List<Item> pool = new ArrayList<>(PlayerEntitiesSystem.tradeSpawnEggItems());
		List<Item> selected = new ArrayList<>();
		int limit = Math.min(AVAILABLE_TRADE_COUNT, pool.size());
		while (selected.size() < limit && !pool.isEmpty()) {
			Item item = pickWeightedSpawnEgg(pool, random);
			if (item == null) {
				break;
			}
			selected.add(item);
			pool.remove(item);
		}
		selected.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
		return selected;
	}

	private List<Item> buildFallbackSpawnEggs(Random random) {
		List<Item> spawnEggs = BuiltInRegistries.ITEM.stream()
			.filter(SpawnEggItem.class::isInstance)
			.map(Item.class::cast)
			.filter(item -> item != MadokuEntities.HAG_SPAWN_EGG)
			.sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
			.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
		Collections.shuffle(spawnEggs, random);
		return spawnEggs.stream().limit(AVAILABLE_TRADE_COUNT).toList();
	}

	private Item pickWeightedSpawnEgg(List<Item> pool, Random random) {
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

	private MerchantOffer createSpawnEggOffer(Item item, boolean petSystemEnabled) {
		int eggCost = petSystemEnabled ? eggCost(item) : DEFAULT_SPAWN_EGG_EGG_COST;
		int emeraldCost = petSystemEnabled ? emeraldCost(item) : DEFAULT_SPAWN_EGG_EMERALD_COST;
		ItemStack resultStack = new ItemStack(item);
		if (petSystemEnabled) {
			PlayerEntitiesSystem.applySupportedSpawnEggLore(resultStack);
		}
		return new MerchantOffer(
			new ItemCost(Items.EGG, eggCost),
			Optional.of(new ItemCost(Items.EMERALD, emeraldCost)),
			resultStack,
			TRADE_MAX_USES,
			0,
			0.0F
		);
	}

	private int rarityWeight(Item item) {
		return PlayerEntitiesSystem.petTradeRarityWeight(petRarity(item));
	}

	private int eggCost(Item item) {
		return switch (petRarity(item)) {
			case PET_RARITY_MYTHIC -> 64;
			case PET_RARITY_EPIC -> 48;
			case PET_RARITY_RARE -> 32;
			case PET_RARITY_COMMON -> 16;
			default -> 16;
		};
	}

	private int emeraldCost(Item item) {
		return switch (petRarity(item)) {
			case PET_RARITY_MYTHIC -> 128;
			case PET_RARITY_EPIC -> 96;
			case PET_RARITY_RARE -> 64;
			case PET_RARITY_COMMON -> 32;
			default -> 32;
		};
	}

	private String petRarity(Item item) {
		return PlayerEntitiesSystem.petRarity(new ItemStack(item));
	}
}
