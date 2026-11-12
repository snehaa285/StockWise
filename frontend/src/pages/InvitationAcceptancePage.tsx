import { useState } from 'react'
import { ArrowLeft, Check, X } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthShell } from '../components/AuthShell'
import { ApiError, stockwiseApi } from '../services/apiClient'

export function InvitationAcceptancePage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token') ?? ''
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function respond(action: 'accept' | 'decline') {
    setBusy(true)
    setError('')
    try {
      if (action === 'accept') {
        await stockwiseApi.acceptInvitation(token)
      } else {
        await stockwiseApi.declineInvitation(token)
      }
      navigate('/', { replace: true })
    } catch (reason) {
      setError(reason instanceof ApiError
        ? reason.message
        : 'The invitation could not be processed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell mode="login">
      <div className="auth-card">
        <div className="auth-heading">
          <p className="eyebrow">TEAM INVITATION</p>
          <h2>Join a StockWise business.</h2>
          <p>Sign in with the email address that received this invitation to continue.</p>
        </div>
        {error && <div className="notice notice--error" role="alert">{error}</div>}
        {!token && (
          <div className="notice notice--error" role="alert">
            This invitation link is missing its secure token. Ask the business owner to resend it.
          </div>
        )}
        {token && (
          <div className="form-actions">
            <button
              className="submit-button"
              disabled={busy}
              onClick={() => void respond('accept')}
              type="button"
            >
              <span>{busy ? 'Processing…' : 'Accept invitation'}</span>
              <Check size={17} />
            </button>
            <button
              className="secondary-button"
              disabled={busy}
              onClick={() => void respond('decline')}
              type="button"
            >
              <X size={16} /> Decline
            </button>
          </div>
        )}
        <p className="auth-switch">
          Need a different account? <Link to="/login">Sign in again <ArrowLeft size={14} /></Link>
        </p>
      </div>
    </AuthShell>
  )
}
