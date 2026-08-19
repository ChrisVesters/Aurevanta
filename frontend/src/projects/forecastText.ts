import type { TFunction } from 'i18next';
import { DATE_AT } from './confidence';
import type { Confidence } from './confidence';
import { formatDay } from './dates';
import { describeWork } from './work';
import type {
  Contribution,
  Forecast,
  ForecastLimitation,
  ForecastResource,
  MovementAt,
  MovementTerm,
  Throughput
} from './forecastTypes';

/**
 * Every sentence this panel says, and nothing that says it.
 *
 * **Prose is the feature here, which is why it is worth holding apart from the markup.**
 * the whole subject of saying a forecast plainly is saying a forecast to somebody who does not know what P90 means, and
 * its sharpest rule — *one date, never a window* — is a rule about a string. A function that
 * builds one is testable, greppable and reviewable on its own; the same rule spread through
 * JSX is a rule nobody can check.
 *
 * Pure, and takes its `t` rather than calling `useTranslation`: none of these is a component,
 * and a hook here would make them impossible to call from a helper.
 */

/**
 * The three ways to have no second date, in the order they are looked for.
 *
 * Each is a different thing to say, and a code this version has never heard of gets a sentence
 * saying so rather than nothing — the rule `describeLimitation` follows, because the server is
 * what versions ahead here.
 */
const NO_SECOND_OPINION = [
  'throughput_nothing_left',
  'throughput_history_too_short',
  'throughput_beyond_horizon'
] as const;

const LIMITATIONS: ForecastLimitation[] = [
  'no_team_factor',
  'no_scope_uncertainty',
  'unestimated_items',
  'inconsistent_estimates',
  'dependencies_on_archived_work',
  'unassigned_work',
  'requirements_on_archived_resources'
];

/**
 * The reading at one confidence, out of an answer that carries all three.
 *
 * **Shared by the drift and the decomposition because it is the same property**: both were
 * computed at every confidence the calendar's control offers, so moving the control changes what is on
 * screen and sends no request. That is the trade the calendar built the control to make immediate,
 * inherited here rather than restated.
 */
export function readingOf<T extends { confidence: number }>(
  answer: { at: T[] } | null,
  confidence: Confidence
): T | null {
  return (
    answer?.at.find((reading) => reading.confidence === confidence) ?? null
  );
}

/**
 * The whole move in one sentence, or nothing when there is no date to have moved.
 *
 * The absence is a guard rather than a case: this question is only offered where both runs
 * resolve a date. The server versions ahead, though, and a headline missing its own dates
 * would otherwise render as a sentence with two blanks in it.
 */
export function describeMoved(
  t: TFunction,
  account: MovementAt,
  locale: string
): string | null {
  if (account.fromDate === null || account.toDate === null) {
    return null;
  }
  const from = formatDay(account.fromDate, locale);
  const to = formatDay(account.toDate, locale);
  const days = Math.round(
    (Date.parse(account.toDate) - Date.parse(account.fromDate)) /
      (24 * 60 * 60 * 1000)
  );
  if (days === 0) {
    return t('projects.forecast.movement.movedNot', {
      confidence: account.confidence,
      date: to
    });
  }
  return days > 0
    ? t('projects.forecast.movement.movedOut', {
        confidence: account.confidence,
        days,
        from,
        to
      })
    : t('projects.forecast.movement.movedIn', {
        confidence: account.confidence,
        days: -days,
        from,
        to
      });
}

/** One term, in the direction it moved the date — and never as a bare signed number. */
export function describeTerm(t: TFunction, term: MovementTerm): string {
  if (term.movedDays === null) {
    return t('projects.forecast.movement.termNoDays');
  }
  if (term.movedDays === 0) {
    return t('projects.forecast.movement.termNone');
  }
  return term.movedDays > 0
    ? t('projects.forecast.movement.termLater', { count: term.movedDays })
    : t('projects.forecast.movement.termEarlier', { count: -term.movedDays });
}

/**
 * One pool a run was scheduled against, as a reader sees it.
 *
 * **Three ways it can stand today, and the same three a contribution ranking already
 * handles**: still here, put away since, or gone from this organisation altogether. The
 * units are always the run's own — the whole point of copying the declaration onto it is
 * that hiring somebody does not rewrite what last month's forecast assumed.
 */
export function describePool(t: TFunction, pool: ForecastResource): string {
  // `count` rather than `units`, because these are the plurals i18next selects on: one
  // backend engineer is a unit and three are units, and a key that took a differently named
  // number would silently resolve to nothing at all.
  if (pool.name === null) {
    return t('projects.forecast.resources.gone', { count: pool.units });
  }
  return pool.archived
    ? t('projects.forecast.resources.putAway', {
        name: pool.name,
        count: pool.units
      })
    : t('projects.forecast.resources.pool', {
        name: pool.name,
        count: pool.units
      });
}

/** Which of the three reasons there is no second date, or that this version cannot say. */
export function describeNoSecondOpinion(
  t: TFunction,
  history: Throughput
): string {
  const reason = NO_SECOND_OPINION.find((code) =>
    history.limitations.includes(code)
  );
  return reason
    ? t(`projects.forecast.throughput.none.${reason}`)
    : t('projects.forecast.throughput.none.unknown');
}

/**
 * How far apart the two are, in days and in words.
 *
 * Later is the ordinary result and says so: the engine sums estimates of focused work and the
 * history contains every meeting, incident and holiday. A team seeing the two agree closely
 * should be more suspicious than one seeing them differ.
 */
export function describeDifference(
  t: TFunction,
  ours: string,
  theirs: string
): string {
  const days = Math.round(
    (Date.parse(theirs) - Date.parse(ours)) / (24 * 60 * 60 * 1000)
  );
  if (days === 0) {
    return t('projects.forecast.throughput.differenceSame');
  }
  return days > 0
    ? t('projects.forecast.throughput.differenceLater', { days })
    : t('projects.forecast.throughput.differenceEarlier', { days: -days });
}

/** What this run assumed about work nobody has written down, which the history cannot see. */
export function describeGrowth(
  t: TFunction,
  run: Forecast,
  locale: string
): string {
  const grows = run.scopeGrowthP10Percent > 0 || run.scopeGrowthP90Percent > 0;
  return grows
    ? t('projects.forecast.throughput.differences.unlistedGrowth', {
        low: decimal(run.scopeGrowthP10Percent, locale, 0),
        high: decimal(run.scopeGrowthP90Percent, locale, 0)
      })
    : t('projects.forecast.throughput.differences.unlistedNone');
}

/** The one place the history is better informed than the engine — decision 7's second row. */
export function describeEstimated(t: TFunction, run: Forecast): string {
  const unestimated = run.itemCount - run.estimatedItemCount;
  return unestimated > 0
    ? t('projects.forecast.throughput.differences.unestimated', {
        unestimated,
        total: run.itemCount
      })
    : t('projects.forecast.throughput.differences.estimated');
}

/**
 * The date somebody would commit to at one confidence, or why there is not one.
 *
 * **Which of the two absences it is matters, and the server is what versions ahead.** A run
 * with no working day was made before a calendar was something anybody stated, and saying
 * so is the point of not having backfilled one. A run that *has* a working day and still no
 * date was read under a calendar this browser has never heard of — the same direction
 * {@link describeLimitation} handles, and the same answer: say what is true rather than
 * show a blank or, far worse, a date worked out under the wrong rule.
 */
export function describeDate(
  t: TFunction,
  run: Forecast,
  plan: string,
  confidence: Confidence,
  locale: string
): string {
  const day = run[DATE_AT[confidence]];
  if (day !== null) {
    return t('projects.forecast.confidence.date', {
      plan,
      confidence,
      date: formatDay(day, locale)
    });
  }
  return run.workingHoursPerDay === null
    ? t('projects.forecast.confidence.noCalendar')
    : t('projects.forecast.confidence.unreadableCalendar');
}

/**
 * What a run's dates were read through, or nothing where it has none.
 *
 * Both halves or neither: they are stored together and cleared together, and a sentence
 * naming one of them would be a sentence with a hole in it.
 */
export function calendarOf(
  run: Forecast,
  locale: string
): { start: string; day: string } | null {
  if (run.startsOn === null || run.workingHoursPerDay === null) {
    return null;
  }
  return {
    start: formatDay(run.startsOn, locale),
    // To the hundredth the column keeps, like the percentages and for the same reason:
    // this is a number somebody typed rather than one this application worked out, so
    // rounding it would show them something other than what they said.
    day: decimal(run.workingHoursPerDay, locale, 2)
  };
}

/**
 * What one source of a plan's spread is called.
 *
 * **Two of the three are not work**, and naming them in words is what keeps the ranking
 * honest: when the shared factor or the unlisted work tops the list, the answer to "what
 * should I spike" is that no estimate below it is the problem.
 *
 * What a piece of work is *called* is {@link describeWork}'s, shared with the cuts below it:
 * one run, two lists naming the same items, and a second copy of that rule would be a second
 * chance for one of them to start rendering a missing item as a blank.
 */
export function describeSource(t: TFunction, source: Contribution): string {
  if (source.kind === 'item') {
    return describeWork(t, source.title, source.archived);
  }
  if (source.kind === 'discovered_work') {
    return t('projects.forecast.contributions.discoveredWork');
  }
  if (source.kind === 'team_factor') {
    return t('projects.forecast.contributions.teamFactor');
  }
  // A kind this version has never heard of, which the types say cannot happen and the
  // server is free to send: it is what versions ahead here. Falling through to the item
  // branch would have labelled it "work no longer in this plan", which is not merely
  // unhelpful but wrong — the same back door `describeLimitation` is closed against.
  return t('projects.forecast.contributions.unknownKind');
}

/**
 * The two assumptions the common-cause model added, as a run reports them — written once because the band and
 * the history below it have to describe a run the same way. A reader comparing two entries
 * is comparing exactly these numbers.
 */
export function stretchAndGrowth(
  run: Forecast,
  locale: string
): { worseBy: string; growthLow: string; growthHigh: string } {
  return {
    worseBy: percent(run.teamFactorWorseByPercent, locale),
    growthLow: percent(run.scopeGrowthP10Percent, locale),
    growthHigh: percent(run.scopeGrowthP90Percent, locale)
  };
}

/**
 * A quantity of effort, in the reader's own locale.
 *
 * To a tenth of an hour, which is six minutes: the engine answers to the hundredth and the
 * columns keep that, and putting either on screen would be claiming a precision the ranges
 * that produced it never had.
 */
export function hours(value: number, locale: string): string {
  return decimal(value, locale, 1);
}

/**
 * A percentage somebody typed, shown to the hundredth the column keeps — which is all of
 * it. Unlike an answer, an assumption is not a quantity this application worked out, so
 * rounding it would be showing somebody a number other than the one they gave.
 */
export function percent(value: number, locale: string): string {
  return decimal(value, locale, 2);
}

export function decimal(
  value: number,
  locale: string,
  maximumFractionDigits: number
): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits }).format(value);
}

/**
 * What a limitation means, in this application's own words.
 *
 * A code this version does not recognise still gets a line rather than being dropped. It
 * means the server is ahead of the browser, which is the direction this pair versions in,
 * and quietly showing nothing would be the one failure decision 12 exists to prevent
 * arriving through the back door. The two codes the common-cause model retired go the other way and are still
 * described here, because a run made before it is still a run this screen lists.
 */
export function describeLimitation(
  t: TFunction,
  limitation: ForecastLimitation
): string {
  return LIMITATIONS.includes(limitation)
    ? t(`projects.forecast.limitations.${limitation}`)
    : t('projects.forecast.limitations.unknown');
}
