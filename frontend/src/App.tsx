import { Route, Routes } from 'react-router';
import { RedirectWhenSignedIn } from './auth/RedirectWhenSignedIn';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './routes/AppLayout';
import { CalibrationPage } from './routes/CalibrationPage';
import { DashboardPage } from './routes/DashboardPage';
import { ForgotPasswordPage } from './routes/ForgotPasswordPage';
import { LandingPage } from './routes/LandingPage';
import { InvitePage } from './routes/InvitePage';
import { LoginPage } from './routes/LoginPage';
import { MembersPage } from './routes/MembersPage';
import { ResourcesPage } from './routes/ResourcesPage';
import { SettingsPage } from './routes/SettingsPage';
import { NotFoundPage } from './routes/NotFoundPage';
import { ProjectPage } from './routes/ProjectPage';
import { ProjectsPage } from './routes/ProjectsPage';
import { RegisterPage } from './routes/RegisterPage';
import { ResetPasswordPage } from './routes/ResetPasswordPage';
import { VerifyEmailPage } from './routes/VerifyEmailPage';
import './App.css';

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />

      {/*
        Where the links in our emails land, and the pages that ask for another. All public
        and outside the guards: everyone who needs them is someone who cannot sign in —
        which is precisely what they are for.
      */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      {/*
        Also where an emailed link lands, and public for a reason of its own: whoever is
        holding it may have no account at all, and deciding whether to make one is what
        the page is for. It stays outside RedirectWhenSignedIn too, since somebody already
        signed in is exactly who accepts with the account they have.
      */}
      <Route path="/invite/:token" element={<InvitePage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      {/* Signing in or up here moves the visitor on to /app automatically. */}
      <Route element={<RedirectWhenSignedIn />}>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Route>

      <Route element={<RequireAuth />}>
        {/* One header, one organisation switcher, however many pages sit under it. */}
        <Route element={<AppLayout />}>
          <Route path="/app" element={<DashboardPage />} />
          {/*
            Addressed by identifier and not by organisation handle: the organisation comes
            from the token, as it does on every other route. That is what lets M1a's two
            deferrals — reserved handles, and redirects for retired ones — stay deferred.
          */}
          <Route path="/app/projects" element={<ProjectsPage />} />
          <Route path="/app/projects/:projectId" element={<ProjectPage />} />
          <Route path="/app/members" element={<MembersPage />} />
          {/*
            Organisation-wide, for the reason the calibration record is: a team is the
            constraint on every plan at once, and declaring it per plan would be one claim
            written down in as many places as there are to get it wrong.
          */}
          <Route path="/app/resources" element={<ResourcesPage />} />
          {/*
            Organisation-wide rather than per plan, and reachable by everybody: a hit rate
            needs tens of completed items before it can tell 45% from 80%, which no single
            plan supplies, and what it measures is people rather than plans.
          */}
          <Route path="/app/calibration" element={<CalibrationPage />} />
          <Route path="/app/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
