import { describe, expect, it } from 'vitest';
import { ApiError, apiRequest } from './client';
import { jsonResponse, mockFetch } from '../test/render';

/** Awaits a request expected to fail and hands back the ApiError it produced. */
async function failureOf(request: Promise<unknown>): Promise<ApiError> {
  return (await request.catch((thrown: unknown) => thrown)) as ApiError;
}

describe('apiRequest', () => {
  const fetchMock = mockFetch();

  it('prefixes the path with /api so the dev proxy picks it up', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiRequest('/auth/me');

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.anything());
  });

  it('sends no content type when there is no body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await apiRequest('/auth/me');

    expect(fetchMock.mock.calls[0][1].headers).toEqual({});
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  });

  it('serialises a body as JSON', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await apiRequest('/auth/login', {
      method: 'POST',
      body: { email: 'ada@acme.test' }
    });

    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"email":"ada@acme.test"}');
  });

  it('presents a token as a bearer credential', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await apiRequest('/auth/me', { token: 'a.test.token' });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe(
      'Bearer a.test.token'
    );
  });

  it('omits the authorization header when there is no token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await apiRequest('/auth/me', { token: null });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('returns the parsed body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { email: 'ada@acme.test' }));

    await expect(apiRequest('/auth/me')).resolves.toEqual({
      email: 'ada@acme.test'
    });
  });

  it('returns nothing for a no-content response', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    await expect(
      apiRequest('/auth/logout', { method: 'POST' })
    ).resolves.toBeUndefined();
  });

  /**
   * 204 is not the only body-less success. Everything that answers 202 — asking for a
   * reset link, asking for another confirmation link — sends nothing back, and treating
   * that as JSON threw an error a form could only report as a network failure. The
   * message said we could not reach the server about a request the server had accepted.
   */
  it('returns nothing for an accepted request that sends no body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(202));

    await expect(
      apiRequest('/auth/password-reset', { method: 'POST' })
    ).resolves.toBeUndefined();
  });

  it('raises a problem document as an ApiError carrying its code', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        title: 'Email already registered',
        detail: 'That email address is already registered',
        code: 'email_already_registered'
      })
    );

    await expect(
      apiRequest('/auth/register', { method: 'POST' })
    ).rejects.toMatchObject({
      status: 409,
      code: 'email_already_registered',
      message: 'That email address is already registered'
    });
  });

  it('exposes per-field messages so a form can place them', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        detail: 'Some fields need attention',
        code: 'validation_failed',
        errors: { password: { code: 'size', min: 12, max: 72 } }
      })
    );

    const error = await failureOf(
      apiRequest('/auth/register', { method: 'POST' })
    );

    expect(error.fieldErrors).toEqual({
      password: { code: 'size', min: 12, max: 72 }
    });
  });

  it('falls back to the title when a problem has no detail', async () => {
    fetchMock.mockResolvedValue(jsonResponse(403, { title: 'Forbidden' }));

    await expect(apiRequest('/auth/me')).rejects.toThrow('Forbidden');
  });

  // A 401 from the security filter chain has no body at all.
  it('still raises a usable error when the failure has an empty body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(401));

    const error = await failureOf(apiRequest('/auth/me'));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.message).toBe('Request failed (401)');
    expect(error.fieldErrors).toEqual({});
    expect(error.code).toBeUndefined();
  });

  it('still raises a usable error when the failure body is not JSON', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 502,
      text: async () => '<html>Bad Gateway</html>'
    } as Response);

    await expect(apiRequest('/auth/me')).rejects.toThrow(
      'Request failed (502)'
    );
  });

  it('propagates a network failure', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(apiRequest('/auth/me')).rejects.toThrow('Failed to fetch');
  });
});
