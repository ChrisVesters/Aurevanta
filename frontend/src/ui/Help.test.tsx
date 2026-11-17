import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
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
   * Places the "?" and the panel at the given spots, as the browser would measure them.
   * jsdom lays nothing out, so every rectangle reads as zero without this. The window is
   * jsdom's 1024 by 768.
   */
  function measured(
    toggle: HTMLElement,
    text: HTMLElement,
    at: { left: number; top: number },
    size = { width: 300, height: 120 }
  ) {
    vi.spyOn(toggle, 'getBoundingClientRect').mockReturnValue({
      left: at.left,
      top: at.top,
      bottom: at.top + 20
    } as DOMRect);
    vi.spyOn(text, 'getBoundingClientRect').mockReturnValue(size as DOMRect);
  }

  /**
   * <strong>Fixed to the window, so it can never widen what it sits in.</strong> Placed
   * absolutely inside a dialog, a panel past the dialog's edge added a horizontal scrollbar
   * under the form. These cases are where that would have happened.
   */
  it('opens under its question mark where there is room', async () => {
    const { toggle, text } = show();
    measured(toggle, text, { left: 100, top: 200 });

    await userEvent.click(toggle);

    expect(text.style.top).toBe('226px');
    expect(text.style.left).toBe('100px');
  });

  it('is pulled back inside the right edge of the window', async () => {
    const { toggle, text } = show();
    measured(toggle, text, { left: 900, top: 200 });

    await userEvent.click(toggle);

    // 1024 - 16 - 300
    expect(text.style.left).toBe('708px');
  });

  it('never runs past the left edge, however narrow the window', async () => {
    const { toggle, text } = show();
    measured(toggle, text, { left: 4, top: 200 });

    await userEvent.click(toggle);

    expect(text.style.left).toBe('16px');
  });

  it('opens above its question mark where there is no room below', async () => {
    const { toggle, text } = show();
    measured(toggle, text, { left: 100, top: 700 });

    await userEvent.click(toggle);

    // 700 - 6 - 120
    expect(text.style.top).toBe('574px');
  });

  it('never runs past the top, when there is room neither above nor below', async () => {
    const { toggle, text } = show();
    measured(
      toggle,
      text,
      { left: 100, top: 300 },
      { width: 300, height: 700 }
    );

    await userEvent.click(toggle);

    expect(text.style.top).toBe('16px');
  });

  /** It follows the "?" when anything scrolls — a dialog as much as the page. */
  it('follows its question mark when something scrolls or the window resizes', async () => {
    const { toggle, text } = show();
    measured(toggle, text, { left: 100, top: 200 });
    await userEvent.click(toggle);

    measured(toggle, text, { left: 100, top: 150 });
    fireEvent.scroll(document.body);
    expect(text.style.top).toBe('176px');

    measured(toggle, text, { left: 120, top: 150 });
    fireEvent(window, new Event('resize'));
    expect(text.style.left).toBe('120px');
  });

  /** And stops listening once shut, leaving no placement behind for the next opening. */
  it('forgets where it was once it is shut', async () => {
    const { toggle, text } = show();
    measured(toggle, text, { left: 100, top: 200 });
    await userEvent.click(toggle);
    await userEvent.click(toggle);

    expect(text.style.top).toBe('');
    measured(toggle, text, { left: 100, top: 150 });
    fireEvent.scroll(document.body);
    expect(text.style.top).toBe('');
  });
});
