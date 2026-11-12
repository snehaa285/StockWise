import { useEffect, useState } from 'react'
import {
  ArrowUpRight,
  Boxes,
  CheckCircle2,
  Clock,
  Laptop,
  Link as LinkIcon,
  LogOut,
  ShieldCheck,
  UserRound,
  XCircle,
} from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import {
  getActiveSessions,
  getLoginHistory,
  linkFirebase,
  revokeOtherSessions,
  revokeSession,
  type AuthLoginHistory,
  type AuthSession,
} from '../services/apiClient'
import { signInWithGoogle } from '../services/firebase'

export function ProfilePage() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [busy, setBusy] = useState(false)
  const [linkingGoogle, setLinkingGoogle] = useState(false)

  const [sessions, setSessions] = useState<AuthSession[]>([])
  const [history, setHistory] = useState<AuthLoginHistory[]>([])
  const [loadingSecurity, setLoadingSecurity] = useState(true)

  useEffect(() => {
    let active = true
    loadSecurityData()
    return () => {
      active = false
    }

    async function loadSecurityData() {
      try {
        const [loadedSessions, loadedHistory] = await Promise.all([
          getActiveSessions(),
          getLoginHistory(),
        ])
        if (active) {
          setSessions(loadedSessions)
          setHistory(loadedHistory)
        }
      } catch {
        // Fallback silently if not available
      } finally {
        if (active) {
          setLoadingSecurity(false)
        }
      }
    }
  }, [])

  async function handleLogout() {
    setError('')
    setBusy(true)
    try {
      await signOut()
      navigate('/login', { replace: true })
    } catch {
      setError('We couldn’t complete sign out. Check your connection and try again.')
    } finally {
      setBusy(false)
    }
  }

  async function handleRevokeSession(sessionId: string, current: boolean) {
    setError('')
    setSuccess('')
    try {
      await revokeSession(sessionId)
      if (current) {
        await signOut()
        navigate('/login', { replace: true })
        return
      }
      setSessions((prev) => prev.filter((s) => s.id !== sessionId))
      setSuccess('Session has been revoked successfully.')
    } catch {
      setError('Could not revoke session. Try again.')
    }
  }

  async function handleRevokeOthers() {
    setError('')
    setSuccess('')
    try {
      const res = await revokeOtherSessions()
      setSessions((prev) => prev.filter((s) => s.current))
      setSuccess(`Logged out from ${res.revokedCount} other device${res.revokedCount === 1 ? '' : 's'}.`)
    } catch {
      setError('Could not revoke other sessions. Try again.')
    }
  }

  async function handleLinkGoogle() {
    setError('')
    setSuccess('')
    setLinkingGoogle(true)
    try {
      const idToken = await signInWithGoogle()
      await linkFirebase(idToken)
      setSuccess('Google account successfully linked to your StockWise account.')
    } catch (reason) {
      if (reason instanceof Error) {
        setError(reason.message)
      } else {
        setError('Could not link Google account.')
      }
    } finally {
      setLinkingGoogle(false)
    }
  }

  if (!user) {
    return null
  }

  const initials = user.displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  function formatDate(iso: string) {
    try {
      return new Date(iso).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return iso
    }
  }

  function formatDevice(agent: string | null) {
    if (!agent) return 'Unknown Device'
    if (agent.includes('Chrome')) return 'Chrome on Desktop'
    if (agent.includes('Firefox')) return 'Firefox on Desktop'
    if (agent.includes('Safari')) return 'Safari on Apple Device'
    if (agent.includes('Edge')) return 'Microsoft Edge'
    return agent.length > 30 ? agent.substring(0, 30) + '…' : agent
  }

  return (
    <main className="profile-page">
      <header className="workspace-header">
        <Link aria-label="StockWise home" className="workspace-brand" to="/">
          <span className="brand-mark" aria-hidden="true"><Boxes size={19} /></span>
          <span>StockWise</span>
        </Link>
        <div className="workspace-user">
          <span className="workspace-user__name">{user.displayName}</span>
          <span className="avatar avatar--small" aria-hidden="true">{initials}</span>
        </div>
      </header>

      <div className="profile-content">
        <div className="profile-intro">
          <p className="eyebrow">PERSONAL ACCOUNT</p>
          <h1>Your space, <em>in order.</em></h1>
          <p>Your sign-in is active. Account details and active sessions are managed here.</p>
        </div>

        {error && <div className="notice notice--error" role="alert">{error}</div>}
        {success && <div className="notice notice--success" role="status">{success}</div>}

        <section className="profile-card" aria-labelledby="profile-title">
          <div className="profile-card__top">
            <div className="avatar" aria-hidden="true">{initials}</div>
            <div>
              <p className="eyebrow">STOCKWISE ACCOUNT</p>
              <h2 id="profile-title">{user.displayName}</h2>
            </div>
            <span className="account-status"><i /> ACTIVE</span>
          </div>

          <div className="profile-details">
            <div className="profile-detail">
              <span className="detail-icon"><UserRound size={17} /></span>
              <div><span className="detail-label">DISPLAY NAME</span><strong>{user.displayName}</strong></div>
            </div>
            <div className="profile-detail">
              <span className="detail-icon"><ArrowUpRight size={17} /></span>
              <div><span className="detail-label">EMAIL ADDRESS</span><strong>{user.email}</strong></div>
            </div>
          </div>

          <div className="profile-security">
            <ShieldCheck size={18} />
            <span>Your account is protected by session-bound JWT access and rotate-on-use refresh tokens.</span>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem', flexWrap: 'wrap' }}>
            <button
              className="oauth-button"
              disabled={linkingGoogle}
              onClick={handleLinkGoogle}
              style={{ width: 'auto' }}
              type="button"
            >
              <LinkIcon size={16} />
              <span>{linkingGoogle ? 'Connecting…' : 'Link Google Account'}</span>
            </button>
            <button className="logout-button" disabled={busy} onClick={handleLogout} type="button">
              <LogOut size={16} />
              {busy ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        </section>

        {/* Active Sessions */}
        <section className="profile-subcard">
          <div className="profile-subcard__header">
            <div>
              <p className="eyebrow">ACTIVE SESSIONS</p>
              <h3>Devices signed into your account</h3>
            </div>
            {sessions.filter((s) => !s.current).length > 0 && (
              <button
                className="workspace-button workspace-button--secondary"
                onClick={handleRevokeOthers}
                type="button"
              >
                Log out other devices
              </button>
            )}
          </div>

          {loadingSecurity ? (
            <p className="workspace-empty">Loading active sessions…</p>
          ) : sessions.length === 0 ? (
            <p className="workspace-empty">No active sessions found.</p>
          ) : (
            <div className="table-scroll">
              <table className="workspace-table">
                <thead>
                  <tr>
                    <th>DEVICE / BROWSER</th>
                    <th>METHOD</th>
                    <th>SIGNED IN</th>
                    <th>LAST ACTIVITY</th>
                    <th>STATUS</th>
                    <th>ACTION</th>
                  </tr>
                </thead>
                <tbody>
                  {sessions.map((s) => (
                    <tr key={s.id}>
                      <td>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
                          <Laptop size={14} />
                          <strong>{formatDevice(s.userAgent)}</strong>
                        </span>
                      </td>
                      <td>
                        <span className="role-pill">
                          {s.authenticationMethod === 'GOOGLE' ? 'Google' : 'Email/Pass'}
                        </span>
                      </td>
                      <td>{formatDate(s.createdAt)}</td>
                      <td>{formatDate(s.lastActivityAt)}</td>
                      <td>
                        {s.current ? (
                          <span className="session-current-pill">
                            <i className="session-status-dot" /> Current Device
                          </span>
                        ) : (
                          <span style={{ color: '#68786e', fontSize: '0.72rem' }}>Active</span>
                        )}
                      </td>
                      <td>
                        <div className="table-actions">
                          <button
                            className="workspace-button workspace-button--ghost"
                            onClick={() => handleRevokeSession(s.id, s.current)}
                            type="button"
                          >
                            {s.current ? 'Sign out' : 'Revoke'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* Login History */}
        <section className="profile-subcard">
          <div className="profile-subcard__header">
            <div>
              <p className="eyebrow">LOGIN AUDIT TRAIL</p>
              <h3>Recent authentication events</h3>
            </div>
          </div>

          {loadingSecurity ? (
            <p className="workspace-empty">Loading login history…</p>
          ) : history.length === 0 ? (
            <p className="workspace-empty">No recent authentication events recorded.</p>
          ) : (
            <div className="table-scroll">
              <table className="workspace-table">
                <thead>
                  <tr>
                    <th>EVENT</th>
                    <th>METHOD</th>
                    <th>DATE & TIME</th>
                    <th>DEVICE</th>
                    <th>RESULT</th>
                  </tr>
                </thead>
                <tbody>
                  {history.slice(0, 20).map((item) => (
                    <tr key={item.id}>
                      <td>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
                          <Clock size={13} />
                          <strong>
                            {item.eventType === 'LOGIN_SUCCESS'
                              ? 'Sign In'
                              : item.eventType === 'LOGIN_FAILURE'
                                ? 'Failed Sign In'
                                : item.eventType === 'LOGOUT'
                                  ? 'Signed Out'
                                  : 'Session Revoked'}
                          </strong>
                        </span>
                      </td>
                      <td>{item.authenticationMethod === 'GOOGLE' ? 'Google' : 'Password'}</td>
                      <td>{formatDate(item.occurredAt)}</td>
                      <td>{formatDevice(item.userAgent)}</td>
                      <td>
                        {item.successful ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', color: '#1f6b48' }}>
                            <CheckCircle2 size={13} /> Success
                          </span>
                        ) : (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', color: '#b23429' }}>
                            <XCircle size={13} /> Failed
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>

      <footer className="profile-footer">
        <span>STOCKWISE / PERSONAL ACCOUNT</span>
        <span>SECURITY & SESSIONS ACTIVE</span>
      </footer>
    </main>
  )
}
