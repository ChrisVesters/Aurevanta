/** Mirrors the backend's `@Size(min = 12, max = 72)` on the password. */
export const MINIMUM_PASSWORD_LENGTH = 12;
export const MAXIMUM_PASSWORD_LENGTH = 72;

/** Mirrors the backend column widths, so the browser stops input the API would reject. */
export const MAXIMUM_NAME_LENGTH = 200;
export const MAXIMUM_EMAIL_LENGTH = 320;
/** A project's description; prose rather than a name, so far longer than one. */
export const MAXIMUM_DESCRIPTION_LENGTH = 2000;
