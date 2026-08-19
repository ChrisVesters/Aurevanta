package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ForecastTerms;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One answer this product gave, on one day, from one set of assumptions.
 *
 * <p>
 * <strong>Nothing here can be changed</strong>, as with an estimate and for a related
 * reason: M10 asks whether a date has been sliding, which is a question about what was
 * said *then*. A run that could be edited afterwards would answer it with whatever
 * somebody most recently believed, and the movement it exists to detect would disappear.
 *
 * <p>
 * <strong>It stores its own seed, its own inputs and its own engine version</strong>,
 * which together mean it can be run again and produce the numbers below. That is worth
 * more than it looks: anything this milestone did not think to store — the per-item
 * durations M6 will want, for instance — can be recovered exactly rather than being gone,
 * so the seed is the compression.
 */
@Entity
@Table(name = "forecast_runs")
public class ForecastRun {

	/**
	 * Hours, to the hundredth. Thirty-six seconds, which is finer than a forecast means.
	 */
	private static final int HOURS_SCALE = 2;

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	/**
	 * The person, not their membership. They may have left by the time anybody reads
	 * this, and they still asked the question — the same rule an estimate's estimator
	 * follows.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requested_by_user_id", nullable = false)
	private User requestedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private long seed;

	@Column(name = "sample_count", nullable = false)
	private int sampleCount;

	@Column(nullable = false)
	private int capacity;

	@Column(name = "priority_rule", nullable = false, length = 40)
	private String priorityRule;

	/**
	 * The pools this run was scheduled against, in declaration order — null on a run made
	 * before there was a team to describe, and {@code []} on one made by an organisation
	 * that has described none. Two different facts, and the second is the one a capacity
	 * still answers.
	 */
	@Column(name = "resourcing", columnDefinition = "text")
	private String resourcing;

	/**
	 * How much longer everything takes in a bad stretch, as a percentage — the P90 of the
	 * one multiplier every item in a run shares. Zero says the caller claimed nothing in
	 * their world has a common cause, which is a claim rather than an absence of one.
	 */
	@Column(name = "team_factor_worse_by_percent", nullable = false, precision = 6, scale = 2)
	private BigDecimal teamFactorWorseByPercent;

	/** How much a plan like this usually grows, as the two ends of a range in percent. */
	@Column(name = "scope_growth_p10_percent", nullable = false, precision = 6, scale = 2)
	private BigDecimal scopeGrowthP10Percent;

	@Column(name = "scope_growth_p90_percent", nullable = false, precision = 6, scale = 2)
	private BigDecimal scopeGrowthP90Percent;

	/**
	 * The day work was said to begin, and what one person's working day was said to hold
	 * — the two halves of turning this run's hours into a date.
	 *
	 * <p>
	 * <strong>Nullable, and nothing backfilled them.</strong> A run made before M4
	 * assumed no calendar, because it produced no date; writing a six-hour day onto it
	 * would invent a claim on behalf of somebody who never made one, in the one table
	 * that exists to say what was assumed. V13 could backfill zeros and call them true,
	 * and this is the mirror of it.
	 */
	@Column(name = "starts_on")
	private LocalDate startsOn;

	/**
	 * <strong>One worker's day, never the team's.</strong> The scheduler already ran
	 * {@code capacity} items at a time, so the hours this divides have capacity inside
	 * them.
	 */
	@Column(name = "working_hours_per_day", precision = 4, scale = 2)
	private BigDecimal workingHoursPerDay;

	/**
	 * Which calendar the two above were read through, held like {@link #priorityRule} and
	 * for the same reason: two defensible rules give two different dates from identical
	 * data, so a run resolves under the calendar it was made with rather than the one
	 * this code happens to have today.
	 */
	@Column(name = "calendar_rule", length = 40)
	private String calendarRule;

	@Column(name = "engine_version", nullable = false)
	private int engineVersion;

	@Column(name = "item_count", nullable = false)
	private int itemCount;

	@Column(name = "estimated_item_count", nullable = false)
	private int estimatedItemCount;

	@Column(name = "mean_hours", nullable = false, precision = 14, scale = 2)
	private BigDecimal meanHours;

	@Column(name = "p10_hours", nullable = false, precision = 14, scale = 2)
	private BigDecimal p10Hours;

	@Column(name = "p50_hours", nullable = false, precision = 14, scale = 2)
	private BigDecimal p50Hours;

	@Column(name = "p80_hours", nullable = false, precision = 14, scale = 2)
	private BigDecimal p80Hours;

	@Column(name = "p90_hours", nullable = false, precision = 14, scale = 2)
	private BigDecimal p90Hours;

	@Column(name = "p95_hours", nullable = false, precision = 14, scale = 2)
	private BigDecimal p95Hours;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String inputs;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String outputs;

	protected ForecastRun() {
		// for JPA
	}

	/**
	 * @param terms everything about the run except the plan it forecast, which is one
	 * argument because it is one idea. Ten of them were listed here separately and the
	 * order was the only thing keeping {@code capacity} out of {@code sampleCount} — both
	 * {@code int}, adjacent, and silently swappable. The record that reads them back on
	 * the way out is the record that writes them on the way in, so the two can no longer
	 * disagree about what a run was asked on.
	 */
	public ForecastRun(Project project, User requestedBy, ForecastTerms terms, int sampleCount, long seed,
			int itemCount, int estimatedItemCount, Forecast forecast, String inputs, String outputs,
			Instant createdAt) {
		// Taken from the project rather than from a caller, so a run cannot be filed
		// under
		// one organisation and against another's plan.
		this.tenant = project.getTenant();
		this.project = project;
		this.requestedBy = requestedBy;
		this.capacity = terms.capacity();
		this.sampleCount = sampleCount;
		this.teamFactorWorseByPercent = terms.teamFactorWorseByPercent();
		this.scopeGrowthP10Percent = terms.scopeGrowthP10Percent();
		this.scopeGrowthP90Percent = terms.scopeGrowthP90Percent();
		this.startsOn = terms.startsOn();
		this.workingHoursPerDay = terms.workingHoursPerDay();
		this.calendarRule = terms.calendarRule();
		this.seed = seed;
		this.engineVersion = terms.engineVersion();
		this.priorityRule = terms.priorityRule();
		this.resourcing = terms.resourcing();
		this.itemCount = itemCount;
		this.estimatedItemCount = estimatedItemCount;
		this.meanHours = hours(forecast.meanHours());
		this.p10Hours = hours(forecast.p10Hours());
		this.p50Hours = hours(forecast.p50Hours());
		this.p80Hours = hours(forecast.p80Hours());
		this.p90Hours = hours(forecast.p90Hours());
		this.p95Hours = hours(forecast.p95Hours());
		this.inputs = inputs;
		this.outputs = outputs;
		this.createdAt = createdAt;
	}

	/**
	 * What this run was asked on, as {@code Comparison} and a decomposition take it.
	 *
	 * <p>
	 * Here rather than in a service, because it is the exact inverse of the constructor
	 * above and the two belong within sight of each other: a column added to the terms
	 * has one class to change rather than two, and no way to be written without being
	 * read.
	 */
	public ForecastTerms getTerms() {
		return new ForecastTerms(this.engineVersion, this.priorityRule, this.calendarRule, this.workingHoursPerDay,
				this.capacity, this.resourcing, this.teamFactorWorseByPercent, this.scopeGrowthP10Percent,
				this.scopeGrowthP90Percent, this.startsOn);
	}

	/**
	 * How a figure the engine produced becomes a figure this table keeps.
	 *
	 * <p>
	 * Reachable from the package rather than private, because a replay has to round its
	 * own answer the same way before it can be compared with what is stored here. Two
	 * roundings would be two chances to disagree about a run that had not changed at all.
	 */
	static BigDecimal hours(double value) {
		return BigDecimal.valueOf(value).setScale(HOURS_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Which of the five columns one of M4's three confidences names.
	 *
	 * <p>
	 * Here rather than in each of the two things that ask — an account of a movement and
	 * a drift over a history — because two switches over the same three numbers would
	 * eventually be one switch and one that had drifted, and the reader of either would
	 * have no way of telling which. The two percentiles the control does not offer have
	 * no case: nothing asks for a date at 10%, and a default that quietly answered 95 for
	 * one would be a wrong number rather than a refusal.
	 */
	BigDecimal hoursAt(int confidence) {
		return switch (confidence) {
			case 50 -> this.p50Hours;
			case 80 -> this.p80Hours;
			default -> this.p95Hours;
		};
	}

	public UUID getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public User getRequestedBy() {
		return requestedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getSeed() {
		return seed;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public int getCapacity() {
		return capacity;
	}

	public BigDecimal getTeamFactorWorseByPercent() {
		return teamFactorWorseByPercent;
	}

	public BigDecimal getScopeGrowthP10Percent() {
		return scopeGrowthP10Percent;
	}

	public BigDecimal getScopeGrowthP90Percent() {
		return scopeGrowthP90Percent;
	}

	public String getPriorityRule() {
		return priorityRule;
	}

	/**
	 * Whether this run's dates can be worked out by the calendar this code implements.
	 *
	 * <p>
	 * Stated once because two things ask it: the response, which reports no dates for a
	 * run it cannot resolve, and an inverse query, which cannot turn a target date into
	 * hours for one. The rule is the stored rule's name rather than the presence of the
	 * other two columns — a run resolves under the calendar it was made with, and one
	 * made under a name this code has never heard of must not be read through today's.
	 */
	boolean hasReadableCalendar() {
		return WorkingCalendar.RULE.equals(this.calendarRule);
	}

	/** Null for every run made before a calendar existed, and that is a true record. */
	public LocalDate getStartsOn() {
		return startsOn;
	}

	public BigDecimal getWorkingHoursPerDay() {
		return workingHoursPerDay;
	}

	public String getCalendarRule() {
		return calendarRule;
	}

	public int getEngineVersion() {
		return engineVersion;
	}

	/**
	 * What was declared, as it was written down. {@code ForecastInputs.PlannedPool} is
	 * its shape.
	 */
	public String getResourcing() {
		return resourcing;
	}

	public int getItemCount() {
		return itemCount;
	}

	public int getEstimatedItemCount() {
		return estimatedItemCount;
	}

	public BigDecimal getMeanHours() {
		return meanHours;
	}

	public BigDecimal getP10Hours() {
		return p10Hours;
	}

	public BigDecimal getP50Hours() {
		return p50Hours;
	}

	public BigDecimal getP80Hours() {
		return p80Hours;
	}

	public BigDecimal getP90Hours() {
		return p90Hours;
	}

	public BigDecimal getP95Hours() {
		return p95Hours;
	}

	/** The snapshot, as it was written down. {@link ForecastInputs} is its shape. */
	public String getInputs() {
		return inputs;
	}

	public String getOutputs() {
		return outputs;
	}

}
