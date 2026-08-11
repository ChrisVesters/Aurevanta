import { Route, Routes } from 'react-router';
import { RedirectWhenSignedIn } from './auth/RedirectWhenSignedIn';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './routes/AppLayout';
import { DashboardPage } from './routes/DashboardPage';
import { ForgotPasswordPage } from './routes/ForgotPasswordPage';
import { LandingPage } from './routes/LandingPage';
import { InvitePage } from './routes/InvitePage';
import { LoginPage } from './routes/LoginPage';
import { MembersPage } from './routes/MembersPage';
import { SettingsPage } from './routes/SettingsPage';
import { NotFoundPage } from './routes/NotFoundPage';
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
          <Route path="/app/members" element={<MembersPage />} />
          <Route path="/app/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
