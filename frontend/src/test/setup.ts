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
