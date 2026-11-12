import { useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, KeyRound, Mail } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../services/apiClient'
import { AuthShell } from '../components/AuthShell'

export function LoginPage() {
  const { status, signIn, signInWithGoogle } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [googleBusy, setGoogleBusy] = useState(false)
  const [error, setError] = useState('')

  const destination = (location.state as { from?: string } | null)?.from || '/'

  if (status === 'authenticated') {
    return <Navigate to={destination} replace />
  }

  const created = new URLSearchParams(location.search).get('created') === '1'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await signIn(email.trim(), password)
      const destination = (location.state as { from?: string } | null)?.from || '/'
      navigate(destination, { replace: true })
    } catch (reason) {
      setError(reason instanceof ApiError
        ? reason.message
        : 'We couldn’t reach StockWise. Check your connection and try again.')
    } finally {
      setBusy(false)
    }
  }

  async function handleGoogleSignIn() {
    setError('')
    setGoogleBusy(true)
    try {
      await signInWithGoogle()
      const destination = (location.state as { from?: string } | null)?.from || '/'
      navigate(destination, { replace: true })
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 409) {
        setError(
          'An account with this email already exists. Sign in with your password first, then link your Google account in your profile.',
        )
      } else if (reason instanceof Error) {
        setError(reason.message)
      } else {
        setError('Google sign-in could not be completed. Try again or use email sign-in.')
      }
    } finally {
      setGoogleBusy(false)
    }
  }

  return (
    <AuthShell mode="login">
      <div className="auth-card">
        <div className="auth-heading">
          <span className="form-icon"><KeyRound size={18} /></span>
          <p className="eyebrow">Sign in</p>
          <h2>Make room for clarity.</h2>
          <p>Use your StockWise account to continue.</p>
        </div>

        {created && (
          <div className="notice notice--success" role="status">
            Your account is ready. Sign in to open your workspace.
          </div>
        )}
        {error && <div className="notice notice--error" role="alert">{error}</div>}

        <button
          className="oauth-button"
          disabled={busy || googleBusy}
          onClick={handleGoogleSignIn}
          type="button"
        >
          <svg aria-hidden="true" height="18" viewBox="0 0 24 24" width="18">
            <path
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              fill="#4285F4"
            />
            <path
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              fill="#34A853"
            />
            <path
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              fill="#FBBC05"
            />
            <path
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              fill="#EA4335"
            />
          </svg>
          <span>{googleBusy ? 'Connecting Google…' : 'Continue with Google'}</span>
        </button>

        <div className="auth-divider">
          <span>or continue with email</span>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label htmlFor="login-email">Email address</label>
          <div className="input-wrap">
            <Mail size={17} aria-hidden="true" />
            <input
              autoComplete="email"
              id="login-email"
              maxLength={254}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@company.com"
              required
              type="email"
              value={email}
            />
          </div>

          <div className="label-row">
            <label htmlFor="login-password">Password</label>
          </div>
          <div className="input-wrap">
            <KeyRound size={17} aria-hidden="true" />
            <input
              autoComplete="current-password"
              id="login-password"
              onChange={(event) => setPassword(event.target.value)}
              required
              type={showPassword ? 'text' : 'password'}
              value={password}
            />
            <button
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              className="password-toggle"
              onClick={() => setShowPassword((visible) => !visible)}
              type="button"
            >
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>

          <button className="submit-button" disabled={busy} type="submit">
            <span>{busy ? 'Signing in…' : 'Sign in to StockWise'}</span>
            {!busy && <ArrowRight size={17} />}
          </button>
        </form>

        <p className="auth-switch">
          New to StockWise? <Link to="/register" state={location.state}>Create an account <ArrowRight size={14} /></Link>
        </p>
      </div>
    </AuthShell>
  )
}
