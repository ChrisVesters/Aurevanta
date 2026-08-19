import { useTranslation } from 'react-i18next';
import type { ReactNode, SubmitEvent } from 'react';
import { Field } from '../auth/Field';
import { textField } from '../auth/formValues';
import { numberField } from '../projects/fields';
import type { Member } from '../members/types';

type ResourceFormProps = {
  /** Distinguishes the inputs' ids, since a page holds one of these per row and one more. */
  id: string;
  name?: string;
  units?: number;
  personId?: string | null;
  people: Member[];
  busy: boolean;
  submit: string;
  submitting: string;
  banner: string | null;
  fieldErrors: Record<string, string>;
  onSubmit: (values: {
    name: string;
    units: number | null;
    personId: string | null;
  }) => void;
  children?: ReactNode;
};

/**
 * The three questions a pool answers, shared by the form that declares one and the form
 * that changes one — the same questions asked at different moments, which is
 * `ProjectForm`'s arrangement exactly.
 *
 * **The person is a select over colleagues rather than a box.** A pool may only name
 * somebody in this organisation, so offering the list is what makes that rule invisible
 * instead of a refusal somebody meets after typing.
 */
export function ResourceForm({
  id,
  name,
  units,
  personId,
  people,
  busy,
  submit,
  submitting,
  banner,
  fieldErrors,
  onSubmit,
  children
}: ResourceFormProps) {
  const { t } = useTranslation();

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    onSubmit({
      name: textField(values, 'name'),
      // Never `Number('')`, which is zero: an untouched box would otherwise arrive as a
      // pool of nobody and be refused for a number nobody typed.
      units: numberField(values, 'units'),
      personId: textField(values, 'personId') || null
    });
  }

  return (
    <form onSubmit={handle} noValidate>
      {children}
      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}
      <Field
        id={`${id}-name`}
        name="name"
        label={t('resources.fields.name.label')}
        defaultValue={name}
        maxLength={200}
        error={fieldErrors.name}
      />
      <Field
        id={`${id}-units`}
        name="units"
        type="number"
        inputMode="numeric"
        min="1"
        step="1"
        label={t('resources.fields.units.label')}
        hint={t('resources.fields.units.hint')}
        defaultValue={units}
        error={fieldErrors.units}
      />
      <p className="field">
        <label htmlFor={`${id}-person`}>
          {t('resources.fields.person.label')}
        </label>
        <select
          id={`${id}-person`}
          name="personId"
          defaultValue={personId ?? ''}
          aria-describedby={`${id}-person-hint`}
        >
          <option value="">{t('resources.fields.person.nobody')}</option>
          {people.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.displayName}
            </option>
          ))}
        </select>
        <span className="hint" id={`${id}-person-hint`}>
          {t('resources.fields.person.hint')}
        </span>
      </p>
      <p className="actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy ? submitting : submit}
        </button>
      </p>
    </form>
  );
}
