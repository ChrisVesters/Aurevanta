import type { TFunction } from 'i18next';
import { ApiError } from '../api/client';
import { MINIMUM_PASSWORD_LENGTH } from '../auth/constants';
import { en } from './en';

type ErrorCode = keyof typeof en.errors.codes;
type FieldName = keyof typeof en.errors.fields;

function isKnownCode(code: string | undefined): code is ErrorCode {
  return code !== undefined && code in en.errors.codes;
}

function isKnownField(field: string): field is FieldName {
  return field in en.errors.fields;
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
 * A field the catalogue does not cover keeps the server's own message: showing untranslated
 * English beats showing nothing, and it makes the gap visible rather than silent.
 */
export function describeFieldErrors(
  t: TFunction,
  error: unknown
): Record<string, string> {
  if (!(error instanceof ApiError)) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(error.fieldErrors).map(([field, serverMessage]) => [
      field,
      isKnownField(field)
        ? t(`errors.fields.${field}`, { count: MINIMUM_PASSWORD_LENGTH })
        : serverMessage
    ])
  );
}
