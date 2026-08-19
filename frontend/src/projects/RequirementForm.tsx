import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import type { Requirement, Resource } from '../resource/types';

type RequirementFormProps = {
  id: string;
  /** Every pool the organisation has in use, in the order it declared them. */
  resources: Resource[];
  /** What this piece of work needs today, which the boxes start from. */
  needs: Requirement[];
  busy: boolean;
  banner: string | null;
  onSubmit: (needs: { resourceId: string; units: number }[]) => void;
  onCancel: () => void;
};

/**
 * What one piece of work needs, asked as a whole set.
 *
 * **A number against every pool rather than a list somebody adds to**, because a
 * requirement means little alone: what a task needs is the list, and the endpoint replaces
 * it in one go. Nothing here adds a line and nothing removes one — a box left empty is the
 * claim that this work needs none of that pool.
 *
 * **Only pools in use are offered.** A team that has put one away is a team that no longer
 * has it, and a forecast leaves such a requirement out and says so — so inviting somebody
 * to make a new one would be inviting them to make a limitation.
 *
 * **An empty set is a claim and the form says which claim it is.** Work that names nothing
 * is scheduled against one unit of whatever is free, which is generic work anybody can pick
 * up — a different thing from work that is outside the competition for a team.
 */
export function RequirementForm({
  id,
  resources,
  needs,
  busy,
  banner,
  onSubmit,
  onCancel
}: RequirementFormProps) {
  const { t } = useTranslation();
  const [wanted, setWanted] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      needs.map((need) => [need.resourceId, String(need.units)])
    )
  );

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(
      resources
        .map((resource) => ({
          resourceId: resource.id,
          units: Number(wanted[resource.id] ?? '')
        }))
        // An empty box and a zero are the same claim — this work needs none of that pool —
        // and the server refuses a line of nothing rather than storing one.
        .filter((need) => Number.isFinite(need.units) && need.units > 0)
    );
  }

  if (resources.length === 0) {
    return (
      <div className="needs">
        <p className="empty">{t('projects.items.needs.noResources')}</p>
        <p className="actions">
          <button type="button" className="secondary" onClick={onCancel}>
            {t('projects.items.needs.cancel')}
          </button>
        </p>
      </div>
    );
  }

  return (
    <form className="needs" onSubmit={handle} noValidate>
      <p className="hint">{t('projects.items.needs.lede')}</p>
      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}
      {resources.map((resource) => (
        <p className="field" key={resource.id}>
          <label htmlFor={`${id}-${resource.id}`}>{resource.name}</label>
          <input
            id={`${id}-${resource.id}`}
            type="number"
            inputMode="numeric"
            min="0"
            max={resource.units}
            step="1"
            value={wanted[resource.id] ?? ''}
            onChange={(event) =>
              setWanted((held) => ({
                ...held,
                [resource.id]: event.target.value
              }))
            }
          />
          <span className="hint">
            {t('projects.items.needs.available', { units: resource.units })}
          </span>
        </p>
      ))}
      {/*
        Said where somebody is deciding rather than left to be discovered in a limitation
        beside a date: leaving every box empty is a claim, and it is the ordinary one.
      */}
      <p className="hint">{t('projects.items.needs.anyone')}</p>
      <p className="actions">
        <button type="submit" disabled={busy}>
          {t('projects.items.needs.submit')}
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          {t('projects.items.needs.cancel')}
        </button>
      </p>
    </form>
  );
}
