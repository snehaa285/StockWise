export interface HealthResponse {
  status: string
  service: string
}

export interface UserProfile {
  id: string
  displayName: string
  email: string
}

interface AccessTokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
}

interface LoginResponse extends AccessTokenResponse {
  user: UserProfile
}

interface CsrfResponse {
  csrfToken: string
}

export type BusinessRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export interface Business {
  id: string
  name: string
  role: BusinessRole
  createdAt: string
  updatedAt: string
}

export interface BusinessMember {
  userId: string
  displayName: string
  email: string
  role: BusinessRole
  memberSince: string
}

export interface BusinessInvitation {
  id: string
  email: string
  role: 'ADMIN' | 'MEMBER'
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED'
  expiresAt: string
  createdAt: string
}

export interface ExcelSyncStatus {
  businessId: string
  status: 'DISCONNECTED' | 'CONNECTED' | 'REAUTH_REQUIRED' | 'PROCESSING' | 'FAILED'
  connectedAccount: string | null
  lastSuccessfulSync: string | null
  pendingChanges: number
  failedChanges: number
  lastError: string | null
}

export interface Category {
  id: string
  name: string
  description: string | null
  batchTrackingDefault: boolean
  expiryTrackingDefault: boolean
  active: boolean
}

export interface Product {
  id: string
  categoryId: string
  categoryName: string
  name: string
  sku: string | null
  unitOfMeasure: string
  description: string | null
  batchTrackingOverride: boolean | null
  expiryTrackingOverride: boolean | null
  batchTracked: boolean
  expiryTracked: boolean
  active: boolean
  attributes: Record<string, unknown>
}

export interface AttributeOption {
  id: string
  label: string
  active: boolean
  position: number
}

export interface AttributeDefinition {
  id: string
  categoryId: string
  name: string
  type: 'TEXT' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'SINGLE_SELECT' | 'MULTI_SELECT'
  required: boolean
  description: string | null
  active: boolean
  constraints: Record<string, unknown>
  options: AttributeOption[]
}

export interface InventoryLocation {
  id: string
  name: string
  description: string | null
  active: boolean
}

export interface StockBalance {
  productId: string
  productName: string
  sku: string | null
  unitOfMeasure: string
  productActive: boolean
  locationId: string
  locationName: string
  batchId: string | null
  batchCode: string | null
  expiryDate: string | null
  quantity: number
}

export interface InventoryBatch {
  id: string
  productId: string
  productName: string
  batchCode: string
  expiryDate: string | null
}

export interface InventoryMovement {
  id: string
  operationType: string
  movementType: string
  productName: string
  locationName: string
  batchCode: string | null
  expiryDate: string | null
  quantityDelta: number
  quantityAfter: number
  reason: string | null
  createdAt: string
}

export interface PagedMovements {
  items: InventoryMovement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface LowStockAlert {
  productId: string
  productName: string
  sku: string | null
  unitOfMeasure: string
  locationId: string
  locationName: string
  availableQuantity: number
  reorderLevel: number
  shortfallToReorderLevel: number
}

export interface ExpiryStock {
  batchId: string
  productId: string
  productName: string
  sku: string | null
  unitOfMeasure: string
  locationId: string
  locationName: string
  batchCode: string
  expiryDate: string
  daysUntilExpiry: number
  quantity: number
}

export interface ExpiryMonitoring {
  asOfDate: string
  nearExpiryDays: number
  upcoming: ExpiryStock[]
  expired: ExpiryStock[]
}

export interface InventorySummary {
  businessId: string
  asOfDate: string
  onHandByUnitOfMeasure: Record<string, number>
  stockedProductCount: number
  activeLocationCount: number
  lowStockLocationProductCount: number
  upcomingExpiryLocationBatchCount: number
  expiredLocationBatchCount: number
}

export interface InventoryReport {
  businessId: string
  generatedAt: string
  onHandByUnitOfMeasure: Record<string, number>
  items: Array<{
    productId: string
    productName: string
    sku: string | null
    unitOfMeasure: string
    locationId: string
    locationName: string
    batchId: string | null
    batchCode: string | null
    expiryDate: string | null
    quantity: number
  }>
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ReorderLevel {
  id: string
  productId: string
  productName: string
  sku: string | null
  unitOfMeasure: string
  locationId: string
  locationName: string
  reorderLevel: number
}

export interface BusinessInventorySettings {
  businessId: string
  nearExpiryDays: number
}

export interface MovementTrends {
  fromDate: string
  toDate: string
  points: Array<{
    movementDate: string
    movementType: string
    unitOfMeasure: string
    movementCount: number
    quantityDelta: number
  }>
}

export interface AuthSession {
  id: string
  createdAt: string
  lastActivityAt: string
  expiresAt: string
  authenticationMethod: 'EMAIL_PASSWORD' | 'GOOGLE'
  userAgent: string | null
  current: boolean
}

export interface AuthLoginHistory {
  id: string
  eventType: 'LOGIN_SUCCESS' | 'LOGIN_FAILURE' | 'LOGOUT' | 'SESSION_REVOKED'
  authenticationMethod: 'EMAIL_PASSWORD' | 'GOOGLE'
  successful: boolean
  sessionId: string | null
  occurredAt: string
  userAgent: string | null
}

type JsonRecord = Record<string, unknown>

export interface RegistrationInput {
  displayName: string
  email: string
  password: string
}

interface ProblemResponse {
  detail?: string
  fieldErrors?: Record<string, string>
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080')
  .replace(/\/+$/, '')

let accessToken: string | null = null
let refreshFlight: Promise<string | null> | null = null

export class ApiError extends Error {
  readonly fieldErrors: Record<string, string>
  readonly status: number

  constructor(
    message: string,
    status: number,
    fieldErrors: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  bearerToken?: string,
): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (bearerToken) {
    headers.set('Authorization', `Bearer ${bearerToken}`)
  }

  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers,
      credentials: 'include',
    })
  } catch {
    throw new ApiError(
      'The StockWise API could not be reached. Check that the backend is running and that this browser origin is allowed by its CORS configuration.',
      0,
    )
  }

  if (!response.ok) {
    let problem: ProblemResponse = {}
    try {
      problem = (await response.json()) as ProblemResponse
    } catch {
      problem = {}
    }
    throw new ApiError(
      problem.detail || 'The request could not be completed.',
      response.status,
      problem.fieldErrors,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function getHealth(): Promise<HealthResponse> {
  return request<HealthResponse>('/api/v1/health')
}

export async function registerUser(input: RegistrationInput): Promise<UserProfile> {
  return request<UserProfile>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function loginUser(email: string, password: string): Promise<UserProfile> {
  const result = await request<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  accessToken = result.accessToken

  try {
    return await loadCurrentUser()
  } catch (error) {
    accessToken = null
    throw error
  }
}

export async function loadCurrentUser(): Promise<UserProfile> {
  const token = accessToken
  if (!token) {
    throw new ApiError('Authentication is required.', 401)
  }
  return authenticatedRequest<UserProfile>('/api/v1/users/me', {}, token)
}

export async function restoreSession(): Promise<UserProfile | null> {
  if (accessToken) {
    return loadCurrentUser()
  }
  const token = await refreshAccessToken()
  return token ? loadCurrentUser() : null
}

export async function logoutUser(): Promise<void> {
  const csrf = await request<CsrfResponse>('/api/v1/auth/csrf')
  await request<void>('/api/v1/auth/logout', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrf.csrfToken },
  })
  accessToken = null
}

export function clearAccessToken(): void {
  accessToken = null
}

export async function loginWithFirebase(idToken: string): Promise<UserProfile> {
  const result = await request<LoginResponse>('/api/v1/auth/firebase', {
    method: 'POST',
    body: JSON.stringify({ idToken }),
  })
  accessToken = result.accessToken

  try {
    return await loadCurrentUser()
  } catch (error) {
    accessToken = null
    throw error
  }
}

export async function linkFirebase(idToken: string): Promise<void> {
  const token = accessToken
  if (!token) {
    throw new ApiError('Authentication is required.', 401)
  }
  await authenticatedRequest<void>(
    '/api/v1/auth/firebase/link',
    {
      method: 'POST',
      body: JSON.stringify({ idToken }),
    },
    token,
  )
}

export async function getActiveSessions(): Promise<AuthSession[]> {
  return authenticatedJson<AuthSession[]>('/api/v1/auth/sessions')
}

export async function revokeSession(sessionId: string): Promise<void> {
  return authenticatedJson<void>(
    `/api/v1/auth/sessions/${encodeURIComponent(sessionId)}`,
    'DELETE',
  )
}

export async function revokeOtherSessions(): Promise<{ revokedCount: number }> {
  return authenticatedJson<{ revokedCount: number }>(
    '/api/v1/auth/sessions/revoke-others',
    'POST',
  )
}

export async function getLoginHistory(): Promise<AuthLoginHistory[]> {
  return authenticatedJson<AuthLoginHistory[]>('/api/v1/auth/login-history')
}

async function authenticatedRequest<T>(
  path: string,
  init: RequestInit,
  tokenUsed: string,
): Promise<T> {
  try {
    return await request<T>(path, init, tokenUsed)
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) {
      throw error
    }

    const currentToken = accessToken
    const newToken = currentToken && currentToken !== tokenUsed
      ? currentToken
      : await refreshAccessToken()
    if (!newToken) {
      throw error
    }
    return request<T>(path, init, newToken)
  }
}

function refreshAccessToken(): Promise<string | null> {
  if (!refreshFlight) {
    refreshFlight = performRefresh()
  }
  const flight = refreshFlight
  return flight.finally(() => {
    if (refreshFlight === flight) {
      refreshFlight = null
    }
  })
}

async function performRefresh(): Promise<string | null> {
  try {
    const csrf = await request<CsrfResponse>('/api/v1/auth/csrf')
    const result = await request<AccessTokenResponse>('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'X-CSRF-TOKEN': csrf.csrfToken },
    })
    accessToken = result.accessToken
    return accessToken
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      accessToken = null
      return null
    }
    throw error
  }
}

function businessPath(businessId: string, suffix = ''): string {
  return `/api/v1/businesses/${encodeURIComponent(businessId)}${suffix}`
}

function jsonBody(method: string, value: unknown): RequestInit {
  return { method, body: JSON.stringify(value) }
}

async function authenticatedJson<T>(
  path: string,
  method = 'GET',
  body?: unknown,
): Promise<T> {
  const init = body === undefined ? { method } : jsonBody(method, body)
  return authenticatedRequest<T>(path, init, accessToken || '')
}

export const stockwiseApi = {
  businesses: () => authenticatedJson<Business[]>('/api/v1/businesses'),
  createBusiness: (name: string) =>
    authenticatedJson<Business>('/api/v1/businesses', 'POST', { name }),
  updateBusiness: (businessId: string, name: string) =>
    authenticatedJson<Business>(businessPath(businessId), 'PATCH', { name }),
  members: (businessId: string) =>
    authenticatedJson<BusinessMember[]>(businessPath(businessId, '/members')),
  invitations: (businessId: string) =>
    authenticatedJson<BusinessInvitation[]>(businessPath(businessId, '/invitations')),
  inviteMember: (businessId: string, email: string, role: 'ADMIN' | 'MEMBER') =>
    authenticatedJson<BusinessInvitation>(
      businessPath(businessId, '/invitations'),
      'POST',
      { email, role },
    ),
  resendInvitation: (businessId: string, invitationId: string) =>
    authenticatedJson<BusinessInvitation>(
      businessPath(businessId, `/invitations/${encodeURIComponent(invitationId)}/resend`),
      'POST',
    ),
  revokeInvitation: (businessId: string, invitationId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, `/invitations/${encodeURIComponent(invitationId)}/revoke`),
      'POST',
    ),
  acceptInvitation: (token: string) =>
    authenticatedJson<void>('/api/v1/invitations/accept', 'POST', { token }),
  declineInvitation: (token: string) =>
    authenticatedJson<void>('/api/v1/invitations/decline', 'POST', { token }),
  changeMemberRole: (businessId: string, userId: string, role: BusinessRole) =>
    authenticatedJson<BusinessMember>(
      businessPath(businessId, `/members/${encodeURIComponent(userId)}`),
      'PATCH',
      { role },
    ),
  removeMember: (businessId: string, userId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, `/members/${encodeURIComponent(userId)}`),
      'DELETE',
    ),
  categories: (businessId: string) =>
    authenticatedJson<Category[]>(businessPath(businessId, '/categories')),
  createCategory: (businessId: string, input: JsonRecord) =>
    authenticatedJson<Category>(businessPath(businessId, '/categories'), 'POST', input),
  updateCategory: (businessId: string, categoryId: string, input: JsonRecord) =>
    authenticatedJson<Category>(
      businessPath(businessId, `/categories/${encodeURIComponent(categoryId)}`),
      'PUT',
      input,
    ),
  archiveCategory: (businessId: string, categoryId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, `/categories/${encodeURIComponent(categoryId)}/archive`),
      'POST',
    ),
  deleteCategory: (businessId: string, categoryId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, `/categories/${encodeURIComponent(categoryId)}`),
      'DELETE',
    ),
  products: (businessId: string, categoryId?: string) => {
    const query = categoryId ? `?categoryId=${encodeURIComponent(categoryId)}` : ''
    return authenticatedJson<Product[]>(
      businessPath(businessId, `/products${query}`),
    )
  },
  createProduct: (businessId: string, input: JsonRecord) =>
    authenticatedJson<Product>(businessPath(businessId, '/products'), 'POST', input),
  updateProduct: (businessId: string, productId: string, input: JsonRecord) =>
    authenticatedJson<Product>(
      businessPath(businessId, `/products/${encodeURIComponent(productId)}`),
      'PUT',
      input,
    ),
  archiveProduct: (businessId: string, productId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, `/products/${encodeURIComponent(productId)}`),
      'DELETE',
    ),
  attributes: (businessId: string, categoryId: string) =>
    authenticatedJson<AttributeDefinition[]>(
      businessPath(businessId, `/categories/${encodeURIComponent(categoryId)}/attributes`),
    ),
  createAttribute: (businessId: string, categoryId: string, input: JsonRecord) =>
    authenticatedJson<AttributeDefinition>(
      businessPath(businessId, `/categories/${encodeURIComponent(categoryId)}/attributes`),
      'POST',
      input,
    ),
  updateAttribute: (
    businessId: string,
    categoryId: string,
    attributeId: string,
    input: JsonRecord,
  ) =>
    authenticatedJson<AttributeDefinition>(
      businessPath(
        businessId,
        `/categories/${encodeURIComponent(categoryId)}/attributes/${encodeURIComponent(attributeId)}`,
      ),
      'PUT',
      input,
    ),
  archiveAttribute: (businessId: string, categoryId: string, attributeId: string) =>
    authenticatedJson<void>(
      businessPath(
        businessId,
        `/categories/${encodeURIComponent(categoryId)}/attributes/${encodeURIComponent(attributeId)}`,
      ),
      'DELETE',
    ),
  addAttributeOption: (
    businessId: string,
    categoryId: string,
    attributeId: string,
    input: JsonRecord,
  ) =>
    authenticatedJson<AttributeDefinition>(
      businessPath(
        businessId,
        `/categories/${encodeURIComponent(categoryId)}/attributes/${encodeURIComponent(attributeId)}/options`,
      ),
      'POST',
      input,
    ),
  updateAttributeOption: (
    businessId: string,
    categoryId: string,
    attributeId: string,
    optionId: string,
    input: JsonRecord,
  ) =>
    authenticatedJson<AttributeDefinition>(
      businessPath(
        businessId,
        `/categories/${encodeURIComponent(categoryId)}/attributes/${encodeURIComponent(attributeId)}/options/${encodeURIComponent(optionId)}`,
      ),
      'PUT',
      input,
    ),
  deleteAttributeOption: (
    businessId: string,
    categoryId: string,
    attributeId: string,
    optionId: string,
  ) =>
    authenticatedJson<void>(
      businessPath(
        businessId,
        `/categories/${encodeURIComponent(categoryId)}/attributes/${encodeURIComponent(attributeId)}/options/${encodeURIComponent(optionId)}`,
      ),
      'DELETE',
    ),
  locations: (businessId: string) =>
    authenticatedJson<InventoryLocation[]>(businessPath(businessId, '/locations')),
  createLocation: (businessId: string, input: JsonRecord) =>
    authenticatedJson<InventoryLocation>(businessPath(businessId, '/locations'), 'POST', input),
  updateLocation: (businessId: string, locationId: string, input: JsonRecord) =>
    authenticatedJson<InventoryLocation>(
      businessPath(businessId, `/locations/${encodeURIComponent(locationId)}`),
      'PATCH',
      input,
    ),
  archiveLocation: (businessId: string, locationId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, `/locations/${encodeURIComponent(locationId)}`),
      'DELETE',
    ),
  stock: (businessId: string) =>
    authenticatedJson<StockBalance[]>(businessPath(businessId, '/inventory/stock')),
  batches: (businessId: string, productId: string) =>
    authenticatedJson<InventoryBatch[]>(
      businessPath(businessId, `/inventory/batches?productId=${encodeURIComponent(productId)}`),
    ),
  movements: (businessId: string, page = 0, size = 100) =>
    authenticatedJson<PagedMovements>(
      businessPath(businessId, `/inventory/movements?page=${page}&size=${size}`),
    ),
  receiveStock: (businessId: string, key: string, input: JsonRecord) =>
    authenticatedRequest(
      businessPath(businessId, '/inventory/receipts'),
      { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(input) },
      accessToken || '',
    ),
  adjustStock: (businessId: string, key: string, input: JsonRecord) =>
    authenticatedRequest(
      businessPath(businessId, '/inventory/adjustments'),
      { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(input) },
      accessToken || '',
    ),
  transferStock: (businessId: string, key: string, input: JsonRecord) =>
    authenticatedRequest(
      businessPath(businessId, '/inventory/transfers'),
      { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(input) },
      accessToken || '',
    ),
  reorderLevels: (businessId: string) =>
    authenticatedJson<ReorderLevel[]>(businessPath(businessId, '/inventory/reorder-levels')),
  saveReorderLevel: (businessId: string, input: JsonRecord) =>
    authenticatedJson<ReorderLevel>(
      businessPath(businessId, '/inventory/reorder-levels'),
      'PUT',
      input,
    ),
  deleteReorderLevel: (businessId: string, productId: string, locationId: string) =>
    authenticatedJson<void>(
      businessPath(
        businessId,
        `/inventory/reorder-levels/${encodeURIComponent(productId)}/${encodeURIComponent(locationId)}`,
      ),
      'DELETE',
    ),
  lowStock: (businessId: string) =>
    authenticatedJson<LowStockAlert[]>(
      businessPath(businessId, '/inventory/alerts/low-stock'),
    ),
  expiry: (businessId: string) =>
    authenticatedJson<ExpiryMonitoring>(
      businessPath(businessId, '/inventory/alerts/expiry'),
    ),
  settings: (businessId: string) =>
    authenticatedJson<BusinessInventorySettings>(
      businessPath(businessId, '/inventory/settings'),
    ),
  updateSettings: (businessId: string, input: JsonRecord) =>
    authenticatedJson<BusinessInventorySettings>(
      businessPath(businessId, '/inventory/settings'),
      'PUT',
      input,
    ),
  summary: (businessId: string) =>
    authenticatedJson<InventorySummary>(
      businessPath(businessId, '/inventory/analytics/summary'),
    ),
  movementTrends: (businessId: string, fromDate: string, toDate: string) =>
    authenticatedJson<MovementTrends>(
      businessPath(
        businessId,
        `/inventory/analytics/movements?fromDate=${encodeURIComponent(fromDate)}&toDate=${encodeURIComponent(toDate)}`,
      ),
    ),
  report: (businessId: string, page = 0, size = 100) =>
    authenticatedJson<InventoryReport>(
      businessPath(businessId, `/inventory/reports/inventory?page=${page}&size=${size}`),
    ),
  excelSyncStatus: (businessId: string) =>
    authenticatedJson<ExcelSyncStatus>(
      businessPath(businessId, '/excel-sync'),
    ),
  beginExcelSyncConnection: (businessId: string) =>
    authenticatedJson<{ authorizationUrl: string }>(
      businessPath(businessId, '/excel-sync/connect'),
      'POST',
    ),
  disconnectExcelSync: (businessId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, '/excel-sync/connect'),
      'DELETE',
    ),
  retryExcelSync: (businessId: string) =>
    authenticatedJson<{ queuedChanges: number }>(
      businessPath(businessId, '/excel-sync/retry'),
      'POST',
    ),
  resynchronizeExcel: (businessId: string) =>
    authenticatedJson<void>(
      businessPath(businessId, '/excel-sync/resynchronize'),
      'POST',
    ),
}
