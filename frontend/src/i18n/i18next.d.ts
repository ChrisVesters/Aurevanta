import type { en } from './en';

/**
 * Makes `t()` keys type-checked against the English catalogue, so a typo or a key that
 * was never added is a compile error rather than raw key text rendered on screen.
 */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: { translation: typeof en };
  }
}
