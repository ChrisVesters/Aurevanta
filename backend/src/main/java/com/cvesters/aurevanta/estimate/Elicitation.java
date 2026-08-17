package com.cvesters.aurevanta.estimate;

import java.util.Set;

import com.cvesters.aurevanta.problem.UnknownElicitationMethodException;

/**
 * How a three-point range was asked for.
 *
 * <p>
 * <strong>Stored because it cannot be worked out later, unlike everything else about an
 * estimate.</strong> Whether a range is worth questioning is arithmetic over three
 * columns and one constant, so it is derived on the way out; how the question was *put*
 * leaves no trace in the numbers at all. That asymmetry is the whole reason this is a
 * column and {@code EstimateQuality} is not.
 *
 * <p>
 * <strong>It is also the only instrument that can say whether M5 worked.</strong> This
 * milestone's claim is that changing the question produces honester ranges, and its
 * failure mode is a form that feels better and changes nothing — which no test can
 * detect. The only evidence is M8's calibration record split by these names, and a split
 * needs a column.
 *
 * <p>
 * Names rather than an enum, following {@code Schedule.PRIORITY_RULE} and
 * {@code WorkingCalendar.RULE}: a value the code has never heard of should read back as
 * something unrecognised rather than making the row unreadable, which is what
 * {@code @Enumerated} would do.
 */
public final class Elicitation {

	/**
	 * Three boxes labelled P10, P50 and P90, filled in together and in any order.
	 *
	 * <p>
	 * Every estimate written before M5 is one of these, which is what {@code V15}
	 * backfilled and why that backfill is true rather than convenient: it is the only
	 * form this product has ever had. {@code product-concept.md} is blunt about what it
	 * produces — 3/5/8 without anybody thinking — and this constant is what lets that
	 * claim eventually be measured rather than merely asserted.
	 */
	public static final String THREE_POINT = "three_point";

	/**
	 * The bad case first, asked as what would be genuinely surprising to exceed; then the
	 * good case; then the typical one, last and alone in being allowed to be anchored.
	 */
	public static final String SURPRISE_FRAMED = "surprise_framed";

	private static final Set<String> KNOWN = Set.of(THREE_POINT, SURPRISE_FRAMED);

	private Elicitation() {
	}

	/**
	 * Refuses a name this server cannot record truthfully.
	 *
	 * <p>
	 * A caller naming a method that does not exist would otherwise write a row claiming
	 * to have been collected a way nobody has ever collected one — in the column whose
	 * only purpose is to say how it was. Rejecting it is not strictness for its own sake:
	 * the value of this column is exactly its trustworthiness.
	 * @throws UnknownElicitationMethodException if the name is not one of the above
	 */
	public static void require(String method) {
		if (!KNOWN.contains(method)) {
			throw new UnknownElicitationMethodException();
		}
	}

}
