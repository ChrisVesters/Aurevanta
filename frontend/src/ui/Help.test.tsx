import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Help } from './Help';
import { renderRouted } from '../test/render';

/**
 * The "?" and the panel it opens. The panel floats, so what is worth asserting is what a
 * floating panel owes somebody: it opens and shuts on the button, it shuts on Escape and on a
 * press anywhere else, and it stays inside the window.
 */
describe('Help', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  function show() {
    renderRouted(
      <p>
        <span>Something worth explaining</span>
        <Help label="What does this mean?">It means this.</Help>
        <button type="button">Elsewhere</button>
      </p>
    );
    return {
      toggle: screen.getByRole('button', { name: 'What does this mean?' }),
      text: screen.getByText('It means this.')
    };
  }

  it('opens and shuts on the question mark, and says which it is', async () => {
    const { toggle, text } = show();
    expect(toggle).toHaveAttribute('aria-controls', text.id);
    expect(text).not.toBeVisible();

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(text).toBeVisible();

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(text).not.toBeVisible();
  });

  /** Escape shuts it and puts focus back where somebody pressed, not on the page. */
  it('shuts on Escape and hands focus back to the question mark', async () => {
    const { toggle, text } = show();
    await userEvent.click(toggle);

    await userEvent.keyboard('{Tab}');
    await userEvent.keyboard('{Escape}');

    expect(text).not.toBeVisible();
    expect(toggle).toHaveFocus();
  });

  it('ignores every other key', async () => {
    const { toggle, text } = show();
    await userEvent.click(toggle);

    await userEvent.keyboard('a');

    expect(text).toBeVisible();
  });

  /** A press anywhere else shuts it, so a panel is never left open over the form. */
  it('shuts on a press anywhere else', async () => {
    const { toggle, text } = show();
    await userEvent.click(toggle);

    await userEvent.click(screen.getByRole('button', { name: 'Elsewhere' }));

    expect(text).not.toBeVisible();
  });

  /** Reading it — selecting a word in it — is not a press elsewhere. */
  it('stays open for a press on its own text', async () => {
    const { toggle, text } = show();
    await userEvent.click(toggle);

    await userEvent.click(text);

    expect(text).toBeVisible();
  });

  /**
   * Under its "?" where there is room; nudged left by exactly what would have run past the
   * window's right edge less its gutter, and never past the left one.
   */
  it('stays inside the window', async () => {
    const { toggle, text } = show();
    const at = (left: number, right: number) =>
      vi
        .spyOn(text, 'getBoundingClientRect')
        .mockReturnValue({ left, right } as DOMRect);

    at(100, 420);
    await userEvent.click(toggle);
    expect(text.style.left).toBe('');

    await userEvent.click(toggle);
    at(800, 1120);
    await userEvent.click(toggle);
    // 1120 - (1024 - 16) = 112
    expect(text.style.left).toBe('-112px');

    await userEvent.click(toggle);
    at(40, 1100);
    await userEvent.click(toggle);
    // Would need 92, but only 24 is available before the left gutter.
    expect(text.style.left).toBe('-24px');
  });
});
