import { describe, expect, it, vi } from 'vitest';
import { DEFAULT_LOCALE, i18nOptions, resources } from './config';
import { en } from './en';

describe('i18n options', () => {
  it('serves the English catalogue as the default and the fallback', () => {
    const options = i18nOptions();

    expect(options.lng).toBe(DEFAULT_LOCALE);
    expect(options.fallbackLng).toBe(DEFAULT_LOCALE);
    expect(resources.en.translation).toBe(en);
  });

  // React already escapes; a second pass would render entities as visible text.
  it('leaves escaping to React', () => {
    expect(i18nOptions().interpolation?.escapeValue).toBe(false);
  });

  it('does not report missing keys when no handler is given', () => {
    const options = i18nOptions();

    expect(options.saveMissing).toBe(false);
    expect(options.missingKeyHandler).toBeUndefined();
  });

  it('reports a missing key to the handler it was given', () => {
    const onMissingKey = vi.fn();
    const options = i18nOptions(onMissingKey);

    expect(options.saveMissing).toBe(true);
    const handler = options.missingKeyHandler;
    if (typeof handler !== 'function') {
      throw new Error('expected a missing-key handler to be configured');
    }
    handler(['en'], 'translation', 'some.absent.key', '', false, {});

    expect(onMissingKey).toHaveBeenCalledWith('some.absent.key');
  });
});
