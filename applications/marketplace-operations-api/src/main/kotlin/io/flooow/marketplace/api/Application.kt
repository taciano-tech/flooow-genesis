package io.flooow.marketplace.api

import io.flooow.integration.connector.ConnectorRuntime
import io.flooow.integration.connector.IntegrationControlPlaneConnectorAccess
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.credential.CredentialRotationExecutor
import io.flooow.integration.credential.IntegrationControlPlaneCredentialRotationAccess
import io.flooow.integration.provider.mercadolivre.MercadoLivreOAuthCredentialRotator
import io.flooow.marketplace.persistence.postgres.PostgresCredentialRotationExecutionStore
import io.flooow.integration.security.MvpRuntimeMasterKey
import io.flooow.integration.security.MvpSecureRuntime
import io.flooow.marketplace.operations.economics.provider.mercadolivre.MercadoLivreOrderSourceConnector
import io.flooow.marketplace.operations.economics.provider.omie.OmieProviderConnector
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicOrderSourceCapability
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.economics.reconciliation.GovernedReconciliationCaseOrchestrator
import io.flooow.marketplace.operations.economics.reconciliation.DeterministicSystemicDivergenceDetector
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergencePolicies
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceAnalysisTrigger
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionService
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionService
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionProcessor
import io.flooow.marketplace.operations.inventory.AssessmentIdentifierFactory
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentJournal
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentRecorder
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import io.flooow.marketplace.operations.inventory.PersistenceIntegrityException
import io.flooow.marketplace.operations.inventory.PersistenceUnavailableException
import io.flooow.marketplace.operations.inventory.RecordedInventoryRiskAssessment
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityConfirmationService
import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.marketplace.persistence.postgres.PostgresIntegrationControlPlaneRepository
import io.flooow.marketplace.persistence.postgres.PostgresInventoryRiskAssessmentJournal
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceEconomicEvidenceChangeFeed
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceIndependentEconomicEvidenceRepository
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceOrderSourcePromotionRepository
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceSalesIntelligenceProjection
import io.flooow.marketplace.persistence.postgres.PostgresDurableReconciliationCaseRepository
import io.flooow.marketplace.persistence.postgres.PostgresSystemicDivergenceSignalRepository
import io.flooow.marketplace.persistence.postgres.PostgresMercadoLivreOrderSourceCommitter
import io.flooow.marketplace.persistence.postgres.PostgresOmieTransactionEvidenceCommitter
import io.flooow.marketplace.persistence.postgres.PostgresOmieProductCostCommitter
import io.flooow.marketplace.persistence.postgres.PostgresOmieIdentityEvidenceReader
import io.flooow.marketplace.persistence.postgres.PostgresOmieProductCatalogEvidenceReader
import io.flooow.marketplace.persistence.postgres.PostgresCrossSystemProductIdentityDecisionRepository
import io.flooow.marketplace.persistence.postgres.PostgresMercadoLivreIdentityEvidenceReader
import io.flooow.marketplace.operations.live.ConnectorRuntimeMarketplaceLivePipelineSourceRunner
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineService
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineLimits
import io.flooow.marketplace.operations.live.MarketplaceOrderRevenuePromotionLivePipelineAdapter
import io.flooow.marketplace.operations.live.MarketplaceOrderSourcePromotionLivePipelineAdapter
import io.flooow.marketplace.operations.live.MarketplaceSalesIntelligenceLivePipelineAdapter
import io.flooow.organization.OrganizationId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import java.util.logging.Logger
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private data class RuntimeCodecs(
    val secureRuntime: MvpSecureRuntime,
    val cursorCodec: SalesIntelligenceCursorCodec,
    val reconciliationCursorCodec: ReconciliationCaseCursorCodec,
    val systemicDivergenceCursorCodec: SystemicDivergenceCursorCodec
)

private const val ASSESSMENT_PATH =
    "/v1/marketplace-operations/inventory-risk-assessments"
private val problemContentType = ContentType.parse("application/problem+json")
private val jsonContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

private val json = Json {
    explicitNulls = false
    ignoreUnknownKeys = false
}

fun main() {
    val environment = System.getenv()
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val serviceToken = ServiceToken.fromEnvironment(environment)
    val serviceOrganizationId = serviceOrganizationFromEnvironment(environment)
    val oauthConfiguration = mercadoLivreOAuthBootstrapConfigurationOrNull(environment)
    val connectionId = mercadoLivreConnectionFromEnvironmentOrNull(environment)
        ?: if (oauthConfiguration == null) {
        mercadoLivreConnectionFromEnvironment(environment)
    } else {
        null
    }
    val omieConnectionId = omieConnectionFromEnvironmentOrNull(environment)
    val configuration = PostgresConfiguration.fromEnvironment(environment)
    val journal = PostgresInventoryRiskAssessmentJournal.connect(configuration)
    val recorder = InventoryRiskAssessmentRecorder(journal)
    val (secureRuntime, cursorCodec, reconciliationCursorCodec, systemicDivergenceCursorCodec) = MvpRuntimeMasterKey.fromEnvironment(environment).use {
        RuntimeCodecs(
            MvpSecureRuntime(
                it,
                Path.of(requireNotNull(environment["FLOOOW_SECRET_VAULT_PATH"]) {
                    "FLOOOW_SECRET_VAULT_PATH is required"
                })
            ),
            it.useBytes(::SalesIntelligenceCursorCodec),
            it.useBytes(::ReconciliationCaseCursorCodec),
            it.useBytes(::SystemicDivergenceCursorCodec)
        )
    }
    secureRuntime.use { security ->
        val controlPlane = IntegrationControlPlaneService(
            PostgresIntegrationControlPlaneRepository.connect(configuration),
            security.secretVault
        )
        val oauthBootstrap = oauthConfiguration?.let {
            MercadoLivreOAuthBootstrap(controlPlane, serviceOrganizationId, it)
        }
        val credentialRotationExecutor = CredentialRotationExecutor(
            IntegrationControlPlaneCredentialRotationAccess(controlPlane),
            PostgresCredentialRotationExecutionStore(configuration),
            listOf(MercadoLivreOAuthCredentialRotator())
        )
        val mercadoLivreCredentialRotationApi = MercadoLivreCredentialRotationApi(
            credentialRotationExecutor,
            connectionId
        )
        val omieBootstrap = OmieStaticCredentialBootstrap(controlPlane)
        val connectorRuntime = ConnectorRuntime(
            IntegrationControlPlaneConnectorAccess(controlPlane),
            listOf(
                MercadoLivreOrderSourceConnector(),
                OmieProviderConnector()
            ),
            listOf(
                PostgresMercadoLivreOrderSourceCommitter(
                    configuration,
                    security.progressProtector
                ),
                PostgresOmieTransactionEvidenceCommitter(configuration, security.progressProtector),
                PostgresMercadoLivreOrderSourceCommitter(
                    configuration, security.progressProtector,
                    capability = MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY
                ),
                PostgresOmieTransactionEvidenceCommitter(
                    configuration, security.progressProtector,
                    capability = OmieTransactionEvidenceCapability.REACQUISITION_KEY
                ),
                PostgresOmieProductCostCommitter(configuration, security.progressProtector)
            )
        )
        val omieEvidenceRefresh = OmieEvidenceRefreshApi(
            controlPlane,
            connectorRuntime,
            omieConnectionId
        )
        val omieEvidenceReacquisition = OmieEvidenceRefreshApi(
            controlPlane,
            connectorRuntime,
            omieConnectionId,
            capability = OmieTransactionEvidenceCapability.REACQUISITION_KEY
        )
        val omieProductCostRefresh = OmieEvidenceRefreshApi(
            controlPlane,
            connectorRuntime,
            omieConnectionId,
            capability = MarketplaceEconomicProductCostCapability.KEY,
            maxPages = 100
        )
        val commerceIdentityRecompute = CommerceIdentityRecomputeApi(
            PostgresMercadoLivreIdentityEvidenceReader(configuration, connectionId),
            PostgresOmieIdentityEvidenceReader(configuration, omieConnectionId),
            productCatalog = omieConnectionId?.let {
                PostgresOmieProductCatalogEvidenceReader(configuration, it)
            },
            omieConnectionId = omieConnectionId?.value?.toString()
        )
        val crossSystemProductIdentity = CrossSystemProductIdentityConfirmationApi(
            CrossSystemProductIdentityConfirmationService(
                PostgresCrossSystemProductIdentityDecisionRepository(configuration)
            ),
            connectionId,
            omieConnectionId
        )
        val promotionRepository =
            PostgresMarketplaceOrderSourcePromotionRepository(configuration)
        val evidenceRepository =
            PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
        val projection = PostgresMarketplaceSalesIntelligenceProjection(configuration)
        val reconciliationCaseRepository = PostgresDurableReconciliationCaseRepository(configuration)
        val systemicSignalRepository = PostgresSystemicDivergenceSignalRepository(configuration)
        val systemicDetector = DeterministicSystemicDivergenceDetector(systemicSignalRepository)
        // The orchestrator is composed at the durable boundary. Only an
        // explicit accepted assessment may invoke it; no HTTP/provider path
        // can manufacture an assessment or choose an organization.
        val reconciliationCaseOrchestrator = GovernedReconciliationCaseOrchestrator(
            reconciliationCaseRepository,
            SystemicDivergenceAnalysisTrigger { organizationId, evaluatedAt ->
                val cases = reconciliationCaseRepository.list(organizationId, null, 100).cases
                systemicDetector.analyze(organizationId, cases, SystemicDivergencePolicies.current, evaluatedAt)
            }
        )
        val reconciliationCases = ReconciliationCasesApi(reconciliationCaseRepository, reconciliationCursorCodec)
        val systemicDivergences = SystemicDivergencesApi(systemicSignalRepository, systemicDivergenceCursorCodec)
        val pipeline = MarketplaceLivePipelineService(
            ConnectorRuntimeMarketplaceLivePipelineSourceRunner(connectorRuntime),
            MarketplaceOrderSourcePromotionLivePipelineAdapter(
                MarketplaceOrderSourcePromotionService(
                    promotionRepository,
                    evidenceRepository
                )
            ),
            MarketplaceOrderRevenuePromotionLivePipelineAdapter(
                MarketplaceOrderRevenuePromotionService(
                    promotionRepository,
                    evidenceRepository
                )
            ),
            MarketplaceSalesIntelligenceLivePipelineAdapter(
                MarketplaceSalesIntelligenceProjectionProcessor(
                    evidenceRepository,
                    PostgresMarketplaceEconomicEvidenceChangeFeed(configuration),
                    projection
                )
            )
        )
        val reacquisitionPipeline = MarketplaceLivePipelineService(
            ConnectorRuntimeMarketplaceLivePipelineSourceRunner(connectorRuntime),
            MarketplaceOrderSourcePromotionLivePipelineAdapter(
                MarketplaceOrderSourcePromotionService(promotionRepository, evidenceRepository)
            ),
            MarketplaceOrderRevenuePromotionLivePipelineAdapter(
                MarketplaceOrderRevenuePromotionService(promotionRepository, evidenceRepository)
            ),
            MarketplaceSalesIntelligenceLivePipelineAdapter(
                MarketplaceSalesIntelligenceProjectionProcessor(
                    evidenceRepository,
                    PostgresMarketplaceEconomicEvidenceChangeFeed(configuration),
                    projection
                )
            ),
            sourceCapability = MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY
        )
        val salesIntelligenceApi = connectionId?.let {
            SalesIntelligenceApi(
                projection = projection,
                refresh = { organizationId, configuredConnectionId, deadline ->
                    pipeline.run(organizationId, configuredConnectionId, deadline)
                },
                connectionId = it,
                cursors = cursorCodec,
                reacquire = { organizationId, configuredConnectionId, deadline ->
                    reacquisitionPipeline.run(
                        organizationId,
                        configuredConnectionId,
                        deadline,
                        limits = MarketplaceLivePipelineLimits(maxSourcePages = 48),
                        stopAfterSource = true
                    )
                }
            )
        }
        embeddedServer(Netty, host = host, port = port) {
            configureApi(
                serviceToken,
                serviceOrganizationId,
                recorder::record,
                recorder::findById,
                salesIntelligenceApi,
                reconciliationCases,
                systemicDivergences,
                CommerceIdentityHealthApi(commerceIdentityRecompute::current),
                oauthBootstrap,
                omieBootstrap,
                omieEvidenceRefresh,
                omieEvidenceReacquisition,
                omieProductCostRefresh,
                commerceIdentityRecompute,
                mercadoLivreCredentialRotationApi,
                crossSystemProductIdentity
            )
        }.start(wait = true)
    }
}

fun Application.module() {
    val journal = InMemoryAssessmentJournal()
    val sequence = AtomicLong()
    val recorder = InventoryRiskAssessmentRecorder(
        journal = journal,
        identifierFactory = AssessmentIdentifierFactory {
            val suffix = sequence.incrementAndGet().toString().padStart(12, '1')
            "11111111-1111-4111-8111-$suffix"
        },
        clock = Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneOffset.UTC)
    )
    configureApi(
        ServiceToken.test(TEST_SERVICE_TOKEN),
        TEST_ORGANIZATION_ID,
        recorder::record,
        recorder::findById,
        testSalesIntelligenceApi()
    )
}

internal fun Application.configureApi(
    serviceToken: ServiceToken,
    serviceOrganizationId: OrganizationId,
    record: (OrganizationId, InventoryRiskInput) -> RecordedInventoryRiskAssessment,
    findById: (OrganizationId, String) -> RecordedInventoryRiskAssessment? = { _, _ -> null },
    salesIntelligenceApi: SalesIntelligenceApi? = null,
    reconciliationCasesApi: ReconciliationCasesApi? = null,
    systemicDivergencesApi: SystemicDivergencesApi? = null,
    commerceIdentityHealthApi: CommerceIdentityHealthApi? = null,
    mercadoLivreOAuthBootstrap: MercadoLivreOAuthBootstrap? = null,
    omieStaticCredentialBootstrap: OmieStaticCredentialBootstrap? = null,
    omieEvidenceRefreshApi: OmieEvidenceRefreshApi? = null,
    omieEvidenceReacquisitionApi: OmieEvidenceRefreshApi? = null,
    omieProductCostRefreshApi: OmieEvidenceRefreshApi? = null,
    commerceIdentityRecomputeApi: CommerceIdentityRecomputeApi? = null,
    mercadoLivreCredentialRotationApi: MercadoLivreCredentialRotationApi? = null,
    crossSystemProductIdentityApi: CrossSystemProductIdentityConfirmationApi? = null
) {
    install(Authentication) {
        bearer("service-bearer") {
            realm = "flooow-marketplace-operations"
            authHeader { call ->
                try {
                    val values = call.request.headers
                        .getAll(HttpHeaders.Authorization)
                        .orEmpty()
                    if (values.size != 1) {
                        null
                    } else {
                        val parts = values.single().split(' ', limit = 2)
                        if (parts.size == 2 && parts.all { it.isNotEmpty() }) {
                            HttpAuthHeader.Single(parts[0], parts[1])
                        } else {
                            null
                        }
                    }
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            authenticate { credential ->
                if (serviceToken.matches(credential.token)) {
                    ServicePrincipal(serviceOrganizationId)
                } else {
                    null
                }
            }
        }
    }

    install(StatusPages) {
        exception<MalformedRequestException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "https://flooow.io/problems/malformed-request",
                title = "Malformed request",
                detail = cause.message ?: "Request body is invalid",
                code = "MALFORMED_REQUEST"
            )
        }
        exception<DomainValidationException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.UnprocessableEntity,
                type = "https://flooow.io/problems/invalid-inventory-risk-request",
                title = "Invalid inventory risk request",
                detail = cause.message ?: "Inventory risk request is invalid",
                code = "INVALID_INVENTORY_RISK_REQUEST"
            )
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.UnsupportedMediaType,
                type = "https://flooow.io/problems/unsupported-media-type",
                title = "Unsupported media type",
                detail = "Content-Type must be application/json",
                code = "UNSUPPORTED_MEDIA_TYPE"
            )
        }
        exception<MalformedAssessmentIdException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "https://flooow.io/problems/malformed-assessment-id",
                title = "Malformed assessment identifier",
                detail = cause.message ?: "Assessment identifier is invalid",
                code = "MALFORMED_ASSESSMENT_ID"
            )
        }
        exception<AssessmentNotFoundException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.NotFound,
                type = "https://flooow.io/problems/assessment-not-found",
                title = "Assessment not found",
                detail = "The requested assessment was not found",
                code = "ASSESSMENT_NOT_FOUND"
            )
        }
        exception<OmieEvidenceRefreshConfigurationUnavailableException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/omie-evidence-refresh-unavailable",
                "Omie evidence refresh unavailable",
                "The Omie connection is not configured",
                "OMIE_EVIDENCE_REFRESH_UNAVAILABLE"
            )
        }
        exception<OmieEvidenceRefreshConnectionUnavailableException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/omie-evidence-refresh-unavailable",
                "Omie evidence refresh unavailable",
                "The configured Omie connection is not active for this organization",
                "OMIE_EVIDENCE_REFRESH_UNAVAILABLE"
            )
        }
        exception<OmieEvidenceRefreshFailureException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/omie-evidence-refresh-failed",
                "Omie evidence refresh failed",
                "Omie evidence could not be refreshed",
                "OMIE_EVIDENCE_REFRESH_FAILED"
            )
        }
        exception<CommerceIdentityRecomputeFailureException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/commerce-identity-recompute-failed",
                "Commerce identity recompute failed",
                "Persisted identity evidence could not be evaluated",
                "COMMERCE_IDENTITY_RECOMPUTE_FAILED"
            )
        }
        exception<CrossSystemProductIdentityMalformedException> { call, cause ->
            call.respondProblem(
                HttpStatusCode.BadRequest,
                "https://flooow.io/problems/malformed-product-identity-decision",
                "Malformed product identity decision",
                cause.message ?: "The product identity decision is invalid",
                "MALFORMED_PRODUCT_IDENTITY_DECISION"
            )
        }
        exception<MercadoLivreCredentialRotationUnavailableException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/mercado-livre-credential-rotation-unavailable",
                "Mercado Livre credential rotation unavailable",
                "The configured Mercado Livre connection is unavailable",
                "MERCADO_LIVRE_CREDENTIAL_ROTATION_UNAVAILABLE"
            )
        }
        exception<MercadoLivreCredentialRotationFailureException> { call, cause ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/mercado-livre-credential-rotation-failed",
                "Mercado Livre credential rotation failed",
                "Credential rotation could not be completed safely: ${cause.kind.name}",
                "MERCADO_LIVRE_CREDENTIAL_ROTATION_${cause.kind.name}"
            )
        }
        exception<PersistenceUnavailableException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.ServiceUnavailable,
                type = "https://flooow.io/problems/persistence-unavailable",
                title = "Persistence unavailable",
                detail = "Assessment persistence is temporarily unavailable",
                code = "PERSISTENCE_UNAVAILABLE"
            )
        }
        exception<PersistenceIntegrityException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.InternalServerError,
                type = "https://flooow.io/problems/persistence-integrity-failure",
                title = "Persistence integrity failure",
                detail = "The persisted assessment could not be verified",
                code = "PERSISTENCE_INTEGRITY_FAILURE"
            )
        }
        exception<InvalidSalesIntelligenceCursorException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.BadRequest,
                "https://flooow.io/problems/invalid-sales-intelligence-cursor",
                "Invalid Sales Intelligence cursor",
                "The Sales Intelligence cursor or page limit is invalid",
                "INVALID_SALES_INTELLIGENCE_CURSOR"
            )
        }
        exception<InvalidSalesIntelligenceOrderIdException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.BadRequest,
                "https://flooow.io/problems/invalid-sales-intelligence-order-id",
                "Invalid Sales Intelligence order identifier",
                "The Sales Intelligence order identifier is invalid",
                "INVALID_SALES_INTELLIGENCE_ORDER_ID"
            )
        }
        exception<SalesIntelligenceNotFoundException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.NotFound,
                "https://flooow.io/problems/sales-intelligence-not-found",
                "Sales Intelligence not found",
                "The requested Sales Intelligence order was not found",
                "SALES_INTELLIGENCE_NOT_FOUND"
            )
        }
        exception<SalesIntelligenceReadFailureException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/sales-intelligence-read-failure",
                "Sales Intelligence read failure",
                "Sales Intelligence is temporarily unavailable",
                "SALES_INTELLIGENCE_READ_FAILURE"
            )
        }
        exception<InvalidReconciliationCaseCursorException> { call, _ -> call.respondProblem(HttpStatusCode.BadRequest, "https://flooow.io/problems/invalid-reconciliation-case-cursor", "Invalid reconciliation case cursor", "The reconciliation case cursor or page limit is invalid", "INVALID_RECONCILIATION_CASE_CURSOR") }
        exception<InvalidReconciliationCaseIdException> { call, _ -> call.respondProblem(HttpStatusCode.BadRequest, "https://flooow.io/problems/invalid-reconciliation-case-id", "Invalid reconciliation case identifier", "The reconciliation case identifier is invalid", "INVALID_RECONCILIATION_CASE_ID") }
        exception<ReconciliationCaseNotFoundException> { call, _ -> call.respondProblem(HttpStatusCode.NotFound, "https://flooow.io/problems/reconciliation-case-not-found", "Reconciliation case not found", "The requested reconciliation case was not found", "RECONCILIATION_CASE_NOT_FOUND") }
        exception<ReconciliationCaseReadFailureException> { call, _ -> call.respondProblem(HttpStatusCode.ServiceUnavailable, "https://flooow.io/problems/reconciliation-case-read-failure", "Reconciliation case read failure", "Reconciliation cases are temporarily unavailable", "RECONCILIATION_CASE_READ_FAILURE") }
        exception<InvalidSystemicDivergenceCursorException> { call, _ -> call.respondProblem(HttpStatusCode.BadRequest, "https://flooow.io/problems/invalid-systemic-divergence-cursor", "Invalid systemic divergence cursor", "The systemic divergence cursor or page limit is invalid", "INVALID_SYSTEMIC_DIVERGENCE_CURSOR") }
        exception<InvalidSystemicDivergenceSignalIdException> { call, _ -> call.respondProblem(HttpStatusCode.BadRequest, "https://flooow.io/problems/invalid-systemic-divergence-id", "Invalid systemic divergence identifier", "The systemic divergence identifier is invalid", "INVALID_SYSTEMIC_DIVERGENCE_ID") }
        exception<SystemicDivergenceSignalNotFoundException> { call, _ -> call.respondProblem(HttpStatusCode.NotFound, "https://flooow.io/problems/systemic-divergence-not-found", "Systemic divergence not found", "The requested systemic divergence was not found", "SYSTEMIC_DIVERGENCE_NOT_FOUND") }
        exception<LiveRefreshBlockedException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.Conflict,
                "https://flooow.io/problems/live-refresh-blocked",
                "Live refresh blocked",
                "The live refresh could not safely advance",
                "LIVE_REFRESH_BLOCKED"
            )
        }
        exception<LiveRefreshUnavailableException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                "https://flooow.io/problems/live-refresh-unavailable",
                "Live refresh unavailable",
                "The live refresh is temporarily unavailable",
                "LIVE_REFRESH_UNAVAILABLE"
            )
        }
        exception<LiveRefreshInternalFailureException> { call, _ ->
            call.respondProblem(
                HttpStatusCode.InternalServerError,
                "https://flooow.io/problems/live-refresh-internal-failure",
                "Live refresh internal failure",
                "The live refresh could not be completed",
                "LIVE_REFRESH_INTERNAL_FAILURE"
            )
        }
        exception<Throwable> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.InternalServerError,
                type = "https://flooow.io/problems/internal-error",
                title = "Internal server error",
                detail = "The server could not complete the request",
                code = "INTERNAL_ERROR"
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.NotFound,
                type = "https://flooow.io/problems/resource-not-found",
                title = "Resource not found",
                detail = "The requested resource was not found",
                code = "RESOURCE_NOT_FOUND"
            )
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.response.header("Cache-Control", "no-store")
            call.respondProblem(
                status = HttpStatusCode.Unauthorized,
                type = "https://flooow.io/problems/authentication-required",
                title = "Authentication required",
                detail = "A valid service bearer token is required",
                code = "AUTHENTICATION_REQUIRED"
            )
        }
    }

    routing {
        get("/health/live") {
            call.respondJson(buildJsonObject { put("status", "UP") })
        }
        get("/health/ready") {
            call.respondJson(buildJsonObject { put("status", "UP") })
        }
        if (mercadoLivreOAuthBootstrap != null) {
            get("/v1/integrations/mercadolivre/oauth/callback") {
                try {
                    val result = mercadoLivreOAuthBootstrap.callback(
                        call.request.queryParameters["code"],
                        call.request.queryParameters["state"]
                    )
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(buildJsonObject {
                        put("status", "READY")
                        put("connectionId", result.connectionId.value.toString())
                        put("authorizedUserId", result.authorizedUserId)
                    })
                } catch (_: IllegalArgumentException) {
                    call.respondProblem(
                        HttpStatusCode.BadRequest,
                        "https://flooow.io/problems/invalid-mercado-livre-oauth-callback",
                        "Invalid Mercado Livre OAuth callback",
                        "The authorization callback could not be validated",
                        "INVALID_MERCADO_LIVRE_OAUTH_CALLBACK"
                    )
                } catch (_: IllegalStateException) {
                    call.respondProblem(
                        HttpStatusCode.BadRequest,
                        "https://flooow.io/problems/invalid-mercado-livre-oauth-callback",
                        "Invalid Mercado Livre OAuth callback",
                        "The authorization callback could not be completed",
                        "INVALID_MERCADO_LIVRE_OAUTH_CALLBACK"
                    )
                }
            }
        }
        authenticate("service-bearer") {
            if (crossSystemProductIdentityApi != null) {
                post(CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH) {
                    if (!call.request.contentType().withoutParameters().match(ContentType.Application.Json) ||
                        call.request.queryParameters.names().isNotEmpty()
                    ) {
                        throw CrossSystemProductIdentityMalformedException(
                            "Product identity decisions require JSON and accept no query"
                        )
                    }
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    val result = crossSystemProductIdentityApi.record(
                        principal.organizationId,
                        call.receiveText()
                    )
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(result.body, result.status)
                }
                get("$CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH/{decisionId}") {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    val result = crossSystemProductIdentityApi.find(
                        principal.organizationId,
                        call.parameters["decisionId"].orEmpty()
                    )
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(result.body, result.status)
                }
            }
            if (mercadoLivreCredentialRotationApi != null) {
                post(MERCADO_LIVRE_CREDENTIAL_ROTATION_PATH) {
                    if (
                        call.request.queryParameters.names().isNotEmpty() ||
                        call.receiveText().isNotEmpty()
                    ) {
                        throw MalformedRequestException(
                            "Mercado Livre credential rotation accepts no request body or query"
                        )
                    }

                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(
                        mercadoLivreCredentialRotationApi.rotate(principal.organizationId)
                    )
                }
            }

            if (mercadoLivreOAuthBootstrap != null) {
                get("/v1/integrations/mercadolivre/oauth/start") {
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(buildJsonObject {
                        put("authorizationUrl", mercadoLivreOAuthBootstrap.start().authorizationUrl)
                    })
                }
            }
            if (omieStaticCredentialBootstrap != null) {
                post("/v1/integrations/omie/bootstrap") {
                    if (!call.request.contentType().withoutParameters()
                            .match(ContentType.Application.Json)
                    ) {
                        throw UnsupportedMediaTypeException()
                    }
                    val request = decodeOmieBootstrapRequest(call.receiveText())
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    val result = omieStaticCredentialBootstrap.bootstrap(
                        principal.organizationId,
                        request.appKey,
                        request.appSecret
                    )
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(buildJsonObject {
                        put("status", "READY")
                        put("connectionId", result.connectionId.value.toString())
                    }, HttpStatusCode.Created)
                }
            }
            if (omieEvidenceRefreshApi != null) {
                post(OMIE_EVIDENCE_REFRESH_PATH) {
                    if (call.request.queryParameters.names().isNotEmpty() ||
                        call.receiveText().isNotEmpty()
                    ) {
                        throw MalformedRequestException("Omie refresh accepts no request body or query")
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>()).organizationId
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(omieEvidenceRefreshApi.refresh(organizationId))
                }
            }
            if (omieEvidenceReacquisitionApi != null) {
                post(OMIE_EVIDENCE_REACQUISITION_PATH) {
                    if (call.request.queryParameters.names().isNotEmpty() || call.receiveText().isNotEmpty()) {
                        throw MalformedRequestException("Omie reacquisition accepts no request body or query")
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>()).organizationId
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(omieEvidenceReacquisitionApi.refresh(organizationId))
                }
            }
            if (omieProductCostRefreshApi != null) {
                post(OMIE_PRODUCT_COST_REFRESH_PATH) {
                    if (call.request.queryParameters.names().isNotEmpty() || call.receiveText().isNotEmpty()) {
                        throw MalformedRequestException("Omie product-cost refresh accepts no request body or query")
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>()).organizationId
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(omieProductCostRefreshApi.refresh(organizationId))
                }
            }
            if (commerceIdentityRecomputeApi != null) {
                post("/v1/commerce-identity/recompute") {
                    if (call.request.queryParameters.names().isNotEmpty() ||
                        call.receiveText().isNotEmpty()
                    ) {
                        throw MalformedRequestException("Commerce identity recompute accepts no request body or query")
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>()).organizationId
                    call.response.header("Cache-Control", "no-store")
                    try {
                        call.respondJson(commerceIdentityRecomputeApi.recompute(organizationId))
                    } catch (failure: CommerceIdentityRecomputeFailureException) {
                        COMMERCE_IDENTITY_LOGGER.warning("commerce_identity_recompute_failure category=${failure.category}")
                        throw failure
                    } catch (_: Exception) {
                        COMMERCE_IDENTITY_LOGGER.warning("commerce_identity_recompute_failure category=UNKNOWN")
                        throw CommerceIdentityRecomputeFailureException(CommerceIdentityFailureCategory.UNKNOWN)
                    }
                }
            }
            get("/openapi.json") {
                val openApi = requireNotNull(
                    Application::class.java.getResource("/openapi.json")
                ) { "Committed OpenAPI resource is missing" }.readText()
                call.respondText(openApi, jsonContentType, HttpStatusCode.OK)
            }
            post(ASSESSMENT_PATH) {
                if (!call.request.contentType().withoutParameters()
                        .match(ContentType.Application.Json)
                ) {
                    throw UnsupportedMediaTypeException()
                }

                val request = decodeRequest(call.receiveText())
                val input = try {
                    request.toDomain()
                } catch (error: IllegalArgumentException) {
                    throw DomainValidationException(
                        error.message ?: "Inventory risk request is invalid"
                    )
                }
                val organizationId = requireNotNull(call.principal<ServicePrincipal>())
                    .organizationId
                val recorded = record(organizationId, input)
                call.response.header(
                    "Location",
                    "$ASSESSMENT_PATH/${recorded.assessmentId}"
                )
                call.respondJson(recordedAssessmentJson(recorded), HttpStatusCode.Created)
            }
            get("$ASSESSMENT_PATH/{assessmentId}") {
                val assessmentId = canonicalAssessmentId(
                    call.parameters["assessmentId"].orEmpty()
                )
                val organizationId = requireNotNull(call.principal<ServicePrincipal>())
                    .organizationId
                val recorded = findById(organizationId, assessmentId) ?:
                    throw AssessmentNotFoundException()
                call.respondJson(recordedAssessmentJson(recorded))
            }
            if (salesIntelligenceApi != null) {
                get(SALES_INTELLIGENCE_ORDERS_PATH) {
                    val query = call.request.queryParameters
                    if (query.names().any { it !in setOf("cursor", "limit") } ||
                        query.getAll("cursor").orEmpty().size > 1 ||
                        query.getAll("limit").orEmpty().size > 1
                    ) {
                        throw InvalidSalesIntelligenceCursorException()
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>())
                        .organizationId
                    call.respondJson(
                        salesIntelligenceApi.list(
                            organizationId,
                            query["cursor"],
                            query["limit"]
                        )
                    )
                }
                get("$SALES_INTELLIGENCE_ORDERS_PATH/{marketplaceOrderId}") {
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>())
                        .organizationId
                    call.respondJson(
                        salesIntelligenceApi.detail(
                            organizationId,
                            call.parameters["marketplaceOrderId"].orEmpty()
                        )
                    )
                }
                post(SALES_INTELLIGENCE_REFRESH_PATH) {
                    if (call.request.queryParameters.names().isNotEmpty() ||
                        call.receiveText().isNotEmpty()
                    ) {
                        throw MalformedRequestException("Refresh accepts no request body or query")
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>())
                        .organizationId
                    call.respondJson(salesIntelligenceApi.refresh(organizationId))
                }
                post(SALES_INTELLIGENCE_REACQUISITION_PATH) {
                    if (call.request.queryParameters.names().isNotEmpty() || call.receiveText().isNotEmpty()) {
                        throw MalformedRequestException("Sales Intelligence reacquisition accepts no request body or query")
                    }
                    val organizationId = requireNotNull(call.principal<ServicePrincipal>()).organizationId
                    call.response.header("Cache-Control", "no-store")
                    call.respondJson(salesIntelligenceApi.reacquire(organizationId))
                }
            }
            if (reconciliationCasesApi != null) {
                get(RECONCILIATION_CASES_PATH) {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.respondJson(reconciliationCasesApi.list(principal.organizationId, call.request.queryParameters["cursor"], call.request.queryParameters["limit"]))
                }
                get("$RECONCILIATION_CASES_PATH/{caseId}") {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.respondJson(reconciliationCasesApi.detail(principal.organizationId, call.parameters["caseId"].orEmpty()))
                }
            }
            if (systemicDivergencesApi != null) {
                get(SYSTEMIC_DIVERGENCES_PATH) {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.respondJson(systemicDivergencesApi.list(principal.organizationId, call.request.queryParameters["cursor"], call.request.queryParameters["limit"]))
                }
                get("$SYSTEMIC_DIVERGENCES_PATH/{signalId}") {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.respondJson(systemicDivergencesApi.detail(principal.organizationId, call.parameters["signalId"].orEmpty()))
                }
            }
            if (commerceIdentityHealthApi != null) {
                get("/v1/commerce-identity/health") {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.respondJson(commerceIdentityHealthApi.health(principal.organizationId))
                }
                get("/v1/commerce-identity/relations") {
                    val principal = requireNotNull(call.principal<ServicePrincipal>())
                    call.respondJson(commerceIdentityHealthApi.relations(principal.organizationId))
                }
            }
        }
    }
}

internal fun mercadoLivreConnectionFromEnvironment(
    environment: Map<String, String> = System.getenv()
): IntegrationConnectionId {
    val value = requireNotNull(mercadoLivreConnectionFromEnvironmentOrNull(environment)) {
        "FLOOOW_MERCADO_LIVRE_CONNECTION_ID is required"
    }
    return value
}

internal fun mercadoLivreConnectionFromEnvironmentOrNull(
    environment: Map<String, String> = System.getenv()
): IntegrationConnectionId? = parseOptionalConnectionId(
    environment["FLOOOW_MERCADO_LIVRE_CONNECTION_ID"],
    "FLOOOW_MERCADO_LIVRE_CONNECTION_ID"
)

internal fun omieConnectionFromEnvironmentOrNull(
    environment: Map<String, String> = System.getenv()
): IntegrationConnectionId? = parseOptionalConnectionId(
    environment["FLOOOW_OMIE_CONNECTION_ID"],
    "FLOOOW_OMIE_CONNECTION_ID"
)

private fun parseOptionalConnectionId(value: String?, name: String): IntegrationConnectionId? {
    if (value.isNullOrBlank()) return null
    return try {
        val parsed = UUID.fromString(value)
        require(parsed.toString() == value)
        IntegrationConnectionId(parsed)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException(
            "$name must be a canonical UUID"
        )
    }
}

internal fun mercadoLivreOAuthBootstrapConfigurationOrNull(
    environment: Map<String, String>
): MercadoLivreOAuthBootstrapConfiguration? {
    val configured = listOf(
        "FLOOOW_MERCADO_LIVRE_CLIENT_ID",
        "FLOOOW_MERCADO_LIVRE_CLIENT_SECRET",
        "FLOOOW_MERCADO_LIVRE_REDIRECT_URI"
    ).map { !environment[it].isNullOrBlank() }
    if (configured.none { it }) return null
    require(configured.all { it }) { "Mercado Livre OAuth bootstrap configuration is incomplete" }
    return MercadoLivreOAuthBootstrapConfiguration.fromEnvironment(environment)
}

internal suspend fun io.ktor.server.application.ApplicationCall.respondJson(
    body: JsonElement,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(json.encodeToString(JsonElement.serializer(), body), jsonContentType, status)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondProblem(
    status: HttpStatusCode,
    type: String,
    title: String,
    detail: String,
    code: String
) {
    val body = buildJsonObject {
        put("type", type)
        put("title", title)
        put("status", status.value)
        put("detail", detail)
        put("instance", request.path())
        put("code", code)
    }
    respondText(
        json.encodeToString(JsonElement.serializer(), body),
        problemContentType.withCharset(Charsets.UTF_8),
        status
    )
}

private data class InventoryRiskRequest(
    val sku: String,
    val periodEnd: LocalDate,
    val targetUnits: Int,
    val unitsSold: Int,
    val availableUnits: Int,
    val dailySalesVelocity: Int,
    val observedOn: LocalDate,
    val expectedReplenishmentOn: LocalDate
) {
    fun toDomain() = InventoryRiskInput(
        sku = sku,
        periodEnd = periodEnd,
        targetUnits = targetUnits,
        unitsSold = unitsSold,
        availableUnits = availableUnits,
        dailySalesVelocity = dailySalesVelocity,
        observedOn = observedOn,
        expectedReplenishmentOn = expectedReplenishmentOn
    )
}

private fun decodeRequest(body: String): InventoryRiskRequest {
    val objectNode = try {
        json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        throw MalformedRequestException("Request body must be a valid JSON object")
    }

    val required = setOf(
        "sku",
        "periodEnd",
        "targetUnits",
        "unitsSold",
        "availableUnits",
        "dailySalesVelocity",
        "observedOn",
        "expectedReplenishmentOn"
    )
    val unknown = objectNode.keys - required
    if (unknown.isNotEmpty()) {
        throw MalformedRequestException("Unknown request property: ${unknown.sorted().first()}")
    }
    val missing = required - objectNode.keys
    if (missing.isNotEmpty()) {
        throw MalformedRequestException("Missing required property: ${missing.sorted().first()}")
    }

    return InventoryRiskRequest(
        sku = objectNode.requiredString("sku"),
        periodEnd = objectNode.requiredDate("periodEnd"),
        targetUnits = objectNode.requiredInt("targetUnits"),
        unitsSold = objectNode.requiredInt("unitsSold"),
        availableUnits = objectNode.requiredInt("availableUnits"),
        dailySalesVelocity = objectNode.requiredInt("dailySalesVelocity"),
        observedOn = objectNode.requiredDate("observedOn"),
        expectedReplenishmentOn = objectNode.requiredDate("expectedReplenishmentOn")
    )
}

private fun JsonObject.requiredString(name: String): String = try {
    getValue(name).jsonPrimitive.content.also {
        if (!getValue(name).jsonPrimitive.isString) {
            throw IllegalArgumentException()
        }
    }
} catch (_: Exception) {
    throw MalformedRequestException("Property '$name' must be a string")
}

private fun JsonObject.requiredInt(name: String): Int = try {
    getValue(name).jsonPrimitive.int
} catch (_: Exception) {
    throw MalformedRequestException("Property '$name' must be an integer")
}

private fun JsonObject.requiredDate(name: String): LocalDate {
    val value = requiredString(name)
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw MalformedRequestException("Property '$name' must be an ISO-8601 date")
    }
}

private data class OmieBootstrapRequest(val appKey: String, val appSecret: String)

private fun decodeOmieBootstrapRequest(body: String): OmieBootstrapRequest {
    val objectNode = try {
        json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        throw MalformedRequestException("Request body must be a valid JSON object")
    }
    val required = setOf("appKey", "appSecret")
    val unknown = objectNode.keys - required
    if (unknown.isNotEmpty()) {
        throw MalformedRequestException("Unknown request property: ${unknown.sorted().first()}")
    }
    val missing = required - objectNode.keys
    if (missing.isNotEmpty()) {
        throw MalformedRequestException("Missing required property: ${missing.sorted().first()}")
    }
    val appKey = objectNode.requiredString("appKey")
    val appSecret = objectNode.requiredString("appSecret")
    if (appKey.isBlank() || appSecret.isBlank()) {
        throw MalformedRequestException("Credential properties must not be blank")
    }
    return OmieBootstrapRequest(appKey, appSecret)
}

private fun recordedAssessmentJson(assessment: RecordedInventoryRiskAssessment): JsonObject {
    val selected = assessment.recommendation
    return buildJsonObject {
        put("assessmentId", assessment.assessmentId)
        put("recordedAt", assessment.recordedAt.toString())
        put("sku", assessment.input.sku)
        put("observedOn", assessment.input.observedOn.toString())
        put("projection", buildJsonObject {
            put("stockCoverageDays", assessment.projection.stockCoverageDays)
            put("projectedStockoutOn", assessment.projection.projectedStockoutOn.toString())
            put("expectedReplenishmentOn", assessment.projection.expectedReplenishmentOn.toString())
            put("projectedStockoutDays", assessment.projection.projectedStockoutDays)
            put("unitsPotentiallyUnavailable", assessment.projection.unitsPotentiallyUnavailable)
            put("unitsRemainingToGoal", assessment.projection.unitsRemainingToGoal)
            put("unitsAtRiskAgainstGoal", assessment.projection.unitsAtRiskAgainstGoal)
            put("shortageProjected", assessment.projection.shortageProjected)
        })
        put("recommendation", buildJsonObject {
            put("type", selected.type.name)
            put("explanation", selected.explanation)
            put("expectedUnitsPreserved", selected.expectedUnitsPreserved)
        })
        put("expectedImpact", assessment.expectedImpact)
        put("trace", buildJsonArray {
            assessment.trace.forEach { add(JsonPrimitive(it)) }
        })
    }
}

private fun canonicalAssessmentId(value: String): String {
    val parsed = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw MalformedAssessmentIdException("Assessment identifier must be a canonical UUID")
    }
    if (parsed.toString() != value) {
        throw MalformedAssessmentIdException("Assessment identifier must be a canonical UUID")
    }
    return value
}

private class InMemoryAssessmentJournal : InventoryRiskAssessmentJournal {
    private val records = linkedMapOf<Pair<OrganizationId, String>, RecordedInventoryRiskAssessment>()

    override fun append(
        organizationId: OrganizationId,
        record: RecordedInventoryRiskAssessment
    ) {
        require(record.organizationId == organizationId)
        check(records.putIfAbsent(organizationId to record.assessmentId, record) == null)
    }

    override fun findById(
        organizationId: OrganizationId,
        assessmentId: String
    ): RecordedInventoryRiskAssessment? = records[organizationId to assessmentId]
}

private val COMMERCE_IDENTITY_LOGGER: Logger = Logger.getLogger("io.flooow.marketplace.api.commerce-identity")

private class MalformedRequestException(message: String) : RuntimeException(message)
private class DomainValidationException(message: String) : RuntimeException(message)
private class UnsupportedMediaTypeException : RuntimeException()
private class MalformedAssessmentIdException(message: String) : RuntimeException(message)
private class AssessmentNotFoundException : RuntimeException()

internal const val TEST_SERVICE_TOKEN =
    "test-only-service-token-00000000000000000000000000000000"
internal val TEST_ORGANIZATION_ID =
    OrganizationId.parse("11111111-1111-4111-8111-111111111111")
