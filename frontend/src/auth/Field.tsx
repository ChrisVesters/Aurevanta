import type { ComponentProps } from 'react';

type FieldProps = ComponentProps<'input'> & {
  id: string;
  label: string;
  /** Message from the server for this field, shown beneath the input. */
  error?: string;
  hint?: string;
};

export function Field({ id, label, error, hint, ...input }: FieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;

  return (
    <p className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={
          [errorId, hintId].filter(Boolean).join(' ') || undefined
        }
        {...input}
      />
      {hint && (
        <span className="hint" id={hintId}>
          {hint}
        </span>
      )}
      {error && (
        <span className="field-error" id={errorId}>
          {error}
        </span>
      )}
    </p>
  );
}
