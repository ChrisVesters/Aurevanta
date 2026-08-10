import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from './LoginForm';
import { RegisterForm } from './RegisterForm';
import {
  UNVERIFIED_ACCOUNT,
  jsonResponse,
  mockFetch,
  renderRouted
} from '../test/render';

describe('sign-up form', () => {
  const fetchMock = mockFetch();

  async function fillAndSubmit() {
    await userEvent.type(screen.getByLabelText('Organisation name'), 'Acme');
    await userEvent.type(screen.getByLabelText('Your name'), 'Ada');
    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-long-enough-passphrase'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Create organisation' })
    );
  }

  it('sends what the visitor typed', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, UNVERIFIED_ACCOUNT));

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      organisationName: 'Acme',
      displayName: 'Ada',
      email: 'ada@acme.test',
      password: 'a-long-enough-passphrase'
    });
  });

  it('places a field complaint against its own input', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        detail: 'Some fields need attention',
        errors: { password: { code: 'size', min: 12, max: 72 } }
      })
    );

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    const password = await screen.findByLabelText('Password');
    await waitFor(() =>
      expect(password).toHaveAttribute('aria-invalid', 'true')
    );
    // The server's English prose is replaced by wording from our own catalogue.
    expect(
      screen.getByText('Use between 12 and 72 characters.')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('size must be between 12 and 72')
    ).not.toBeInTheDocument();
    // A per-field message is enough; the form-level banner would just repeat it.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a form-level message when the failure belongs to no field', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        code: 'email_already_registered',
        detail: 'That email address is already registered'
      })
    );

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That email address is already registered'
    );
  });

  it('explains a network failure in plain terms', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i
    );
  });

  /**
   * Signing up used to land on the dashboard. It cannot any more — the address has not
   * been confirmed, and an unconfirmed one is refused a session — so the visitor is sent
   * to their inbox and told why.
   */
  it('ends on a screen telling the visitor to confirm their address', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, UNVERIFIED_ACCOUNT));

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    expect(
      await screen.findByRole('heading', { name: 'Confirm your email address' })
    ).toBeInTheDocument();
    expect(screen.getByText(/ada@acme.test/)).toBeInTheDocument();
    // The form is gone: there is nothing left to submit.
    expect(
      screen.queryByLabelText('Organisation name')
    ).not.toBeInTheDocument();
  });

  /**
   * The screen where a message that never arrives is first noticed. It told the visitor to
   * check their spam folder and then offered them nothing to do about it — the way on was
   * to attempt a sign-in certain to be refused, and read the escape out of that refusal.
   */
  it('offers a new link from the screen where a lost message is noticed', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, UNVERIFIED_ACCOUNT));

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    expect(
      await screen.findByRole('link', { name: 'Ask for a new link' })
    ).toHaveAttribute('href', '/verify-email');
  });

  /**
   * "That address is already registered" is true and, on its own, useless. The likeliest
   * person to read it is somebody whose confirmation link never arrived, trying again
   * because signing in does not work either — and the message left them nowhere to go.
   */
  it('offers a new link when the address turns out to be taken', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, { code: 'email_already_registered' })
    );

    renderRouted(<RegisterForm />);
    await fillAndSubmit();
    await screen.findByRole('alert');

    fetchMock.mockResolvedValue(jsonResponse(202));
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a new link' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenLastCalledWith(
        '/api/auth/verify-email/resend',
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(JSON.parse(fetchMock.mock.calls.at(-1)?.[1].body).email).toBe(
      'ada@acme.test'
    );
  });

  /** Any other refusal is not about a confirmation link, so it must not offer one. */
  it('offers nothing of the sort for an unrelated failure', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, { code: 'organisation_name_unavailable' })
    );

    renderRouted(<RegisterForm />);
    await fillAndSubmit();
    await screen.findByRole('alert');

    expect(
      screen.queryByRole('button', { name: 'Send a new link' })
    ).not.toBeInTheDocument();
  });

  it('re-enables the button after a failure so the visitor can retry', async () => {
    fetchMock.mockResolvedValue(jsonResponse(409, { detail: 'Taken' }));

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Create organisation' })
      ).toBeEnabled()
    );
  });

  it('links to sign-in for people who already have an account', () => {
    renderRouted(<RegisterForm />);

    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login'
    );
  });
});

describe('sign-in form', () => {
  const fetchMock = mockFetch();

  async function fillAndSubmit() {
    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-long-enough-passphrase'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  }

  it('sends the credentials', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      email: 'ada@acme.test',
      password: 'a-long-enough-passphrase'
    });
  });

  /**
   * Submitting an empty field used to reach "Some fields need attention." and stop there,
   * saying neither which field nor why, because this form asked only for the form-level
   * message and threw the per-field complaints away.
   */
  it('places a field complaint against its own input', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        detail: 'Some fields need attention',
        errors: { password: { code: 'not_blank' } }
      })
    );

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    const password = await screen.findByLabelText('Password');
    await waitFor(() =>
      expect(password).toHaveAttribute('aria-invalid', 'true')
    );
    expect(screen.getByText('This cannot be empty.')).toBeInTheDocument();
    // Saying it twice, once vaguely, helps nobody.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('places a complaint about the email against the email input', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { email: { code: 'not_blank' } }
      })
    );

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveAttribute(
        'aria-invalid',
        'true'
      )
    );
    expect(screen.getByLabelText('Password')).not.toHaveAttribute(
      'aria-invalid'
    );
  });

  /**
   * A malformed address used to reach the credential check and come back as "email or
   * password is incorrect", which points at the wrong thing entirely — the password is
   * fine, the address could not be anyone's.
   */
  it('says an address is malformed rather than blaming the credentials', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { email: { code: 'email' } }
      })
    );

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    expect(
      await screen.findByText('Enter a valid email address.')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Email or password is incorrect.')
    ).not.toBeInTheDocument();
  });

  /**
   * Suppressing the banner is only safe for complaints the visitor can actually see. A
   * field name this form does not render — the two disagreeing about what something is
   * called — would otherwise be shown nowhere at all, leaving a rejected submission with
   * no visible explanation.
   */
  it('falls back to the banner for a field it does not render', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { organisationName: { code: 'not_blank' } }
      })
    );

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Some fields need attention.'
    );
  });

  it('reports refused credentials without saying which part was wrong', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(401, {
        code: 'invalid_credentials',
        detail: 'Email or password is incorrect'
      })
    );

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email or password is incorrect'
    );
  });

  /**
   * The one refusal that is not about the credentials at all. Telling someone their email
   * or password is wrong when both are right would leave them changing a password that
   * was never the problem.
   */
  it('says the address needs confirming rather than blaming the credentials', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(403, {
        code: 'email_not_verified',
        detail: 'Confirm your email address before signing in'
      })
    );

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /confirm your email address/i
    );
    expect(
      screen.queryByText('Email or password is incorrect.')
    ).not.toBeInTheDocument();
  });

  describe('when the gate refuses an unconfirmed address', () => {
    const refusal = jsonResponse(403, {
      code: 'email_not_verified',
      detail: 'Confirm your email address before signing in'
    });

    async function beRefused() {
      fetchMock.mockResolvedValue(refusal);
      renderRouted(<LoginForm />);
      await fillAndSubmit();
      await screen.findByRole('alert');
    }

    /**
     * The whole point of doing this here rather than on another page: the address has
     * just been typed, and asking for it again is where people give up.
     */
    it('asks for a new link without making the visitor retype anything', async () => {
      await beRefused();
      fetchMock.mockResolvedValue(jsonResponse(202));

      await userEvent.click(
        screen.getByRole('button', { name: 'Send a new link' })
      );

      await waitFor(() =>
        expect(fetchMock).toHaveBeenLastCalledWith(
          '/api/auth/verify-email/resend',
          expect.objectContaining({ method: 'POST' })
        )
      );
      const sent = fetchMock.mock.calls.at(-1)?.[1];
      expect(JSON.parse(sent.body)).toEqual({ email: 'ada@acme.test' });
    });

    it('acknowledges without saying whether the address has an account', async () => {
      await beRefused();
      fetchMock.mockResolvedValue(jsonResponse(202));

      await userEvent.click(
        screen.getByRole('button', { name: 'Send a new link' })
      );

      expect(await screen.findByRole('status')).toHaveTextContent(
        /a new link is on its way/i
      );
      expect(
        screen.queryByRole('button', { name: 'Send a new link' })
      ).not.toBeInTheDocument();
    });

    /**
     * The resend is now rate limited, so this refusal is reachable — and it must not
     * replace the message explaining why a resend was being offered at all.
     */
    it('keeps explaining the gate when the resend itself is refused', async () => {
      await beRefused();
      fetchMock.mockResolvedValue(
        jsonResponse(429, { code: 'too_many_requests' })
      );

      await userEvent.click(
        screen.getByRole('button', { name: 'Send a new link' })
      );

      await waitFor(() =>
        expect(screen.getByText(/too many requests/i)).toBeInTheDocument()
      );
      expect(
        screen.getByText(/confirm your email address/i)
      ).toBeInTheDocument();
      // Still offered, so a visitor who waits can try again without starting over.
      expect(
        screen.getByRole('button', { name: 'Send a new link' })
      ).toBeInTheDocument();
    });

    /** Two ways to ask for the same thing, side by side, is a choice nobody needs. */
    it('replaces the standing link rather than sitting beside it', async () => {
      await beRefused();

      expect(
        screen.queryByRole('link', { name: 'Ask for a new one' })
      ).not.toBeInTheDocument();
    });

    it('offers nothing of the sort for ordinary wrong credentials', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(401, { code: 'invalid_credentials' })
      );
      renderRouted(<LoginForm />);
      await fillAndSubmit();
      await screen.findByRole('alert');

      expect(
        screen.queryByRole('button', { name: 'Send a new link' })
      ).not.toBeInTheDocument();
      expect(
        screen.getByRole('link', { name: 'Ask for a new one' })
      ).toBeInTheDocument();
    });

    /** Trying again is a fresh attempt, so last time's acknowledgement must not linger. */
    it('forgets the acknowledgement when the visitor signs in again', async () => {
      await beRefused();
      fetchMock.mockResolvedValue(jsonResponse(202));
      await userEvent.click(
        screen.getByRole('button', { name: 'Send a new link' })
      );
      await screen.findByRole('status');

      fetchMock.mockResolvedValue(refusal);
      await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

      // Enabled, not merely present: it came back reading "Sending…" and refusing clicks,
      // because finishing a send left that flag set where nothing was rendering it.
      expect(
        await screen.findByRole('button', { name: 'Send a new link' })
      ).toBeEnabled();
    });
  });

  it('explains a network failure in plain terms', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    renderRouted(<LoginForm />);
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i
    );
  });

  it('links to sign-up for people without an account', () => {
    renderRouted(<LoginForm />);

    expect(
      screen.getByRole('link', { name: 'Create an organisation' })
    ).toHaveAttribute('href', '/register');
  });
});
