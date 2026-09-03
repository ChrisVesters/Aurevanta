import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
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

  /**
   * Open the form that starts a plan, which is asked for now rather than always on screen.
   * Every case that creates one goes through here, because the button is what a person
   * presses and a case reaching into the dialog without it would be testing a form nobody
   * can get to.
   */
  async function startCreating() {
    await userEvent.click(
      screen.getByRole('button', { name: 'Start a project' })
    );
    return within(await screen.findByRole('dialog'));
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

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.type(form.getByLabelText('What it covers'), 'Next quarter');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

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

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/projects',
        expect.objectContaining({
          body: JSON.stringify({ name: 'Q4 platform', description: null })
        })
      )
    );
  });

  /**
   * <strong>It shuts once the server has it, and opens empty next time.</strong> The form
   * used to sit under the list and be remounted to clear it; now it exists only while it is
   * open, which is the same rule reached by not being there rather than by being replaced.
   */
  it('shuts once a project has been made, and reopens empty', async () => {
    await open();

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect((await startCreating()).getByLabelText('Project name')).toHaveValue(
      ''
    );
  });

  /** Two ways out, and both are ways somebody meant: the form's own, and the platform's. */
  it('takes a cancelled project off the screen', async () => {
    await open();
    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');

    await userEvent.click(form.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(
      fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    ).toHaveLength(0);
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

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

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

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

    expect(
      await screen.findByText('This cannot be empty.')
    ).toBeInTheDocument();
    // Still up, holding what was typed: shutting on submit would ask for the whole thing
    // again in order to read the complaint about it.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
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

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

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

    const form = await startCreating();
    await userEvent.type(form.getByLabelText('Project name'), 'Q4 platform');
    await userEvent.click(form.getByRole('button', { name: 'Create project' }));

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
