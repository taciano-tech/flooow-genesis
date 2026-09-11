import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, listOrders, request } from './api'

const page = { orders: [{ marketplaceOrderId: 'order-1', sourceEvidenceVersion: 'V023', projectedAt: '2026-01-01T00:00:00Z', assemblyPolicyVersion: 'v1', state: 'UNRESOLVED' }] }

afterEach(() => vi.restoreAllMocks())

describe('typed API boundary', () => {
  it('passes an opaque cursor without parsing or changing it', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(page), { status: 200 }))
    await listOrders('v1.A%2Bopaque.cursor')
    const url = fetchMock.mock.calls[0]?.[0]
    expect(String(url)).toContain('cursor=v1.A%252Bopaque.cursor')
  })

  it('never sends organizationId or connectionId', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(page), { status: 200 }))
    await listOrders()
    const [url, init] = fetchMock.mock.calls[0] ?? []
    expect(String(url)).not.toMatch(/organizationId|connectionId/i)
    expect(JSON.stringify(init)).not.toMatch(/organizationId|connectionId/i)
  })

  it('sends the bearer only when runtime configuration provides one', async () => {
    window.__FLOOOW_CONFIG__ = { serviceToken: 'runtime-token' }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(page), { status: 200 }))
    await listOrders()
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer runtime-token')
    window.__FLOOOW_CONFIG__ = undefined
  })

  it('maps unauthorized responses to sanitized actionable errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"detail":"secret db password"}', { status: 401 }))
    await expect(listOrders()).rejects.toMatchObject({ status: 401, message: 'Sessão não autorizada. Verifique o acesso do ambiente.' })
  })

  it('maps invalid cursor and not-found responses without exposing provider data', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"code":"INVALID_SALES_INTELLIGENCE_CURSOR","detail":"internal"}', { status: 400 }))
    await expect(listOrders()).rejects.toBeInstanceOf(ApiError)
    vi.restoreAllMocks()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 404 }))
    await expect(request('/v1/sales-intelligence/orders/nope')).rejects.toMatchObject({ status: 404, message: 'Pedido não encontrado na projeção atual.' })
  })

  it('defensively rejects malformed JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>', { status: 200 }))
    await expect(listOrders()).rejects.toMatchObject({ message: 'Resposta inválida do backend.' })
  })

  it('turns timeout aborts into a stable timeout error', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new DOMException('aborted', 'AbortError'))
    await expect(listOrders()).rejects.toMatchObject({ status: 408, message: 'Tempo limite de resposta excedido.' })
  })
})
