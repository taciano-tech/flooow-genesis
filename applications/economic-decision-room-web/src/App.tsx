import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, demoModeEnabled, getCommerceIdentityHealth, getOrder, listOrders, listReconciliationCases, listSystemicDivergences, refreshOrders, type CommerceIdentityHealth, type EconomicState, type RefreshResult, type ReconciliationCase, type SalesOrder, type SystemicDivergenceSignal } from './api'
import { demoOrders, demoPage, demoRefresh } from './demo'
import './styles.css'

const stateMeta: Record<EconomicState, { label: string; icon: string; tone: string; description: string }> = {
  UNRESOLVED: { label: 'Não resolvido', icon: '○', tone: 'red', description: 'A identidade ou evidência ainda não fecha.' },
  CALCULATED_INCOMPLETE: { label: 'Calculado · Incompleto', icon: '△', tone: 'amber', description: 'Há cálculo, mas componentes econômicos estão ausentes.' },
  CALCULATED_COMPLETE: { label: 'Calculado · Completo', icon: '●', tone: 'green', description: 'O conjunto econômico disponível está completo.' },
}

const money = (value?: string, currency = 'BRL') => value === undefined ? 'Não disponível' : new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(Number(value))
const date = (value?: string) => value ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

function App() {
  const demo = demoModeEnabled()
  const [orders, setOrders] = useState<SalesOrder[]>(demo ? demoOrders : [])
  const [nextCursor, setNextCursor] = useState<string | undefined>(undefined)
  const [selectedId, setSelectedId] = useState<string | undefined>(demo ? demoOrders[0]?.marketplaceOrderId : undefined)
  const [selected, setSelected] = useState<SalesOrder | undefined>(demo ? demoOrders[0] : undefined)
  const [loading, setLoading] = useState(!demo)
  const [detailLoading, setDetailLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | undefined>()
  const [refreshResult, setRefreshResult] = useState<RefreshResult | undefined>()
  const [lastRefresh, setLastRefresh] = useState<Date | undefined>()
  const [page, setPage] = useState(0)
  const [cases, setCases] = useState<ReconciliationCase[]>([])
  const [caseCursor, setCaseCursor] = useState<string | undefined>()
  const [signals, setSignals] = useState<SystemicDivergenceSignal[]>([])
  const [identityHealth, setIdentityHealth] = useState<CommerceIdentityHealth | undefined>()

  const load = useCallback(async (cursor?: string, background = false) => {
    if (!background) setLoading(true)
    setError(undefined)
    try {
      const response = demo ? (await wait(260), demoPage) : await listOrders(cursor)
      setOrders((current) => cursor ? [...current, ...response.orders] : response.orders)
      setNextCursor(response.nextCursor)
      if (!cursor && response.orders[0]) { setSelectedId(response.orders[0].marketplaceOrderId); setSelected(response.orders[0]) }
    } catch (cause) { setError(cause instanceof ApiError ? cause.message : 'Falha ao carregar a sala.') }
    finally { setLoading(false) }
  }, [demo])

  useEffect(() => { if (!demo) void load() }, [demo, load])
  useEffect(() => {
    if (demo) return
    void listReconciliationCases().then((response) => { setCases(response.cases ?? []); setCaseCursor(response.nextCursor) }).catch(() => setCases([]))
    void listSystemicDivergences().then((response) => setSignals(response.signals ?? [])).catch(() => setSignals([]))
    void getCommerceIdentityHealth().then(setIdentityHealth).catch(() => setIdentityHealth({ available: false }))
  }, [demo])

  const select = async (order: SalesOrder) => {
    setSelectedId(order.marketplaceOrderId)
    setDetailLoading(true)
    setError(undefined)
    try { setSelected(demo ? (await wait(140), demoOrders.find((item) => item.marketplaceOrderId === order.marketplaceOrderId)) : await getOrder(order.marketplaceOrderId)) }
    catch (cause) { setError(cause instanceof ApiError ? cause.message : 'Não foi possível abrir o pedido.') }
    finally { setDetailLoading(false) }
  }

  const refresh = async () => {
    setRefreshing(true); setError(undefined); setRefreshResult(undefined)
    try {
      const result = demo ? (await wait(800), demoRefresh) : await refreshOrders()
      setRefreshResult(result); setLastRefresh(new Date()); await load(undefined, true)
    } catch (cause) { setError(cause instanceof ApiError ? cause.message : 'A atualização governada falhou com segurança.') }
    finally { setRefreshing(false) }
  }

  const counts = useMemo(() => orders.reduce((acc, order) => { acc[order.state] += 1; return acc }, { UNRESOLVED: 0, CALCULATED_INCOMPLETE: 0, CALCULATED_COMPLETE: 0 } as Record<EconomicState, number>), [orders])
  const backendLabel = demo ? 'Modo demonstração' : error ? 'Backend indisponível' : loading ? 'Conectando ao backend' : 'Backend conectado'

  return <><SystemicPanel signals={signals} /><IdentityHealthPanel health={identityHealth} /><div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark">F</div><div><strong>flooow</strong><span>trusted decision layer</span></div></div>
      <div className="workspace-label">WORKSPACE</div><div className="workspace"><span className="workspace-dot" /> Operações · Brasil <span className="chevron">⌄</span></div>
      <nav aria-label="Navegação principal">
        <NavItem active icon="◈" label="Visão Geral" /><NavItem icon="⌁" label="Pedidos" /><NavItem icon="✦" label="Decisões" /><NavItem icon="⊙" label="Evidências" /><NavItem icon="⇄" label="Reconciliação" /><NavItem icon="↻" label="Atualização" /><NavItem icon="⚙" label="Configurações" />
      </nav>
      <div className="sidebar-bottom"><div className="security-note"><span className="lock">⌁</span><div><b>Ambiente governado</b><small>Dados com escopo organizacional</small></div></div><div className="user"><div className="avatar">TS</div><div><b>Operador Flooow</b><small>sessão autenticada</small></div><span className="more">···</span></div></div>
    </aside>
    <main className="main-content">
      <header className="topbar"><div className="breadcrumb"><span>Flooow</span><b>/</b><strong>Sala de Decisão Econômica</strong></div><div className="top-actions"><span className={`connection ${error ? 'down' : ''}`}><i /> {backendLabel}</span><span className="environment">PRODUÇÃO <em>●</em></span><button className="icon-button" aria-label="Ajuda">?</button><button className="icon-button" aria-label="Notificações">♢</button></div></header>
      <section className="page-header"><div><div className="eyebrow">DECISION ROOM <span>·</span> {demo ? 'DEMO' : 'LIVE'}</div><h1>Entenda o estado econômico<br /><em>antes de decidir.</em></h1><p>Inteligência governada para decisões de alto impacto no marketplace.</p></div><div className="header-actions"><div className="refresh-meta">{lastRefresh ? `Atualizado ${date(lastRefresh.toISOString())}` : 'Projeção durável · leitura segura'}</div><button className="primary-button" onClick={() => void refresh()} disabled={refreshing || loading}><span className={refreshing ? 'spin' : ''}>↻</span>{refreshing ? 'Atualizando realidade…' : 'Atualizar realidade'}</button></div></section>
      {demo && <div className="demo-banner"><span>◈</span><b>Modo demonstração</b><span>Dados determinísticos locais. Nenhuma chamada ao backend é feita nesta sessão.</span></div>}
      {error && <div className="alert" role="alert"><span>!</span><div><b>Não foi possível concluir</b><span>{error}</span></div><button onClick={() => void load()} aria-label="Tentar novamente">Tentar novamente</button></div>}
      {refreshResult && <div className="refresh-result" role="status"><span className="success-icon">✓</span><div><b>Realidade atualizada com governança</b><span>{refreshResult.projection.processedChanges} mudanças processadas · fonte {refreshResult.source.status.toLowerCase()}</span></div><button onClick={() => setRefreshResult(undefined)} aria-label="Fechar resultado">×</button></div>}
      <section className="reconciliation-panel panel"><div className="panel-heading"><div><div className="card-eyebrow">EVIDENCE → RECONCILIAÇÃO → CASE</div><h2>Reconciliação</h2></div><span className="result-count">{cases.length} casos visíveis</span></div><div className="reconciliation-summary"><span className="state-badge red"><i>○</i>Divergência detectada</span><b>{cases.length}</b><span>memória operacional durável</span><span className="future-capability">Recuperação futura · sem autoridade nesta versão</span></div>{cases.length ? <div className="case-list">{cases.map((item) => <div className="case-row" key={item.caseId}><div><b>{item.caseId}</b><small>Pedido {item.marketplaceOrderId}</small></div><span className={`state-badge ${item.status === 'RESOLVED' ? 'green' : 'red'}`}><i>{item.status === 'RESOLVED' ? '●' : '○'}</i>{item.status === 'RESOLVED' ? 'Resolvido' : 'Divergência detectada'}</span><strong>{item.absoluteDifferenceSummary.currency} {item.absoluteDifferenceSummary.amount}</strong></div>)}</div> : <div className="empty-state compact"><b>Nenhuma divergência persistida</b><small>Dentro da tolerância não cria caso. A evidência permanece inalterada.</small></div>}{caseCursor && <button className="secondary-button" onClick={() => void listReconciliationCases(caseCursor).then((response) => { setCases((current) => [...current, ...response.cases]); setCaseCursor(response.nextCursor) })}>Próxima página</button>}</section>
      <section className="status-grid"><StatusCard label="Não resolvido" value={counts.UNRESOLVED} tone="red" detail="identidade ou evidência pendente" /><StatusCard label="Calculado · Incompleto" value={counts.CALCULATED_INCOMPLETE} tone="amber" detail="componentes econômicos ausentes" /><StatusCard label="Calculado · Completo" value={counts.CALCULATED_COMPLETE} tone="green" detail="pronto para análise econômica" /><div className="coverage-card"><div className="card-eyebrow">COBERTURA DA PROJEÇÃO <span>ⓘ</span></div><div className="coverage-value">{orders.length ? Math.round((counts.CALCULATED_COMPLETE / orders.length) * 100) : 0}<small>%</small></div><div className="coverage-bar"><i style={{ width: `${orders.length ? (counts.CALCULATED_COMPLETE / orders.length) * 100 : 0}%` }} /></div><div className="coverage-foot"><span>completa</span><span>{orders.length} pedidos visíveis</span></div></div></section>
      <div className="content-grid"><section className="orders-panel panel"><div className="panel-heading"><div><div className="card-eyebrow">LEITURA DERIVADA <span className="blue-dot" /></div><h2>Pedidos econômicos</h2></div><span className="result-count">{orders.length} nesta página</span></div>{loading ? <LoadingRows /> : orders.length === 0 ? <EmptyState /> : <><div className="table-wrap"><table><thead><tr><th>Pedido</th><th>Estado econômico</th><th>Receita bruta</th><th>Contribuição</th><th>Projetado em</th><th><span className="sr-only">Abrir</span></th></tr></thead><tbody>{orders.map((order) => <OrderRow key={order.marketplaceOrderId} order={order} selected={selectedId === order.marketplaceOrderId} onSelect={() => void select(order)} />)}</tbody></table></div><div className="pagination"><button disabled={page === 0} onClick={() => { setPage(0); void load() }}>← Anterior</button><span>Página {page + 1}</span><button disabled={!nextCursor} onClick={() => { setPage((current) => current + 1); void load(nextCursor) }}>Próxima →</button></div></>}</section><DetailPanel order={selected} loading={detailLoading} /></div>
      <section className="trust-strip"><div className="trust-title"><span className="trust-icon">◒</span><div><b>Como este número é governado?</b><small>Uma linha clara entre fato, interpretação e decisão.</small></div></div><TrustStep tone="blue" title="Economic Truth" text="autoridade canônica" /><span className="trust-arrow">→</span><TrustStep tone="purple" title="Sales Intelligence" text="leitura derivada" /><span className="trust-arrow">→</span><TrustStep tone="green" title="Decision Surface" text="você decide" /></section>
      <footer><span>FLOOOW / SALA DE DECISÃO ECONÔMICA</span><span>Projection policy v1 · <b>Sem autoridade de execução</b></span></footer>
    </main>
  </div></>
}

function SystemicPanel({ signals }: { signals: SystemicDivergenceSignal[] }) {
  return <section className="reconciliation-panel panel systemic-panel"><div className="panel-heading"><div><div className="card-eyebrow">SYSTEMIC ANALYSIS</div><h2>Padrão sistêmico detectado</h2></div><span className="result-count">{signals.length} sinais</span></div>{signals.length ? signals.map((signal) => <div className="case-row" key={signal.signalId}><div><b>{signal.stage}</b><small>{signal.occurrenceCount} casos · policy {signal.policyVersion}</small></div><strong>{signal.absoluteDifference.currency} {signal.absoluteDifference.amount}</strong><span className="future-capability">Detecção não representa valor recuperável nem autoriza recuperação</span></div>) : <div className="empty-state compact"><b>Nenhum padrão sistêmico detectado</b><small>Policy, janela e limiar são explícitos e determinísticos.</small></div>}</section>
}
function IdentityHealthPanel({ health }: { health?: CommerceIdentityHealth }) {
  if (!health || health.available === false) return <section className="reconciliation-panel panel systemic-panel"><div className="panel-heading"><div><div className="card-eyebrow">COMMERCE IDENTITY HEALTH</div><h2>Avaliação real indisponível</h2></div></div><div className="empty-state compact"><b>Nenhuma avaliação real disponível</b><small>Configure as fontes seguras ML e Omie para avaliar identidade sem alterar Economic Truth.</small></div></section>
  return <section className="reconciliation-panel panel systemic-panel"><div className="panel-heading"><div><div className="card-eyebrow">COMMERCE IDENTITY HEALTH</div><h2>Saúde da identidade cross-system</h2></div><span className="result-count">policy {health.policyVersion}</span></div><div className="reconciliation-summary"><b>{health.coveragePercentage ?? '—'}%</b><span>cobertura EXACT_CONFIRMED</span><span className="future-capability">Identity confirmation links records across systems. It does not alter Economic Truth or authorize financial action.</span></div><div className="case-list"><div className="case-row"><b>ML {health.mlTransactionsInspected ?? '—'}</b><b>Omie {health.omieTransactionsInspected ?? '—'}</b><span>Exact {health.exactConfirmed ?? '—'}</span><span>Candidate {health.candidate ?? '—'}</span><span>Ambiguous {health.ambiguous ?? '—'}</span><span>Conflict {health.conflict ?? '—'}</span><span>Unresolved {health.unresolved ?? '—'}</span></div></div></section>
}

function NavItem({ icon, label, active = false }: { icon: string; label: string; active?: boolean }) { return <button className={`nav-item ${active ? 'active' : ''}`}><span>{icon}</span>{label}{active && <i />}</button> }
function StatusCard({ label, value, tone, detail }: { label: string; value: number; tone: string; detail: string }) { return <div className={`status-card ${tone}`}><div className="status-top"><span className="state-symbol">{tone === 'green' ? '●' : tone === 'amber' ? '△' : '○'}</span><span>{label}</span><span className="info">ⓘ</span></div><strong>{value.toString().padStart(2, '0')}</strong><small>{detail}</small></div> }
function OrderRow({ order, selected, onSelect }: { order: SalesOrder; selected: boolean; onSelect: () => void }) { const state = stateMeta[order.state]; return <tr className={selected ? 'selected' : ''} onClick={onSelect}><td><div className="order-id"><span className="order-glyph">⌁</span><div><b>#{order.marketplaceOrderId.slice(0, 8).toUpperCase()}</b><small>{order.marketplaceOrderId}</small></div></div></td><td><span className={`state-badge ${state.tone}`}><i>{state.icon}</i>{state.label}</span></td><td>{order.economicResult ? money(order.economicResult.grossRevenue, order.economicResult.currency) : <span className="missing">Não disponível</span>}</td><td>{order.economicResult ? money(order.economicResult.contribution, order.economicResult.currency) : <span className="missing">—</span>}</td><td><span className="date-cell">{date(order.projectedAt)}</span></td><td><button className="row-arrow" aria-label={`Abrir pedido ${order.marketplaceOrderId}`}>→</button></td></tr> }
function DetailPanel({ order, loading }: { order?: SalesOrder; loading: boolean }) { if (loading) return <aside className="detail-panel panel"><LoadingRows count={3} /></aside>; if (!order) return <aside className="detail-panel panel"><EmptyState /></aside>; const state = stateMeta[order.state]; const result = order.economicResult; return <aside className="detail-panel panel"><div className="panel-heading"><div><div className="card-eyebrow">INSPEÇÃO DO REGISTRO</div><h2>Detalhe do pedido</h2></div><span className={`state-badge ${state.tone}`}><i>{state.icon}</i>{state.label}</span></div><div className="detail-id"><span className="order-glyph large">⌁</span><div><small>MARKETPLACE ORDER ID</small><b>{order.marketplaceOrderId}</b></div></div><div className="detail-callout"><span className={`callout-icon ${state.tone}`}>{state.icon}</span><div><b>{state.label === 'Calculado · Completo' ? 'Pronto para análise econômica' : state.label === 'Calculado · Incompleto' ? 'Evidência econômica ausente' : 'Identidade ou evidência não resolvida'}</b><span>{state.description}</span></div></div>{result ? <div className="metrics"><Metric label="Receita bruta" value={money(result.grossRevenue, result.currency)} /><Metric label="Contribuição" value={money(result.contribution, result.currency)} /><Metric label="Margem de contribuição" value={result.contributionMargin ? `${(Number(result.contributionMargin) * 100).toFixed(1)}%` : 'Não disponível'} /></div> : <div className="missing-block"><b>Valor econômico não disponível</b><span>{order.missingComponentTypes?.length ? `Ausências: ${order.missingComponentTypes.join(', ')}` : 'A projeção ainda não possui um resultado calculável.'}</span></div>}<div className="detail-list"><DetailLine label="Estado da projeção" value="Derivado · não canônico" /><DetailLine label="Evidência de origem" value={order.sourceEvidenceVersion} /><DetailLine label="Projetado em" value={date(order.projectedAt)} /><DetailLine label="Política" value={order.assemblyPolicyVersion} /></div><div className="detail-footer"><span>◈</span> Economic Truth permanece a autoridade upstream</div></aside> }
function Metric({ label, value }: { label: string; value: string }) { return <div><small>{label}</small><b>{value}</b></div> }
function DetailLine({ label, value }: { label: string; value: string }) { return <div><span>{label}</span><b>{value}</b></div> }
function TrustStep({ tone, title, text }: { tone: string; title: string; text: string }) { return <div className="trust-step"><i className={tone} /> <div><b>{title}</b><small>{text}</small></div></div> }
function LoadingRows({ count = 5 }: { count?: number }) { return <div className="loading-rows">{Array.from({ length: count }, (_, index) => <div className="skeleton" key={index} />)}</div> }
function EmptyState() { return <div className="empty-state"><span>◌</span><b>Nenhum pedido projetado</b><small>Atualize a realidade para buscar a próxima janela de evidência.</small></div> }

export default App
