import { useTranslation } from 'react-i18next';
import { useLoaded } from '../api/useLoaded';
import { useAuth } from '../auth/AuthContext';
import { TrackRecord } from '../calibration/TrackRecord';
import type { Calibration } from '../calibration/types';

/**
 * How often the ranges written here contained what the work actually took.
 *
 * **Organisation-wide and not per plan**, because a single plan holds far too few completed
 * items to reach the counts at which a hit rate distinguishes 45% from 80% — and because
 * calibration is a property of people rather than of plans.
 *
 * Reachable by every member, for the reason the member list is: colleagues may see what
 * their colleagues estimated, and the estimates themselves are already on the plan screen.
 */
export function CalibrationPage() {
  const { t } = useTranslation();
  const { account } = useAuth();
  const organisationId = account?.organisation.id;
  // Keyed on the organisation, so switching to another one asks again rather than leaving
  // the previous organisation's record on screen.
  const { data: record, failure } = useLoaded<Calibration>(
    organisationId ? '/calibration' : null,
    [organisationId]
  );

  return (
    <main className="calibration">
      <h1>{t('calibration.title')}</h1>
      <p className="lede">{t('calibration.lede')}</p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      {record === null ? (
        failure === null && (
          <p className="loading" role="status">
            {t('calibration.loading')}
          </p>
        )
      ) : (
        <TrackRecord record={record} />
      )}
    </main>
  );
}
