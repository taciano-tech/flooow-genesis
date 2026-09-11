import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const complete = { marketplaceOrderId: '11111111-1111-4111-8111-111111111111', sourceEvidenceVersion: 'V023', projectedAt: '2026-01-01T00:00:00Z', assemblyPolicyVersion: 'v1', state: 'CALCULATED_COMPLETE', economicResult: { orderOccurredAt: '2026-01-01T00:00:00Z', currency: 'BRL', grossRevenue: '100.00', totalMarketplaceFees: '10.00', totalShipping: '0.00', totalAdvertising: '0.00', totalTaxes: '0.00', totalProductCost: '20.00', totalFinancialCost: '0.00', totalOtherAdjustments: '0.00', contribution: '70.00', contributionMargin: '0.7', truthQuality: 'COMPLETE' } }
const incomplete = { marketplaceOrderId: '22222222-2222-4222-8222-222222222222', sourceEvidenceVersion: 'V023', projectedAt: '2026-01-01T00:00:00Z', assemblyPolicyVersion: 'v1', state: 'CALCULATED_INCOMPLETE', missingComponentTypes: ['PRODUCT_COST'] }
const unresolved = { marketplaceOrderId: '33333333-3333-4333-8333-333333333333', sourceEvidenceVersion: 'V023', projectedAt: '2026-01-01T00:00:00Z', assemblyPolicyVersion: 'v1', state: 'UNRESOLVED', unresolvedReasons: ['ORDER_IDENTITY_UNRESOLVED'] }

beforeEach(() => {
  vi.stubEnv('VITE_DEMO_MODE', 'false')
  vi.stubGlobal('fetch', vi.fn((input: string | URL, init?: { method?: string }) => {
    const url = String(input)
    const path = new URL(url).pathname
    if (path.endsWith('/orders') && !init?.method) return Promise.resolve(new Response(JSON.stringify({ orders: [complete, incomplete, unresolved], nextCursor: 'opaque-next' }), { status: 200 }))
    if (url.includes('/orders/')) return Promise.resolve(new Response(JSON.stringify(complete), { status: 200 }))
    if (url.endsWith('/refresh')) return Promise.resolve(new Response(JSON.stringify({ status: 'COMPLETED', source: { status: 'EXHAUSTED', invocations: 1, committedPages: 1, alreadyCommittedPages: 0, records: 3 }, occurrence: {}, revenue: {}, projection: { batches: 1, processedChanges: 3, drained: true } }), { status: 200 }))
    return Promise.resolve(new Response('{}', { status: 404 }))
  }))
})

describe('Decision Room', () => {
  it('maps all three backend states and preserves missing versus zero', async () => {
    render(<App />)
    expect(await screen.findByText('Calculado · Completo')).toBeInTheDocument()
    expect(screen.getAllByText('Calculado · Incompleto').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Não resolvido').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*100,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Não disponível').length).toBeGreaterThan(0)
  })

  it('shows selected order detail and derived boundary', async () => {
    render(<App />)
    const row = await screen.findByText('#11111111')
    await userEvent.click(row)
    expect(await screen.findByText('Derivado · não canônico')).toBeInTheDocument()
    expect(screen.getByText('Economic Truth permanece a autoridade upstream')).toBeInTheDocument()
  })

  it('invokes only the governed refresh endpoint', async () => {
    render(<App />)
    const button = await screen.findByRole('button', { name: /Atualizar realidade/ })
    await userEvent.click(button)
    expect(await screen.findByText('Realidade atualizada com governança')).toBeInTheDocument()
    const calls = vi.mocked(fetch).mock.calls.map(([url, init]) => ({ url: String(url), method: init?.method ?? 'GET' }))
    expect(calls.some((call) => call.url.endsWith('/refresh') && call.method === 'POST')).toBe(true)
    expect(calls.every((call) => !call.url.includes('organizationId') && !call.url.includes('connectionId'))).toBe(true)
  })

  it('supports opaque next-page pagination', async () => {
    render(<App />)
    expect(await screen.findByRole('button', { name: 'Próxima →' })).not.toBeDisabled()
  })

  it('renders a keyboard-accessible semantic table', async () => {
    render(<App />)
    const table = await screen.findByRole('table')
    expect(within(table).getAllByRole('columnheader')).toHaveLength(6)
    expect(screen.getByRole('button', { name: /Atualizar realidade/ })).toBeVisible()
  })

  it('renders a truthful empty state', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ orders: [] }), { status: 200 })))
    render(<App />)
    expect(await screen.findByText('Nenhum pedido projetado')).toBeInTheDocument()
  })

  it('surfaces refresh failure without inventing a result', async () => {
    vi.mocked(fetch).mockImplementation((input, init) => {
      if (String(input).includes('/refresh') && init?.method === 'POST') return Promise.resolve(new Response('{"code":"LIVE_REFRESH_UNAVAILABLE"}', { status: 503 }))
      return Promise.resolve(new Response(JSON.stringify({ orders: [] }), { status: 200 }))
    })
    render(<App />)
    const button = await screen.findByRole('button', { name: /Atualizar realidade/ })
    await userEvent.click(button)
    expect(await screen.findByRole('alert')).toHaveTextContent('O backend está indisponível')
    expect(screen.queryByText('Realidade atualizada com governança')).not.toBeInTheDocument()
  })

  it('does not enter demo mode without its explicit build flag', async () => {
    render(<App />)
    expect(await screen.findByText(/DECISION ROOM/)).toBeInTheDocument()
    expect(screen.queryByText('Modo demonstração')).not.toBeInTheDocument()
  })
})
