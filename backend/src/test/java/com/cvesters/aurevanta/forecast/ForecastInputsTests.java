package com.cvesters.aurevanta.forecast;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedPool;
import com.cvesters.aurevanta.item.WorkItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Everything a forecast was given, and the two counterfactuals that vary it.
 *
 * <p>
 * <strong>What is asserted here is that a variation varies one thing.</strong> A replay
 * is only worth anything if the two runs being compared differ where somebody asked and
 * nowhere else — which is M7's lesson about a cut taking a draw and discarding it,
 * arriving on the one type both counterfactuals go through.
 */
class ForecastInputsTests {

	private static final UUID BACKEND = UUID.fromString("b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1");

	private static final UUID STAGING = UUID.fromString("57a91a91-1111-2222-3333-444455556666");

	@Test
	void hiringChangesOnePoolAndNothingElse() {
		ForecastInputs inputs = declared();

		ForecastInputs larger = inputs.withMore(BACKEND, 2);

		assertThat(larger.pools()).containsExactly(new PlannedPool(BACKEND, 5), new PlannedPool(STAGING, 1));
		// The items, the arrows and what each piece of work needs are the same objects,
		// so a
		// replay draws the same numbers in the same order from the same seed.
		assertThat(larger.items()).isEqualTo(inputs.items());
		assertThat(larger.edges()).isEqualTo(inputs.edges());
		assertThat(larger.needs()).isEqualTo(inputs.needs());
		// And the original is untouched, so a baseline measured before the counterfactual
		// is
		// still a baseline afterwards.
		assertThat(inputs.pools()).containsExactly(new PlannedPool(BACKEND, 3), new PlannedPool(STAGING, 1));
	}

	/**
	 * A pool this run never held is a programming error rather than a refusal: the
	 * service has already answered it with a code somebody can act on, and reaching here
	 * would mean that check had been removed.
	 */
	@Test
	void hiringIntoAPoolThisRunNeverHeldIsRefused() {
		ForecastInputs inputs = declared();

		assertThatIllegalArgumentException().isThrownBy(() -> inputs.withMore(UUID.randomUUID(), 1));
	}

	private static ForecastInputs declared() {
		return new ForecastInputs(
				List.of(new ForecastInputs.PlannedItem(UUID.randomUUID(), WorkItemStatus.NOT_STARTED, null, List.of())),
				List.of(), List.of(new PlannedPool(BACKEND, 3), new PlannedPool(STAGING, 1)), List.of());
	}

}
