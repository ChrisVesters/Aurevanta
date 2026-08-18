package com.cvesters.aurevanta.calibration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
		Set<UUID> scoredItems = new HashSet<>();
		int movedByTheStartDay = 0;

		for (List<ScorableEstimate> said : byItemAndEstimator(scorable).values()) {
			UUID itemId = said.get(0).itemId();
			scoredItems.add(itemId);
			LocalDate started = began.get(itemId);
			if (started == null) {
				// Nobody ever said when this began, so nothing here can be told from a
				// report. Named rather than dropped: `DONE` needs no start date, so
				// excluding these outright would silently discard most of the evidence a
				// typical organisation holds.
				unbounded.scored(score(newest(said)));
				continue;
			}
			Instant boundary = startOfDay(started);
			ScorableEstimate beforehand = lastBefore(said, boundary);
			if (beforehand != null) {
				forecasts.scored(score(beforehand));
				continue;
			}
			reports.scored(score(newest(said)));
			if (lastBefore(said, boundary.plus(1, ChronoUnit.DAYS)) != null) {
				movedByTheStartDay++;
			}
		}

		CalibrationCoverage coverage = new CalibrationCoverage(completed.completed(), completed.withActual(), estimated,
				scoredItems.size(), movedByTheStartDay);
		return new OrganisationCalibration(forecasts, reports, unbounded, coverage);
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

}
