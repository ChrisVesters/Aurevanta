import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProjectsPage } from './ProjectsPage';
import {
  ACCOUNT,
  ARCHIVED_PROJECT,
  PROJECTS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';

describe('ProjectsPage', () => {
  const fetchMock = mockFetch();

  /**
   * Answers by URL rather than in bulk: restoring the session and loading the list are
   * separate questions, and a double that answered both alike would hand the list an
   * account.
   */
  async function open(projects = PROJECTS) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(200, projects)
      )
    );
    renderRouted(<ProjectsPage />, { route: '/app/projects' });
    await screen.findByRole('heading', { name: 'Projects' });
  }

  it('lists the plans the organisation is working from', async () => {
    await open();

    expect(
      await screen.findByRole('link', { name: 'Q3 platform work' })
    ).toHaveAttribute('href', `/app/projects/${PROJECTS[0].id}`);
    expect(
      screen.getByText('Everything we promised the board')
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Migration' })).toBeInTheDocument();
  });

  it('says so when there is nothing to plan yet', async () => {
    await open([]);

    expect(await screen.findByText(/No projects yet/)).toBeInTheDocument();
  });

  it('starts a project and asks the server what the list looks like now', async () => {
    await open();
    await screen.findByRole('link', { name: 'Q3 platform work' });

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.type(
      screen.getByLabelText('What it covers'),
      'Next quarter'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/projects',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            name: 'Q4 platform',
            description: 'Next quarter'
          })
        })
      )
    );
  });

  /**
   * A description nobody wrote is stored as nothing rather than as an empty string, and
   * the form is where that starts: the server would take `''` and keep it.
   */
  it('sends no description rather than an empty one', async () => {
    await open();

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/projects',
        expect.objectContaining({
          body: JSON.stringify({ name: 'Q4 platform', description: null })
        })
      )
    );
  });

  /** So the next project starts from nothing rather than from the name of the last one. */
  it('empties the form once a project has been made', async () => {
    await open();

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    await waitFor(() =>
      expect(screen.getByLabelText('Project name')).toHaveValue('')
    );
  });

  /**
   * The rule `useFormFailure` exists to keep: a complaint belonging to a field is shown
   * against that field, and the banner is for what belongs to the form as a whole.
   */
  it('puts a complaint about the name against the name', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { name: { code: 'max_size', max: 200 } }
      })
    );

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4');
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    expect(
      await screen.findByText('Use no more than 200 characters.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /** And what somebody typed survives being told why it was refused. */
  it('keeps what was typed when the server refuses it', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { name: { code: 'not_blank' } }
      })
    );

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    expect(
      await screen.findByText('This cannot be empty.')
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Project name')).toHaveValue('Q4 platform');
  });

  it('asks for the archived ones separately', async () => {
    await open();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(200, [ARCHIVED_PROJECT])
      )
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived projects' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/projects?archived=true',
        expect.anything()
      )
    );
    expect(
      await screen.findByRole('link', { name: 'Last year' })
    ).toBeInTheDocument();
    // And what was archived is not sitting among the live work.
    expect(screen.queryByRole('link', { name: 'Q3 platform work' })).toBeNull();
  });

  /** A different sentence from an empty organisation, because it is a different state. */
  it('says so when nothing has been put away', async () => {
    await open();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(200, [])
      )
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived projects' })
    );

    expect(
      await screen.findByText('Nothing has been put away.')
    ).toBeInTheDocument();
  });

  it('says so when the list cannot be loaded', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(500, null)
      )
    );
    renderRouted(<ProjectsPage />, { route: '/app/projects' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Something went wrong/
    );
  });

  /**
   * The other half of the same rule: a failure belonging to no field on screen gets the
   * banner, because showing it nowhere is the only worse answer.
   */
  it('puts a failure that belongs to no field in the banner', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(jsonResponse(500, null));

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Something went wrong/
    );
  });

  it('puts a complaint about the description against the description', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { description: { code: 'max_size', max: 2000 } }
      })
    );

    await userEvent.type(screen.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(
      screen.getByRole('button', { name: 'Create project' })
    );

    expect(
      await screen.findByText('Use no more than 2000 characters.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /** The guard that keeps a list arriving late from touching a page that has gone. */
  it('drops a list that arrives after the page has been left', async () => {
    storeAccessToken();
    let deliver: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => {
            deliver = resolve;
          })
    );
    const { unmount } = renderRouted(<ProjectsPage />, {
      route: '/app/projects'
    });
    await screen.findByRole('status');

    unmount();
    deliver(jsonResponse(200, PROJECTS));

    await waitFor(() =>
      expect(
        screen.queryByRole('link', { name: 'Q3 platform work' })
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
    const { unmount } = renderRouted(<ProjectsPage />, {
      route: '/app/projects'
    });
    await screen.findByRole('status');

    unmount();
    refuse(jsonResponse(500, null));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  // Reached only if the guard above it is ever bypassed.
  it('renders nothing without an account', () => {
    const { container } = renderRouted(<ProjectsPage />, {
      route: '/app/projects'
    });

    expect(container).toBeEmptyDOMElement();
  });
});
