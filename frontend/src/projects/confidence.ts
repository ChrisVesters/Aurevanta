/**
 * How a forecast is *read*, which is a different thing from what it says.
 *
 * Shared because three files ask the same question of one run — the panel, the sentence it
 * prints, and the second forecast beside it — and a second list of confidences would be a
 * control offering a reading nothing could answer.
 */

/**
 * The three confidences worth committing at, and the percentile each reads.
 *
 * **A view over one run and never a re-run.** All five percentiles are already in the
 * response, so moving between these changes a date on screen without a request going out —
 * which is not an optimisation but the feature. The trade this work exists to make
 * visible only works if it is immediate: somebody asks whether the plan can go faster, and
 * the answer is a control moving from 95 to 80 and a date moving with it, on one screen,
 * from one forecast. A round trip would make two readings of one run look like two
 * different forecasts.
 *
 * It also means the confidence is not stored on the run: there is no such thing as the
 * confidence a forecast was *made* at, only the one somebody is reading it at.
 */
export const CONFIDENCES = [50, 80, 95] as const;

export type Confidence = (typeof CONFIDENCES)[number];

/**
 * Which percentile each of them reads. The other two dates have no control and no need.
 *
 * **Both forecasts, because a throughput answer carries the same five percentiles under the
 * same names.** That is what keeps the trade immediate on both sides: moving from 95 to 80
 * changes two dates and sends no request. A second constant holding the same three fields
 * would be two names for one thing and a reader having to check whether they agree.
 */
export const DATE_AT: Record<Confidence, 'p50Date' | 'p80Date' | 'p95Date'> = {
  50: 'p50Date',
  80: 'p80Date',
  95: 'p95Date'
};

/**
 * Eight tenths of the probability, which is what the band sentence beneath already states.
 * A view has to start somewhere and this is the reading the rest of the screen agrees with
 * — unlike the assumptions above, which have no right answer and so are left empty.
 */
export const USUAL_CONFIDENCE: Confidence = 80;
