import { useTranslation } from 'react-i18next';
import { describeMoved, describeTerm } from './forecastText';
import type { MovementAt } from './forecastTypes';

/**
 * The question "why did the date move?", and its answer once somebody has asked it.
 *
 * **It costs seven simulations and says so.** M7's rule: a number that is expensive to produce
 * should say what it cost rather than surprise somebody, and this is the second place in the
 * product where a read runs the engine.
 */
export function MovementAsked({
  asking,
  account,
  simulations,
  failure,
  onAsk
}: {
  asking: boolean;
  account: MovementAt | null;
  simulations: number;
  failure: string | null;
  onAsk: () => void;
}) {
  const { t, i18n } = useTranslation();

  if (!asking) {
    return (
      <p className="actions">
        <button type="button" className="secondary" onClick={onAsk}>
          {t('projects.forecast.movement.open')}
        </button>
      </p>
    );
  }
  if (failure !== null) {
    return <p className="empty">{failure}</p>;
  }
  if (account === null) {
    return <p className="hint">{t('projects.forecast.movement.loading')}</p>;
  }
  return (
    <div className="movement">
      <p className="date">{describeMoved(t, account, i18n.language)}</p>
      {/*
        In the order the server attributed them and never re-sorted. The order *is* the rule:
        two defensible ones split the same eight days differently, so a list sorted by size
        here would be an account read under an order nobody stated.
      */}
      <ul className="terms">
        {account.terms.map((term) => (
          <li key={term.step}>
            <span className="what">
              {t(`projects.forecast.movement.steps.${term.step}`)}
            </span>
            <span className="days">{describeTerm(t, term)}</span>
          </li>
        ))}
      </ul>
      {/*
        Why they add up, and what that cost — beside the numbers rather than behind a link,
        which is the rule the assumptions and the limitations already follow.
      */}
      <p className="caveat">
        {t('projects.forecast.movement.cost', { simulations })}
      </p>
    </div>
  );
}
