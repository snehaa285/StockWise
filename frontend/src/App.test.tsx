import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { clearAccessToken } from './services/apiClient'

vi.mock('./services/firebase', () => ({
  signInWithGoogle: vi.fn().mockRejectedValue(new Error(
    'Google sign-in is not configured. Add VITE_FIREBASE_* environment variables to configure Firebase.',
  )),
}))

const profile = {
  id: 'user-42',
  displayName: 'Ada Stockwell',
  email: 'ada@example.test',
}

function jsonResponse(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('StockWise authentication experience', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    window.history.replaceState({}, '', '/')
    clearAccessToken()
    fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input)).pathname
      const method = init?.method || 'GET'

      if (path === '/api/v1/auth/csrf') {
        return jsonResponse({ detail: 'Refresh authentication failed.' }, 401)
      }
      if (path === '/api/v1/auth/login') {
        return jsonResponse({
          accessToken: 'memory-only-access-token',
          tokenType: 'Bearer',
          expiresInSeconds: 600,
          user: profile,
        })
      }
      if (path === '/api/v1/auth/register' && method === 'POST') {
        return jsonResponse(profile, 201)
      }
      if (path === '/api/v1/users/me') {
        const headers = new Headers(init?.headers)
        if (headers.get('Authorization') !== 'Bearer memory-only-access-token') {
          return jsonResponse({ detail: 'Authentication is required.' }, 401)
        }
        return jsonResponse(profile)
      }
      return jsonResponse({ detail: 'Not found.' }, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('protects the profile route and loads the current user after login', async () => {
    window.history.replaceState({}, '', '/account')
    const user = userEvent.setup()
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Make room for clarity.' }))
      .toBeInTheDocument()
    expect(screen.queryByText(profile.email)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Email address'), profile.email)
    await user.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-1')
    await user.click(screen.getByRole('button', { name: 'Sign in to StockWise' }))

    expect(await screen.findByRole('heading', { name: 'Your space, in order.' }))
      .toBeInTheDocument()
    expect(screen.getByText(profile.email)).toBeInTheDocument()
    const profileCall = fetchMock.mock.calls.find(([input]) =>
      new URL(String(input)).pathname === '/api/v1/users/me')
    expect(profileCall).toBeDefined()
    expect(new Headers(profileCall?.[1]?.headers).get('Authorization'))
      .toBe('Bearer memory-only-access-token')
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('loads the selected business and renders its real inventory summary', async () => {
    const business = {
      id: 'business-7',
      name: 'Northside Supply',
      role: 'OWNER',
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    }
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = new URL(String(input)).pathname
      if (path === '/api/v1/auth/csrf') {
        return jsonResponse({ detail: 'Refresh authentication failed.' }, 401)
      }
      if (path === '/api/v1/auth/login') {
        return jsonResponse({
          accessToken: 'memory-only-access-token',
          tokenType: 'Bearer',
          expiresInSeconds: 600,
          user: profile,
        })
      }
      if (path === '/api/v1/users/me') return jsonResponse(profile)
      if (path === '/api/v1/businesses') return jsonResponse([business])
      if (path === '/api/v1/businesses/business-7/categories') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/products') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/locations') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/inventory/stock') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/members') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/invitations') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/excel-sync') {
        return jsonResponse({
          businessId: business.id,
          status: 'DISCONNECTED',
          connectedAccount: null,
          lastSuccessfulSync: null,
          pendingChanges: 0,
          failedChanges: 0,
          lastError: null,
        })
      }
      if (path === '/api/v1/businesses/business-7/inventory/movements') {
        return jsonResponse({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
      }
      if (path === '/api/v1/businesses/business-7/inventory/alerts/low-stock') return jsonResponse([])
      if (path === '/api/v1/businesses/business-7/inventory/alerts/expiry') {
        return jsonResponse({ asOfDate: '2026-01-01', nearExpiryDays: 30, upcoming: [], expired: [] })
      }
      if (path === '/api/v1/businesses/business-7/inventory/analytics/summary') {
        return jsonResponse({
          businessId: business.id,
          asOfDate: '2026-01-01',
          onHandByUnitOfMeasure: { each: 42 },
          stockedProductCount: 3,
          activeLocationCount: 2,
          lowStockLocationProductCount: 1,
          upcomingExpiryLocationBatchCount: 0,
          expiredLocationBatchCount: 0,
        })
      }
      if (path === '/api/v1/businesses/business-7/inventory/reports/inventory') {
        return jsonResponse({
          businessId: business.id,
          generatedAt: '2026-01-01T00:00:00',
          onHandByUnitOfMeasure: { each: 42 },
          items: [],
          page: 0,
          size: 100,
          totalElements: 0,
          totalPages: 0,
        })
      }
      if (path === '/api/v1/businesses/business-7/inventory/reorder-levels') return jsonResponse([])
      return jsonResponse({ detail: 'Not found.' }, 404)
    })

    window.history.replaceState({}, '', '/')
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: 'Make room for clarity.' })
    await user.type(screen.getByLabelText('Email address'), profile.email)
    await user.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-1')
    await user.click(screen.getByRole('button', { name: 'Sign in to StockWise' }))

    expect(await screen.findByText('Live data')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Active business' }))
      .toHaveValue('business-7')
    expect(await screen.findByText('42')).toBeInTheDocument()
    const summaryCall = fetchMock.mock.calls.find(([input]) =>
      new URL(String(input)).pathname.endsWith('/inventory/analytics/summary'))
    expect(summaryCall).toBeDefined()
    expect(new Headers(summaryCall?.[1]?.headers).get('Authorization')?.startsWith('Bearer '))
      .toBe(true)

    await user.click(screen.getByRole('button', { name: 'Products & categories' }))
    expect(await screen.findByRole('heading', { name: 'Products & categories.' }))
      .toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Inventory settings' }))
    expect(await screen.findByText('OneDrive disconnected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Connect OneDrive' })).toBeInTheDocument()
  })

  it('registers safely and directs the user to sign in', async () => {
    window.history.replaceState({}, '', '/')
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: 'Make room for clarity.' })
    await user.click(screen.getByRole('link', { name: /Create an account/ }))

    await user.type(screen.getByLabelText('Display name'), profile.displayName)
    await user.type(screen.getByLabelText('Email address'), profile.email)
    await user.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-1')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('status'))
      .toHaveTextContent('Your account is ready.')
    expect(screen.getByRole('heading', { name: 'Make room for clarity.' }))
      .toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([input]) =>
      new URL(String(input)).pathname === '/api/v1/auth/register'))
      .toBe(true)
  })

  it('shows safe authentication errors without echoing credentials', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = new URL(String(input)).pathname
      if (path === '/api/v1/auth/csrf') {
        return jsonResponse({ detail: 'Refresh authentication failed.' }, 401)
      }
      if (path === '/api/v1/auth/login') {
        return jsonResponse({ detail: 'Invalid email or password.' }, 401)
      }
      return jsonResponse({ detail: 'Not found.' }, 404)
    })

    window.history.replaceState({}, '', '/')
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: 'Make room for clarity.' })
    await user.type(screen.getByLabelText('Email address'), profile.email)
    await user.type(screen.getByLabelText('Password'), 'not-the-password')
    await user.click(screen.getByRole('button', { name: 'Sign in to StockWise' }))

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Invalid email or password.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('not-the-password')
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled()
    })
  })

  it('bootstraps CSRF and calls backend logout without reading the refresh cookie', async () => {
    let signedIn = false
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input)).pathname
      const method = init?.method || 'GET'

      if (path === '/api/v1/auth/csrf') {
        return signedIn
          ? jsonResponse({ csrfToken: 'session-csrf-token' })
          : jsonResponse({ detail: 'Refresh authentication failed.' }, 401)
      }
      if (path === '/api/v1/auth/login') {
        signedIn = true
        return jsonResponse({
          accessToken: 'memory-only-access-token',
          tokenType: 'Bearer',
          expiresInSeconds: 600,
          user: profile,
        })
      }
      if (path === '/api/v1/users/me') {
        return jsonResponse(profile)
      }
      if (path === '/api/v1/auth/logout' && method === 'POST') {
        expect(new Headers(init?.headers).get('X-CSRF-TOKEN'))
          .toBe('session-csrf-token')
        signedIn = false
        return new Response(null, { status: 204 })
      }
      return jsonResponse({ detail: 'Not found.' }, 404)
    })

    window.history.replaceState({}, '', '/account')
    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('heading', { name: 'Make room for clarity.' })
    await user.type(screen.getByLabelText('Email address'), profile.email)
    await user.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-1')
    await user.click(screen.getByRole('button', { name: 'Sign in to StockWise' }))
    await screen.findByRole('heading', { name: 'Your space, in order.' })
    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Make room for clarity.' }))
      .toBeInTheDocument()
    const logoutCall = fetchMock.mock.calls.find(([input]) =>
      new URL(String(input)).pathname === '/api/v1/auth/logout')
    expect(logoutCall).toBeDefined()
    expect(logoutCall?.[1]?.credentials).toBe('include')
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('renders Google sign-in option and informs user when not configured', async () => {
    window.history.replaceState({}, '', '/')
    const user = userEvent.setup()
    render(<App />)

    const googleButton = await screen.findByRole('button', { name: /continue with google/i })
    expect(googleButton).toBeInTheDocument()

    await user.click(googleButton)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /google sign-in is not configured/i,
    )
  })

  it('displays active sessions and login history in the user profile', async () => {
    const mockSessions = [
      {
        id: 'sess-1',
        createdAt: '2026-03-01T10:00:00Z',
        lastActivityAt: '2026-03-01T11:00:00Z',
        expiresAt: '2026-03-31T10:00:00Z',
        authenticationMethod: 'EMAIL_PASSWORD',
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0',
        current: true,
      },
      {
        id: 'sess-2',
        createdAt: '2026-03-01T08:00:00Z',
        lastActivityAt: '2026-03-01T08:30:00Z',
        expiresAt: '2026-03-31T08:00:00Z',
        authenticationMethod: 'GOOGLE',
        userAgent: 'Firefox/115.0',
        current: false,
      },
    ]

    const mockHistory = [
      {
        id: 'hist-1',
        eventType: 'LOGIN_SUCCESS',
        authenticationMethod: 'EMAIL_PASSWORD',
        successful: true,
        sessionId: 'sess-1',
        occurredAt: '2026-03-01T10:00:00Z',
        userAgent: 'Chrome on Desktop',
      },
      {
        id: 'hist-2',
        eventType: 'LOGIN_FAILURE',
        authenticationMethod: 'EMAIL_PASSWORD',
        successful: false,
        sessionId: null,
        occurredAt: '2026-03-01T09:59:00Z',
        userAgent: 'Chrome on Desktop',
      },
    ]

    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = new URL(String(input)).pathname
      if (path === '/api/v1/auth/csrf') {
        return jsonResponse({ detail: 'Refresh authentication failed.' }, 401)
      }
      if (path === '/api/v1/auth/login') {
        return jsonResponse({
          accessToken: 'memory-only-access-token',
          tokenType: 'Bearer',
          expiresInSeconds: 600,
          user: profile,
        })
      }
      if (path === '/api/v1/users/me') {
        return jsonResponse(profile)
      }
      if (path === '/api/v1/auth/sessions') {
        return jsonResponse(mockSessions)
      }
      if (path === '/api/v1/auth/login-history') {
        return jsonResponse(mockHistory)
      }
      return jsonResponse({ detail: 'Not found.' }, 404)
    })

    window.history.replaceState({}, '', '/account')
    const user = userEvent.setup()
    render(<App />)

    const emailInput = await screen.findByLabelText('Email address')
    await user.type(emailInput, profile.email)
    await user.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-1')
    await user.click(screen.getByRole('button', { name: 'Sign in to StockWise' }))

    expect(await screen.findByText('Current Device')).toBeInTheDocument()
    expect(screen.getByText('ACTIVE SESSIONS')).toBeInTheDocument()
    expect(screen.getByText('LOGIN AUDIT TRAIL')).toBeInTheDocument()
    expect(screen.getByText('Sign In')).toBeInTheDocument()
    expect(screen.getByText('Failed Sign In')).toBeInTheDocument()
  })
})
