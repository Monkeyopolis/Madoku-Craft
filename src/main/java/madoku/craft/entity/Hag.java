package madoku.craft.entity;

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
	private static final int SPAWN_EGG_EGG_COST = 8;
	private static final int SPAWN_EGG_EMERALD_COST = 16;
	private static final int AVAILABLE_TRADE_COUNT = 7;
	private static final int TRADE_MAX_USES = 999999;

	private MerchantOffers offers;
	private Player tradingPlayer;
	private int merchantXp;

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
		if (this.offers == null) {
			this.offers = createSpawnEggOffers();
		}
		return this.offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
		this.offers = offers;
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

	private MerchantOffers createSpawnEggOffers() {
		MerchantOffers offers = new MerchantOffers();
		List<Item> spawnEggs = BuiltInRegistries.ITEM.stream()
			.filter(SpawnEggItem.class::isInstance)
			.map(Item.class::cast)
			.filter(item -> item != MadokuEntities.HAG_SPAWN_EGG)
			.sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
			.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

		Collections.shuffle(
			spawnEggs,
			new Random(this.getUUID().getMostSignificantBits() ^ this.getUUID().getLeastSignificantBits())
		);

		spawnEggs.stream()
			.limit(AVAILABLE_TRADE_COUNT)
			.forEach(item -> offers.add(new MerchantOffer(
				new ItemCost(Items.EGG, SPAWN_EGG_EGG_COST),
				Optional.of(new ItemCost(Items.EMERALD, SPAWN_EGG_EMERALD_COST)),
				new ItemStack(item),
				TRADE_MAX_USES,
				0,
				0.0F
			)));
		return offers;
	}
}
