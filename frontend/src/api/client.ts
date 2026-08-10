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
  /** Field name to complaint, present when `code` is `validation_failed`. */
  errors?: Record<string, FieldProblem>;
};

/**
 * Why one field was rejected. `code` names the constraint that failed — `size`, `email` —
 * and the remaining keys are whatever a message interpolates, such as `min` and `max`.
 * The server never sends prose here, so there is nothing to display without translating.
 */
export type FieldProblem = {
  code: string;
  // The backend publishes only the constraint's numeric attributes, since bounds are what
  // a message interpolates. Typed as such so the whole problem can be handed straight to
  // `t()` as its interpolation values.
  [attribute: string]: string | number;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  /** Per-field complaints, so a form can put each one next to its input. */
  readonly fieldErrors: Record<string, FieldProblem>;

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
  // A success can carry no body at all: 204 from redeeming a link, 202 from anything that
  // only promises to do the work later. `Response.json()` rejects on an empty body rather
  // than resolving to undefined, and that rejection is indistinguishable from a network
  // failure by the time it reaches a form — which is how every body-less 202 came to tell
  // the visitor we could not reach the server. Read the text and parse what is there,
  // exactly as the failure path already does.
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
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
