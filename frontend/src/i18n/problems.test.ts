import { describe, expect, it } from 'vitest';
import i18n from './config';
import { describeFailure, describeFieldErrors } from './problems';
import { ApiError, type FieldProblem } from '../api/client';
import { en } from './en';

const t = i18n.getFixedT('en');

/** Every `code` the backend can put in a problem document, written out rather than
 * derived, so that this file states the contract instead of agreeing with itself. */
const BACKEND_CODES = [
  'email_already_registered',
  'slug_taken',
  'invalid_credentials',
  'email_not_verified',
  'invalid_token',
  'not_a_member',
  'not_an_owner',
  'last_owner',
  'member_not_found',
  'project_not_found',
  'work_item_not_found',
  'estimate_out_of_order',
  'already_a_member',
  'invitation_already_pending',
  'invitation_not_found',
  'invitation_expired',
  'invitation_revoked',
  'sign_in_required',
  'invitation_for_another_address',
  'credentials_required',
  'too_many_requests',
  'conflict',
  'validation_failed'
];

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
    for (const code of BACKEND_CODES) {
      const message = describeFailure(t, new ApiError(400, { code }));
      expect(message, code).not.toBe('Something went wrong. Please try again.');
      expect(message, code).not.toBe('');
    }
  });

  /**
   * The list above is the contract, so it has to be the whole contract: checked against
   * the catalogue in both directions, since a list that only had to be a subset would
   * quietly stop covering anything added after it was written — which is exactly what it
   * had done.
   */
  it('lists exactly the codes the catalogue has wording for', () => {
    expect([...BACKEND_CODES].sort()).toEqual(
      Object.keys(en.errors.codes).sort()
    );
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

  it('describes each field by the constraint it failed', () => {
    const error = new ApiError(400, {
      code: 'validation_failed',
      errors: {
        organisationName: { code: 'not_blank' },
        email: { code: 'email' },
        password: { code: 'size', min: 12, max: 72 }
      }
    });

    expect(describeFieldErrors(t, error)).toEqual({
      organisationName: 'This cannot be empty.',
      email: 'Enter a valid email address.',
      password: 'Use between 12 and 72 characters.'
    });
  });

  /**
   * The bounds come from the server, so changing `@Size` on the request object changes
   * what the user is told without touching the catalogue.
   */
  /** "Use between 0 and 200 characters" is not a sentence worth showing anyone. */
  it('states a ceiling without inventing a floor', () => {
    const error = new ApiError(400, {
      code: 'validation_failed',
      errors: { organisationName: { code: 'max_size', min: 0, max: 200 } }
    });

    expect(describeFieldErrors(t, error).organisationName).toBe(
      'Use no more than 200 characters.'
    );
  });

  it('takes the bounds it interpolates from the failure, not from the catalogue', () => {
    const error = new ApiError(400, {
      code: 'validation_failed',
      errors: { password: { code: 'size', min: 16, max: 64 } }
    });

    expect(describeFieldErrors(t, error).password).toBe(
      'Use between 16 and 64 characters.'
    );
  });

  /**
   * Each constraint paired with the attributes the backend sends for it. The leftover
   * `{{` check is the point: it catches a catalogue entry that interpolates a value the
   * server does not actually publish, which would put `{{min}}` in front of a user.
   */
  it('renders every constraint code the backend can send', () => {
    const problems: FieldProblem[] = [
      { code: 'not_blank' },
      { code: 'not_null' },
      { code: 'size', min: 12, max: 72 },
      { code: 'max_size', min: 0, max: 200 },
      { code: 'email' },
      { code: 'pattern' },
      { code: 'invalid' }
    ];

    for (const problem of problems) {
      const message = describeFieldErrors(
        t,
        new ApiError(400, {
          code: 'validation_failed',
          errors: { f: problem }
        })
      ).f;

      expect(message, problem.code).not.toBe('');
      expect(message, problem.code).not.toContain('{{');
    }
  });

  // The server sends no prose, so an unmapped constraint has nothing to fall back to
  // except our own generic wording — showing nothing would leave the field unexplained.
  it('falls back to a generic complaint for a constraint it does not know', () => {
    const error = new ApiError(400, {
      code: 'validation_failed',
      errors: { somethingNew: { code: 'decimal_min', value: 5 } }
    });

    expect(describeFieldErrors(t, error)).toEqual({
      somethingNew: 'Check this and try again.'
    });
  });
});
