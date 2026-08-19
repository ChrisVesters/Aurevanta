import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLoaded } from '../api/useLoaded';
import { DATE_AT } from './confidence';
import type { Confidence } from './confidence';
import { MovementAsked } from './MovementAsked';
import { calendarOf, hours, readingOf, stretchAndGrowth } from './forecastText';
import type { Forecast, Movement } from './forecastTypes';

/**
 * Every forecast of this plan before the newest, and why the date moved between two of them.
 *
 * **Its own component because the account is its own request, and an expensive one**: seven
 * whole simulations, which is cheap for somebody who asked and rude to charge everybody who
 * opened the page. That is the same argument the breakdown beside it makes, and the same
 * reason the state lives here rather than three more pieces of it in the panel above.
 *
 * **The account is asked *of* the newest run and *since* an older one**, which is why both
 * identifiers are needed: a movement is a property of a pair, and there is nowhere on either
 * run to hang it.
 */
export function EarlierRuns({
  latestId,
  latestHasDate,
  earlier,
  confidence
}: {
  latestId: string;
  /**
   * Whether the newest run has a date at this confidence at all.
   *
   * Asked of the *newest* run and answered by the panel, because a movement is a property of
   * a pair: an account is offered only where both ends have a day to name, and a run made
   * before there was a calendar has hours and no date. "Why did the date move" is not a
   * question about a run that never had one.
   */
  latestHasDate: boolean;
  /** Every run but the newest, newest first — the listing's own order, never re-sorted. */
  earlier: Forecast[];
  confidence: Confidence;
}) {
  const { t, i18n } = useTranslation();
  /**
   * Which earlier forecast somebody is asking about, and the account that came back.
   *
   * Its own request, like the breakdown above and for the same reason: an account of a
   * movement costs seven whole simulations, which is cheap for somebody who asked and rude to
   * charge everybody who opened the page.
   */
  const [explainingMove, setExplainingMove] = useState<string | null>(null);

  // Nothing until somebody asks, which is what the null path says: seven simulations is
  // long enough that somebody can navigate away while it is in flight.
  //
  // The failure includes this endpoint's own refusal: two runs made by different versions of
  // the model are not a rougher comparison, they are an exact account of a movement that
  // never happened.
  const { data: movement, failure: movementFailure } = useLoaded<Movement>(
    explainingMove === null
      ? null
      : `/forecasts/${latestId}/movement?since=${explainingMove}`,
    [explainingMove, latestId]
  );

  const movedBy = readingOf(movement, confidence);

  return (
    <div className="earlier">
      <h3>{t('projects.forecast.earlier.title')}</h3>
      <ul>
        {earlier.map((run) => {
          const readUnder = calendarOf(run, i18n.language);
          return (
            <li key={run.id}>
              {t('projects.forecast.earlier.entry', {
                middle: hours(run.p50Hours, i18n.language),
                high: hours(run.p90Hours, i18n.language),
                capacity: run.capacity,
                who: run.requestedByName,
                ...stretchAndGrowth(run, i18n.language)
              })}
              {/*
                Its own calendar, for the reason it carries its own assumptions:
                two runs read under different working days are two readings rather
                than a date moving, which is the mistake M10 exists to avoid.
              */}
              {readUnder && (
                <> {t('projects.forecast.earlier.calendar', readUnder)}</>
              )}
              {/*
                **Asked of a pair rather than of a run**, which is why it is here
                and not beside the date: the question is what happened between this
                forecast and the newest one.

                Offered only where both ends have a date to have moved. A run made
                before there was a calendar has hours and no date, and "why did the
                date move" is not a question about it — the same reason the
                confidence control is absent rather than disabled on such a run.
              */}
              {latestHasDate && run[DATE_AT[confidence]] !== null && (
                <MovementAsked
                  asking={explainingMove === run.id}
                  account={movedBy}
                  simulations={movement?.simulations ?? 0}
                  failure={movementFailure}
                  // The account on screen is about a different pair the moment
                  // somebody asks about another one, and clearing it is the read's
                  // own business: naming a new one changes the path, and a read
                  // that has started has not failed and has no answer yet.
                  onAsk={() => setExplainingMove(run.id)}
                />
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
