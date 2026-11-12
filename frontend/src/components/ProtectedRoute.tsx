import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <main className="auth-loading" role="status" aria-live="polite">
        <span className="loading-mark" aria-hidden="true" />
        <p>Checking your account…</p>
      </main>
    )
  }

  if (status !== 'authenticated') {
    return <Navigate
      to="/login"
      replace
      state={{ from: `${location.pathname}${location.search}` }}
    />
  }

  return <Outlet />
}
