import { describe, expect, it } from 'vitest';
import { textField } from './formValues';

describe('textField', () => {
  it('reads what was entered', () => {
    const form = new FormData();
    form.set('email', 'ada@acme.test');

    expect(textField(form, 'email')).toBe('ada@acme.test');
  });

  it('reads an empty field as an empty string', () => {
    const form = new FormData();
    form.set('email', '');

    expect(textField(form, 'email')).toBe('');
  });

  it('treats a missing field as empty rather than the text "null"', () => {
    expect(textField(new FormData(), 'email')).toBe('');
  });

  it('treats a file as empty rather than an object string', () => {
    const form = new FormData();
    form.set('email', new File(['x'], 'x.txt'));

    expect(textField(form, 'email')).toBe('');
  });
});
