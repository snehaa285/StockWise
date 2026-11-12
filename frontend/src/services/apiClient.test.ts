import { afterEach, describe, expect, it, vi } from 'vitest'

function jsonResponse(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('API authentication client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('shares one CSRF bootstrap and refresh across simultaneous 401 responses', async () => {
    let releaseCsrf: ((response: Response) => void) | undefined
    let markUnauthorizedPair: (() => void) | undefined
    const csrfGate = new Promise<Response>((resolve) => {
      releaseCsrf = resolve
    })
    const unauthorizedPair = new Promise<void>((resolve) => {
      markUnauthorizedPair = resolve
    })
    let protectedCalls = 0
    let csrfCalls = 0
    let refreshCalls = 0

    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input)).pathname
      const headers = new Headers(init?.headers)
      if (path === '/api/v1/auth/login') {
        return jsonResponse({
          accessToken: 'old-access-token',
          tokenType: 'Bearer',
          expiresInSeconds: 600,
          user: { id: 'user-1', displayName: 'Test User', email: 'test@example.test' },
        })
      }
      if (path === '/api/v1/users/me') {
        if (headers.get('Authorization') === 'Bearer old-access-token') {
          protectedCalls += 1
          if (protectedCalls === 1) {
            return jsonResponse({
              id: 'user-1',
              displayName: 'Test User',
              email: 'test@example.test',
            })
          }
          if (protectedCalls === 3) {
            markUnauthorizedPair?.()
          }
          return jsonResponse({ detail: 'Authentication is required.' }, 401)
        }
        expect(headers.get('Authorization')).toBe('Bearer new-access-token')
        return jsonResponse({ id: 'user-1', displayName: 'Test User', email: 'test@example.test' })
      }
      if (path === '/api/v1/auth/csrf') {
        csrfCalls += 1
        return csrfGate
      }
      if (path === '/api/v1/auth/refresh') {
        refreshCalls += 1
        expect(headers.get('X-CSRF-TOKEN')).toBe('session-bound-csrf')
        return jsonResponse({
          accessToken: 'new-access-token',
          tokenType: 'Bearer',
          expiresInSeconds: 600,
        })
      }
      return jsonResponse({ detail: 'Not found.' }, 404)
    }))

    const api = await import('./apiClient')
    await api.loginUser('test@example.test', 'a-test-password')
    const requests = Promise.all([api.loadCurrentUser(), api.loadCurrentUser()])
    await unauthorizedPair
    await vi.waitFor(() => expect(csrfCalls).toBe(1))
    releaseCsrf?.(jsonResponse({ csrfToken: 'session-bound-csrf' }))

    const profiles = await requests
    expect(profiles).toHaveLength(2)
    expect(csrfCalls).toBe(1)
    expect(refreshCalls).toBe(1)
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('explains transport failures without pretending the request succeeded', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    }))

    const api = await import('./apiClient')
    await expect(api.getHealth()).rejects.toMatchObject({
      name: 'ApiError',
      status: 0,
      message: expect.stringContaining('backend is running'),
    })
  })
})
