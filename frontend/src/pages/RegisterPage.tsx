import { useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, KeyRound, Mail, UserRound } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../services/apiClient'
import { AuthShell } from '../components/AuthShell'

export function RegisterPage() {
  const { status, signUp } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    if (displayName.trim().length > 120) {
      setFieldErrors({ displayName: 'Use 120 characters or fewer.' })
      return
    }
    if (new TextEncoder().encode(password).length > 72) {
      setFieldErrors({ password: 'Password must be no more than 72 UTF-8 bytes.' })
      return
    }
    setBusy(true)
    try {
      await signUp({ displayName: displayName.trim(), email: email.trim(), password })
      navigate('/login?created=1', {
        replace: true,
        state: (location.state as { from?: string } | null)?.from
          ? { from: (location.state as { from: string }).from }
          : undefined,
      })
    } catch (reason) {
      if (reason instanceof ApiError) {
        setError(reason.message)
        setFieldErrors(reason.fieldErrors)
      } else {
        setError('We couldn’t reach StockWise. Check your connection and try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell mode="register">
      <div className="auth-card auth-card--register">
        <div className="auth-heading">
          <span className="form-icon"><UserRound size={18} /></span>
          <p className="eyebrow">Create your account</p>
          <h2>A clearer start.</h2>
          <p>Set up your personal StockWise sign-in.</p>
        </div>

        {error && <div className="notice notice--error" role="alert">{error}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <label htmlFor="register-name">Display name</label>
          <div className={`input-wrap ${fieldErrors.displayName ? 'input-wrap--error' : ''}`}>
            <UserRound size={17} aria-hidden="true" />
            <input
              autoComplete="name"
              id="register-name"
              maxLength={120}
              onChange={(event) => setDisplayName(event.target.value)}
              required
              type="text"
              value={displayName}
            />
          </div>
          {fieldErrors.displayName && <span className="field-error">{fieldErrors.displayName}</span>}

          <label htmlFor="register-email">Email address</label>
          <div className={`input-wrap ${fieldErrors.email ? 'input-wrap--error' : ''}`}>
            <Mail size={17} aria-hidden="true" />
            <input
              autoComplete="email"
              id="register-email"
              maxLength={254}
              onChange={(event) => setEmail(event.target.value)}
              required
              type="email"
              value={email}
            />
          </div>
          {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}

          <label htmlFor="register-password">Password</label>
          <div className={`input-wrap ${fieldErrors.password ? 'input-wrap--error' : ''}`}>
            <KeyRound size={17} aria-hidden="true" />
            <input
              autoComplete="new-password"
              id="register-password"
              maxLength={72}
              minLength={12}
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
          {fieldErrors.password
            ? <span className="field-error">{fieldErrors.password}</span>
            : <span className="field-hint">At least 12 characters. Keep it private.</span>}

          <button className="submit-button" disabled={busy} type="submit">
            <span>{busy ? 'Creating account…' : 'Create account'}</span>
            {!busy && <ArrowRight size={17} />}
          </button>
        </form>

        <p className="auth-switch">
          Already have an account? <Link to="/login" state={location.state}>Sign in <ArrowRight size={14} /></Link>
        </p>
      </div>
    </AuthShell>
  )
}
