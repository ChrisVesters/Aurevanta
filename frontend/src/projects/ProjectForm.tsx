import { useTranslation } from 'react-i18next';
import type { ReactNode, SubmitEvent } from 'react';
import { Field } from '../auth/Field';
import {
  MAXIMUM_DESCRIPTION_LENGTH,
  MAXIMUM_NAME_LENGTH
} from '../auth/constants';
import { textField } from '../auth/formValues';

type ProjectFormProps = {
  /** Distinguishes the inputs' ids, since both forms can be on screen in one app. */
  id: string;
  name?: string;
  description?: string | null;
  busy: boolean;
  submit: string;
  submitting: string;
  banner: string | null;
  fieldErrors: Record<string, string>;
  onSubmit: (values: { name: string; description: string | null }) => void;
  children?: ReactNode;
};

/**
 * The two fields a project has, shared by the form that starts one and the form that
 * changes one.
 *
 * They are the same two questions asked at different moments, and the thing worth not
 * repeating is what an empty description means: **the server stores nothing rather than an
 * empty string**, so this sends null for one. Written twice, one of them would eventually
 * send `''` and give the column two spellings for the same absence.
 */
export function ProjectForm({
  id,
  name,
  description,
  busy,
  submit,
  submitting,
  banner,
  fieldErrors,
  onSubmit,
  children
}: ProjectFormProps) {
  const { t } = useTranslation();

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    onSubmit({
      name: textField(values, 'name'),
      description: textField(values, 'description') || null
    });
  }

  return (
    <form className="project-form" onSubmit={handle} noValidate>
      {children}

      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}

      <Field
        id={`${id}-name`}
        name="name"
        label={t('projects.fields.name.label')}
        // Not the browser's autofill business: a project name is neither a person nor an
        // address, and offering one somebody typed on another site would be noise.
        autoComplete="off"
        required
        maxLength={MAXIMUM_NAME_LENGTH}
        defaultValue={name}
        error={fieldErrors.name}
      />

      <p className="field">
        <label htmlFor={`${id}-description`}>
          {t('projects.fields.description.label')}
        </label>
        <textarea
          id={`${id}-description`}
          name="description"
          rows={3}
          maxLength={MAXIMUM_DESCRIPTION_LENGTH}
          defaultValue={description ?? ''}
          aria-describedby={`${id}-description-hint`}
        />
        <span className="hint" id={`${id}-description-hint`}>
          {t('projects.fields.description.hint')}
        </span>
        {fieldErrors.description && (
          <span className="field-error">{fieldErrors.description}</span>
        )}
      </p>

      <button type="submit" className="primary" disabled={busy}>
        {busy ? submitting : submit}
      </button>
    </form>
  );
}
