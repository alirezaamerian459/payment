package ir.privatepay.sandbox;

import java.util.Map;
import java.util.UUID;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("sandbox")
@RequestMapping("/api/v1/sandbox")
public class SandboxController {
    private final SandboxService service;
    private final SandboxScenarioRunner scenarios;
    private final DemoAuthorization authorization;

    public SandboxController(SandboxService service, SandboxScenarioRunner scenarios, DemoAuthorization authorization) {
        this.service = service;
        this.scenarios = scenarios;
        this.authorization = authorization;
    }

    @GetMapping("/state")
    public Map<String, Object> state() { return service.state(); }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.mobile(), request.code());
    }

    @PostMapping("/applications")
    public Map<String, Object> submitApplication(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                                  @Valid @RequestBody ApplicationRequest request) {
        authorization.requireRole(role, "CUSTOMER");
        return service.submitApplication(request.requestedLimitToman());
    }

    @PostMapping("/applications/{applicationId}/review")
    public Map<String, Object> reviewApplication(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                                   @PathVariable UUID applicationId, @Valid @RequestBody ReviewRequest request) {
        authorization.requireRole(role, "CREDIT_REVIEWER", "SUPER_ADMIN");
        return service.reviewApplication(applicationId, request.decision(), request.reason());
    }

    @PostMapping("/purchases")
    public Map<String, Object> purchase(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                        @Valid @RequestBody PurchaseRequest request) {
        authorization.requireRole(role, "CUSTOMER", "CASHIER");
        UUID branchId = request.branchId() == null ? SandboxService.DEMO_BRANCH : request.branchId();
        return service.purchase(request.amountToman(), request.description(), request.idempotencyKey(), request.providerResult(), branchId);
    }

    @PostMapping("/payments/callback")
    public Map<String, Object> paymentCallback(@Valid @RequestBody CallbackRequest request) {
        return service.providerCallback(request.intentId(), request.providerEventId(), request.result());
    }

    @PostMapping("/repayments")
    public Map<String, Object> repay(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                      @Valid @RequestBody RepaymentRequest request) {
        authorization.requireRole(role, "CUSTOMER");
        return service.repay(request.amountToman(), request.idempotencyKey(), request.providerResult());
    }

    @PostMapping("/refunds")
    public Map<String, Object> requestRefund(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                              @Valid @RequestBody RefundRequest request) {
        authorization.requireRole(role, "CUSTOMER", "BRANCH_MANAGER");
        return service.requestRefund(request.orderId(), request.amountToman(), request.reason(), request.idempotencyKey());
    }

    @PostMapping("/refunds/{refundId}/review")
    public Map<String, Object> reviewRefund(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                             @PathVariable UUID refundId, @Valid @RequestBody RefundReviewRequest request) {
        authorization.requireRole(role, "FINANCE_OFFICER", "SUPER_ADMIN");
        return service.processRefund(refundId, request.action(), request.reason());
    }

    @PostMapping("/admin/credit/{status}")
    public Map<String, Object> creditStatus(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                             @PathVariable String status, @Valid @RequestBody ReasonRequest request) {
        authorization.requireRole(role, "SUPER_ADMIN");
        return service.setCreditStatus(status.toUpperCase(), request.reason());
    }

    @PostMapping("/admin/funding-circuit")
    public Map<String, Object> fundingCircuit(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                               @Valid @RequestBody CircuitRequest request) {
        authorization.requireRole(role, "SUPER_ADMIN");
        return service.setFundingCircuit(request.enabled(), request.reason());
    }

    @PostMapping("/admin/reconcile")
    public Map<String, Object> reconcile(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                          @Valid @RequestBody ReconcileRequest request) {
        authorization.requireRole(role, "FINANCE_OFFICER", "SUPER_ADMIN");
        return service.reconcile(request.reference(), request.expectedToman(), request.observedToman());
    }

    @PostMapping("/admin/overdue")
    public Map<String, Object> refreshOverdue(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role) {
        authorization.requireRole(role, "FINANCE_OFFICER", "SUPER_ADMIN");
        return service.refreshOverdue();
    }

    @PostMapping("/admin/jobs/recover")
    public Map<String, Object> recoverJobs(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role) {
        authorization.requireRole(role, "SUPER_ADMIN");
        return service.processReadyJobs(50);
    }

    @GetMapping("/scenarios/results")
    public List<Map<String, Object>> scenarioResults(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role) {
        authorization.requireRole(role, "SUPER_ADMIN");
        return scenarios.latestRuns();
    }

    @PostMapping("/scenarios/{scenarioKey}")
    public Map<String, Object> runScenario(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
                                            @PathVariable String scenarioKey) {
        authorization.requireRole(role, "SUPER_ADMIN");
        return scenarios.run(scenarioKey);
    }

    @PostMapping("/scenarios/run")
    public Map<String, Object> runAllScenarios(@RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role) {
        authorization.requireRole(role, "SUPER_ADMIN");
        return Map.of("results", scenarios.runAll());
    }

    public record LoginRequest(@NotBlank String mobile, @NotBlank String code) { }
    public record ApplicationRequest(@Positive long requestedLimitToman) { }
    public record PurchaseRequest(@Positive long amountToman, String description, @NotBlank String idempotencyKey,
                                  @NotBlank String providerResult, UUID branchId) { }
    public record CallbackRequest(UUID intentId, @NotBlank String providerEventId, @NotBlank String result) { }
    public record RepaymentRequest(@Positive long amountToman, @NotBlank String idempotencyKey, @NotBlank String providerResult) { }
    public record RefundRequest(UUID orderId, @Positive long amountToman, @NotBlank String reason, @NotBlank String idempotencyKey) { }
    public record ReviewRequest(@NotBlank String decision, @NotBlank String reason) { }
    public record RefundReviewRequest(@NotBlank String action, @NotBlank String reason) { }
    public record ReasonRequest(@NotBlank String reason) { }
    public record CircuitRequest(boolean enabled, @NotBlank String reason) { }
    public record ReconcileRequest(@NotBlank String reference, long expectedToman, long observedToman) { }
}
