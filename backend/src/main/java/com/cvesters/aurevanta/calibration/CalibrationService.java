package com.cvesters.aurevanta.calibration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.estimate.EstimateService;
import com.cvesters.aurevanta.estimate.ScorableEstimate;
import com.cvesters.aurevanta.forecast.model.BandScore;
import com.cvesters.aurevanta.forecast.model.Calibration;
import com.cvesters.aurevanta.item.CompletedWork;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.problem.NotAMemberException;

/**
 * How often this organisation's ranges contained the truth.
 *
 * <p>
 * <strong>It reads and stores nothing</strong>, which is M6's decision 1 spent a third
 * time: a stored hit rate would only ever explain the estimates written after somebody
 * added the column, and a derived one explains every estimate this product has ever held.
 * It is also the only thing here that can be true — a calibration figure changes every
 * time anybody finishes a task, so writing one down would freeze a number whose whole
 * nature is that it moves.
 *
 * <p>
 * <strong>Two reads joined in memory rather than one query</strong>, and the reason is
 * the arrow between the packages. The estimates come from {@code estimate}, which already
 * knows about work items; the day each item began comes from {@code item}, which is the
 * only package that knows progress has a history at all. Joining them in SQL would mean
 * teaching {@code estimate} about a table it has no business in, to save a pass over an
 * organisation's worth of completed work.
 *
 * <p>
 * <strong>No membership check of its own</strong>, and that is deliberate rather than an
 * omission. Every read below goes through another feature's service, each of which
 * re-reads the caller's standing before it touches a row — the rule those services own. A
 * fourth copy here would be a fourth chance for one of them to drift.
 */
@Service
public class CalibrationService {

	private final EstimateService estimates;

	private final WorkItemService items;

	CalibrationService(EstimateService estimates, WorkItemService items) {
		this.estimates = estimates;
		this.items = items;
	}

	/**
	 * The whole record for one organisation.
	 * @throws NotAMemberException if the caller no longer belongs to it
	 */
	@Transactional(readOnly = true)
	public OrganisationCalibration recordFor(UUID callerId, UUID tenantId) {
		List<ScorableEstimate> scorable = this.estimates.scorable(callerId, tenantId);
		Map<UUID, LocalDate> began = this.items.earliestReportedStarts(callerId, tenantId);
		CompletedWork completed = this.items.completedWork(callerId, tenantId);
		long estimated = this.estimates.completedItemsEstimated(callerId, tenantId);

		Calibration forecasts = new Calibration();
		Calibration reports = new Calibration();
		Calibration unbounded = new Calibration();
		Map<UUID, Attributed> byEstimator = new HashMap<>();
		Map<String, Calibration> byMethod = new HashMap<>();
		Set<UUID> scoredItems = new HashSet<>();
		List<Instant> scoredAt = new ArrayList<>();
		int movedByTheStartDay = 0;

		for (List<ScorableEstimate> said : byItemAndEstimator(scorable).values()) {
			UUID itemId = said.get(0).itemId();
			scoredItems.add(itemId);
			LocalDate started = began.get(itemId);
			ScorableEstimate counted;
			if (started == null) {
				// Nobody ever said when this began, so nothing here can be told from a
				// report. Named rather than dropped: `DONE` needs no start date, so
				// excluding these outright would silently discard most of the evidence a
				// typical organisation holds.
				counted = newest(said);
				unbounded.scored(score(counted));
			}
			else {
				Instant boundary = startOfDay(started);
				ScorableEstimate beforehand = lastBefore(said, boundary);
				if (beforehand != null) {
					counted = beforehand;
					forecasts.scored(score(counted));
					// The two breakdowns attribute the forecasts and nothing else: a
					// report
					// is what somebody wrote once they could see how the task was going,
					// and
					// crediting that to a person — or to the way the question was put —
					// would
					// measure how late they filed rather than how well they predicted.
					attributed(byEstimator, counted).record().scored(score(counted));
					byMethod.computeIfAbsent(counted.elicitationMethod(), (method) -> new Calibration())
						.scored(score(counted));
				}
				else {
					counted = newest(said);
					reports.scored(score(counted));
					if (lastBefore(said, boundary.plus(1, ChronoUnit.DAYS)) != null) {
						movedByTheStartDay++;
					}
				}
			}
			scoredAt.add(counted.createdAt());
		}

		CalibrationCoverage coverage = new CalibrationCoverage(completed.completed(), completed.withActual(), estimated,
				scoredItems.size(), movedByTheStartDay);
		return new OrganisationCalibration(forecasts, reports, unbounded, named(byEstimator), byMethod(byMethod),
				coverage, endOf(scoredAt, Comparator.naturalOrder()), endOf(scoredAt, Comparator.reverseOrder()));
	}

	/**
	 * The rows of the two breakdowns, each in a total order.
	 *
	 * <p>
	 * <strong>By name and then by identifier</strong>, which is the lesson
	 * {@code ProjectRepository} states about its own listing: two people may share a
	 * display name, and an order settled only by name is one that rearranges itself
	 * between requests. Never by hit rate — see {@link EstimatorCalibration}.
	 */
	private static List<EstimatorCalibration> named(Map<UUID, Attributed> byEstimator) {
		return byEstimator.entrySet()
			.stream()
			.map((entry) -> new EstimatorCalibration(entry.getKey(), entry.getValue().name(),
					entry.getValue().record()))
			.sorted(Comparator.comparing(EstimatorCalibration::estimatorName)
				.thenComparing(EstimatorCalibration::estimatorId))
			.toList();
	}

	private static List<MethodCalibration> byMethod(Map<String, Calibration> byMethod) {
		return byMethod.entrySet()
			.stream()
			.map((entry) -> new MethodCalibration(entry.getKey(), entry.getValue()))
			.sorted(Comparator.comparing(MethodCalibration::method))
			.toList();
	}

	private static Attributed attributed(Map<UUID, Attributed> byEstimator, ScorableEstimate counted) {
		return byEstimator.computeIfAbsent(counted.estimatorId(),
				(id) -> new Attributed(counted.estimatorName(), new Calibration()));
	}

	/**
	 * The first or last of the moments the scored estimates were written, or null when
	 * nothing was scored.
	 *
	 * <p>
	 * A fold over the whole list rather than a running minimum kept beside the loop,
	 * which is not only shorter: the order rows arrive in is the order of their items'
	 * identifiers, so a hand-written comparison has one arm that a given fixture may
	 * never reach, and which arm that is changes with the random identifiers a test
	 * happens to generate. Order-independent here means order-independent in the coverage
	 * report too.
	 */
	private static Instant endOf(List<Instant> scoredAt, Comparator<Instant> order) {
		return scoredAt.stream().min(order).orElse(null);
	}

	/**
	 * The moment a reported day begins, in UTC.
	 *
	 * <p>
	 * <strong>The two sides of this comparison are different kinds of thing and there is
	 * no timezone that makes it exact.</strong> An estimate carries an instant the server
	 * observed; a start date is a day a person reported, and {@code V10} is explicit that
	 * this is the one place in the schema where that distinction is deliberate — there is
	 * no time of day in "we started it on the twelfth" to be faithful to. So the rule has
	 * to be one whose error runs in the safe direction, and this is it: an estimate
	 * written at any hour of the start day counts as a report.
	 *
	 * <p>
	 * That costs real forecasts — estimating at planning and starting work the same
	 * afternoon is an ordinary Monday — and the cost is paid on purpose, because the
	 * alternative admits hindsight into the headline. What makes it honest is that the
	 * price is counted: {@code movedByTheStartDay} says how many rows this rule alone
	 * moved out of the forecasts.
	 */
	private static Instant startOfDay(LocalDate day) {
		return day.atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	/**
	 * The last thing this estimator said about this item before a given moment, or null
	 * if they had said nothing yet.
	 *
	 * <p>
	 * The last rather than the first: a revision written while the work was still ahead
	 * of them is a better forecast and the one they stand behind. The list is oldest
	 * first, so this is a walk rather than a sort.
	 */
	private static ScorableEstimate lastBefore(List<ScorableEstimate> said, Instant moment) {
		ScorableEstimate found = null;
		for (ScorableEstimate estimate : said) {
			if (!estimate.createdAt().isBefore(moment)) {
				break;
			}
			found = estimate;
		}
		return found;
	}

	private static ScorableEstimate newest(List<ScorableEstimate> said) {
		return said.get(said.size() - 1);
	}

	private static BandScore score(ScorableEstimate estimate) {
		return BandScore.of(estimate.p10Hours().doubleValue(), estimate.p90Hours().doubleValue(),
				estimate.actualHours().doubleValue());
	}

	/**
	 * One group per person per piece of work, because that is the unit being scored: an
	 * item three people estimated is three answers about three people and not one about
	 * the task.
	 *
	 * <p>
	 * The query already orders by item, then estimator, then oldest first, so this
	 * preserves that within each group and needs no sort of its own.
	 */
	private static Map<Said, List<ScorableEstimate>> byItemAndEstimator(List<ScorableEstimate> scorable) {
		Map<Said, List<ScorableEstimate>> grouped = new LinkedHashMap<>();
		for (ScorableEstimate estimate : scorable) {
			grouped.computeIfAbsent(new Said(estimate.itemId(), estimate.estimatorId()), (key) -> new ArrayList<>())
				.add(estimate);
		}
		return grouped;
	}

	/** One person's opinion of one piece of work, however many times they revised it. */
	private record Said(UUID itemId, UUID estimatorId) {
	}

	/**
	 * A person's record while it is being accumulated, holding the name off whichever of
	 * their estimates arrived first — the display name is on every row, so any of them
	 * will do and none of them needs a second lookup.
	 */
	private record Attributed(String name, Calibration record) {
	}

}
