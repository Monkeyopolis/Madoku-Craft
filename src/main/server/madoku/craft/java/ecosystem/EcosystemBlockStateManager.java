package madoku.craft.java.ecosystem;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.references.BlockItemId;

/**
 * Bridges ecosystem providers to vanilla's BlockState random-tick contract.
 *
 * <p>The two properties are deliberately state-only bookkeeping. They do not
 * replace the block's implementation or introduce a second position
 * dispatcher:</p>
 *
 * <ul>
 *   <li>{@code madoku_checked}: this state has had its local eligibility
 *       conditions evaluated.</li>
 *   <li>{@code madoku_eligible}: at least one persisted/active ecosystem
 *       candidate currently exists at this position.</li>
 * </ul>
 */
public final class EcosystemBlockStateManager {
	public static final BooleanProperty MADOKU_CHECKED = BooleanProperty.create("madoku_checked");
	public static final BooleanProperty MADOKU_ELIGIBLE = BooleanProperty.create("madoku_eligible");

	private static final List<EcosystemBlockProvider> PROVIDERS = new CopyOnWriteArrayList<>();
	private static volatile boolean initialized;

	private EcosystemBlockStateManager() {
	}

	/** Used during vanilla's Blocks bootstrap, before block instances exist. */
	public static boolean shouldAddStateProperties(BlockItemId id) {
		if (id == null || id.block() == null || id.block().identifier() == null) {
			return false;
		}
		return switch (id.block().identifier().getPath()) {
			case "dirt", "coarse_dirt", "podzol", "mycelium", "rooted_dirt", "mud", "dirt_path",
				"grass_block", "sand", "red_sand", "leaf_litter",
				"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves",
				"cherry_leaves", "dark_oak_leaves", "pale_oak_leaves", "mangrove_leaves",
				"azalea_leaves", "flowering_azalea_leaves" -> true;
			default -> false;
		};
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		PROVIDERS.clear();
		PROVIDERS.add(new EcosystemNaturalGrowthBlockProvider());
		PROVIDERS.add(new EcosystemNaturalErosionBlockProvider());
		PROVIDERS.add(new EcosystemNaturalDecayBlockProvider());
		initialized = true;
	}

	public static boolean hasProperties(BlockState state) {
		return state != null
			&& state.hasProperty(MADOKU_CHECKED)
			&& state.hasProperty(MADOKU_ELIGIBLE);
	}

	/** Called by the BlockState.isRandomlyTicking mixin. */
	public static boolean shouldRandomTick(BlockState state) {
		return hasProperties(state)
			&& (!state.getValue(MADOKU_CHECKED) || state.getValue(MADOKU_ELIGIBLE));
	}

	/** Called at the beginning of BlockState.randomTick, before native logic. */
	public static void onRandomTick(ServerLevel level, BlockState state, BlockPos position, RandomSource random) {
		if (level == null || position == null || state == null || !hasProperties(state)) {
			return;
		}

		BlockState currentState = level.getBlockState(position);
		if (!hasProperties(currentState)) {
			return;
		}
		if (!EcosystemAPIManager.isEnabled()) {
			setEcosystemState(level, position, currentState, true, false);
			return;
		}

		if (!currentState.getValue(MADOKU_CHECKED)) {
			checkEligibility(level, position, currentState);
			currentState = level.getBlockState(position);
		}

		if (!hasProperties(currentState) || !currentState.getValue(MADOKU_ELIGIBLE)) {
			return;
		}

		for (EcosystemBlockProvider provider : PROVIDERS) {
			if (provider.supports(currentState)) {
				provider.randomTick(level, position, currentState, random);
			}
		}
	}

	/** Checks one position without scanning its chunk or section. */
	public static void checkEligibility(ServerLevel level, BlockPos position, BlockState state) {
		if (level == null || position == null || state == null || !hasProperties(state)) {
			return;
		}
		for (EcosystemBlockProvider provider : PROVIDERS) {
			if (provider.supports(state)) {
				provider.checkEligibility(level, position, state);
			}
		}
		applyEligibilityState(level, position);
	}

	/**
	 * Receives all server-side block changes, including player placement and
	 * breaking, pistons, fluids, explosions, and feature placement.
	 */
	public static void onBlockChanged(ServerLevel level, BlockPos position, BlockState oldState, BlockState newState) {
		if (level == null || position == null || oldState == newState) {
			return;
		}
		if (hasProperties(oldState) && hasProperties(newState)
			&& oldState.getBlock() == newState.getBlock()
			&& sameNonEcosystemState(oldState, newState)) {
			return;
		}

		// A replaced candidate cannot retain progress against a different block.
		if (oldState != null && oldState.getBlock() != newState.getBlock()) {
			EcosystemAPIManager.removeAllCandidatesAt(level, position);
		}

		markUnchecked(level, position);
		for (Direction direction : Direction.values()) {
			markUnchecked(level, position.relative(direction));
		}
	}

	/** Keeps persisted candidate masks and block-state eligibility in sync. */
	public static void onCandidateMaskChanged(String levelId, long packedPosition, int mask) {
		ServerLevel level = EcosystemAPIManager.loadedLevel(levelId);
		if (level == null) {
			return;
		}
		BlockPos position = BlockPos.of(packedPosition);
		BlockState state = level.getBlockState(position);
		if (!hasProperties(state)) {
			return;
		}
		setEcosystemState(level, position, state, true, mask != 0);
	}

	private static void markUnchecked(ServerLevel level, BlockPos position) {
		BlockState state = level.getBlockState(position);
		if (!hasProperties(state)) {
			return;
		}
		setEcosystemState(level, position, state, false, false);
	}

	private static void applyEligibilityState(ServerLevel level, BlockPos position) {
		BlockState state = level.getBlockState(position);
		if (!hasProperties(state)) {
			return;
		}
		int mask = EcosystemAPIManager.candidateMaskAt(level, position);
		setEcosystemState(level, position, state, true, mask != 0);
	}

	private static void setEcosystemState(ServerLevel level, BlockPos position, BlockState state, boolean checked, boolean eligible) {
		BlockState next = state
			.setValue(MADOKU_CHECKED, checked)
			.setValue(MADOKU_ELIGIBLE, eligible);
		if (next != state) {
			level.setBlock(position, next, 2);
		}
	}

	private static boolean sameNonEcosystemState(BlockState left, BlockState right) {
		BlockState normalizedLeft = left
			.setValue(MADOKU_CHECKED, right.getValue(MADOKU_CHECKED))
			.setValue(MADOKU_ELIGIBLE, right.getValue(MADOKU_ELIGIBLE));
		return normalizedLeft.equals(right);
	}
}
