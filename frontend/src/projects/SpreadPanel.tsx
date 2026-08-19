import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLoaded } from '../api/useLoaded';
import { describeSource } from './forecastText';
import type { Contribution } from './forecastTypes';

/**
 * What the band is made of, ranked — and the sentence saying the ranking does not add up.
 *
 * **Its own component because it is its own request, and an expensive one.** Working this
 * out replays the whole run, about half a second at the five hundred items a plan may hold.
 * That is cheap for somebody who asked and rude to charge everybody who opened the page, so
 * nothing here happens until the button is pressed — which is a reason to own the state
 * rather than to hand four more pieces of it to the panel above.
 *
 * **Nothing may render these as percentages.** They are squared correlations, not a
 * partition: the common-cause model's shared factor multiplies every item by the same draw, so everything moves
 * with everything and the shares add to well over one. A bar per source, and a line saying
 * why they overlap.
 */
export function SpreadPanel({ runId }: { runId: string }) {
  const { t } = useTranslation();
  // Null until somebody asks. Working this out replays the whole run — about half a second
  // at five hundred items — which is not a cost to put on opening a page, and most readers
  // never scroll this far.
  /** Which run somebody has asked about, which is what starts the work below. */
  const [explaining, setExplaining] = useState<string | null>(null);

  // Nothing until somebody asks, which is what the null path says: this is by far the
  // slowest request this panel makes — a whole replay — so it is by far the likeliest to be
  // in flight when somebody navigates away.
  //
  // The failure includes the one refusal this endpoint has of its own: a run the engine no
  // longer reproduces is not explained at all, because a ranking from a different model is
  // an exact ranking of a plan nobody forecast.
  const { data: spread, failure: spreadFailure } = useLoaded<Contribution[]>(
    explaining === null ? null : `/forecasts/${explaining}/contributions`,
    [explaining]
  );
  const breakingDown =
    explaining !== null && spread === null && spreadFailure === null;

  return (
    <div className="spread">
      {spread === null && spreadFailure === null ? (
        <p className="actions">
          <button
            type="button"
            className="secondary"
            disabled={breakingDown}
            onClick={() => setExplaining(runId)}
          >
            {breakingDown
              ? t('projects.forecast.contributions.loading')
              : t('projects.forecast.contributions.open')}
          </button>
        </p>
      ) : spreadFailure !== null ? (
        <p className="empty">{spreadFailure}</p>
      ) : (
        <>
          <h3>{t('projects.forecast.contributions.title')}</h3>
          <p className="hint">{t('projects.forecast.contributions.lede')}</p>
          <ul className="ranking">
            {spread?.map((source) => (
              <li key={source.itemId ?? source.kind}>
                <span className="what">{describeSource(t, source)}</span>
                {/*
                  A bar rather than a number, deliberately. These are shares of the
                  spread and they overlap, so a percentage beside each would invite
                  somebody to add them up and find their plan accounts for three
                  hundred percent of its own uncertainty.
                */}
                <span
                  className="bar"
                  style={{
                    width: `${Math.min(100, source.shareOfSpread * 100)}%`
                  }}
                />
              </li>
            ))}
          </ul>
          {/*
            Beside the ranking and not behind a disclosure, for the reason the
            assumptions and the limitations are: a number seen without its caveat is
            already in somebody's slide.
          */}
          <p className="caveat">
            {t('projects.forecast.contributions.caveat')}
          </p>
        </>
      )}
    </div>
  );
}
