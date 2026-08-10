import { Route, Routes } from 'react-router';
import { RedirectWhenSignedIn } from './auth/RedirectWhenSignedIn';
import { RequireAuth } from './auth/RequireAuth';
import { DashboardPage } from './routes/DashboardPage';
import { LandingPage } from './routes/LandingPage';
import { LoginPage } from './routes/LoginPage';
import { NotFoundPage } from './routes/NotFoundPage';
import { RegisterPage } from './routes/RegisterPage';
import { VerifyEmailPage } from './routes/VerifyEmailPage';
import './App.css';

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />

      {/*
        Where the link in a confirmation email lands. Public and outside the guards: the
        person following it cannot sign in yet — that is precisely what it is for.
      */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />

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
