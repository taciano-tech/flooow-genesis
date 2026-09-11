import type { RefreshResult, SalesOrder, SalesPage } from './api'

const order = (id: string, state: SalesOrder['state'], overrides: Partial<SalesOrder> = {}): SalesOrder => ({
  marketplaceOrderId: id,
  sourceEvidenceVersion: 'V023 · 2026-08-10',
  projectedAt: '2026-08-10T14:22:00Z',
  assemblyPolicyVersion: 'economic-assembly.v1',
  state,
  ...overrides,
})

export const demoOrders: SalesOrder[] = [
  order('a7b1d50a-4a47-4c6e-9ef6-000000000101', 'CALCULATED_COMPLETE', { economicResult: { orderOccurredAt: '2026-08-10T13:40:00Z', currency: 'BRL', grossRevenue: '289.90', totalMarketplaceFees: '34.78', totalShipping: '18.50', totalAdvertising: '0.00', totalTaxes: '20.29', totalProductCost: '96.00', totalFinancialCost: '3.20', totalOtherAdjustments: '0.00', contribution: '117.13', contributionMargin: '0.4039', truthQuality: 'COMPLETE' } }),
  order('a7b1d50a-4a47-4c6e-9ef6-000000000102', 'CALCULATED_INCOMPLETE', { missingComponentTypes: ['PRODUCT_COST'], partialComponentTypes: ['MARKETPLACE_FEE'] }),
  order('a7b1d50a-4a47-4c6e-9ef6-000000000103', 'UNRESOLVED', { unresolvedReasons: ['ORDER_IDENTITY_UNRESOLVED'] }),
]

export const demoPage: SalesPage = { orders: demoOrders }
export const demoRefresh: RefreshResult = {
  status: 'COMPLETED', source: { status: 'EXHAUSTED', invocations: 1, committedPages: 1, alreadyCommittedPages: 0, records: 3 },
  occurrence: { batches: 1, examined: 3, promoted: 3, duplicates: 0, identityConflicts: 0, evidenceConflicts: 0, drained: true },
  revenue: { batches: 1, examined: 3, promoted: 2, duplicates: 0, identityConflicts: 0, evidenceConflicts: 0, drained: true },
  projection: { batches: 1, processedChanges: 3, drained: true },
}
