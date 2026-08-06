import { RegisterForm } from '../auth/RegisterForm';
import { AuthLayout } from './AuthLayout';

export function RegisterPage() {
  return (
    <AuthLayout>
      <RegisterForm />
    </AuthLayout>
  );
}
