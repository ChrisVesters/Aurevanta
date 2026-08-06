import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Field } from './Field';

describe('Field', () => {
  it('associates the label with its input', () => {
    render(<Field id="email" label="Email" name="email" />);

    expect(screen.getByLabelText('Email')).toHaveAttribute('id', 'email');
  });

  it('is not marked invalid when there is no error', () => {
    render(<Field id="email" label="Email" name="email" />);

    expect(screen.getByLabelText('Email')).not.toHaveAttribute('aria-invalid');
    expect(screen.getByLabelText('Email')).not.toHaveAttribute(
      'aria-describedby'
    );
  });

  it('points a screen reader at the hint', () => {
    render(
      <Field id="email" label="Email" name="email" hint="Work address." />
    );

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Work address.'
    );
  });

  it('marks the input invalid and describes the error', () => {
    render(
      <Field id="email" label="Email" name="email" error="Already taken." />
    );

    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Already taken.');
  });

  it('describes the error before the hint when both are present', () => {
    render(
      <Field
        id="email"
        label="Email"
        name="email"
        hint="Work address."
        error="Already taken."
      />
    );

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Already taken. Work address.'
    );
  });

  it('passes input attributes straight through', () => {
    render(
      <Field
        id="email"
        label="Email"
        name="email"
        type="email"
        required
        maxLength={320}
      />
    );

    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('type', 'email');
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('maxlength', '320');
  });
});
