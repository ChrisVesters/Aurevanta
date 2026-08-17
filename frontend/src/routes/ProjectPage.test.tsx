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
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Project } from '../projects/types';

describe('ProjectPage', () => {
  const fetchMock = mockFetch();

  /**
   * Rendered through a route, since the project is named by a path parameter.
   *
   * Five URLs answered separately, because the page loads resources that fail
   * independently: the plan itself, the forecasts made of it, and — through `WorkItems` —
   * the work inside it, what that work is estimated at, and how it is joined up. A double
   * that answered them alike would hand the estimate list a project, and the page would
   * crash rather than the test failing where the mistake is.
   */
  async function open(project: Project | null = PROJECTS[0]) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url.endsWith('/items') ||
              url.endsWith('/estimates') ||
              url.endsWith('/dependencies') ||
              url.endsWith('/forecasts')
            ? jsonResponse(200, [])
            : project
              ? jsonResponse(200, project)
              : jsonResponse(404, { code: 'project_not_found' })
      )
    );
    renderRouted(
      <Routes>
        <Route path="/app/projects/:projectId" element={<ProjectPage />} />
      </Routes>,
      { route: `/app/projects/${project?.id ?? PROJECTS[0].id}` }
    );
  }

  /**
   * Whatever this page's next *write* is refused with, answered by what the request is
   * rather than by which turn it takes.
   *
   * **`mockResolvedValueOnce` was the wrong tool and it hid a passing test.** This page
   * loads five resources, and the settle above waits only for the project — so a queued
   * "next response" was sometimes eaten by one of the four still in flight, which then
   * showed the very same refusal in *its* own banner while the write it was meant for
   * quietly succeeded. The assertion passed either way; only the coverage gate noticed,
   * because the branch it was written for stopped running about half the time.
   */
  function refuseWrites(problem: Record<string, unknown>, status = 400) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        init?.method !== undefined && init.method !== 'GET'
          ? jsonResponse(status, problem)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : url.endsWith('/items') ||
                url.endsWith('/estimates') ||
                url.endsWith('/dependencies') ||
                url.endsWith('/forecasts')
              ? jsonResponse(200, [])
              : jsonResponse(200, PROJECTS[0])
      )
    );
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
    fetchMock.mockResolvedValueOnce(
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
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { ...PROJECTS[0], description: null })
    );

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
    fetchMock.mockResolvedValueOnce(
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
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { ...ARCHIVED_PROJECT, archivedAt: null })
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
