export type EconomicState = 'UNRESOLVED' | 'CALCULATED_COMPLETE' | 'CALCULATED_INCOMPLETE'

export interface EconomicResult {
  orderOccurredAt: string
  currency: string
  grossRevenue: string
  totalMarketplaceFees: string
  totalShipping: string
  totalAdvertising: string
  totalTaxes: string
  totalProductCost: string
  totalFinancialCost: string
  totalOtherAdjustments: string
  contribution: string
  contributionMargin?: string
  contributionMarginUndefinedReason?: string
  truthQuality: string
}

export interface SalesOrder {
  marketplaceOrderId: string
  sourceEvidenceVersion: string
  projectedAt: string
  assemblyPolicyVersion: string
  state: EconomicState
  unresolvedReasons?: string[]
  calculationPolicyVersion?: string
  economicResult?: EconomicResult
  missingComponentTypes?: string[]
  partialComponentTypes?: string[]
}

export interface SalesPage { orders: SalesOrder[]; nextCursor?: string }

export interface RefreshResult {
  status: 'COMPLETED'
  source: { status: string; invocations: number; committedPages: number; alreadyCommittedPages: number; records: number }
  occurrence: PromotionSummary
  revenue: PromotionSummary
  projection: { batches: number; processedChanges: number; drained: boolean }
}

export type ReconciliationCaseStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
export interface ExactMoney { currency: string; amount: string }
export interface ReconciliationStage { stage: string; expected?: ExactMoney; actual?: ExactMoney; signedDifference?: ExactMoney; absoluteDifference?: ExactMoney; tolerance: ExactMoney; expectedEntryIds: string[]; actualEntryIds: string[] }
export interface ReconciliationCase { caseId: string; marketplaceOrderId: string; financialTraceId: string; policyVersion: string; currency: string; status: ReconciliationCaseStatus; openedAt: string; lastObservedAt: string; resolvedAt?: string; revision: number; absoluteDifferenceSummary: ExactMoney; evidenceEntryIds: string[]; stages: ReconciliationStage[] }
export interface ReconciliationCasePage { cases: ReconciliationCase[]; nextCursor?: string }
export interface SystemicDivergenceSignal { signalId: string; stage: string; currency: string; policyVersion: string; window: string; firstSeenAt: string; lastSeenAt: string; occurrenceCount: number; absoluteDifference: ExactMoney; status: 'ACTIVE'; revision: number; caseIds: string[] }
export interface SystemicDivergenceSignalPage { signals: SystemicDivergenceSignal[]; nextCursor?: string }
export interface CommerceIdentityHealth { available?: boolean; mlTransactionsInspected?: number; omieTransactionsInspected?: number; exactConfirmed?: number; candidate?: number; ambiguous?: number; conflict?: number; unresolved?: number; coveragePercentage?: string; evaluationWindow?: string; policyVersion?: string; evaluatedAt?: string; topMatchReasons?: Record<string, number>; topGapReasons?: Record<string, number> }

export interface PromotionSummary {
  batches: number
  examined: number
  promoted: number
  duplicates: number
  identityConflicts: number
  evidenceConflicts: number
  drained: boolean
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

type RuntimeConfig = { apiBaseUrl?: string; serviceToken?: string; demoMode?: boolean }
// eslint-disable-next-line no-unused-vars
declare global { interface Window { __FLOOOW_CONFIG__?: RuntimeConfig } }

const config = (): RuntimeConfig => window.__FLOOOW_CONFIG__ ?? {}
export const demoModeEnabled = (): boolean => import.meta.env.VITE_DEMO_MODE === 'true' && config().demoMode !== false

const baseUrl = (): string => config().apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080'
const token = (): string | undefined => config().serviceToken

function problemMessage(status: number, body: unknown): string {
  if (status === 401) return 'Sessão não autorizada. Verifique o acesso do ambiente.'
  if (status === 404) return 'Pedido não encontrado na projeção atual.'
  if (status === 400) return 'A consulta não foi aceita. Atualize a sala e tente novamente.'
  if (status === 408 || status === 504) return 'O backend demorou além do limite. Tente novamente.'
  if (status >= 500) return 'O backend está indisponível ou falhou com segurança.'
  if (typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string') return body.detail
  return 'Não foi possível concluir a operação.'
}

export async function request<T>(path: string, init: globalThis.RequestInit = {}, signal?: AbortSignal): Promise<T> {
  const controller = new AbortController()
  if (signal) {
    if (signal.aborted) controller.abort()
    else signal.addEventListener('abort', () => controller.abort(), { once: true })
  }
  const timeout = window.setTimeout(() => controller.abort(), 12_000)
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  const bearer = token()
  if (bearer) headers.set('Authorization', `Bearer ${bearer}`)
  try {
    const response = await fetch(`${baseUrl().replace(/\/$/, '')}${path}`, { ...init, headers, signal: controller.signal })
    const raw = await response.text()
    let body: unknown = undefined
    if (raw) { try { body = JSON.parse(raw) as unknown } catch { throw new ApiError('Resposta inválida do backend.', response.status) } }
    if (!response.ok) throw new ApiError(problemMessage(response.status, body), response.status, typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string' ? body.code : undefined)
    return body as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError('Tempo limite de resposta excedido.', 408)
    throw new ApiError('Backend indisponível. Confira a conexão e tente novamente.', 0)
  } finally { window.clearTimeout(timeout) }
}

export const listOrders = (cursor?: string, signal?: AbortSignal) => request<SalesPage>(`/v1/sales-intelligence/orders?limit=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`, {}, signal)
export const getOrder = (id: string, signal?: AbortSignal) => request<SalesOrder>(`/v1/sales-intelligence/orders/${encodeURIComponent(id)}`, {}, signal)
export const refreshOrders = (signal?: AbortSignal) => request<RefreshResult>('/v1/sales-intelligence/refresh', { method: 'POST' }, signal)
export const listReconciliationCases = (cursor?: string, signal?: AbortSignal) => request<ReconciliationCasePage>(`/v1/reconciliation/cases?limit=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`, {}, signal)
export const getReconciliationCase = (id: string, signal?: AbortSignal) => request<ReconciliationCase>(`/v1/reconciliation/cases/${encodeURIComponent(id)}`, {}, signal)
export const listSystemicDivergences = (cursor?: string, signal?: AbortSignal) => request<SystemicDivergenceSignalPage>(`/v1/reconciliation/systemic-divergences?limit=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`, {}, signal)
export const getCommerceIdentityHealth = (signal?: AbortSignal) => request<CommerceIdentityHealth>('/v1/commerce-identity/health', {}, signal)
