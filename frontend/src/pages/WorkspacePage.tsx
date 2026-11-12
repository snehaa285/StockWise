import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import {
  Activity,
  Archive,
  ArrowDownToLine,
  ArrowLeftRight,
  Boxes,
  Building2,
  ChartNoAxesCombined,
  ChevronDown,
  CircleAlert,
  ClipboardList,
  Cloud,
  CloudOff,
  Layers3,
  LogOut,
  Package,
  Plus,
  RefreshCw,
  Settings2,
  ShieldCheck,
  Users,
  Warehouse,
} from 'lucide-react'
import { useAuth } from '../auth/useAuth'
import {
  ApiError,
  stockwiseApi,
  type AttributeDefinition,
  type Business,
  type BusinessInvitation,
  type BusinessMember,
  type BusinessRole,
  type Category,
  type ExcelSyncStatus,
  type ExpiryMonitoring,
  type InventoryLocation,
  type InventoryMovement,
  type InventoryReport,
  type InventorySummary,
  type LowStockAlert,
  type Product,
  type ReorderLevel,
  type StockBalance,
} from '../services/apiClient'
import { Link, useLocation, useNavigate } from 'react-router-dom'

type View =
  | 'overview'
  | 'business'
  | 'catalog'
  | 'attributes'
  | 'locations'
  | 'stock'
  | 'operations'
  | 'movements'
  | 'alerts'
  | 'analytics'
  | 'settings'

const navigation: Array<{ id: View; label: string; icon: typeof Boxes; group: string }> = [
  { id: 'overview', label: 'Overview', icon: ChartNoAxesCombined, group: 'Workspace' },
  { id: 'business', label: 'Business & team', icon: Users, group: 'Workspace' },
  { id: 'catalog', label: 'Products & categories', icon: Package, group: 'Manage' },
  { id: 'attributes', label: 'Product attributes', icon: Layers3, group: 'Manage' },
  { id: 'locations', label: 'Locations', icon: Warehouse, group: 'Manage' },
  { id: 'stock', label: 'Stock overview', icon: Boxes, group: 'Inventory' },
  { id: 'operations', label: 'Receive & move stock', icon: ArrowLeftRight, group: 'Inventory' },
  { id: 'movements', label: 'Movement history', icon: ClipboardList, group: 'Inventory' },
  { id: 'alerts', label: 'Alerts & expiry', icon: CircleAlert, group: 'Insights' },
  { id: 'analytics', label: 'Analytics & reports', icon: Activity, group: 'Insights' },
  { id: 'settings', label: 'Inventory settings', icon: Settings2, group: 'Insights' },
]

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'The request could not be completed. Check your connection and try again.'
}

function fields(event: FormEvent<HTMLFormElement>): Record<string, string> {
  return Object.fromEntries(new FormData(event.currentTarget).entries()) as Record<string, string>
}

function Panel({
  title,
  detail,
  children,
  action,
}: {
  title: string
  detail?: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <section className="workspace-panel">
      <div className="panel-heading">
        <div>
          <h2>{title}</h2>
          {detail && <p>{detail}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  )
}

function DataTable({
  headers,
  rows,
  empty,
}: {
  headers: string[]
  rows: ReactNode[][]
  empty: string
}) {
  if (rows.length === 0) {
    return <p className="workspace-empty">{empty}</p>
  }
  return (
    <div className="table-scroll">
      <table className="workspace-table">
        <thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TextField({
  label,
  name,
  required = false,
  type = 'text',
  defaultValue,
  placeholder,
  min,
  step,
}: {
  label: string
  name: string
  required?: boolean
  type?: string
  defaultValue?: string | number
  placeholder?: string
  min?: number
  step?: number | string
}) {
  return (
    <label className="workspace-field">
      <span>{label}{required && ' *'}</span>
      <input name={name} type={type} required={required} defaultValue={defaultValue} placeholder={placeholder} min={min} step={step} />
    </label>
  )
}

function SelectField({
  label,
  name,
  options,
  required = false,
  defaultValue,
}: {
  label: string
  name: string
  options: Array<{ value: string; label: string }>
  required?: boolean
  defaultValue?: string
}) {
  return (
    <label className="workspace-field">
      <span>{label}{required && ' *'}</span>
      <select name={name} required={required} defaultValue={defaultValue ?? options[0]?.value}>
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    </label>
  )
}

function TextAreaField({
  label,
  name,
  defaultValue,
  required,
  rows = 3,
}: {
  label: string
  name: string
  defaultValue?: string
  required?: boolean
  rows?: number
}) {
  return (
    <label className="workspace-field workspace-field--wide">
      <span>{label}{required && ' *'}</span>
      <textarea name={name} defaultValue={defaultValue} required={required} rows={rows} />
    </label>
  )
}

function CheckField({ label, name, defaultChecked = false }: {
  label: string
  name: string
  defaultChecked?: boolean
}) {
  return (
    <label className="workspace-check">
      <input name={name} type="checkbox" defaultChecked={defaultChecked} />
      <span>{label}</span>
    </label>
  )
}

export function WorkspacePage() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const callbackResult = new URLSearchParams(location.search).get('onedrive')
  const [businesses, setBusinesses] = useState<Business[]>([])
  const [businessId, setBusinessId] = useState('')
  const [view, setView] = useState<View>('overview')
  const [categories, setCategories] = useState<Category[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [locations, setLocations] = useState<InventoryLocation[]>([])
  const [stock, setStock] = useState<StockBalance[]>([])
  const [members, setMembers] = useState<BusinessMember[]>([])
  const [invitations, setInvitations] = useState<BusinessInvitation[]>([])
  const [movements, setMovements] = useState<InventoryMovement[]>([])
  const [attributes, setAttributes] = useState<AttributeDefinition[]>([])
  const [lowStock, setLowStock] = useState<LowStockAlert[]>([])
  const [expiry, setExpiry] = useState<ExpiryMonitoring | null>(null)
  const [summary, setSummary] = useState<InventorySummary | null>(null)
  const [report, setReport] = useState<InventoryReport | null>(null)
  const [reorderLevels, setReorderLevels] = useState<ReorderLevel[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(() => callbackResult === 'denied'
    ? 'Microsoft authorization was cancelled. No OneDrive connection was created.'
    : callbackResult && callbackResult !== 'connected'
      ? 'OneDrive could not be connected. Check the Microsoft app configuration and try again.'
      : '')
  const [notice, setNotice] = useState(() => callbackResult === 'connected'
    ? 'OneDrive is connected. The StockWise workbook is being prepared.'
    : '')
  const [selectedCategory, setSelectedCategory] = useState('')
  const [selectedProduct, setSelectedProduct] = useState('')
  const [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [editingLocation, setEditingLocation] = useState<InventoryLocation | null>(null)
  const [trendFrom, setTrendFrom] = useState(() => new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10))
  const [trendTo, setTrendTo] = useState(() => new Date().toISOString().slice(0, 10))
  const [trendPoints, setTrendPoints] = useState<Record<string, number>>({})
  const [nearExpiryDays, setNearExpiryDays] = useState(30)
  const [excelSync, setExcelSync] = useState<ExcelSyncStatus | null>(null)

  const selectedBusiness = businesses.find((business) => business.id === businessId) ?? null
  const canManageTeam = selectedBusiness?.role === 'OWNER'
  const canManageCatalog = selectedBusiness?.role === 'OWNER' || selectedBusiness?.role === 'ADMIN'
  const activeCategories = categories.filter((category) => category.active)
  const activeCategoryId = activeCategories.some((category) => category.id === selectedCategory)
    ? selectedCategory
    : activeCategories[0]?.id ?? ''
  const activeProductId = products.some((product) => product.id === selectedProduct)
    ? selectedProduct
    : products[0]?.id ?? ''
  const activeAttributes = attributes.filter((attribute) => attribute.categoryId === activeCategoryId)

  const initials = useMemo(() => user?.displayName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() ?? '', [user])
  const callbackBusinessId = useMemo(
    () => new URLSearchParams(location.search).get('businessId'),
    [location.search],
  )

  const loadBusinesses = useCallback(async () => {
    try {
      const results = await stockwiseApi.businesses()
      setBusinesses(results)
      setBusinessId((current) => results.some((business) => business.id === callbackBusinessId)
        ? callbackBusinessId ?? ''
        : results.some((business) => business.id === current)
          ? current
          : results[0]?.id ?? '')
      setError('')
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setLoading(false)
    }
  }, [callbackBusinessId])

  const loadWorkspace = useCallback(async (id: string) => {
    if (!id) {
      setCategories([])
      setProducts([])
      setLocations([])
      setStock([])
      setMembers([])
      setInvitations([])
      setMovements([])
      setAttributes([])
      setLowStock([])
      setExpiry(null)
      setSummary(null)
      setReport(null)
      setReorderLevels([])
      setExcelSync(null)
      return
    }
    setLoading(true)
    try {
      const [
        nextCategories,
        nextProducts,
        nextLocations,
        nextStock,
        nextMembers,
        nextInvitations,
        nextMovements,
        nextLowStock,
        nextExpiry,
        nextSummary,
        nextReport,
        nextReorderLevels,
      ] = await Promise.all([
        stockwiseApi.categories(id),
        stockwiseApi.products(id),
        stockwiseApi.locations(id),
        stockwiseApi.stock(id),
        businesses.find((business) => business.id === id)?.role === 'OWNER'
          ? stockwiseApi.members(id)
          : Promise.resolve([]),
        businesses.find((business) => business.id === id)?.role === 'OWNER'
          ? stockwiseApi.invitations(id)
          : Promise.resolve([]),
        stockwiseApi.movements(id),
        stockwiseApi.lowStock(id),
        stockwiseApi.expiry(id),
        stockwiseApi.summary(id),
        stockwiseApi.report(id),
        stockwiseApi.reorderLevels(id),
      ])
      setCategories(nextCategories)
      setProducts(nextProducts)
      setLocations(nextLocations)
      setStock(nextStock)
      setMembers(nextMembers)
      setInvitations(nextInvitations)
      setMovements(nextMovements.items)
      setLowStock(nextLowStock)
      setExpiry(nextExpiry)
      setSummary(nextSummary)
      setReport(nextReport)
      setReorderLevels(nextReorderLevels)
      setError('')
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setLoading(false)
    }
  }, [businesses])

  const refreshExcelSync = useCallback(async (id: string) => {
    try {
      setExcelSync(await stockwiseApi.excelSyncStatus(id))
    } catch (loadError) {
      setError(messageFor(loadError))
    }
  }, [])

  useEffect(() => {
    if (!businessId) return
    let active = true
    const refresh = async () => {
      try {
        const status = await stockwiseApi.excelSyncStatus(businessId)
        if (active) setExcelSync(status)
      } catch (loadError) {
        if (active) setError(messageFor(loadError))
      }
    }
    void refresh()
    const interval = window.setInterval(() => void refresh(), 8000)
    return () => {
      active = false
      window.clearInterval(interval)
    }
  }, [businessId])

  useEffect(() => {
    const query = new URLSearchParams(location.search)
    const result = query.get('onedrive')
    if (result) query.delete('onedrive')
    const shouldSelectBusiness = Boolean(
      callbackBusinessId
      && businesses.some((business) => business.id === callbackBusinessId),
    )
    if (shouldSelectBusiness) {
      query.delete('businessId')
    }
    if (result || shouldSelectBusiness) {
      navigate({ pathname: location.pathname, search: query.toString() ? `?${query}` : '' }, { replace: true })
    }
  }, [businesses, callbackBusinessId, location.pathname, location.search, navigate])

  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (active) void loadBusinesses()
    })
    return () => { active = false }
  }, [loadBusinesses])

  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (active) void loadWorkspace(businessId)
    })
    return () => { active = false }
  }, [businessId, loadWorkspace])

  useEffect(() => {
    if (!businessId || !activeCategoryId) {
      return
    }
    let active = true
    stockwiseApi.attributes(businessId, activeCategoryId)
      .then((definitions) => {
        if (active) setAttributes(definitions)
      })
      .catch((loadError: unknown) => {
        if (active) setError(messageFor(loadError))
      })
    return () => { active = false }
  }, [businessId, activeCategoryId])

  async function runAction(action: () => Promise<unknown>, success: string, reload = true): Promise<boolean> {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
      if (reload) {
        await loadBusinesses()
        await loadWorkspace(businessId)
      }
      return true
    } catch (actionError) {
      setError(messageFor(actionError))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function submit(
    event: FormEvent<HTMLFormElement>,
    action: (values: Record<string, string>) => Promise<unknown>,
    success: string,
    reload = true,
  ) {
    event.preventDefault()
    const form = event.currentTarget
    const succeeded = await runAction(() => action(fields(event)), success, reload)
    if (succeeded) form.reset()
  }

  async function signOutNow() {
    setBusy(true)
    setError('')
    try {
      await signOut()
      navigate('/login', { replace: true })
    } catch (signOutError) {
      setError(messageFor(signOutError))
    } finally {
      setBusy(false)
    }
  }

  async function updateAttributes() {
    if (!businessId || !activeCategoryId) return
    try {
      setAttributes(await stockwiseApi.attributes(businessId, activeCategoryId))
    } catch (loadError) {
      setError(messageFor(loadError))
    }
  }

  async function connectOneDrive() {
    if (!businessId) return
    setBusy(true)
    setError('')
    try {
      const { authorizationUrl } = await stockwiseApi.beginExcelSyncConnection(businessId)
      window.location.assign(authorizationUrl)
    } catch (actionError) {
      setError(messageFor(actionError))
      setBusy(false)
    }
  }

  async function disconnectOneDrive() {
    if (!businessId || !window.confirm('Disconnect OneDrive? StockWise will stop syncing until you reconnect.')) {
      return
    }
    await runAction(
      () => stockwiseApi.disconnectExcelSync(businessId),
      'OneDrive disconnected. Pending changes are retained.',
      false,
    )
    await refreshExcelSync(businessId)
  }

  async function retryExcelSync() {
    if (!businessId) return
    await runAction(
      () => stockwiseApi.retryExcelSync(businessId),
      'Synchronization has been queued.',
      false,
    )
    await refreshExcelSync(businessId)
  }

  async function requestExcelResync() {
    if (!businessId) return
    await runAction(
      () => stockwiseApi.resynchronizeExcel(businessId),
      'A full workbook synchronization has been queued.',
      false,
    )
    await refreshExcelSync(businessId)
  }

  async function loadTrends(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!businessId) return
    const values = fields(event)
    setTrendFrom(values.fromDate)
    setTrendTo(values.toDate)
    setBusy(true)
    setError('')
    try {
      const trends = await stockwiseApi.movementTrends(businessId, trendFrom, trendTo)
      setTrendPoints(Object.fromEntries(trends.points.map((point, index) => [
        `${point.movementDate} · ${point.movementType} · ${point.unitOfMeasure} · ${index + 1}`,
        point.quantityDelta,
      ])))
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setBusy(false)
    }
  }

  if (!user) return null

  const title = navigation.find((item) => item.id === view)?.label ?? 'Overview'

  return (
    <main className="workspace-shell">
      <aside className="workspace-sidebar">
        <Link className="workspace-brand" to="/" aria-label="StockWise home">
          <span className="brand-mark"><Boxes size={18} /></span>
          <span>StockWise</span>
        </Link>
        <div className="workspace-context">
          <span className="detail-label">BUSINESS SPACE</span>
          <label className="business-picker">
            <Building2 size={16} aria-hidden="true" />
            <select
              aria-label="Active business"
              value={businessId}
              onChange={(event) => setBusinessId(event.target.value)}
            >
              <option value="">Choose a business</option>
              {businesses.map((business) => <option value={business.id} key={business.id}>{business.name}</option>)}
            </select>
            <ChevronDown size={15} aria-hidden="true" />
          </label>
        </div>
        <nav className="workspace-navigation" aria-label="Main navigation">
          {[...new Set(navigation.map((item) => item.group))].map((group) => (
            <div className="navigation-group" key={group}>
              <span className="detail-label">{group}</span>
              {navigation.filter((item) => item.group === group).map(({ id, label, icon: Icon }) => (
                <button
                  type="button"
                  className={`navigation-link${view === id ? ' is-active' : ''}`}
                  key={id}
                  onClick={() => setView(id)}
                  disabled={!businessId && id !== 'business'}
                >
                  <Icon size={16} aria-hidden="true" />
                  <span>{label}</span>
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-user">
            <span className="avatar avatar--small">{initials}</span>
            <span><strong>{user.displayName}</strong><small>{user.email}</small></span>
          </div>
          <button type="button" className="sidebar-signout" onClick={() => void signOutNow()} disabled={busy}>
            <LogOut size={15} /> Sign out
          </button>
        </div>
      </aside>

      <section className="workspace-main">
        <header className="workspace-topbar">
          <div className="workspace-breadcrumb">
            <span>{selectedBusiness?.name ?? 'Your workspace'}</span>
            <span className="breadcrumb-divider">/</span>
            <strong>{title}</strong>
          </div>
          <div className="workspace-top-actions">
            {selectedBusiness && <span className="role-pill">{selectedBusiness.role}</span>}
            <button type="button" className="icon-button" aria-label="Refresh workspace" onClick={() => {
              void loadBusinesses()
              void loadWorkspace(businessId)
            }}>
              <RefreshCw size={16} />
            </button>
            <span className="avatar avatar--small">{initials}</span>
          </div>
        </header>

        <div className="workspace-content">
          {error && <div className="workspace-message workspace-message--error" role="alert">{error}</div>}
          {notice && <div className="workspace-message workspace-message--success" role="status">{notice}</div>}
          {loading && <div className="workspace-loading" role="status"><span className="loading-mark" />Loading business data…</div>}
          {!loading && !businessId && (
            <div className="workspace-welcome">
              <p className="eyebrow">PERSONAL ACCOUNT</p>
              <h1>Your space, <em>in order.</em></h1>
              <p>Welcome, {user.displayName}. Create a business to begin organizing products and stock.</p>
              <Panel title="Create your business" detail="Start with the business name. Invite teammates by email when you are ready.">
                <form className="workspace-form workspace-form--inline" onSubmit={(event) => {
                  void submit(event, (values) => stockwiseApi.createBusiness(values.name), 'Business created.')
                }}>
                  <TextField name="name" label="Business name" required placeholder="e.g. Northside Supply" />
                  <button className="primary-button" disabled={busy}><Plus size={16} /> Create business</button>
                </form>
              </Panel>
            </div>
          )}
          {selectedBusiness && !loading && (
            <>
              {view === 'overview' && (
                <section className="workspace-view">
                  <div className="view-intro">
                    <div>
                      <p className="eyebrow">INVENTORY, IN FOCUS</p>
                      <h1>Your space, <em>in order.</em></h1>
                      <p>A live view of stock across {selectedBusiness.name}. Quantities stay grouped by unit.</p>
                    </div>
                    <span className="live-status"><i /> Live data</span>
                  </div>
                  <div className="metric-grid">
                    <Metric label="Stocked products" value={summary?.stockedProductCount ?? 0} icon={<Package size={18} />} />
                    <Metric label="Active locations" value={summary?.activeLocationCount ?? 0} icon={<Warehouse size={18} />} />
                    <Metric label="Low-stock items" value={summary?.lowStockLocationProductCount ?? 0} icon={<CircleAlert size={18} />} tone="warning" />
                    <Metric label="Near expiry" value={summary?.upcomingExpiryLocationBatchCount ?? 0} icon={<Activity size={18} />} tone="warning" />
                  </div>
                  <div className="workspace-two-column">
                    <Panel title="On hand by unit" detail={`As of ${summary?.asOfDate ?? '—'}`}>
                      <div className="unit-summary">
                        {Object.entries(summary?.onHandByUnitOfMeasure ?? {}).length === 0
                          ? <p className="workspace-empty">No stock has been recorded yet.</p>
                          : Object.entries(summary?.onHandByUnitOfMeasure ?? {}).map(([unit, quantity]) => (
                            <div className="unit-row" key={unit}><span>{unit}</span><strong>{quantity.toLocaleString()}</strong></div>
                          ))}
                      </div>
                    </Panel>
                    <Panel title="Needs attention" detail="Current computed alerts">
                      <div className="attention-list">
                        <button type="button" onClick={() => setView('alerts')}><span>Low-stock locations</span><strong>{lowStock.length}</strong></button>
                        <button type="button" onClick={() => setView('alerts')}><span>Upcoming expiry batches</span><strong>{expiry?.upcoming.length ?? 0}</strong></button>
                        <button type="button" onClick={() => setView('alerts')}><span>Expired batches</span><strong>{expiry?.expired.length ?? 0}</strong></button>
                      </div>
                    </Panel>
                  </div>
                  <Panel title="Recently recorded movements" detail="Latest persisted inventory activity">
                    <MovementTable movements={movements.slice(0, 6)} />
                  </Panel>
                </section>
              )}

              {view === 'business' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">WORKSPACE</p><h1>Business & <em>team.</em></h1><p>Manage your business profile, members, and invitations.</p></div></div>
                  {canManageTeam ? <Panel title="Business profile">
                    <form className="workspace-form workspace-form--inline" onSubmit={(event) => {
                      void submit(event, (values) => stockwiseApi.updateBusiness(businessId, values.name), 'Business profile updated.')
                    }}>
                      <TextField name="name" label="Business name" required defaultValue={selectedBusiness.name} />
                      <button className="secondary-button" disabled={busy}>Save changes</button>
                    </form>
                  </Panel> : <Panel title="Business profile">
                    <p className="workspace-empty">{selectedBusiness.name} · You can view this business profile, but only the owner can change it.</p>
                  </Panel>}
                  {canManageTeam && (
                    <Panel title="Invite by email" detail="The recipient must authenticate as this email address and accept the invitation. Links expire after 7 days.">
                      <form className="workspace-form workspace-form--inline" onSubmit={(event) => {
                        void submit(event, (values) => stockwiseApi.inviteMember(businessId, values.email, values.role as 'ADMIN' | 'MEMBER'), 'Invitation email sent.')
                      }}>
                        <TextField name="email" label="Email address" required type="email" placeholder="teammate@company.com" />
                        <SelectField name="role" label="Role" options={[{ value: 'MEMBER', label: 'Member' }, { value: 'ADMIN', label: 'Admin' }]} />
                        <button className="primary-button" disabled={busy}><Plus size={16} /> Send invitation</button>
                      </form>
                    </Panel>
                  )}
                  {canManageTeam && <Panel title="Invitations" detail="Pending invitations and their current status">
                    <DataTable
                      headers={['Email', 'Role', 'Status', 'Expires', 'Actions']}
                      rows={invitations.map((invitation) => [
                        invitation.email,
                        invitation.role,
                        invitation.status,
                        new Date(invitation.expiresAt).toLocaleDateString(),
                        invitation.status === 'PENDING'
                          ? <span className="table-actions" key="actions">
                            <button className="text-button" type="button" disabled={busy} onClick={() => void runAction(
                              () => stockwiseApi.resendInvitation(businessId, invitation.id),
                              'Invitation email resent.',
                            )}>Resend</button>
                            <button className="text-button text-button--danger" type="button" disabled={busy} onClick={() => {
                              if (window.confirm(`Revoke the invitation for ${invitation.email}?`)) {
                                void runAction(
                                  () => stockwiseApi.revokeInvitation(businessId, invitation.id),
                                  'Invitation revoked.',
                                )
                              }
                            }}>Revoke</button>
                          </span>
                          : '—',
                      ])}
                      empty="No invitations have been sent."
                    />
                  </Panel>}
                  {canManageTeam && <Panel title="Members" detail={`${members.length} active member${members.length === 1 ? '' : 's'}`}>
                    <DataTable
                      headers={['Member', 'Email', 'Role', 'Member since', '']}
                      rows={members.map((member) => [
                        <strong key="name">{member.displayName}</strong>,
                        member.email,
                        canManageTeam && member.role !== 'OWNER'
                          ? <select aria-label={`Role for ${member.displayName}`} key="role" value={member.role} onChange={(event) => void runAction(
                            () => stockwiseApi.changeMemberRole(businessId, member.userId, event.target.value as BusinessRole),
                            'Member role updated.',
                          )}><option value="ADMIN">Admin</option><option value="MEMBER">Member</option></select>
                          : <span className="role-pill" key="role">{member.role}</span>,
                        new Date(member.memberSince).toLocaleDateString(),
                        canManageTeam && member.role !== 'OWNER'
                          ? <button className="text-button text-button--danger" key="remove" type="button" onClick={() => {
                            if (window.confirm(`Remove ${member.displayName} from this business?`)) {
                              void runAction(() => stockwiseApi.removeMember(businessId, member.userId), 'Member removed.')
                            }
                          }}>Remove</button>
                          : '—',
                      ])}
                      empty="No members are available."
                    />
                  </Panel>}
                </section>
              )}

              {view === 'catalog' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">PRODUCT CATALOG</p><h1>Products & <em>categories.</em></h1><p>Track product identifiers, units, category defaults, and approved tracking overrides.</p></div></div>
                  {canManageCatalog && (
                    <Panel title={editingCategory ? 'Edit category' : 'Add a category'}>
                      <form className="workspace-form" key={editingCategory?.id ?? 'new-category'} onSubmit={(event) => {
                        void submit(event, (values) => {
                          const input = {
                            name: values.name,
                            description: values.description,
                            batchTrackingDefault: values.batchTrackingDefault === 'on',
                            expiryTrackingDefault: values.expiryTrackingDefault === 'on',
                          }
                          return editingCategory
                            ? stockwiseApi.updateCategory(businessId, editingCategory.id, input)
                            : stockwiseApi.createCategory(businessId, input)
                        }, editingCategory ? 'Category updated.' : 'Category created.')
                        setEditingCategory(null)
                      }}>
                        <div className="workspace-form-grid">
                          <TextField name="name" label="Category name" required defaultValue={editingCategory?.name} />
                          <TextField name="description" label="Description" defaultValue={editingCategory?.description ?? ''} />
                          <CheckField name="batchTrackingDefault" label="Batch tracking default" defaultChecked={editingCategory?.batchTrackingDefault} />
                          <CheckField name="expiryTrackingDefault" label="Expiry tracking default" defaultChecked={editingCategory?.expiryTrackingDefault} />
                        </div>
                        <div className="form-actions"><button className="primary-button" disabled={busy}>{editingCategory ? 'Save category' : <><Plus size={16} /> Create category</>}</button>{editingCategory && <button className="text-button" type="button" onClick={() => setEditingCategory(null)}>Cancel edit</button>}</div>
                      </form>
                    </Panel>
                  )}
                  <Panel title="Categories">
                    <DataTable headers={['Name', 'Tracking defaults', 'Status', 'Actions']} rows={categories.map((category) => [
                      <strong key="name">{category.name}</strong>,
                      `${category.batchTrackingDefault ? 'Batch' : 'No batch'} · ${category.expiryTrackingDefault ? 'Expiry' : 'No expiry'}`,
                      category.active ? 'Active' : 'Archived',
                      canManageCatalog ? <span className="table-actions" key="actions">
                        <button className="text-button" type="button" onClick={() => { setEditingCategory(category); setSelectedCategory(category.id) }}>Edit</button>
                        {category.active && <>
                          <button
                            className="text-button text-button--danger"
                            type="button"
                            onClick={() => {
                              if (window.confirm(`Archive “${category.name}”? Existing products will keep this category, but it will no longer be available for new products.`)) {
                                void runAction(
                                  () => stockwiseApi.archiveCategory(businessId, category.id),
                                  'Category archived.',
                                )
                              }
                            }}
                          >
                            Archive
                          </button>
                          <button
                            className="text-button text-button--danger"
                            type="button"
                            onClick={() => {
                              if (window.confirm(`Permanently delete “${category.name}”? This is only possible when no products reference it.`)) {
                                void runAction(
                                  () => stockwiseApi.deleteCategory(businessId, category.id),
                                  'Category deleted.',
                                )
                              }
                            }}
                          >
                            Delete
                          </button>
                        </>}
                      </span> : '—',
                    ])} empty="Create a category before adding products." />
                  </Panel>
                  {canManageCatalog && activeCategories.length > 0 && (
                    <Panel title={editingProduct ? 'Edit product' : 'Add a product'} detail="Attribute values must be valid JSON keyed by the category attribute IDs. The backend validates every value against its definition.">
                      <form className="workspace-form" key={editingProduct?.id ?? 'new-product'} onSubmit={(event) => {
                        void submit(event, (values) => {
                          let productAttributes: Record<string, unknown>
                          try {
                            productAttributes = JSON.parse(values.attributes || '{}') as Record<string, unknown>
                          } catch {
                            throw new Error('Attribute values must be valid JSON.')
                          }
                          const input = {
                            categoryId: values.categoryId,
                            name: values.name,
                            sku: values.sku || null,
                            unitOfMeasure: values.unitOfMeasure,
                            description: values.description,
                            batchTrackingOverride: values.batchTrackingOverride === '' ? null : values.batchTrackingOverride === 'true',
                            expiryTrackingOverride: values.expiryTrackingOverride === '' ? null : values.expiryTrackingOverride === 'true',
                            attributes: productAttributes,
                          }
                          return editingProduct
                            ? stockwiseApi.updateProduct(businessId, editingProduct.id, input)
                            : stockwiseApi.createProduct(businessId, input)
                        }, editingProduct ? 'Product updated.' : 'Product created.')
                        setEditingProduct(null)
                      }}>
                        <div className="workspace-form-grid">
                          <SelectField label="Category" name="categoryId" required defaultValue={editingProduct?.categoryId ?? categories[0]?.id} options={categories.filter((category) => category.active).map((category) => ({ value: category.id, label: category.name }))} />
                          <TextField name="name" label="Product name" required defaultValue={editingProduct?.name} />
                          <TextField name="sku" label="SKU" defaultValue={editingProduct?.sku ?? ''} />
                          <TextField name="unitOfMeasure" label="Unit of measure" required defaultValue={editingProduct?.unitOfMeasure} placeholder="e.g. each, kg, L" />
                          <SelectField label="Batch tracking" name="batchTrackingOverride" defaultValue={editingProduct?.batchTrackingOverride == null ? '' : String(editingProduct.batchTrackingOverride)} options={[{ value: '', label: 'Inherit category' }, { value: 'true', label: 'Enabled' }, { value: 'false', label: 'Disabled' }]} />
                          <SelectField label="Expiry tracking" name="expiryTrackingOverride" defaultValue={editingProduct?.expiryTrackingOverride == null ? '' : String(editingProduct.expiryTrackingOverride)} options={[{ value: '', label: 'Inherit category' }, { value: 'true', label: 'Enabled' }, { value: 'false', label: 'Disabled' }]} />
                          <TextAreaField name="description" label="Description" defaultValue={editingProduct?.description ?? ''} />
                          <TextAreaField name="attributes" label="Attribute values (JSON)" defaultValue={JSON.stringify(editingProduct?.attributes ?? {}, null, 2)} />
                        </div>
                        <div className="form-actions"><button className="primary-button" disabled={busy}>{editingProduct ? 'Save product' : <><Plus size={16} /> Create product</>}</button>{editingProduct && <button className="text-button" type="button" onClick={() => setEditingProduct(null)}>Cancel edit</button>}</div>
                      </form>
                    </Panel>
                  )}
                  <Panel title="Products">
                    <DataTable headers={['Product', 'SKU', 'Category', 'Unit', 'Tracking', 'Actions']} rows={products.map((product) => [
                      <strong key="name">{product.name}</strong>,
                      product.sku || '—',
                      product.categoryName,
                      product.unitOfMeasure,
                      `${product.batchTracked ? 'Batch' : 'No batch'} · ${product.expiryTracked ? 'Expiry' : 'No expiry'}`,
                      canManageCatalog ? <span className="table-actions" key="actions">
                        <button className="text-button" type="button" onClick={() => setEditingProduct(product)}>Edit</button>
                        {product.active && (
                          <button
                            className="text-button text-button--danger"
                            type="button"
                            onClick={() => {
                              if (window.confirm(`Archive “${product.name}”? Its history will be preserved.`)) {
                                void runAction(
                                  () => stockwiseApi.archiveProduct(businessId, product.id),
                                  'Product archived.',
                                )
                              }
                            }}
                          >
                            Archive
                          </button>
                        )}
                      </span> : '—',
                    ])} empty="No products yet." />
                  </Panel>
                </section>
              )}

              {view === 'attributes' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">CATEGORY-SPECIFIC FIELDS</p><h1>Product <em>attributes.</em></h1><p>Define structured fields and options. The API enforces required values, types, and constraints.</p></div></div>
                  <Panel title="Choose a category">
                    <label className="workspace-field workspace-field--compact"><span>Category</span><select value={activeCategoryId} onChange={(event) => setSelectedCategory(event.target.value)}><option value="">Select category</option>{activeCategories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
                  </Panel>
                  {canManageCatalog && activeCategoryId && <Panel title="Add an attribute" detail="Use JSON for type-specific bounds, and one option label per line for select fields.">
                    <form className="workspace-form" onSubmit={(event) => {
                      void submit(event, async (values) => {
                        let constraints: Record<string, unknown>
                        try {
                          constraints = JSON.parse(values.constraints || '{}') as Record<string, unknown>
                        } catch {
                          throw new Error('Constraints must be a valid JSON object.')
                        }
                        const options = values.options.split(/\r?\n/).map((label) => label.trim()).filter(Boolean).map((label, position) => ({ label, active: true, position }))
                        const created = await stockwiseApi.createAttribute(businessId, activeCategoryId, {
                          name: values.name,
                          type: values.type,
                          required: values.required === 'on',
                          description: values.description,
                          constraints,
                          options,
                        })
                        await updateAttributes()
                        return created
                      }, 'Attribute definition created.', false)
                    }}>
                      <div className="workspace-form-grid">
                        <TextField name="name" label="Attribute name" required />
                        <SelectField name="type" label="Value type" options={['TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'SINGLE_SELECT', 'MULTI_SELECT'].map((value) => ({ value, label: value.replace('_', ' ') }))} />
                        <CheckField name="required" label="Required on product" />
                        <TextField name="description" label="Description" />
                        <TextAreaField name="constraints" label="Constraints (JSON object)" defaultValue="{}" />
                        <TextAreaField name="options" label="Select options (one per line)" />
                      </div>
                      <div className="form-actions"><button className="primary-button" disabled={busy}><Plus size={16} /> Add attribute</button></div>
                    </form>
                  </Panel>}
                  <Panel title="Definitions" detail={`${activeAttributes.length} for this category`}>
                    <DataTable headers={['Name', 'Type', 'Required', 'Options', 'Actions']} rows={activeAttributes.map((attribute) => [
                      <strong key="name">{attribute.name}</strong>,
                      attribute.type,
                      attribute.required ? 'Yes' : 'No',
                      <div className="option-list" key="options">
                        {attribute.options.map((option) => <span className="option-chip" key={option.id}>
                          {option.label}
                          {canManageCatalog && <>
                            <button type="button" className="text-button" aria-label={`Edit ${option.label}`} onClick={() => {
                              const label = window.prompt('Option label', option.label)
                              if (label?.trim()) void runAction(() => stockwiseApi.updateAttributeOption(businessId, activeCategoryId, attribute.id, option.id, {
                                label: label.trim(), active: option.active, position: option.position,
                              }), 'Attribute option updated.', false)
                            }}>Edit</button>
                            <button type="button" className="text-button text-button--danger" aria-label={`Remove ${option.label}`} onClick={() => {
                              if (window.confirm(`Remove option '${option.label}'?`)) {
                                void runAction(() => stockwiseApi.deleteAttributeOption(businessId, activeCategoryId, attribute.id, option.id), 'Attribute option removed.', false)
                              }
                            }}>Remove</button>
                          </>}
                        </span>)}
                      </div>,
                      canManageCatalog ? <span className="table-actions" key="actions">
                        <button className="text-button" type="button" onClick={() => {
                          const updatedName = window.prompt('New display name', attribute.name)
                          if (updatedName?.trim()) void runAction(() => stockwiseApi.updateAttribute(businessId, activeCategoryId, attribute.id, {
                            name: updatedName.trim(), type: attribute.type, required: attribute.required,
                            description: attribute.description, constraints: attribute.constraints,
                          }), 'Attribute updated.', false)
                        }}>Rename</button>
                        {attribute.options.length > 0 && <button className="text-button" type="button" onClick={() => {
                          const label = window.prompt('New option label')
                          if (label?.trim()) void runAction(() => stockwiseApi.addAttributeOption(businessId, activeCategoryId, attribute.id, {
                            label: label.trim(), active: true, position: attribute.options.length,
                          }), 'Option added.', false)
                        }}>Add option</button>}
                        {attribute.options.length === 0 && <button className="text-button" type="button" onClick={() => {
                          const label = window.prompt('Option label')
                          if (label?.trim()) void runAction(() => stockwiseApi.addAttributeOption(businessId, activeCategoryId, attribute.id, {
                            label: label.trim(), active: true, position: 0,
                          }), 'Option added.', false)
                        }}>Add option</button>}
                        <button className="text-button text-button--danger" type="button" onClick={() => void runAction(() => stockwiseApi.archiveAttribute(businessId, activeCategoryId, attribute.id), 'Attribute archived.', false)}>Archive</button>
                      </span> : '—',
                    ])} empty="No attribute definitions for this category." />
                  </Panel>
                </section>
              )}

              {view === 'locations' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">STORAGE NETWORK</p><h1>Inventory <em>locations.</em></h1><p>Organize balances by business location. Transfers stay within this business.</p></div></div>
                  {canManageCatalog && <Panel title={editingLocation ? 'Edit location' : 'Add a location'}>
                    <form className="workspace-form workspace-form--inline" key={editingLocation?.id ?? 'new-location'} onSubmit={(event) => {
                      void submit(event, (values) => editingLocation
                        ? stockwiseApi.updateLocation(businessId, editingLocation.id, { name: values.name, description: values.description })
                        : stockwiseApi.createLocation(businessId, { name: values.name, description: values.description }),
                      editingLocation ? 'Location updated.' : 'Location created.')
                      setEditingLocation(null)
                    }}>
                      <TextField name="name" label="Location name" required defaultValue={editingLocation?.name} />
                      <TextField name="description" label="Description" defaultValue={editingLocation?.description ?? ''} />
                      <button className="primary-button" disabled={busy}>{editingLocation ? 'Save changes' : <><Plus size={16} /> Add location</>}</button>
                      {editingLocation && <button type="button" className="text-button" onClick={() => setEditingLocation(null)}>Cancel</button>}
                    </form>
                  </Panel>}
                  <Panel title="Locations">
                    <DataTable headers={['Location', 'Description', 'Status', 'Actions']} rows={locations.map((location) => [
                      <strong key="name">{location.name}</strong>,
                      location.description || '—',
                      location.active ? 'Active' : 'Archived',
                      canManageCatalog ? <span className="table-actions" key="actions"><button type="button" className="text-button" onClick={() => setEditingLocation(location)}>Edit</button>{location.active && <button type="button" className="text-button text-button--danger" onClick={() => void runAction(() => stockwiseApi.archiveLocation(businessId, location.id), 'Location archived.')}>Archive</button>}</span> : '—',
                    ])} empty="Add a location to start tracking stock." />
                  </Panel>
                </section>
              )}

              {view === 'stock' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">CURRENT BALANCES</p><h1>Stock <em>overview.</em></h1><p>Persisted quantities by product, location, and batch. No valuation is calculated.</p></div></div>
                  <Panel title="On-hand stock" detail={`${stock.length} balance records`}>
                    <DataTable headers={['Product', 'SKU', 'Location', 'Batch', 'Expiry', 'On hand']} rows={stock.map((balance) => [
                      <strong key="product">{balance.productName}</strong>,
                      balance.sku || '—',
                      balance.locationName,
                      balance.batchCode || 'Unbatched',
                      balance.expiryDate || '—',
                      `${Number(balance.quantity).toLocaleString()} ${balance.unitOfMeasure}`,
                    ])} empty="No stock has been received." />
                  </Panel>
                  <Panel title="Batch register" detail="Select a batch-tracked product to view its recorded batches">
                    <div className="workspace-toolbar"><label className="workspace-field workspace-field--compact"><span>Product</span><select value={activeProductId} onChange={(event) => setSelectedProduct(event.target.value)}><option value="">Select product</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label></div>
                    {activeProductId && <BatchList businessId={businessId} productId={activeProductId} />}
                  </Panel>
                </section>
              )}

              {view === 'operations' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">STOCK OPERATIONS</p><h1>Receive & <em>move stock.</em></h1><p>Operations are atomic and idempotent. Batch selection is explicit for tracked products.</p></div></div>
                  <div className="workspace-two-column">
                    <Panel title="Receive stock" detail="Enter batch and expiry when required by the product tracking settings.">
                      <StockOperationForm products={products} locations={locations} submitLabel="Record receipt" onSubmit={(input, key) => {
                        return runAction(() => stockwiseApi.receiveStock(businessId, key, input), 'Receipt recorded.')
                      }} mode="receipt" />
                    </Panel>
                    <Panel title="Adjust stock" detail="Adjustment reason is recorded in immutable movement history.">
                      <StockOperationForm products={products} locations={locations} submitLabel="Record adjustment" onSubmit={(input, key) => {
                        return runAction(() => stockwiseApi.adjustStock(businessId, key, input), 'Adjustment recorded.')
                      }} mode="adjustment" />
                    </Panel>
                  </div>
                  <Panel title="Transfer stock" detail="Transfers must use two active locations in this business. Batch-tracked products require a batch code.">
                    <StockOperationForm products={products} locations={locations} submitLabel="Transfer stock" onSubmit={(input, key) => {
                      return runAction(() => stockwiseApi.transferStock(businessId, key, input), 'Transfer completed.')
                    }} mode="transfer" />
                  </Panel>
                </section>
              )}

              {view === 'movements' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">AUDIT TRAIL</p><h1>Movement <em>history.</em></h1><p>Immutable receipt, adjustment, and transfer records for this business.</p></div></div>
                  <Panel title="Inventory movements" detail="Most recent 100 records">
                    <MovementTable movements={movements} />
                  </Panel>
                </section>
              )}

              {view === 'alerts' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">LIVE MONITORING</p><h1>Alerts & <em>expiry.</em></h1><p>Computed from current balances and configured reorder levels.</p></div></div>
                  <Panel title="Low stock" detail="Alerts are location-specific and disappear automatically when stock recovers.">
                    <DataTable headers={['Product', 'Location', 'Available', 'Reorder at', 'Shortfall']} rows={lowStock.map((alert) => [
                      <strong key="product">{alert.productName}</strong>,
                      alert.locationName,
                      `${alert.availableQuantity} ${alert.unitOfMeasure}`,
                      alert.reorderLevel,
                      <span className="quantity-alert" key="shortfall">{alert.shortfallToReorderLevel} {alert.unitOfMeasure}</span>,
                    ])} empty="No products are at or below their reorder levels." />
                  </Panel>
                  <Panel title="Near expiry" detail={`Window: today through ${expiry?.nearExpiryDays ?? 30} days inclusive`}>
                    <ExpiryTable items={expiry?.upcoming ?? []} empty="No batches are approaching expiry in this window." />
                  </Panel>
                  <Panel title="Expired stock" detail={`As of ${expiry?.asOfDate ?? '—'}`}>
                    <ExpiryTable items={expiry?.expired ?? []} empty="No expired stock is recorded." />
                  </Panel>
                </section>
              )}

              {view === 'analytics' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">BUSINESS INTELLIGENCE</p><h1>Analytics & <em>reports.</em></h1><p>Summaries use persisted quantities. Incompatible units are not combined.</p></div></div>
                  <div className="metric-grid">
                    <Metric label="Products with stock" value={summary?.stockedProductCount ?? 0} icon={<Package size={18} />} />
                    <Metric label="Low-stock locations" value={summary?.lowStockLocationProductCount ?? 0} icon={<CircleAlert size={18} />} tone="warning" />
                    <Metric label="Upcoming expiry batches" value={summary?.upcomingExpiryLocationBatchCount ?? 0} icon={<Activity size={18} />} tone="warning" />
                    <Metric label="Expired batches" value={summary?.expiredLocationBatchCount ?? 0} icon={<Archive size={18} />} tone="warning" />
                  </div>
                  <Panel title="Movement trends" detail="Groupings retain movement type and unit of measure.">
                    <form className="workspace-form workspace-form--inline" onSubmit={(event) => void loadTrends(event)}>
                      <TextField name="fromDate" label="From" type="date" required defaultValue={trendFrom} />
                      <TextField name="toDate" label="To" type="date" required defaultValue={trendTo} />
                      <button className="secondary-button" disabled={busy}>Load trends</button>
                    </form>
                    <DataTable headers={['Date · type · unit', 'Quantity delta']} rows={Object.entries(trendPoints).map(([label, amount]) => [label, String(amount)])} empty="Choose a date range to view movement trends." />
                  </Panel>
                  <Panel title="Inventory report" detail={`Generated ${report ? new Date(report.generatedAt).toLocaleString() : '—'} · ${report?.totalElements ?? 0} records`}>
                    <div className="unit-summary report-totals">{Object.entries(report?.onHandByUnitOfMeasure ?? {}).map(([unit, quantity]) => <div className="unit-row" key={unit}><span>On hand · {unit}</span><strong>{quantity.toLocaleString()}</strong></div>)}</div>
                    <DataTable headers={['Product', 'Location', 'Batch', 'Expiry', 'Quantity']} rows={(report?.items ?? []).map((item) => [
                      item.productName, item.locationName, item.batchCode || 'Unbatched', item.expiryDate || '—', `${item.quantity} ${item.unitOfMeasure}`,
                    ])} empty="No report rows are available." />
                  </Panel>
                </section>
              )}

              {view === 'settings' && (
                <section className="workspace-view">
                  <div className="view-intro"><div><p className="eyebrow">BUSINESS PREFERENCES</p><h1>Inventory <em>settings.</em></h1><p>Configure per-location reorder levels and near-expiry window.</p></div></div>
                  <Panel title="OneDrive workbook" detail="A private, business-specific Excel workbook follows StockWise changes. StockWise remains the source of truth.">
                    <div className="excel-sync-card" aria-live="polite">
                      <div className="excel-sync-card__identity">
                        <span className={`excel-sync-icon excel-sync-icon--${(excelSync?.status ?? 'unknown').toLowerCase()}`}>
                          {excelSync?.status === 'DISCONNECTED' || !excelSync
                            ? <CloudOff size={19} />
                            : <Cloud size={19} />}
                        </span>
                        <div>
                          <strong>{excelSync?.status === 'PROCESSING' ? 'Synchronizing workbook' : excelSync?.status === 'CONNECTED' ? 'OneDrive connected' : excelSync?.status === 'REAUTH_REQUIRED' ? 'Reconnect required' : excelSync?.status === 'FAILED' ? 'Synchronization failed' : excelSync?.status === 'DISCONNECTED' ? 'OneDrive disconnected' : 'Checking connection'}</strong>
                          <span>{excelSync?.connectedAccount || 'No Microsoft account connected'}</span>
                        </div>
                      </div>
                      <div className="excel-sync-card__stats">
                        <span><small>Last successful sync</small><strong>{excelSync?.lastSuccessfulSync ? new Date(excelSync.lastSuccessfulSync).toLocaleString() : 'Not synchronized yet'}</strong></span>
                        <span><small>Pending changes</small><strong>{excelSync?.pendingChanges ?? '—'}</strong></span>
                        <span><small>Failed changes</small><strong>{excelSync?.failedChanges ?? '—'}</strong></span>
                      </div>
                      {excelSync?.lastError && <p className="excel-sync-error" role="alert">{excelSync.lastError}</p>}
                      <div className="form-actions">
                        {canManageTeam && (excelSync?.status === 'DISCONNECTED' || excelSync?.status === 'REAUTH_REQUIRED' || !excelSync) && (
                          <button type="button" className="primary-button" onClick={() => void connectOneDrive()} disabled={busy}>
                            <Cloud size={15} /> {excelSync?.status === 'REAUTH_REQUIRED' ? 'Reconnect OneDrive' : 'Connect OneDrive'}
                          </button>
                        )}
                        {canManageTeam && excelSync?.status !== 'DISCONNECTED' && excelSync && (
                          <button type="button" className="text-button text-button--danger" onClick={() => void disconnectOneDrive()} disabled={busy}>
                            Disconnect
                          </button>
                        )}
                        {canManageTeam && excelSync?.status === 'FAILED' && (
                          <button type="button" className="secondary-button" onClick={() => void retryExcelSync()} disabled={busy}>
                            <RefreshCw size={15} /> Retry failed sync
                          </button>
                        )}
                        {canManageTeam && ['CONNECTED', 'PROCESSING', 'FAILED'].includes(excelSync?.status ?? '') && (
                          <button type="button" className="secondary-button" onClick={() => void requestExcelResync()} disabled={busy}>
                            <RefreshCw size={15} /> Sync now
                          </button>
                        )}
                      </div>
                      {!canManageTeam && <p className="form-hint">Only the business owner can connect, disconnect, or retry OneDrive synchronization.</p>}
                    </div>
                  </Panel>
                  {canManageCatalog && <Panel title="Reorder level" detail="A separate threshold applies to each product at each location.">
                    <form className="workspace-form workspace-form--inline" onSubmit={(event) => {
                      void submit(event, (values) => stockwiseApi.saveReorderLevel(businessId, {
                        productId: values.productId, locationId: values.locationId, reorderLevel: Number(values.reorderLevel),
                      }), 'Reorder level saved.')
                    }}>
                      <SelectField name="productId" label="Product" required options={products.map((product) => ({ value: product.id, label: product.name }))} />
                      <SelectField name="locationId" label="Location" required options={locations.map((location) => ({ value: location.id, label: location.name }))} />
                      <TextField name="reorderLevel" label="Threshold" type="number" required min={0} step="0.001" defaultValue={0} />
                      <button className="primary-button" disabled={busy}>Save threshold</button>
                    </form>
                    <DataTable headers={['Product', 'Location', 'Threshold', '']} rows={reorderLevels.map((level) => [
                      level.productName, level.locationName, `${level.reorderLevel} ${level.unitOfMeasure}`,
                      canManageCatalog ? <button className="text-button text-button--danger" type="button" key="remove" onClick={() => void runAction(() => stockwiseApi.deleteReorderLevel(businessId, level.productId, level.locationId), 'Reorder level removed.')}>Remove</button> : '—',
                    ])} empty="No reorder levels configured." />
                  </Panel>}
                  <NearExpirySettings businessId={businessId} value={nearExpiryDays} onChange={setNearExpiryDays} canEdit={canManageCatalog} />
                </section>
              )}
            </>
          )}
        </div>
      </section>
    </main>
  )
}

function Metric({ label, value, icon, tone }: {
  label: string
  value: number
  icon: ReactNode
  tone?: 'warning'
}) {
  return <article className={`metric-card${tone ? ' metric-card--warning' : ''}`}><span className="metric-icon">{icon}</span><strong>{value.toLocaleString()}</strong><span>{label}</span></article>
}

function MovementTable({ movements }: { movements: InventoryMovement[] }) {
  return <DataTable headers={['Date', 'Operation', 'Product', 'Location', 'Batch', 'Change', 'After', 'Reason']} rows={movements.map((movement) => [
    new Date(movement.createdAt).toLocaleString(),
    movement.movementType,
    movement.productName,
    movement.locationName,
    movement.batchCode || '—',
    String(movement.quantityDelta),
    String(movement.quantityAfter),
    movement.reason || '—',
  ])} empty="No inventory movements have been recorded." />
}

function ExpiryTable({ items, empty }: {
  items: NonNullable<ExpiryMonitoring['upcoming']>
  empty: string
}) {
  return <DataTable headers={['Product', 'Location', 'Batch', 'Expiry date', 'Days', 'Quantity']} rows={items.map((item) => [
    <strong key="product">{item.productName}</strong>,
    item.locationName,
    item.batchCode,
    item.expiryDate,
    item.daysUntilExpiry < 0 ? `${Math.abs(item.daysUntilExpiry)} days overdue` : `${item.daysUntilExpiry} days`,
    `${item.quantity} ${item.unitOfMeasure}`,
  ])} empty={empty} />
}

function StockOperationForm({
  products,
  locations,
  submitLabel,
  onSubmit,
  mode,
}: {
  products: Product[]
  locations: InventoryLocation[]
  submitLabel: string
  onSubmit: (input: Record<string, unknown>, idempotencyKey: string) => Promise<unknown>
  mode: 'receipt' | 'adjustment' | 'transfer'
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const activeProducts = products.filter((product) => product.active)
  const activeLocations = locations.filter((location) => location.active)
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    const values = fields(event)
    const input: Record<string, unknown> = {
      productId: values.productId,
      quantity: Number(values.quantity),
      batchCode: values.batchCode || null,
      expiryDate: values.expiryDate || null,
    }
    if (mode === 'transfer') {
      input.sourceLocationId = values.sourceLocationId
      input.destinationLocationId = values.destinationLocationId
      input.reason = values.reason || null
      delete input.expiryDate
    } else {
      input.locationId = values.locationId
    }
    if (mode === 'adjustment') {
      input.direction = values.direction
      input.reason = values.reason
    }
    try {
      const succeeded = await onSubmit(input, idempotencyKey)
      if (succeeded !== false) {
        event.currentTarget.reset()
        setIdempotencyKey(crypto.randomUUID())
      }
    } catch (formError) {
      setError(messageFor(formError))
    } finally {
      setBusy(false)
    }
  }
  return (
    <form className="workspace-form" onSubmit={(event) => void handleSubmit(event)}>
      {error && <div className="workspace-message workspace-message--error" role="alert">{error}</div>}
      <div className="workspace-form-grid">
        <SelectField name="productId" label="Product" required options={activeProducts.map((product) => ({ value: product.id, label: `${product.name}${product.batchTracked ? ' · batch tracked' : ''}` }))} />
        {mode === 'transfer' ? <>
          <SelectField name="sourceLocationId" label="From location" required options={activeLocations.map((location) => ({ value: location.id, label: location.name }))} />
          <SelectField name="destinationLocationId" label="To location" required options={activeLocations.map((location) => ({ value: location.id, label: location.name }))} />
        </> : <SelectField name="locationId" label="Location" required options={activeLocations.map((location) => ({ value: location.id, label: location.name }))} />}
        <TextField name="quantity" label="Quantity" type="number" required min={0.001} step="0.001" placeholder="0.000" />
        <TextField name="batchCode" label="Batch code (when required)" />
        {mode !== 'transfer' && <TextField name="expiryDate" label="Expiry date (when required)" type="date" />}
        {mode === 'adjustment' && <SelectField name="direction" label="Adjustment" options={[{ value: 'INCREASE', label: 'Increase' }, { value: 'DECREASE', label: 'Decrease' }]} />}
        {mode !== 'receipt' && <TextAreaField name="reason" label="Reason" required={mode === 'adjustment'} />}
      </div>
      <p className="form-hint"><ShieldCheck size={14} /> The backend validates tracking rules, quantity precision, availability, and business authorization.</p>
      <div className="form-actions"><button className="primary-button" disabled={busy || !activeProducts.length || !activeLocations.length}>
        {mode === 'receipt' ? <ArrowDownToLine size={16} /> : <ArrowLeftRight size={16} />}{busy ? 'Saving…' : submitLabel}
      </button></div>
    </form>
  )
}

function BatchList({ businessId, productId }: { businessId: string; productId: string }) {
  const [batches, setBatches] = useState<Array<{ id: string; batchCode: string; expiryDate: string | null }>>([])
  const [error, setError] = useState('')
  useEffect(() => {
    stockwiseApi.batches(businessId, productId)
      .then(setBatches)
      .catch((loadError: unknown) => setError(messageFor(loadError)))
  }, [businessId, productId])
  if (error) return <p className="workspace-message workspace-message--error" role="alert">{error}</p>
  return <DataTable headers={['Batch code', 'Expiry date']} rows={batches.map((batch) => [batch.batchCode, batch.expiryDate || 'Not tracked'])} empty="No batches have been recorded for this product." />
}

function NearExpirySettings({
  businessId,
  value,
  onChange,
  canEdit,
}: {
  businessId: string
  value: number
  onChange: (value: number) => void
  canEdit: boolean
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState('')
  useEffect(() => {
    stockwiseApi.settings(businessId)
      .then((settings: { nearExpiryDays: number }) => onChange(settings.nearExpiryDays))
      .catch((loadError: unknown) => setError(messageFor(loadError)))
  }, [businessId, onChange])
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setSaved('')
    try {
      const response = await stockwiseApi.updateSettings(businessId, { nearExpiryDays: value })
      onChange(response.nearExpiryDays)
      setSaved('Expiry window updated.')
    } catch (saveError) {
      setError(messageFor(saveError))
    } finally {
      setBusy(false)
    }
  }
  return (
    <Panel title="Near-expiry window" detail="Business-level window, from 1 to 3650 days. Default is 30 days.">
      {error && <div className="workspace-message workspace-message--error" role="alert">{error}</div>}
      {saved && <div className="workspace-message workspace-message--success" role="status">{saved}</div>}
      {canEdit
        ? <form className="workspace-form workspace-form--inline" key={value} onSubmit={(event) => void save(event)}>
          <TextField name="nearExpiryDays" label="Days" type="number" required defaultValue={value} />
          <button className="secondary-button" disabled={busy}>Save window</button>
        </form>
        : <p className="workspace-empty">Current window: {value} days. Only Owners and Admins can change it.</p>}
    </Panel>
  )
}
