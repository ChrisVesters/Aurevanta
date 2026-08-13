import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { ProjectForm } from '../projects/ProjectForm';
import type { Project } from '../projects/types';

/**
 * One plan: what it is called, what it covers, and whether it is still in use.
 *
 * Work items go here in step 2 and estimates in step 3 — for now it is the project itself
 * and the two things that can be done to it. Deliberately plain, which is the milestone's
 * position on every screen it adds: M2's output is a schema and a way to fill it.
 *
 * An identifier belonging to another organisation is a 404 from the server and reads here
 * exactly as one that never existed, because that is the whole point of answering both the
 * same way.
 */
export function ProjectPage() {
  const { t } = useTranslation();
  const { projectId } = useParams();
  const { account, request } = useAuth();
  const [project, setProject] = useState<Project | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { message, fieldErrors, report, clear } = useFormFailure([
    'name',
    'description'
  ]);

  const organisationId = account?.organisation.id;

  useEffect(() => {
    if (!organisationId || !projectId) {
      return;
    }
    let cancelled = false;

    request<Project>(`/projects/${projectId}`)
      .then((loaded) => {
        if (!cancelled) {
          setProject(loaded);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, organisationId, projectId, t]);

  /**
   * Both actions here answer with the project as it now is, so the screen takes what the
   * server said rather than editing what it was holding. The two can disagree — somebody
   * else may have renamed it a moment ago — and the server is the one that knows.
   */
  const save = useCallback(
    async (values: { name: string; description: string | null }) => {
      setBusy(true);
      setNotice(null);
      clear();
      try {
        setProject(
          await request<Project>(`/projects/${projectId}`, {
            method: 'PATCH',
            body: values
          })
        );
        setNotice(t('projects.project.saved'));
      } catch (error) {
        report(error);
      }
      setBusy(false);
    },
    [request, projectId, report, clear, t]
  );

  const setArchived = useCallback(
    async (archived: boolean) => {
      setBusy(true);
      setNotice(null);
      clear();
      try {
        setProject(
          await request<Project>(
            `/projects/${projectId}/${archived ? 'archive' : 'unarchive'}`,
            { method: 'POST' }
          )
        );
        setNotice(
          archived
            ? t('projects.project.archived')
            : t('projects.project.unarchived')
        );
      } catch (error) {
        setFailure(describeFailure(t, error));
      }
      setBusy(false);
    },
    [request, projectId, clear, t]
  );

  if (!account) {
    return null;
  }

  return (
    <main className="projects">
      <p className="back">
        <Link to="/app/projects">{t('projects.project.back')}</Link>
      </p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      {project === null ? (
        !failure && (
          <p className="loading" role="status">
            {t('projects.project.loading')}
          </p>
        )
      ) : (
        <>
          <h1>{project.name}</h1>
          {project.archivedAt && (
            <p className="empty">{t('projects.project.archivedNotice')}</p>
          )}

          <ProjectForm
            // Keyed on what the server last said, so the fields show the saved project
            // rather than whatever was in them before somebody else's change arrived.
            key={`${project.name}:${project.description ?? ''}`}
            id="project"
            name={project.name}
            description={project.description}
            busy={busy}
            submit={t('projects.project.save')}
            submitting={t('projects.project.saving')}
            banner={message}
            fieldErrors={fieldErrors}
            onSubmit={(values) => void save(values)}
          />

          <p className="archive">
            <button
              type="button"
              className={project.archivedAt ? 'secondary' : 'danger'}
              disabled={busy}
              onClick={() => void setArchived(!project.archivedAt)}
            >
              {project.archivedAt
                ? t('projects.project.unarchive')
                : t('projects.project.archive')}
            </button>
            <span className="hint">{t('projects.project.archiveHint')}</span>
          </p>
        </>
      )}
    </main>
  );
}
