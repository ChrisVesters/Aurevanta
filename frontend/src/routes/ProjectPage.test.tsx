import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { ProjectPage } from './ProjectPage';
import {
  ACCOUNT,
  ARCHIVED_PROJECT,
  PROJECTS,
  jsonResponse,
  mockFetch,
  NO_HISTORY,
  NOTHING_SCORED,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Project } from '../projects/types';

describe('ProjectPage', () => {
  const fetchMock = mockFetch();

  /**
   * Every read this page makes, answered by what it is asking for.
   *
   * Six URLs now, because the page loads resources that fail independently: the plan
   * itself, the forecasts made of it, what the plan has been delivering, the organisation's
   * track record, and — through `WorkItems` — the work inside it, what that work is
   * estimated at, and how it is joined up. **A double that answered them alike would hand
   * the estimate list a project**, and the page would crash rather than the test failing
   * where the mistake is.
   */
  function reads(url: string, project: Project | null) {
    if (url === '/api/auth/me') {
      return jsonResponse(200, ACCOUNT);
    }
    if (url.includes('/throughput')) {
      return jsonResponse(200, NO_HISTORY);
    }
    if (url === '/api/calibration') {
      return jsonResponse(200, NOTHING_SCORED);
    }
    // Answered by URL like everything else here: a double that fell through to the project
    // would hand the panel an object where it expects a list of pools, which is the shape
    // this file's own note about lying doubles is about.
    if (url === '/api/resources') {
      return jsonResponse(200, []);
    }
    if (url.endsWith('/requirements')) {
      return jsonResponse(200, []);
    }
    if (url.endsWith('/forecasts')) {
      return jsonResponse(200, { runs: [], drift: null });
    }
    if (
      url.endsWith('/items') ||
      url.endsWith('/estimates') ||
      url.endsWith('/dependencies')
    ) {
      return jsonResponse(200, []);
    }
    return project
      ? jsonResponse(200, project)
      : jsonResponse(404, { code: 'project_not_found' });
  }

  /** Rendered through a route, since the project is named by a path parameter. */
  async function open(project: Project | null = PROJECTS[0]) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(reads(url, project))
    );
    renderRouted(
      <Routes>
        <Route path="/app/projects/:projectId" element={<ProjectPage />} />
      </Routes>,
      { route: `/app/projects/${project?.id ?? PROJECTS[0].id}` }
    );
  }

  /**
   * Whatever this page's next *write* answers with, decided by what the request is rather
   * than by which turn it takes.
   *
   * **`mockResolvedValueOnce` was the wrong tool and it hid a passing test.** This page
   * loads six resources and the settle above waits only for the project, so a queued "next
   * response" was sometimes eaten by one still in flight — which then showed that response
   * in *its* own place while the write it was meant for quietly did something else. The
   * assertion passed either way. It was written up here for the refusals and left in place
   * for the successes, and adding a sixth read is what finally made it fail: the same four
   * cases went red under coverage and nowhere else, which is the failure mode this comment
   * already describes.
   */
  function answerWrites(
    response: Response,
    project: Project | null = PROJECTS[0]
  ) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        init?.method !== undefined && init.method !== 'GET'
          ? response
          : reads(url, project)
      )
    );
  }

  function refuseWrites(problem: Record<string, unknown>, status = 400) {
    answerWrites(jsonResponse(status, problem));
  }

  it('shows what the project is called and what it covers', async () => {
    await open();

    expect(
      await screen.findByRole('heading', { name: 'Q3 platform work' })
    ).toBeInTheDocument();
    expect(screen.getByLabelText('What it covers')).toHaveValue(
      'Everything we promised the board'
    );
  });

  it('renames it and takes what the server said back', async () => {
    await open();
    await screen.findByRole('heading', { name: 'Q3 platform work' });
    answerWrites(
      jsonResponse(200, { ...PROJECTS[0], name: 'Q4 platform work' })
    );

    await userEvent.clear(screen.getByLabelText('Project name'));
    await userEvent.type(
      screen.getByLabelText('Project name'),
      'Q4 platform work'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/projects/${PROJECTS[0].id}`,
        expect.objectContaining({ method: 'PATCH' })
      )
    );
    expect(
      await screen.findByRole('heading', { name: 'Q4 platform work' })
    ).toBeInTheDocument();
  });

  /** The only way to take back something somebody wrote. */
  it('clears a description by sending nothing rather than an empty string', async () => {
    await open();
    await screen.findByRole('heading', { name: 'Q3 platform work' });
    answerWrites(jsonResponse(200, { ...PROJECTS[0], description: null }));

    await userEvent.clear(screen.getByLabelText('What it covers'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/projects/${PROJECTS[0].id}`,
        expect.objectContaining({
          body: JSON.stringify({
            name: 'Q3 platform work',
            description: null
          })
        })
      )
    );
  });

  it('puts a project away without losing it', async () => {
    await open();
    await screen.findByRole('heading', { name: 'Q3 platform work' });
    answerWrites(
      jsonResponse(200, { ...PROJECTS[0], archivedAt: '2026-08-13T10:00:00Z' })
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Archive this project' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/projects/${PROJECTS[0].id}/archive`,
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(
      await screen.findByText(/nothing has been lost/i)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Bring this project back' })
    ).toBeInTheDocument();
  });

  it('brings an archived one back', async () => {
    await open(ARCHIVED_PROJECT);
    await screen.findByRole('heading', { name: 'Last year' });
    expect(screen.getByText(/This project is archived/)).toBeInTheDocument();
    answerWrites(
      jsonResponse(200, { ...ARCHIVED_PROJECT, archivedAt: null }),
      ARCHIVED_PROJECT
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Bring this project back' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/projects/${ARCHIVED_PROJECT.id}/unarchive`,
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(
      await screen.findByRole('button', { name: 'Archive this project' })
    ).toBeInTheDocument();
  });

  it('says so when a change is refused', async () => {
    await open();
    await screen.findByRole('heading', { name: 'Q3 platform work' });
    refuseWrites({
      code: 'validation_failed',
      errors: { name: { code: 'not_blank' } }
    });

    await userEvent.clear(screen.getByLabelText('Project name'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(
      await screen.findByText('This cannot be empty.')
    ).toBeInTheDocument();
  });

  it('says so when archiving is refused', async () => {
    await open();
    await screen.findByRole('heading', { name: 'Q3 platform work' });
    refuseWrites({ code: 'project_not_found' }, 404);

    await userEvent.click(
      screen.getByRole('button', { name: 'Archive this project' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That project is no longer in this organisation.'
    );
  });

  /**
   * A project in another organisation reads exactly as one that never existed, which is
   * the whole point of the server answering both the same way.
   */
  it('says so when there is no such project', async () => {
    await open(null);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That project is no longer in this organisation.'
    );
    expect(screen.queryByRole('button', { name: 'Save changes' })).toBeNull();
  });

  it('offers a way back to the list', async () => {
    await open();

    expect(
      await screen.findByRole('link', { name: '← All projects' })
    ).toHaveAttribute('href', '/app/projects');
  });

  /** The guard that keeps an answer arriving late from touching a page that has gone. */
  it('drops a project that arrives after the page has been left', async () => {
    storeAccessToken();
    let deliver: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => {
            deliver = resolve;
          })
    );
    const { unmount } = renderRouted(
      <Routes>
        <Route path="/app/projects/:projectId" element={<ProjectPage />} />
      </Routes>,
      { route: `/app/projects/${PROJECTS[0].id}` }
    );
    await screen.findByRole('status');

    unmount();
    deliver(jsonResponse(200, PROJECTS[0]));

    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Q3 platform work' })
      ).toBeNull()
    );
  });

  it('drops a failure that arrives after the page has been left', async () => {
    storeAccessToken();
    let refuse: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => {
            refuse = resolve;
          })
    );
    const { unmount } = renderRouted(
      <Routes>
        <Route path="/app/projects/:projectId" element={<ProjectPage />} />
      </Routes>,
      { route: `/app/projects/${PROJECTS[0].id}` }
    );
    await screen.findByRole('status');

    unmount();
    refuse(jsonResponse(500, null));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  // Reached only if the guard above it is ever bypassed.
  it('renders nothing without an account', () => {
    const { container } = renderRouted(<ProjectPage />, {
      route: '/app/projects/x'
    });

    expect(container).toBeEmptyDOMElement();
  });
});
