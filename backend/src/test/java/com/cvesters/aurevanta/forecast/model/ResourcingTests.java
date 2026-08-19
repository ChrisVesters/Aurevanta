package com.cvesters.aurevanta.forecast.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * What a plan has to work with, and what the work needs of it.
 *
 * <p>
 * <strong>Everything refused here is refused when the declaration is made</strong>, and
 * that is not fussiness about inputs. The scheduler's loop has no guard against work that
 * cannot fit, and its termination argument depends on there being none: with nothing
 * running every unit is free, so anything that can ever start can start then. Each of
 * these would break that quietly, by leaving something waiting for ever.
 */
class ResourcingTests {

	/**
	 * <strong>The containment the version bump rests on, said as plainly as it can be
	 * said.</strong> One pool, every item naming nothing — which is what every forecast
	 * made before M11 assumed, and what every forecast made after it assumes until
	 * somebody describes their team.
	 */
	@Test
	void aPlanWithNoTeamDeclaredIsOnePoolAndNothingNamed() {
		Resourcing pooled = Resourcing.pooled(4, 3);

		assertThat(pooled.pools()).isEqualTo(1);
		assertThat(pooled.items()).isEqualTo(3);
		assertThat(pooled.freeUnits()).containsExactly(4);
		assertThat(pooled.namesNothing(0)).isTrue();
		assertThat(pooled.namesNothing(2)).isTrue();
	}

	@Test
	void aDeclaredTeamKnowsWhatEachPieceOfWorkNeeds() {
		Resourcing declared = Resourcing.of(new int[] { 3, 1 }, new int[][] { { 1, 0 }, { 0, 0 }, { 2, 1 } });

		assertThat(declared.pools()).isEqualTo(2);
		assertThat(declared.freeUnits()).containsExactly(3, 1);
		assertThat(declared.needed(0, 0)).isEqualTo(1);
		assertThat(declared.needed(2, 1)).isEqualTo(1);
		// A row of nothing is the claim that anybody can pick this up, and it is the only
		// row that means something other than what it says.
		assertThat(declared.namesNothing(1)).isTrue();
		assertThat(declared.namesNothing(0)).isFalse();
	}

	/**
	 * The units are handed out as a copy, or a run would spend the declaration itself.
	 */
	@Test
	void spendingTheUnitsDoesNotSpendTheDeclaration() {
		Resourcing declared = Resourcing.of(new int[] { 3 }, new int[1][1]);

		int[] free = declared.freeUnits();
		free[0] = 0;

		assertThat(declared.freeUnits()).containsExactly(3);
	}

	/**
	 * And so is the declaration itself, so that a caller's array cannot change it after.
	 */
	@Test
	void changingTheArraysAfterwardsChangesNothing() {
		int[] units = { 2 };
		int[][] needed = { { 1 } };
		Resourcing declared = Resourcing.of(units, needed);

		units[0] = 99;
		needed[0][0] = 99;

		assertThat(declared.freeUnits()).containsExactly(2);
		assertThat(declared.needed(0, 0)).isEqualTo(1);
	}

	// What it refuses -----------------------------------------------------------

	/**
	 * A plan with nothing to work with cannot be scheduled at all: work that names
	 * nothing takes one unit of whatever is free, and there would never be one.
	 */
	@Test
	void aPlanWithNoPoolsIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[0], new int[0][0]));
	}

	@Test
	void aPoolOfNothingIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[] { 0 }, new int[1][1]));
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[] { 2, -1 }, new int[1][2]));
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.pooled(0, 1));
	}

	/**
	 * A row that names a different number of pools than there are is not a claim at all.
	 */
	@Test
	void aRowThatDoesNotFitThePoolsIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[] { 2, 2 }, new int[][] { { 1 } }));
	}

	@Test
	void needingLessThanNoneOfAPoolIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[] { 2 }, new int[][] { { -1 } }));
	}

	/**
	 * <strong>The one that would wait for ever rather than fail.</strong> Everything else
	 * here is a nonsense a caller can see; this is a declaration that reads perfectly
	 * well and describes work nothing can ever start.
	 */
	@Test
	void needingMoreOfAPoolThanItHoldsIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[] { 2 }, new int[][] { { 3 } }))
			.withMessageContaining("holds 2");
	}

}
