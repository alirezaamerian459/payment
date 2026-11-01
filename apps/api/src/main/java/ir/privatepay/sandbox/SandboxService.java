package ir.privatepay.sandbox;

import java.sql.Date;
import java.sql.Array;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SandboxService {
    public static final UUID DEMO_CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID DEMO_BRANCH = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String POLICY_VERSION = "sandbox-four-payments-v1";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SandboxService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> state() {
        return state(DEMO_CUSTOMER);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> state(UUID customerId) {
        Map<String, Object> customer = jdbc.queryForMap("SELECT id, mobile, display_name FROM customers WHERE id = ?", customerId);
        Map<String, Object> credit = jdbc.queryForMap("""
                SELECT tier, limit_irr, outstanding_irr, reserved_irr, status,
                       limit_irr - outstanding_irr - reserved_irr AS available_irr
                FROM credit_accounts WHERE customer_id = ?
                """, customerId);
        Map<String, Object> funding = jdbc.queryForMap("""
                SELECT budget_irr, committed_irr, reserved_irr,
                       budget_irr - committed_irr - reserved_irr AS available_irr,
                       new_financing_enabled
                FROM funding_control WHERE singleton_id = 1
                """);
        List<Map<String, Object>> orders = jdbc.queryForList("""
                SELECT id, amount_irr, description, status, created_at
                FROM orders WHERE customer_id = ? ORDER BY created_at DESC LIMIT 20
                """, customerId);
        List<Map<String, Object>> installments = jdbc.queryForList("""
                SELECT i.id, i.sequence_number, i.due_date, i.original_amount_irr,
                       i.adjusted_amount_irr, i.paid_irr, i.status, p.id AS plan_id,
                       p.order_id, o.description
                FROM installments i
                JOIN installment_plans p ON p.id = i.plan_id
                JOIN orders o ON o.id = p.order_id
                WHERE o.customer_id = ? AND p.status IN ('ACTIVE', 'ADJUSTED', 'SETTLED')
                ORDER BY i.due_date, i.sequence_number
                """, customerId);
        List<Map<String, Object>> applications = jdbc.query("""
                SELECT id, state, requested_limit_irr, policy_version, reason_codes, created_at
                FROM credit_applications WHERE customer_id = ? ORDER BY created_at DESC LIMIT 10
                """, (rs, rowNum) -> {
            Map<String, Object> application = new LinkedHashMap<>();
            application.put("id", rs.getObject("id"));
            application.put("state", rs.getString("state"));
            application.put("requested_limit_irr", rs.getLong("requested_limit_irr"));
            application.put("policy_version", rs.getString("policy_version"));
            Array reasonCodes = rs.getArray("reason_codes");
            try {
                application.put("reason_codes", reasonCodes == null
                        ? List.of() : Arrays.asList((Object[]) reasonCodes.getArray()));
            } finally {
                if (reasonCodes != null) reasonCodes.free();
            }
            application.put("created_at", rs.getTimestamp("created_at"));
            return application;
        }, customerId);
        List<Map<String, Object>> refunds = jdbc.queryForList("""
                SELECT r.id, r.order_id, r.amount_irr, r.status, r.reason, r.created_at, o.description
                FROM refund_requests r JOIN orders o ON o.id = r.order_id
                WHERE o.customer_id = ? ORDER BY r.created_at DESC LIMIT 10
                """, customerId);
        List<Map<String, Object>> activity = jdbc.queryForList("""
                SELECT action, entity_type, entity_id, reason, occurred_at
                FROM audit_events
                WHERE entity_id = ? OR entity_id IN (
                    SELECT id::text FROM orders WHERE customer_id = ?
                    UNION SELECT id::text FROM credit_applications WHERE customer_id = ?
                    UNION SELECT r.id::text FROM refund_requests r JOIN orders o ON o.id = r.order_id WHERE o.customer_id = ?
                    UNION SELECT r.id::text FROM repayments r WHERE r.customer_id = ?
                )
                ORDER BY occurred_at DESC LIMIT 12
                """, customerId.toString(), customerId, customerId, customerId, customerId);
        return Map.of("customer", customer, "credit", credit, "funding", funding,
                "orders", orders, "installments", installments, "applications", applications,
                "refunds", refunds, "activity", activity, "sandbox", true);
    }

    public Map<String, Object> login(String mobile, String code) {
        if (!"00000000000".equals(mobile) || !"246810".equals(code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "کد آزمایشی درست نیست.");
        }
        audit("sandbox-customer", "SANDBOX_LOGIN", "customer", DEMO_CUSTOMER.toString(), "ورود با کد یک‌بارمصرف آزمایشی");
        return Map.of("customerId", DEMO_CUSTOMER, "displayName", "کاربر آزمایشی",
                "sandbox", true, "loginMode", "FIXED_DEMO_OTP");
    }

    @Transactional
    public Map<String, Object> submitApplication(long requestedLimitToman) {
        if (requestedLimitToman <= 0 || requestedLimitToman > 40_000_000L) {
            throw badRequest("سقف درخواستی باید بین ۱ تا ۴۰ میلیون تومان باشد.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO credit_applications (id, customer_id, state, requested_limit_irr, policy_version, reason_codes)
                VALUES (?, ?, 'MANUAL_REVIEW', ?, ?, ARRAY['SANDBOX_MANUAL_REVIEW'])
                """, id, DEMO_CUSTOMER, tomanToRial(requestedLimitToman), POLICY_VERSION);
        audit("customer", "APPLICATION_SUBMITTED", "credit_application", id.toString(), "درخواست اعتبار آزمایشی");
        return jdbc.queryForMap("SELECT id, state, requested_limit_irr, policy_version FROM credit_applications WHERE id = ?", id);
    }

    @Transactional
    public Map<String, Object> reviewApplication(UUID applicationId, String decision, String reason) {
        Map<String, Object> application = jdbc.queryForMap("SELECT * FROM credit_applications WHERE id = ? FOR UPDATE", applicationId);
        String next = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "DECLINE" -> "DECLINED";
            case "MORE_INFO" -> "MORE_INFO";
            default -> throw badRequest("تصمیم بازبینی معتبر نیست.");
        };
        jdbc.update("UPDATE credit_applications SET state = ?, reviewer_reason = ?, updated_at = now() WHERE id = ?",
                next, reason, applicationId);
        if (!List.of("MANUAL_REVIEW", "SUBMITTED", "MORE_INFO").contains(application.get("state"))) {
            throw conflict("این درخواست قبلاً بررسی شده است.");
        }
        if ("APPROVE".equals(decision)) {
            long requested = ((Number) application.get("requested_limit_irr")).longValue();
            long allowed = Math.min(requested, 400_000_000L);
            jdbc.update("UPDATE credit_accounts SET limit_irr = GREATEST(limit_irr, ?), status = 'ACTIVE', updated_at = now() WHERE customer_id = ?",
                    allowed, application.get("customer_id"));
        }
        audit("credit-reviewer", "APPLICATION_" + next, "credit_application", applicationId.toString(), reason);
        return Map.of("id", applicationId, "state", next, "reason", reason);
    }

    @Transactional
    public Map<String, Object> purchase(long amountToman, String description, String idempotencyKey,
                                        String providerResult, UUID branchId) {
        return purchaseFor(DEMO_CUSTOMER, amountToman, description, idempotencyKey, providerResult, branchId);
    }

    @Transactional
    public Map<String, Object> purchaseFor(UUID customerId, long amountToman, String description, String idempotencyKey,
                                           String providerResult, UUID branchId) {
        validateIdempotency(idempotencyKey);
        var existing = jdbc.queryForList("SELECT id, status FROM orders WHERE idempotency_key = ?", idempotencyKey);
        if (!existing.isEmpty()) return Map.of("orderId", existing.getFirst().get("id"), "status", existing.getFirst().get("status"), "duplicate", true);
        if (amountToman < 4 || amountToman > 40_000_000L) throw badRequest("مبلغ خرید باید بین ۴ تومان و ۴۰ میلیون تومان باشد.");
        String result = requireProviderResult(providerResult);
        long amount = tomanToRial(amountToman);
        long first = amount / 4;
        if (first <= 0) throw badRequest("مبلغ برای تقسیم به چهار قسط کافی نیست.");
        Map<String, Object> account = lockAccount(customerId);
        var concurrentDuplicate = jdbc.queryForList("SELECT id, status FROM orders WHERE idempotency_key = ?", idempotencyKey);
        if (!concurrentDuplicate.isEmpty()) return Map.of("orderId", concurrentDuplicate.getFirst().get("id"), "status", concurrentDuplicate.getFirst().get("status"), "duplicate", true);
        if (!account.get("branch_id").equals(branchId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "این شعبه به حساب آزمایشی دسترسی ندارد.");
        Map<String, Object> funding = jdbc.queryForMap("SELECT * FROM funding_control WHERE singleton_id = 1 FOR UPDATE");
        long availableCredit = number(account, "limit_irr") - number(account, "outstanding_irr") - number(account, "reserved_irr");
        long availableFunding = number(funding, "budget_irr") - number(funding, "committed_irr") - number(funding, "reserved_irr");
        if (!Boolean.TRUE.equals(funding.get("new_financing_enabled"))) throw conflict("اعتباردهی آزمایشی متوقف شده است.");
        if (!"ACTIVE".equals(account.get("status"))) throw conflict("اعتبار این حساب موقتاً متوقف است.");
        if (amount > availableCredit) throw conflict("مبلغ خرید از اعتبار در دسترس بیشتر است.");
        if (amount > availableFunding) throw conflict("بودجهٔ آزمایشی شرکت برای این خرید کافی نیست.");

        UUID orderId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        jdbc.update("UPDATE credit_accounts SET reserved_irr = reserved_irr + ?, updated_at = now() WHERE customer_id = ?", amount, customerId);
        jdbc.update("UPDATE funding_control SET reserved_irr = reserved_irr + ?, updated_at = now() WHERE singleton_id = 1", amount);
        jdbc.update("INSERT INTO orders (id, customer_id, branch_id, amount_irr, description, status, idempotency_key) VALUES (?, ?, ?, ?, ?, 'PAYMENT_PENDING', ?)",
                orderId, customerId, branchId, amount, cleanDescription(description), idempotencyKey);
        jdbc.update("INSERT INTO payment_intents (id, order_id, amount_irr, first_installment_irr, status, provider_reference) VALUES (?, ?, ?, ?, 'PENDING', ?)",
                intentId, orderId, amount, first, "sandbox-" + intentId);
        jdbc.update("INSERT INTO payment_attempts (intent_id, attempt_number, result) VALUES (?, 1, ?)", intentId, result);
        if ("TIMEOUT".equals(result)) {
            jdbc.update("UPDATE payment_intents SET status = 'TIMED_OUT', updated_at = now() WHERE id = ?", intentId);
            audit("sandbox-provider", "PAYMENT_TIMEOUT", "payment_intent", intentId.toString(), "وضعیت پرداخت تا استعلام نامشخص است");
            return Map.of("orderId", orderId, "intentId", intentId, "status", "PAYMENT_PENDING", "reservedIrr", amount);
        }
        if ("FAILURE".equals(result)) {
            releaseReservation(orderId, amount);
            jdbc.update("UPDATE orders SET status = 'FAILED' WHERE id = ?", orderId);
            jdbc.update("UPDATE payment_intents SET status = 'FAILED', updated_at = now() WHERE id = ?", intentId);
            audit("sandbox-provider", "PAYMENT_FAILED", "order", orderId.toString(), "پرداخت اول ناموفق بود");
            return Map.of("orderId", orderId, "intentId", intentId, "status", "FAILED");
        }
        finalizePayment(intentId, "local-success-" + intentId);
        return Map.of("orderId", orderId, "intentId", intentId, "status", "SUCCEEDED");
    }

    @Transactional
    public Map<String, Object> providerCallback(UUID intentId, String eventId, String result) {
        if (!List.of("SUCCESS", "FAILURE").contains(result)) throw badRequest("callback فقط نتیجهٔ قطعی موفق یا ناموفق می‌پذیرد.");
        if (eventId == null || eventId.isBlank() || eventId.length() > 120) throw badRequest("شناسهٔ رویداد ارائه‌دهنده معتبر نیست.");
        Map<String, Object> intent = jdbc.queryForMap("SELECT * FROM payment_intents WHERE id = ? FOR UPDATE", intentId);
        var priorEvent = jdbc.queryForList("SELECT intent_id FROM provider_events WHERE provider_event_id = ?", eventId);
        if (!priorEvent.isEmpty()) {
            if (!intentId.equals(priorEvent.getFirst().get("intent_id"))) throw conflict("شناسهٔ رویداد قبلاً به پرداخت دیگری متصل شده است.");
            return Map.of("intentId", intentId, "status", intent.get("status"), "duplicate", true);
        }
        int inserted = jdbc.update("INSERT INTO provider_events (provider_event_id, intent_id) VALUES (?, ?) ON CONFLICT DO NOTHING", eventId, intentId);
        if (inserted == 0) {
            UUID existingIntent = jdbc.queryForObject("SELECT intent_id FROM provider_events WHERE provider_event_id = ?", UUID.class, eventId);
            if (!intentId.equals(existingIntent)) throw conflict("شناسهٔ رویداد هم‌زمان به پرداخت دیگری متصل شد.");
            return Map.of("intentId", intentId, "status", intent.get("status"), "duplicate", true);
        }
        if ("SUCCEEDED".equals(intent.get("status"))) return Map.of("intentId", intentId, "status", "SUCCEEDED", "duplicate", false);
        UUID orderId = (UUID) intent.get("order_id");
        if ("SUCCESS".equals(result)) {
            finalizePayment(intentId, eventId);
            return Map.of("intentId", intentId, "status", "SUCCEEDED");
        }
        long amount = number(intent, "amount_irr");
        releaseReservation(orderId, amount);
        jdbc.update("UPDATE payment_intents SET status = 'FAILED', updated_at = now() WHERE id = ?", intentId);
        jdbc.update("UPDATE orders SET status = 'FAILED' WHERE id = ? AND status = 'PAYMENT_PENDING'", orderId);
        audit("sandbox-provider", "CALLBACK_FAILED", "payment_intent", intentId.toString(), "تأیید وضعیت ناموفق");
        return Map.of("intentId", intentId, "status", "FAILED");
    }

    private void finalizePayment(UUID intentId, String eventReference) {
        Map<String, Object> intent = jdbc.queryForMap("SELECT * FROM payment_intents WHERE id = ? FOR UPDATE", intentId);
        if ("SUCCEEDED".equals(intent.get("status"))) return;
        if (!List.of("PENDING", "TIMED_OUT").contains(intent.get("status"))) throw conflict("این پرداخت در وضعیت فعلی قابل نهایی‌سازی نیست.");
        UUID orderId = (UUID) intent.get("order_id");
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM orders WHERE id = ? FOR UPDATE", orderId);
        long amount = number(intent, "amount_irr");
        long first = number(intent, "first_installment_irr");
        long outstanding = amount - first;
        UUID customerId = (UUID) order.get("customer_id");
        lockAccount(customerId);
        jdbc.update("UPDATE credit_accounts SET reserved_irr = reserved_irr - ?, outstanding_irr = outstanding_irr + ?, updated_at = now() WHERE customer_id = ?",
                amount, outstanding, customerId);
        jdbc.update("UPDATE funding_control SET reserved_irr = reserved_irr - ?, committed_irr = committed_irr + ?, updated_at = now() WHERE singleton_id = 1",
                amount, outstanding);
        jdbc.update("UPDATE payment_intents SET status = 'SUCCEEDED', updated_at = now() WHERE id = ?", intentId);
        jdbc.update("UPDATE orders SET status = 'SUCCEEDED' WHERE id = ?", orderId);
        UUID planId = UUID.randomUUID();
        jdbc.update("INSERT INTO installment_plans (id, order_id, original_amount_irr, first_payment_irr, outstanding_irr, policy_version, agreement_reference, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                planId, orderId, amount, first, outstanding, POLICY_VERSION, "SANDBOX-" + planId);
        LocalDate baseDate = LocalDate.now(clock);
        long baseInstallment = amount / 4;
        for (int sequence = 1; sequence <= 4; sequence++) {
            long installmentAmount = sequence == 4 ? amount - (baseInstallment * 3) : baseInstallment;
            long paid = sequence == 1 ? first : 0;
            String status = sequence == 1 ? "PAID" : "DUE";
            jdbc.update("INSERT INTO installments (id, plan_id, sequence_number, due_date, original_amount_irr, adjusted_amount_irr, paid_irr, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), planId, sequence, Date.valueOf(baseDate.plusMonths(sequence - 1)), installmentAmount, installmentAmount, paid, status);
        }
        postJournal("purchase-" + orderId, "ثبت تعهد خرید آزمایشی", "CUSTOMER_RECEIVABLE", amount, "MERCHANT_PAYABLE", amount);
        postJournal("initial-payment-" + orderId, "پرداخت قسط اول در صندوق آزمایشی", "CASH", first, "CUSTOMER_RECEIVABLE", first);
        audit("sandbox-provider", "PAYMENT_FINALIZED", "order", orderId.toString(), "وضعیت ارائه‌دهندهٔ آزمایشی تأیید شد: " + eventReference);
    }

    @Transactional
    public Map<String, Object> repay(long amountToman, String idempotencyKey, String providerResult) {
        return repayFor(DEMO_CUSTOMER, amountToman, idempotencyKey, providerResult);
    }

    @Transactional
    public Map<String, Object> repayFor(UUID customerId, long amountToman, String idempotencyKey, String providerResult) {
        validateIdempotency(idempotencyKey);
        var existing = jdbc.queryForList("SELECT id, status FROM repayments WHERE idempotency_key = ?", idempotencyKey);
        if (!existing.isEmpty()) return Map.of("repaymentId", existing.getFirst().get("id"), "status", existing.getFirst().get("status"), "duplicate", true);
        long amount = tomanToRial(amountToman);
        if (amount <= 0) throw badRequest("مبلغ بازپرداخت باید بیشتر از صفر باشد.");
        String result = requireProviderResult(providerResult);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO repayments (id, customer_id, amount_irr, status, idempotency_key) VALUES (?, ?, ?, 'PENDING', ?)", id, customerId, amount, idempotencyKey);
        jdbc.update("INSERT INTO repayment_attempts (repayment_id, attempt_number, result) VALUES (?, 1, ?)", id, result);
        if ("FAILURE".equals(result)) {
            jdbc.update("UPDATE repayments SET status = 'FAILED' WHERE id = ?", id);
            return Map.of("repaymentId", id, "status", "FAILED");
        }
        if ("TIMEOUT".equals(result)) return Map.of("repaymentId", id, "status", "TIMED_OUT");
        Map<String, Object> account = lockAccount(customerId);
        long outstanding = number(account, "outstanding_irr");
        if (amount > outstanding) throw conflict("بازپرداخت از ماندهٔ بدهی بیشتر است.");
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT i.*, p.id AS plan_id FROM installments i JOIN installment_plans p ON p.id = i.plan_id
                WHERE p.status IN ('ACTIVE', 'ADJUSTED') AND i.adjusted_amount_irr > i.paid_irr
                  AND p.order_id IN (SELECT id FROM orders WHERE customer_id = ?)
                ORDER BY i.due_date, i.sequence_number FOR UPDATE
                """, customerId);
        long remaining = amount;
        Map<UUID, Long> planAllocations = new java.util.LinkedHashMap<>();
        for (Map<String, Object> installment : due) {
            if (remaining == 0) break;
            long dueAmount = number(installment, "adjusted_amount_irr") - number(installment, "paid_irr");
            long allocation = Math.min(remaining, dueAmount);
            UUID installmentId = (UUID) installment.get("id");
            jdbc.update("INSERT INTO repayment_allocations (repayment_id, installment_id, amount_irr) VALUES (?, ?, ?)", id, installmentId, allocation);
            long paidNow = number(installment, "paid_irr") + allocation;
            long totalDue = number(installment, "adjusted_amount_irr");
            String nextStatus = paidNow == totalDue ? "PAID" : "PARTIAL";
            jdbc.update("UPDATE installments SET paid_irr = ?, status = ? WHERE id = ?", paidNow, nextStatus, installmentId);
            UUID planId = (UUID) installment.get("plan_id");
            planAllocations.merge(planId, allocation, Long::sum);
            remaining -= allocation;
        }
        if (remaining > 0) throw conflict("مبلغ بازپرداخت به اقساط موجود تخصیص‌پذیر نیست.");
        jdbc.update("UPDATE repayments SET status = 'SUCCEEDED' WHERE id = ?", id);
        jdbc.update("UPDATE credit_accounts SET outstanding_irr = outstanding_irr - ?, updated_at = now() WHERE customer_id = ?", amount, customerId);
        jdbc.update("UPDATE funding_control SET committed_irr = committed_irr - ?, updated_at = now() WHERE singleton_id = 1", amount);
        for (Map.Entry<UUID, Long> allocation : planAllocations.entrySet()) {
            jdbc.update("UPDATE installment_plans p SET outstanding_irr = outstanding_irr - ?, status = CASE WHEN NOT EXISTS (SELECT 1 FROM installments i WHERE i.plan_id = p.id AND i.adjusted_amount_irr > i.paid_irr) THEN 'SETTLED' ELSE status END WHERE p.id = ?",
                    allocation.getValue(), allocation.getKey());
        }
        postJournal("repayment-" + id, "دریافت بازپرداخت آزمایشی", "CASH", amount, "CUSTOMER_RECEIVABLE", amount);
        audit("sandbox-provider", "REPAYMENT_POSTED", "repayment", id.toString(), "بازپرداخت آزمایشی با تخصیص خودکار ثبت شد");
        return Map.of("repaymentId", id, "status", "SUCCEEDED", "amountIrr", amount);
    }

    @Transactional
    public Map<String, Object> requestRefund(UUID orderId, long amountToman, String reason, String idempotencyKey) {
        return requestRefundFor(DEMO_CUSTOMER, orderId, amountToman, reason, idempotencyKey);
    }

    @Transactional
    public Map<String, Object> requestRefundFor(UUID customerId, UUID orderId, long amountToman, String reason, String idempotencyKey) {
        validateIdempotency(idempotencyKey);
        var existing = jdbc.queryForList("SELECT id, status FROM refund_requests WHERE idempotency_key = ?", idempotencyKey);
        if (!existing.isEmpty()) return Map.of("refundId", existing.getFirst().get("id"), "status", existing.getFirst().get("status"), "duplicate", true);
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM orders WHERE id = ? AND customer_id = ? FOR UPDATE", orderId, customerId);
        if (!List.of("SUCCEEDED", "PARTIALLY_REFUNDED").contains(order.get("status"))) {
            throw conflict("فقط خرید موفق امکان درخواست بازپرداخت وجه دارد.");
        }
        long amount = tomanToRial(amountToman);
        if (amount <= 0) throw badRequest("مبلغ بازپرداخت وجه باید بیشتر از صفر باشد.");
        Long alreadyCommitted = jdbc.queryForObject("SELECT COALESCE(sum(amount_irr), 0) FROM refund_requests WHERE order_id = ? AND status IN ('REQUESTED', 'APPROVED', 'PENDING_PROVIDER', 'COMPLETED')", Long.class, orderId);
        long committedRefunds = alreadyCommitted == null ? 0 : alreadyCommitted;
        if (amount + committedRefunds > number(order, "amount_irr")) throw conflict("مبلغ درخواست از مبلغ خرید باقیمانده بیشتر است.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO refund_requests (id, order_id, amount_irr, status, reason, idempotency_key) VALUES (?, ?, ?, 'REQUESTED', ?, ?)",
                id, orderId, amount, cleanDescription(reason), idempotencyKey);
        audit("customer", "REFUND_REQUESTED", "refund", id.toString(), reason);
        return Map.of("refundId", id, "status", "REQUESTED", "amountIrr", amount);
    }

    @Transactional
    public Map<String, Object> processRefund(UUID refundId, String action, String reason) {
        Map<String, Object> refund = jdbc.queryForMap("SELECT * FROM refund_requests WHERE id = ? FOR UPDATE", refundId);
        String status = String.valueOf(refund.get("status"));
        if ("APPROVE".equals(action)) {
            if (!"REQUESTED".equals(status)) throw conflict("این درخواست بازپرداخت وجه در وضعیت قابل تأیید نیست.");
            jdbc.update("UPDATE refund_requests SET status = 'APPROVED', updated_at = now() WHERE id = ?", refundId);
            audit("finance-officer", "REFUND_APPROVED", "refund", refundId.toString(), reason);
            return Map.of("refundId", refundId, "status", "APPROVED");
        }
        if ("DECLINE".equals(action)) {
            if (!"REQUESTED".equals(status)) throw conflict("این درخواست بازپرداخت وجه در وضعیت قابل رد نیست.");
            jdbc.update("UPDATE refund_requests SET status = 'DECLINED', updated_at = now() WHERE id = ?", refundId);
            audit("finance-officer", "REFUND_DECLINED", "refund", refundId.toString(), reason);
            return Map.of("refundId", refundId, "status", "DECLINED");
        }
        if (!"CONFIRM_SANDBOX_PROVIDER".equals(action) || !"APPROVED".equals(status)) {
            throw conflict("بازپرداخت وجه فقط پس از تأیید مالی و پاسخ ارائه‌دهندهٔ آزمایشی نهایی می‌شود.");
        }
        UUID orderId = (UUID) refund.get("order_id");
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM orders WHERE id = ? FOR UPDATE", orderId);
        UUID customer = (UUID) order.get("customer_id");
        lockAccount(customer);
        long requested = number(refund, "amount_irr");
        Map<String, Object> plan = jdbc.queryForMap("SELECT * FROM installment_plans WHERE order_id = ? FOR UPDATE", orderId);
        long unpaid = number(plan, "outstanding_irr");
        long applied = Math.min(requested, unpaid);
        long cashRefund = requested - applied;
        reduceFutureInstallments((UUID) plan.get("id"), applied);
        jdbc.update("UPDATE installment_plans SET outstanding_irr = outstanding_irr - ?, status = CASE WHEN outstanding_irr - ? = 0 THEN 'CANCELLED' ELSE 'ADJUSTED' END WHERE id = ?", applied, applied, plan.get("id"));
        jdbc.update("UPDATE credit_accounts SET outstanding_irr = outstanding_irr - ?, updated_at = now() WHERE customer_id = ?", applied, customer);
        jdbc.update("UPDATE funding_control SET committed_irr = committed_irr - ?, updated_at = now() WHERE singleton_id = 1", applied);
        if (applied > 0) postJournal("refund-receivable-" + refundId, "ביטול ماندهٔ قسط‌ها", "MERCHANT_PAYABLE", applied, "CUSTOMER_RECEIVABLE", applied);
        if (cashRefund > 0) postJournal("refund-cash-" + refundId, "بازپرداخت نقدی آزمایشی", "CUSTOMER_REFUNDS", cashRefund, "CASH", cashRefund);
        long completed = alreadyRefunded(orderId) + requested;
        String orderStatus = completed >= number(order, "amount_irr") ? "REFUNDED" : "PARTIALLY_REFUNDED";
        jdbc.update("UPDATE orders SET status = ? WHERE id = ?", orderStatus, orderId);
        jdbc.update("UPDATE refund_requests SET status = 'COMPLETED', updated_at = now() WHERE id = ?", refundId);
        audit("sandbox-provider", "REFUND_CONFIRMED", "refund", refundId.toString(), "پاسخ قطعی ارائه‌دهندهٔ آزمایشی");
        return Map.of("refundId", refundId, "status", "COMPLETED", "principalRestoredIrr", applied, "cashRefundIrr", cashRefund);
    }

    private void reduceFutureInstallments(UUID planId, long reduction) {
        long left = reduction;
        List<Map<String, Object>> installments = jdbc.queryForList("SELECT * FROM installments WHERE plan_id = ? ORDER BY sequence_number DESC FOR UPDATE", planId);
        for (Map<String, Object> installment : installments) {
            if (left == 0) break;
            long unpaid = number(installment, "adjusted_amount_irr") - number(installment, "paid_irr");
            long adjust = Math.min(left, unpaid);
            long newAmount = number(installment, "adjusted_amount_irr") - adjust;
            String nextStatus = newAmount == number(installment, "paid_irr") ? "ADJUSTED" : installment.get("status").toString();
            jdbc.update("UPDATE installments SET adjusted_amount_irr = ?, status = ? WHERE id = ?", newAmount, nextStatus, installment.get("id"));
            left -= adjust;
        }
        if (left != 0) throw conflict("بازپرداخت وجه از اقساط باقیمانده بیشتر است.");
    }

    @Transactional
    public Map<String, Object> setCreditStatus(String status, String reason) {
        return setCreditStatus(DEMO_CUSTOMER, status, reason);
    }

    @Transactional
    public Map<String, Object> setCreditStatus(UUID customerId, String status, String reason) {
        if (!List.of("ACTIVE", "SUSPENDED").contains(status)) throw badRequest("وضعیت اعتبار نامعتبر است.");
        lockAccount(customerId);
        jdbc.update("UPDATE credit_accounts SET status = ?, updated_at = now() WHERE customer_id = ?", status, customerId);
        audit("super-admin", "CREDIT_" + status, "credit_account", customerId.toString(), reason);
        return Map.of("customerId", customerId, "status", status);
    }

    @Transactional
    public Map<String, Object> setFundingCircuit(boolean enabled, String reason) {
        jdbc.queryForMap("SELECT * FROM funding_control WHERE singleton_id = 1 FOR UPDATE");
        jdbc.update("UPDATE funding_control SET new_financing_enabled = ?, updated_at = now() WHERE singleton_id = 1", enabled);
        audit("super-admin", enabled ? "FUNDING_RESUMED" : "FUNDING_CIRCUIT_OPEN", "funding_control", "1", reason);
        return Map.of("newFinancingEnabled", enabled, "reason", reason);
    }

    @Transactional
    public Map<String, Object> reconcile(String reference, long expectedToman, long observedToman) {
        if (expectedToman < 0 || observedToman < 0) throw badRequest("مبلغ مغایرت نمی‌تواند منفی باشد.");
        long expected = tomanToRial(expectedToman);
        long observed = tomanToRial(observedToman);
        String status = expected == observed ? "MATCHED" : "MISMATCH";
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO reconciliation_records (id, external_reference, expected_irr, observed_irr, status) VALUES (?, ?, ?, ?, ?)", id, cleanDescription(reference), expected, observed, status);
        if ("MISMATCH".equals(status)) jdbc.update("INSERT INTO durable_jobs (job_type, payload, state) VALUES ('RECONCILIATION_REVIEW', jsonb_build_object('reconciliationId', ?), 'READY')", id.toString());
        audit("finance-officer", "RECONCILIATION_" + status, "reconciliation", id.toString(), reference);
        return Map.of("id", id, "status", status, "expectedIrr", expected, "observedIrr", observed);
    }

    @Transactional
    public Map<String, Object> refreshOverdue() {
        int changed = jdbc.update("UPDATE installments SET status = 'OVERDUE' WHERE due_date < CURRENT_DATE AND adjusted_amount_irr > paid_irr AND status IN ('DUE', 'PARTIAL')");
        return Map.of("overdueMarked", changed);
    }

    @Transactional
    public Map<String, Object> createScenarioCustomer(UUID customerId, String label) {
        jdbc.update("INSERT INTO customers (id, mobile, display_name) VALUES (?, ?, ?)", customerId,
                "TEST-" + customerId.toString().substring(0, 8), cleanDescription(label));
        jdbc.update("INSERT INTO credit_accounts (customer_id, branch_id, tier, limit_irr) VALUES (?, ?, 'Gold', 200000000)", customerId, DEMO_BRANCH);
        return Map.of("customerId", customerId, "branchId", DEMO_BRANCH);
    }

    @Transactional
    public Map<String, Object> processReadyJobs(int batchSize) {
        int bounded = Math.max(1, Math.min(batchSize, 100));
        List<UUID> jobIds = jdbc.queryForList("SELECT id FROM durable_jobs WHERE state = 'READY' AND available_at <= now() ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED", UUID.class, bounded);
        for (UUID jobId : jobIds) {
            jdbc.update("UPDATE durable_jobs SET state = 'RUNNING', attempts = attempts + 1 WHERE id = ?", jobId);
            jdbc.update("UPDATE durable_jobs SET state = 'COMPLETED' WHERE id = ?", jobId);
        }
        return Map.of("claimed", jobIds.size(), "completed", jobIds.size());
    }

    private Map<String, Object> lockAccount(UUID customerId) {
        return jdbc.queryForMap("SELECT * FROM credit_accounts WHERE customer_id = ? FOR UPDATE", customerId);
    }

    private void releaseReservation(UUID orderId, long amount) {
        UUID customerId = (UUID) jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = ?", UUID.class, orderId);
        lockAccount(customerId);
        jdbc.update("UPDATE credit_accounts SET reserved_irr = GREATEST(0, reserved_irr - ?), updated_at = now() WHERE customer_id = ?", amount, customerId);
        jdbc.update("UPDATE funding_control SET reserved_irr = GREATEST(0, reserved_irr - ?), updated_at = now() WHERE singleton_id = 1", amount);
    }

    private void postJournal(String reference, String description, String debitAccount, long debit,
                             String creditAccount, long credit) {
        if (debit <= 0 || credit <= 0 || debit != credit) throw new IllegalStateException("Journal is not balanced");
        UUID journalId = UUID.randomUUID();
        jdbc.update("INSERT INTO journal_entries (id, event_reference, description) VALUES (?, ?, ?)", journalId, reference, description);
        jdbc.update("INSERT INTO journal_lines (journal_id, account_code, debit_irr) VALUES (?, ?, ?)", journalId, debitAccount, debit);
        jdbc.update("INSERT INTO journal_lines (journal_id, account_code, credit_irr) VALUES (?, ?, ?)", journalId, creditAccount, credit);
    }

    private void audit(String actor, String action, String type, String id, String reason) {
        jdbc.update("INSERT INTO audit_events (actor, action, entity_type, entity_id, reason) VALUES (?, ?, ?, ?, ?)", actor, action, type, id, reason);
    }

    private long alreadyRefunded(UUID orderId) {
        Long amount = jdbc.queryForObject("SELECT COALESCE(sum(amount_irr), 0) FROM refund_requests WHERE order_id = ? AND status = 'COMPLETED'", Long.class, orderId);
        return amount == null ? 0 : amount;
    }

    private static long tomanToRial(long toman) {
        try { return Math.multiplyExact(toman, 10); }
        catch (ArithmeticException exception) { throw badRequest("مبلغ خارج از محدوده است."); }
    }

    private static long number(Map<String, Object> row, String column) { return ((Number) row.get(column)).longValue(); }
    private static void validateIdempotency(String key) {
        if (key == null || key.isBlank() || key.length() > 96) throw badRequest("کلید یکتای عملیات معتبر نیست.");
    }
    private static String requireProviderResult(String value) {
        if (!List.of("SUCCESS", "FAILURE", "TIMEOUT").contains(value)) throw badRequest("نتیجهٔ ارائه‌دهندهٔ آزمایشی معتبر نیست.");
        return value;
    }
    private static String cleanDescription(String value) {
        if (value == null || value.isBlank()) return "خرید آزمایشی";
        return value.length() > 240 ? value.substring(0, 240) : value;
    }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
