import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { initI18n } from '../i18n/config';

/**
 * Keys rendered during a test that the catalogue has no entry for. Collected rather than
 * thrown immediately, because i18next reports them mid-render where a throw would surface
 * as an unrelated React error.
 */
const missingKeys: string[] = [];

initI18n((key) => {
  missingKeys.push(key);
});

/**
 * `<dialog>`'s two methods, which jsdom 29 still does not implement.
 *
 * **A double, and a deliberately shallow one — what it stands in for is the reason to use a
 * `<dialog>` at all.** Opening one in a browser moves focus inside, traps it there, makes
 * everything behind it inert, draws a backdrop and closes on Escape; none of that happens
 * here, so no test in this suite proves any of it. What these two do is the part the
 * application itself is responsible for: whether the thing is open, and telling the page
 * when it stops being.
 *
 * The `close` event is raised because that is the one this application listens to — Escape
 * and `close()` both raise it in a browser, which is what keeps a caller's idea of "open"
 * from drifting from what is on screen. A stub that stayed silent would let that drift pass.
 */
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
    this.dispatchEvent(new Event('close'));
  };
}

beforeEach(() => {
  // Every test starts signed out; a token left behind would leak between tests.
  window.localStorage.clear();
  missingKeys.length = 0;
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  if (missingKeys.length > 0) {
    const missing = [...new Set(missingKeys)].join(', ');
    missingKeys.length = 0;
    throw new Error(`Rendered text with no translation: ${missing}`);
  }
});
