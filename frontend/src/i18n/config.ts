import i18n from 'i18next';
import type { InitOptions } from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './en';

export const DEFAULT_LOCALE = 'en';

export const resources = {
  en: { translation: en }
} as const;

export type MissingKeyHandler = (key: string) => void;

/**
 * Builds the i18next options.
 *
 * Kept separate from {@link initI18n} so both the plain and the missing-key-reporting
 * configurations can be checked without initialising the shared instance.
 */
export function i18nOptions(onMissingKey?: MissingKeyHandler): InitOptions {
  return {
    resources,
    lng: DEFAULT_LOCALE,
    fallbackLng: DEFAULT_LOCALE,
    // React escapes for us; letting i18next escape again would double-encode.
    interpolation: { escapeValue: false },
    saveMissing: Boolean(onMissingKey),
    missingKeyHandler: onMissingKey
      ? (_lngs, _ns, key) => onMissingKey(key)
      : undefined
  };
}

/**
 * Sets up the single default namespace. Passing `onMissingKey` lets tests fail loudly on
 * a key with no translation, which is what stops literal strings creeping back in.
 */
export function initI18n(onMissingKey?: MissingKeyHandler) {
  void i18n.use(initReactI18next).init(i18nOptions(onMissingKey));
  return i18n;
}

export default i18n;
