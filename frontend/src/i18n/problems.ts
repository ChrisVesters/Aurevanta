import type { TFunction } from 'i18next';
import { ApiError, type FieldProblem } from '../api/client';
import { en } from './en';

type ErrorCode = keyof typeof en.errors.codes;
type ValidationCode = keyof typeof en.errors.validation;

function isKnownCode(code: string | undefined): code is ErrorCode {
  return code !== undefined && code in en.errors.codes;
}

function isKnownValidationCode(code: string): code is ValidationCode {
  return code in en.errors.validation;
}

/**
 * Turns a failure into wording this application owns.
 *
 * The backend sends English prose alongside its machine-readable `code`; translating the
 * code rather than showing that prose is what keeps the interface in the user's language.
 */
export function describeFailure(t: TFunction, error: unknown): string {
  if (!(error instanceof ApiError)) {
    // Never reached the server, so there is no problem document to read.
    return t('errors.network');
  }
  if (isKnownCode(error.code)) {
    return t(`errors.codes.${error.code}`);
  }
  return t('errors.unknown');
}

/**
 * Translates the per-field complaints in a validation failure.
 *
 * Keyed on the constraint that failed, never on the name of the field — that is what lets
 * a new form reuse these messages without adding a catalogue entry per input, and it is
 * why the backend sends `{ code: 'size', min: 12 }` rather than a sentence. The problem
 * itself is handed to `t` as the interpolation values, so `size` can say how long.
 *
 * A constraint the catalogue has no wording for falls back to a generic complaint. There
 * is nothing else to show: the server sends no prose to fall back to, by design.
 */
export function describeFieldErrors(
  t: TFunction,
  error: unknown
): Record<string, string> {
  if (!(error instanceof ApiError)) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(error.fieldErrors).map(([field, problem]) => [
      field,
      describeFieldProblem(t, problem)
    ])
  );
}

/**
 * i18next reads each message's `{{placeholders}}` out of the catalogue and requires them
 * as named arguments — it knows `size` needs `min` and `max`. Those values arrive from the
 * server at runtime, so nothing static can pair them up, and this is the one place that
 * says so. The `renders every constraint code` test is the guard that replaces the
 * compiler here: it fails on any placeholder left unfilled.
 */
type Interpolating = (
  key: `errors.validation.${ValidationCode}`,
  values: FieldProblem
) => string;

function describeFieldProblem(t: TFunction, problem: FieldProblem): string {
  if (!isKnownValidationCode(problem.code)) {
    return t('errors.validation.invalid');
  }
  const interpolate = t as unknown as Interpolating;
  return interpolate(`errors.validation.${problem.code}`, problem);
}
