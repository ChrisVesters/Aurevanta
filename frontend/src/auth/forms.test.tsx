import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from './LoginForm';
import { RegisterForm } from './RegisterForm';
import { jsonResponse, mockFetch, renderRouted } from '../test/render';

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
    fetchMock.mockResolvedValue(jsonResponse(201, {}));

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
        errors: { password: 'size must be between 12 and 72' }
      })
    );

    renderRouted(<RegisterForm />);
    await fillAndSubmit();

    const password = await screen.findByLabelText('Password');
    await waitFor(() =>
      expect(password).toHaveAttribute('aria-invalid', 'true')
    );
    // The server's English prose is replaced by wording from our own catalogue.
    expect(screen.getByText('Use at least 12 characters.')).toBeInTheDocument();
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
