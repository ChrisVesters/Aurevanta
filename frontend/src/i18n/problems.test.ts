import { describe, expect, it } from 'vitest';
import i18n from './config';
import { describeFailure, describeFieldErrors } from './problems';
import { ApiError } from '../api/client';

const t = i18n.getFixedT('en');

describe('describeFailure', () => {
  it('uses our wording for a code the catalogue knows', () => {
    const error = new ApiError(409, {
      code: 'email_already_registered',
      detail: 'That email address is already registered'
    });

    expect(describeFailure(t, error)).toBe(
      'That email address is already registered.'
    );
  });

  it('translates every code the backend can send', () => {
    const codes = [
      'email_already_registered',
      'organisation_name_unavailable',
      'organisation_name_unusable',
      'invalid_credentials',
      'registration_conflict',
      'validation_failed'
    ];

    for (const code of codes) {
      const message = describeFailure(t, new ApiError(400, { code }));
      expect(message, code).not.toBe('Something went wrong. Please try again.');
      expect(message, code).not.toBe('');
    }
  });

  // A code we have no wording for must not leak the server's English prose.
  it('falls back to a generic message for an unrecognised code', () => {
    const error = new ApiError(500, {
      code: 'teapot_overheated',
      detail: 'Internal detail that should not be shown'
    });

    expect(describeFailure(t, error)).toBe(
      'Something went wrong. Please try again.'
    );
  });

  it('falls back to a generic message when a failure has no code', () => {
    expect(describeFailure(t, new ApiError(500, null))).toBe(
      'Something went wrong. Please try again.'
    );
  });

  it('reports a failure that never reached the server as a network problem', () => {
    expect(describeFailure(t, new TypeError('Failed to fetch'))).toBe(
      'Could not reach the server. Check your connection and try again.'
    );
  });
});

describe('describeFieldErrors', () => {
  it('has no field errors for a non-API failure', () => {
    expect(describeFieldErrors(t, new TypeError('Failed to fetch'))).toEqual(
      {}
    );
  });

  it('has no field errors when the problem carried none', () => {
    expect(describeFieldErrors(t, new ApiError(401, { code: 'x' }))).toEqual(
      {}
    );
  });

  it('replaces the server wording for fields the catalogue covers', () => {
    const error = new ApiError(400, {
      code: 'validation_failed',
      errors: {
        organisationName: 'must not be blank',
        displayName: 'must not be blank',
        email: 'must be a well-formed email address',
        password: 'size must be between 12 and 72'
      }
    });

    expect(describeFieldErrors(t, error)).toEqual({
      organisationName: 'Enter the name of your organisation.',
      displayName: 'Enter your name.',
      email: 'Enter a valid email address.',
      password: 'Use at least 12 characters.'
    });
  });

  // Better an untranslated message than a silently dropped one.
  it('keeps the server wording for a field the catalogue does not cover', () => {
    const error = new ApiError(400, {
      code: 'validation_failed',
      errors: { somethingNew: 'must be positive' }
    });

    expect(describeFieldErrors(t, error)).toEqual({
      somethingNew: 'must be positive'
    });
  });
});
