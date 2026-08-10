import { Route, Routes } from 'react-router';
import { RedirectWhenSignedIn } from './auth/RedirectWhenSignedIn';
import { RequireAuth } from './auth/RequireAuth';
import { DashboardPage } from './routes/DashboardPage';
import { ForgotPasswordPage } from './routes/ForgotPasswordPage';
import { LandingPage } from './routes/LandingPage';
import { LoginPage } from './routes/LoginPage';
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
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      {/* Signing in or up here moves the visitor on to /app automatically. */}
      <Route element={<RedirectWhenSignedIn />}>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route path="/app" element={<DashboardPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
