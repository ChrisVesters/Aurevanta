import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { Field } from '../auth/Field';
import {
  MAXIMUM_DESCRIPTION_LENGTH,
  MAXIMUM_NAME_LENGTH
} from '../auth/constants';
import { textField } from '../auth/formValues';
import { optionalField } from './fields';

type WorkItemFormProps = {
  /** Distinguishes the inputs' ids: the add form and an open row are on screen at once. */
  id: string;
  title?: string;
  description?: string | null;
  busy: boolean;
  submit: string;
  submitting: string;
  banner: string | null;
  fieldErrors: Record<string, string>;
  onSubmit: (values: { title: string; description: string | null }) => void;
  /** Present only where there is something to go back to — an open row, not the add form. */
  onCancel?: () => void;
};

/**
 * What a work item says about itself, shared by the row that adds one and the row that
 * rewords one.
 *
 * Deliberately two plain boxes. The estimate this item will carry is step 3's, and making
 * *that* form good is M5's whole milestone — `product-concept.md` is explicit that three
 * boxes labelled P10/P50/P90 produce numbers nobody thought about, which is a question a
 * better-looking form cannot answer.
 */
export function WorkItemForm({
  id,
  title,
  description,
  busy,
  submit,
  submitting,
  banner,
  fieldErrors,
  onSubmit,
  onCancel
}: WorkItemFormProps) {
  const { t } = useTranslation();

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    onSubmit({
      title: textField(values, 'title'),
      description: optionalField(values, 'description')
    });
  }

  return (
    <form className="work-item-form" onSubmit={handle} noValidate>
      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}

      <Field
        id={`${id}-title`}
        name="title"
        label={t('projects.items.fields.title.label')}
        autoComplete="off"
        required
        maxLength={MAXIMUM_NAME_LENGTH}
        defaultValue={title}
        error={fieldErrors.title}
      />

      <p className="field">
        <label htmlFor={`${id}-description`}>
          {t('projects.items.fields.description.label')}
        </label>
        <textarea
          id={`${id}-description`}
          name="description"
          rows={2}
          maxLength={MAXIMUM_DESCRIPTION_LENGTH}
          defaultValue={description ?? ''}
        />
        {fieldErrors.description && (
          <span className="field-error">{fieldErrors.description}</span>
        )}
      </p>

      <p className="actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy ? submitting : submit}
        </button>
        {onCancel && (
          <button type="button" className="link" onClick={onCancel}>
            {t('projects.items.edit.cancel')}
          </button>
        )}
      </p>
    </form>
  );
}
