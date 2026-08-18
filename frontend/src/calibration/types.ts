/**
 * One set of ranges and what they turned out to be worth.
 *
 * **Counts are always numbers and the rates are null until there is evidence for them.**
 * Nought out of nought is not a rate of nought, and "0% of your estimates landed inside
 * their range" is the worst thing this screen could say to an organisation that has not
 * finished anything yet — which is most of them, for months.
 *
 * **The rate and the corrections are read together or not at all.** A hit rate on its own is
 * won by estimating one to a thousand hours, which contains every outcome and forecasts
 * nothing; `bandWidthMultiplier` is what reports that as a number below one. Grouping each
 * fact into its own object is what makes that structural rather than a convention a reader
 * has to remember.
 */
export type HitRate = {
  /** Between 0 and 1. A well-judged set of ranges scores 0.8, and higher is not better. */
  value: number;
  /** The 80% interval around it — the same confidence as the band being measured. */
  low: number;
  high: number;
};

export type Corrections = {
  /** Where the truth typically landed on the estimator's own range. Half way is unbiased. */
  medianPercentile: number;
  /** How many times wider the ranges should have been. 1 is right. */
  bandWidthMultiplier: number;
};

export type CalibrationRecord = {
  scored: number;
  hits: number;
  /** Which way the misses went. All one way is bias; both ways is a range too tight. */
  belowP10: number;
  aboveP90: number;
  /** How many claimed certainty. They count in the rate and cannot count in the two above. */
  pointEstimates: number;
  /**
   * Null until there is evidence for it, and **one object rather than three fields**: a rate
   * without its interval is the half of it that means nothing, and as loose fields that was
   * a convention every reader had to keep. Here there is no shape that holds one and not the
   * others.
   */
  rate: HitRate | null;
  /** Null below two outcomes, and one object for the same reason — see {@link Corrections}. */
  corrections: Corrections | null;
};

/** One person's own record, over the forecasts they wrote before the work began. */
export type EstimatorCalibration = {
  estimatorId: string;
  /**
   * Off the estimate rather than off the member list, so somebody who has left this
   * organisation still appears — their estimates did not leave with them.
   */
  estimatorName: string;
  record: CalibrationRecord;
};

/** How the ranges collected one way turned out, against those collected another. */
export type MethodCalibration = {
  /** The name as stored. One this version has never heard of is still a row. */
  method: string;
  record: CalibrationRecord;
};

/**
 * What could not be scored, and why.
 *
 * The most-read part of this page for most of a year, and the reason it is designed rather
 * than treated as an empty state: scoring anything needs finished work carrying both an
 * estimate and a measured actual, and the actual is optional because most teams do not track
 * it. Each number here is a different thing to go and do.
 */
export type CalibrationCoverage = {
  completedItems: number;
  withActual: number;
  withEstimate: number;
  scoredItems: number;
  /**
   * How many estimates counted as reports rather than forecasts *only* because they were
   * written on the day the work began. The server refuses to guess at a time of day, and
   * this is what that refusal cost — published so that it arrives as a number somebody can
   * see rather than as a quietly better hit rate.
   */
  movedByTheStartDay: number;
};

/**
 * How often this organisation's ranges contained the truth.
 *
 * **Three buckets, and nothing may add them together.** They are one question asked of
 * ranges written at three moments relative to the work: before it began, after it began, and
 * where nobody ever said when it began. Only the first is a forecast — the second is what
 * somebody wrote once they could see how the task was going, and folding it in would flatter
 * the one number in this product whose whole value is that it is unflattering.
 */
export type Calibration = {
  forecasts: CalibrationRecord;
  reports: CalibrationRecord;
  unbounded: CalibrationRecord;
  byEstimator: EstimatorCalibration[];
  byMethod: MethodCalibration[];
  coverage: CalibrationCoverage;
  /**
   * When the earliest and latest scored estimates were *written* — moments, so they are
   * shown where the reader is sitting. They are how somebody tells a current record from one
   * describing how the team estimated a year ago. Null together when nothing is scored.
   */
  firstScored: string | null;
  lastScored: string | null;
};
