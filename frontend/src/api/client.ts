/**
 * Thin wrapper over fetch for the `/api` prefix. In dev the Vite proxy forwards these to
 * the backend on :8080, so requests stay same-origin and no CORS handling is needed.
 */

/** RFC 9457 problem document, as returned by the backend for every failure. */
type ProblemDetail = {
  title?: string;
  detail?: string;
  status?: number;
  /** Stable machine-readable identifier, e.g. `email_already_registered`. */
  code?: string;
  /** Field name to message, present when `code` is `validation_failed`. */
  errors?: Record<string, string>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  /** Per-field messages, so a form can put each complaint next to its input. */
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code;
    this.fieldErrors = problem?.errors ?? {};
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  token?: string | null;
};

export async function apiRequest<T>(
  path: string,
  { method = 'GET', body, token }: RequestOptions = {}
): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T);
}

/** A failure may carry no body at all — a 401 from the security filter chain does not. */
async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    const text = await response.text();
    return text ? (JSON.parse(text) as ProblemDetail) : null;
  } catch {
    return null;
  }
}
