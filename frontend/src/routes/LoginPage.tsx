import { LoginForm } from '../auth/LoginForm';
import { AuthLayout } from './AuthLayout';

export function LoginPage() {
  return (
    <AuthLayout>
      <LoginForm />
    </AuthLayout>
  );
}
