import { useTranslation } from 'react-i18next';
import { Field } from '../auth/Field';
import { MAXIMUM_SLUG_LENGTH, MINIMUM_SLUG_LENGTH } from './slug';

/**
 * What the server answers when somebody else holds the handle.
 *
 * Exported because the forms need it twice: to tell this field to say so, and to keep the
 * banner quiet while it does. A refusal about a field the visitor can see belongs beside
 * that field and nowhere else — saying it twice is the thing `useFormFailure` exists to
 * prevent, and it cannot prevent this one because a handle already taken is not a
 * validation failure and never appears in `errors`.
 */
export const SLUG_TAKEN = 'slug_taken';

type SlugFieldProps = {
  id: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  /** Whether the last attempt was refused because somebody else holds this handle. */
  taken: boolean;
  /**
   * Whether that refusal arrived holding a free alternative, which this field has been
   * replaced with. Not every one does: the race between two callers choosing the same
   * handle is refused after the transaction is lost, with nothing left to go and look up.
   * Saying "we have suggested another" there would point at a field still holding the
   * handle that was just refused.
   */
  suggested: boolean;
};

/**
 * The handle an organisation will answer to, shared by the two forms that create one.
 *
 * Controlled, because it is proposed from the name above it until its owner takes it
 * over — and because a refusal replaces it with the free alternative the server sent,
 * which an uncontrolled input could not be told about.
 */
export function SlugField({
  id,
  value,
  onChange,
  error,
  taken,
  suggested
}: SlugFieldProps) {
  const { t } = useTranslation();

  return (
    <>
      <Field
        id={id}
        name={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        label={t('auth.fields.slug.label')}
        autoComplete="off"
        spellCheck={false}
        required
        minLength={MINIMUM_SLUG_LENGTH}
        maxLength={MAXIMUM_SLUG_LENGTH}
        error={error}
        hint={t('auth.fields.slug.hint')}
      />
      {/*
        The refusal belongs to no field as far as `useFormFailure` is concerned — it is
        not a validation failure — so it is placed here, beside the input it is about,
        rather than left in the banner where it would read as a complaint about the form.
      */}
      {taken && (
        <p className="field-error" role="alert">
          {suggested
            ? t('auth.fields.slug.taken')
            : t('auth.fields.slug.takenWithoutSuggestion')}
        </p>
      )}
    </>
  );
}
