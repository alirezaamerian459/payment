package ir.privatepay.sandbox;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Executes the published financial scenarios against PostgreSQL and records their results. */
@Service
public class SandboxScenarioRunner {
    private static final long PURCHASE_TOMAN = 400_000L;
    private static final LinkedHashMap<String, String> SCENARIOS = new LinkedHashMap<>();
    static {
        SCENARIOS.put("four-installment-purchase", "خرید موفق چهارقسطی");
        SCENARIOS.put("failed-first-payment", "ناموفق بودن پرداخت اول");
        SCENARIOS.put("duplicate-callback", "تکرار callback");
        SCENARIOS.put("provider-timeout", "قطع ارتباط در پرداخت");
        SCENARIOS.put("crash-after-provider-success", "بازیابی پس از توقف برنامه");
        SCENARIOS.put("concurrent-purchases", "دو خرید هم‌زمان");
        SCENARIOS.put("partial-repayment", "بازپرداخت بخشی از بدهی");
        SCENARIOS.put("early-settlement", "تسویه زودتر از موعد");
        SCENARIOS.put("partial-refund", "بازپرداخت بخشی از خرید");
        SCENARIOS.put("full-cancellation", "لغو کامل خرید");
        SCENARIOS.put("overdue-installment", "قسط سررسیدگذشته");
        SCENARIOS.put("credit-suspension", "توقف اعتبار");
        SCENARIOS.put("reconciliation-mismatch", "مغایرت تطبیق");
        SCENARIOS.put("unauthorized-credit-override", "تغییر اعتبار بدون نقش");
        SCENARIOS.put("cross-branch-isolation", "محدودیت دسترسی شعبه");
        SCENARIOS.put("repayment-refund-race", "هم‌زمانی قسط و بازپرداخت وجه");
        SCENARIOS.put("late-success-after-timeout", "رسیدن نتیجه پس از timeout");
        SCENARIOS.put("duplicate-refund", "درخواست تکراری بازپرداخت وجه");
        SCENARIOS.put("exhausted-funding-budget", "تمام‌شدن بودجه");
        SCENARIOS.put("database-worker-recovery", "بازیابی کار پس‌زمینه");
    }

    private final SandboxService service;
    private final JdbcTemplate jdbc;
    private final DemoAuthorization authorization;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SandboxScenarioRunner(SandboxService service, JdbcTemplate jdbc,
                                 DemoAuthorization authorization,
                                 PlatformTransactionManager transactionManager, Clock clock) {
        this.service = service;
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public List<Map<String, Object>> runAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String key : SCENARIOS.keySet()) results.add(run(key));
        return results;
    }

    public Map<String, Object> run(String key) {
        String name = SCENARIOS.get(key);
        if (name == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "سناریوی آزمایشی پیدا نشد.");
        long started = System.nanoTime();
        String status = "PASS";
        String detail;
        try {
            detail = switch (key) {
                case "concurrent-purchases" -> runConcurrentPurchases();
                case "crash-after-provider-success" -> runPostCommitRecovery();
                case "repayment-refund-race" -> runRepaymentRefundRace();
                default -> runIsolated(key);
            };
        } catch (RuntimeException | AssertionError failure) {
            status = "FAIL";
            detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Map<String, Object> result = Map.of("key", key, "name", name, "status", status,
                "detail", detail, "durationMs", durationMs);
        jdbc.update("INSERT INTO sandbox_scenario_runs (scenario_key, name, status, detail, duration_ms) VALUES (?, ?, ?, ?, ?)",
                key, name, status, detail, durationMs);
        return result;
    }

    public List<Map<String, Object>> latestRuns() {
        return jdbc.queryForList("""
                SELECT scenario_key AS key, name, status, detail, duration_ms AS "durationMs"
                FROM sandbox_scenario_runs ORDER BY created_at DESC, id DESC LIMIT 40
                """);
    }

    private String runIsolated(String key) {
        String result = transactions.execute(transaction -> {
            UUID customer = UUID.randomUUID();
            service.createScenarioCustomer(customer, "scenario-" + key);
            String detail = runCase(key, customer);
            assertFinancialIntegrity(customer);
            transaction.setRollbackOnly();
            return detail;
        });
        return result == null ? "سناریو اجرا نشد." : result;
    }

    private String runCase(String key, UUID customer) {
        return switch (key) {
            case "four-installment-purchase" -> {
                UUID order = buy(customer, "SUCCESS", key);
                check("SUCCEEDED".equals(orderStatus(order)), "وضعیت سفارش موفق نشد.");
                check(count("SELECT count(*) FROM installments i JOIN installment_plans p ON p.id=i.plan_id WHERE p.order_id=?", order) == 4, "برنامه دقیقاً چهار قسط ندارد.");
                check(count("SELECT count(*) FROM installments i JOIN installment_plans p ON p.id=i.plan_id WHERE p.order_id=? AND i.sequence_number=1 AND i.status='PAID'", order) == 1, "قسط اول در checkout پرداخت نشده است.");
                yield "خرید ثبت شد؛ چهار قسط ساخته شد و قسط اول هنگام checkout پرداخت شد.";
            }
            case "failed-first-payment" -> {
                Map<String, Object> attempt = buyResult(customer, "FAILURE", key);
                UUID order = uuid(attempt.get("orderId"));
                check("FAILED".equals(orderStatus(order)), "سفارش ناموفق نماند.");
                check(customerNumber(customer, "reserved_irr") == 0, "رزرو بعد از پرداخت ناموفق آزاد نشد.");
                check(count("SELECT count(*) FROM installment_plans WHERE order_id=?", order) == 0, "برای پرداخت ناموفق برنامه اقساط ساخته شد.");
                yield "پرداخت اول رد شد؛ هیچ برنامه‌ای نهایی نشد و رزرو اعتبار و بودجه آزاد شد.";
            }
            case "duplicate-callback" -> {
                UUID order = buy(customer, "TIMEOUT", key);
                UUID intent = intentFor(order);
                Map<String, Object> first = service.providerCallback(intent, "evt-" + UUID.randomUUID(), "SUCCESS");
                String eventId = jdbc.queryForObject("SELECT provider_event_id FROM provider_events WHERE intent_id=?", String.class, intent);
                Map<String, Object> second = service.providerCallback(intent, eventId, "SUCCESS");
                check("SUCCEEDED".equals(first.get("status")) && Boolean.TRUE.equals(second.get("duplicate")), "callback تکراری idempotent نبود.");
                check(count("SELECT count(*) FROM installment_plans WHERE order_id=?", order) == 1, "callback تکراری برنامه دوم ساخت.");
                UUID otherOrder = buy(customer, "TIMEOUT", "duplicate-callback-other");
                expectStatus(HttpStatus.CONFLICT, () -> service.providerCallback(intentFor(otherOrder), eventId, "SUCCESS"));
                yield "callback موفق فقط یک‌بار ثبت شد؛ callback تکراری سند مالی یا برنامهٔ دوم نساخت.";
            }
            case "provider-timeout" -> {
                Map<String, Object> result = buyResult(customer, "TIMEOUT", key);
                UUID order = uuid(result.get("orderId"));
                UUID intent = uuid(result.get("intentId"));
                check("PAYMENT_PENDING".equals(orderStatus(order)), "وضعیت مبهم به نتیجه قطعی تبدیل شد.");
                check("TIMED_OUT".equals(jdbc.queryForObject("SELECT status FROM payment_intents WHERE id=?", String.class, intent)), "timeout پرداخت ذخیره نشد.");
                check(customerNumber(customer, "reserved_irr") == toman(PURCHASE_TOMAN), "رزرو تا استعلام وضعیت حفظ نشد.");
                yield "timeout به‌صورت نامشخص نگه داشته شد و اعتبار و بودجه تا نتیجهٔ قطعی رزرو ماند.";
            }
            case "partial-repayment" -> {
                UUID order = buy(customer, "SUCCESS", key);
                service.repayFor(customer, 125_000L, "repay-" + UUID.randomUUID(), "SUCCESS");
                check(customerNumber(customer, "outstanding_irr") == toman(PURCHASE_TOMAN) - toman(PURCHASE_TOMAN) / 4 - toman(125_000L), "مانده پس از پرداخت بخشی درست نیست.");
                check(count("SELECT count(*) FROM installments i JOIN installment_plans p ON p.id=i.plan_id WHERE p.order_id=? AND i.status='PARTIAL'", order) > 0, "قسط جزئی به‌روزرسانی نشد.");
                yield "بازپرداخت جزئی به قدیمی‌ترین اقساط باز تخصیص یافت و ماندهٔ اصل بدهی به همان میزان کاهش یافت.";
            }
            case "early-settlement" -> {
                UUID order = buy(customer, "SUCCESS", key);
                service.repayFor(customer, PURCHASE_TOMAN * 3 / 4, "settle-" + UUID.randomUUID(), "SUCCESS");
                check(customerNumber(customer, "outstanding_irr") == 0, "ماندهٔ اعتبار بعد از تسویه صفر نشد.");
                check("SETTLED".equals(planStatus(order)), "طرح اقساط settled نشد.");
                yield "کل مانده قبل از سررسید پرداخت شد؛ ماندهٔ اعتبار و برنامه صفر و تسویه‌شده است.";
            }
            case "partial-refund" -> {
                UUID order = buy(customer, "SUCCESS", key);
                UUID refund = requestRefund(customer, order, 20_000L, "partial-" + UUID.randomUUID());
                service.processRefund(refund, "APPROVE", "scenario approval");
                service.processRefund(refund, "CONFIRM_SANDBOX_PROVIDER", "scenario provider confirmation");
                check("PARTIALLY_REFUNDED".equals(orderStatus(order)), "سفارش بازپرداخت بخشی نشد.");
                check(customerNumber(customer, "outstanding_irr") == toman(PURCHASE_TOMAN) - toman(PURCHASE_TOMAN) / 4 - toman(20_000L), "بازپرداخت جزئی ماندهٔ اعتبار را درست تعدیل نکرد.");
                yield "بعد از تأیید مالی و پاسخ provider آزمایشی، قسط‌های آینده و تعهد اعتباری به‌صورت جزئی تعدیل شد.";
            }
            case "full-cancellation" -> {
                UUID order = buy(customer, "SUCCESS", key);
                UUID refund = requestRefund(customer, order, PURCHASE_TOMAN, "cancel-" + UUID.randomUUID());
                service.processRefund(refund, "APPROVE", "scenario approval");
                service.processRefund(refund, "CONFIRM_SANDBOX_PROVIDER", "scenario provider confirmation");
                check("REFUNDED".equals(orderStatus(order)), "لغو کامل نهایی نشد.");
                check(customerNumber(customer, "outstanding_irr") == 0 && "CANCELLED".equals(planStatus(order)), "ماندهٔ اعتبار یا قسط بعد از لغو کامل باقی ماند.");
                yield "کل خرید لغو شد؛ تعهد اقساط، ماندهٔ اعتبار و وضعیت سفارش سازگارند.";
            }
            case "overdue-installment" -> {
                UUID order = buy(customer, "SUCCESS", key);
                int changed = jdbc.update("UPDATE installments i SET due_date=CURRENT_DATE-1 FROM installment_plans p WHERE i.plan_id=p.id AND p.order_id=? AND i.sequence_number=2", order);
                check(changed == 1, "قسط آزمایشی برای عقب‌بردن تاریخ پیدا نشد.");
                service.refreshOverdue();
                check(count("SELECT count(*) FROM installments i JOIN installment_plans p ON p.id=i.plan_id WHERE p.order_id=? AND i.sequence_number=2 AND i.status='OVERDUE'", order) == 1, "قسط معوق علامت نخورد.");
                yield "قسط پرداخت‌نشدهٔ دارای سررسید گذشته با وضعیت OVERDUE ثبت شد؛ جریمه‌ای اعمال نشد.";
            }
            case "credit-suspension" -> {
                service.setCreditStatus(customer, "SUSPENDED", "scenario suspension");
                expectStatus(HttpStatus.CONFLICT, () -> buy(customer, "SUCCESS", key));
                check("SUSPENDED".equals(jdbc.queryForObject("SELECT status FROM credit_accounts WHERE customer_id=?", String.class, customer)), "وضعیت توقف اعتبار از بین رفت.");
                yield "حساب متوقف‌شده نتوانست خرید جدید ایجاد کند؛ پرداخت بدهی همچنان فعال است.";
            }
            case "reconciliation-mismatch" -> {
                Map<String, Object> row = service.reconcile("scenario-" + UUID.randomUUID(), 100_000L, 99_000L);
                UUID id = uuid(row.get("id"));
                check("MISMATCH".equals(row.get("status")), "مغایرت ثبت نشد.");
                check(count("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILIATION_REVIEW' AND state='READY' AND payload->>'reconciliationId'=?", id.toString()) == 1, "کار بررسی مغایرت در صف durable قرار نگرفت.");
                yield "مغایرت در PostgreSQL ثبت و یک کار پایدار برای بررسی مالی صف‌بندی شد.";
            }
            case "unauthorized-credit-override" -> {
                expectStatus(HttpStatus.FORBIDDEN, () -> authorization.requireRole("CUSTOMER", "SUPER_ADMIN"));
                check("ACTIVE".equals(jdbc.queryForObject("SELECT status FROM credit_accounts WHERE customer_id=?", String.class, customer)), "اعتبار با نقش غیرمجاز تغییر کرد.");
                yield "درخواست مشتری برای اختیار مدیرکل با 403 رد شد و حساب اعتباری بدون تغییر ماند.";
            }
            case "cross-branch-isolation" -> {
                UUID wrongBranch = UUID.randomUUID();
                expectStatus(HttpStatus.FORBIDDEN, () -> service.purchaseFor(customer, PURCHASE_TOMAN, "wrong branch", "branch-" + UUID.randomUUID(), "SUCCESS", wrongBranch));
                check(count("SELECT count(*) FROM orders WHERE customer_id=?", customer) == 0, "شعبهٔ غیرمجاز سفارش ساخت.");
                yield "خرید از شعبهٔ خارج از دامنهٔ حساب با 403 مسدود شد و سفارشی ثبت نشد.";
            }
            case "late-success-after-timeout" -> {
                UUID order = buy(customer, "TIMEOUT", key);
                UUID intent = intentFor(order);
                service.providerCallback(intent, "late-evt-" + UUID.randomUUID(), "SUCCESS");
                check("SUCCEEDED".equals(orderStatus(order)), "نتیجهٔ موفق دیرهنگام پرداخت نهایی نشد.");
                check(customerNumber(customer, "reserved_irr") == 0, "رزرو پس از callback موفق آزاد نشد.");
                yield "callback موفق دیررس پس از timeout رزرو را به تعهد واقعی تبدیل و خرید را فقط یک‌بار نهایی کرد.";
            }
            case "duplicate-refund" -> {
                UUID order = buy(customer, "SUCCESS", key);
                String idempotency = "refund-same-" + UUID.randomUUID();
                Map<String, Object> first = service.requestRefundFor(customer, order, 10_000L, "duplicate scenario", idempotency);
                Map<String, Object> second = service.requestRefundFor(customer, order, 10_000L, "duplicate scenario", idempotency);
                check(first.get("refundId").equals(second.get("refundId")) && Boolean.TRUE.equals(second.get("duplicate")), "درخواست بازپرداخت وجه idempotent نبود.");
                check(count("SELECT count(*) FROM refund_requests WHERE order_id=?", order) == 1, "درخواست تکراری ردیف دوم ساخت.");
                yield "درخواست تکراری با همان کلید به همان بازپرداخت اشاره کرد و ردیف دوم نساخت.";
            }
            case "exhausted-funding-budget" -> {
                jdbc.update("UPDATE funding_control SET budget_irr=committed_irr+reserved_irr WHERE singleton_id=1");
                expectStatus(HttpStatus.CONFLICT, () -> buy(customer, "SUCCESS", key));
                check(customerNumber(customer, "reserved_irr") == 0 && count("SELECT count(*) FROM orders WHERE customer_id=?", customer) == 0, "در بودجهٔ تمام‌شده رزرو یا سفارش ساخته شد.");
                yield "با بودجهٔ آزاد صفر، سیستم خرید را رد کرد و هیچ تعهد یا رزروی ثبت نشد.";
            }
            case "database-worker-recovery" -> {
                Map<String, Object> row = service.reconcile("worker-" + UUID.randomUUID(), 20_000L, 19_000L);
                UUID id = uuid(row.get("id"));
                int readyBefore = count("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILIATION_REVIEW' AND state='READY' AND payload->>'reconciliationId'=?", id.toString());
                Map<String, Object> processed = service.processReadyJobs(50);
                check(readyBefore == 1 && ((Number) processed.get("completed")).intValue() >= 1, "worker کار آماده را بازیابی نکرد.");
                check(count("SELECT count(*) FROM durable_jobs WHERE job_type='RECONCILIATION_REVIEW' AND state='COMPLETED' AND payload->>'reconciliationId'=?", id.toString()) == 1, "وضعیت job پس از بازیابی کامل نشد.");
                yield "کار آماده از صف durable خوانده شد، شمارندهٔ تلاش افزایش یافت و پس از بازیابی COMPLETED شد.";
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "سناریوی آزمایشی پیدا نشد.");
        };
    }

    private String runConcurrentPurchases() {
        UUID customer = UUID.randomUUID();
        service.createScenarioCustomer(customer, "scenario-concurrent-purchases");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String key = "parallel-" + UUID.randomUUID();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("همگام‌سازی خریدهای موازی timeout شد.");
                    try {
                        return service.purchaseFor(customer, 12_000_000L, "دو خرید هم‌زمان", key, "SUCCESS", SandboxService.DEMO_BRANCH);
                    } catch (ResponseStatusException exception) {
                        return Map.of("rejectedStatus", exception.getStatusCode().value());
                    }
                }));
            }
            check(ready.await(10, TimeUnit.SECONDS), "دو درخواست هم‌زمان آماده نشدند.");
            start.countDown();
            List<Map<String, Object>> results = List.of(
                    futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            long succeeded = results.stream().filter(row -> "SUCCEEDED".equals(row.get("status"))).count();
            long rejected = results.stream().filter(row -> Integer.valueOf(409).equals(row.get("rejectedStatus"))).count();
            check(succeeded == 1 && rejected == 1, "انتظار یک خرید موفق و یک رد به‌دلیل سقف اعتبار بود؛ نتیجه: " + results);
            check(customerNumber(customer, "outstanding_irr") == 90_000_000L, "مبلغ هم‌زمان بیش از یک بار مصرف شد.");
            service.repayFor(customer, 9_000_000L, "parallel-settle-" + UUID.randomUUID(), "SUCCESS");
            check(customerNumber(customer, "outstanding_irr") == 0, "تسویهٔ خرید برنده انجام نشد.");
            assertFinancialIntegrity(customer);
            return "دو درخواست واقعاً موازی روی PostgreSQL اجرا شد؛ قفل حساب فقط یکی را پذیرفت و خرید پذیرفته‌شده تسویه شد.";
        } catch (Exception failure) {
            throw new IllegalStateException("آزمون هم‌زمانی شکست خورد: " + failure.getMessage(), failure);
        } finally {
            pool.shutdownNow();
        }
    }

    private String runPostCommitRecovery() {
        UUID customer = UUID.randomUUID();
        service.createScenarioCustomer(customer, "scenario-crash-after-provider-success");
        String idempotencyKey = "crash-recovery-" + UUID.randomUUID();
        Map<String, Object> firstAttempt = service.purchaseFor(customer, PURCHASE_TOMAN,
                "خرید موفق پیش از توقف برنامه", idempotencyKey, "SUCCESS", SandboxService.DEMO_BRANCH);

        // The first transaction has committed; simulate losing its HTTP response, then retry after restart.
        SandboxService restartedInstance = new SandboxService(jdbc, clock);
        Map<String, Object> retry = restartedInstance.purchaseFor(customer, PURCHASE_TOMAN,
                "تکرار پس از راه‌اندازی مجدد", idempotencyKey, "SUCCESS", SandboxService.DEMO_BRANCH);
        UUID order = uuid(firstAttempt.get("orderId"));
        check("SUCCEEDED".equals(orderStatus(order)) && Boolean.TRUE.equals(retry.get("duplicate")), "تکرار پس از crash سفارش را idempotent بازیابی نکرد.");
        check(count("SELECT count(*) FROM orders WHERE idempotency_key=?", idempotencyKey) == 1, "retry پس از crash سفارش دوم ساخت.");
        check(count("SELECT count(*) FROM installment_plans WHERE order_id=?", order) == 1, "retry پس از crash برنامهٔ قسط دوم ساخت.");
        check(count("SELECT count(*) FROM journal_entries WHERE event_reference IN (?, ?)", "purchase-" + order, "initial-payment-" + order) == 2, "retry پس از crash دوباره سند مالی ثبت کرد.");
        service.repayFor(customer, PURCHASE_TOMAN * 3 / 4, "crash-settle-" + UUID.randomUUID(), "SUCCESS");
        check(customerNumber(customer, "outstanding_irr") == 0, "ماندهٔ مشتری مصنوعی بعد از recovery تسویه نشد.");
        assertFinancialIntegrity(customer);
        return "خرید و دفتر commit شدند اما پاسخ فرضاً گم شد؛ retry پس از شروع دوباره همان سفارش را برگرداند و بدهی آزمایشی تسویه شد.";
    }

    private String runRepaymentRefundRace() {
        UUID customer = UUID.randomUUID();
        service.createScenarioCustomer(customer, "scenario-repayment-refund-race");
        UUID order = buy(customer, "SUCCESS", "race-" + UUID.randomUUID());
        UUID refund = requestRefund(customer, order, 20_000L, "race-refund-" + UUID.randomUUID());
        service.processRefund(refund, "APPROVE", "scenario approval");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Map<String, Object>> repayment = pool.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("شروع هم‌زمان timeout شد.");
                return service.repayFor(customer, 50_000L, "race-repay-" + UUID.randomUUID(), "SUCCESS");
            });
            Future<Map<String, Object>> refundConfirmation = pool.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("شروع هم‌زمان timeout شد.");
                return service.processRefund(refund, "CONFIRM_SANDBOX_PROVIDER", "scenario confirmation");
            });
            start.countDown();
            Map<String, Object> repaymentResult = repayment.get(20, TimeUnit.SECONDS);
            Map<String, Object> refundResult = refundConfirmation.get(20, TimeUnit.SECONDS);
            check("SUCCEEDED".equals(repaymentResult.get("status")) && "COMPLETED".equals(refundResult.get("status")), "یکی از دو عملیات مالی هم‌زمان کامل نشد.");
            long remainingIrr = customerNumber(customer, "outstanding_irr");
            check(remainingIrr == 2_300_000L, "مانده بعد از تخصیص هم‌زمان repayment/refund درست نیست: " + remainingIrr);
            service.repayFor(customer, remainingIrr / 10, "race-settle-" + UUID.randomUUID(), "SUCCESS");
            assertFinancialIntegrity(customer);
            return "بازپرداخت و تأیید refund هم‌زمان روی یک حساب اجرا شد؛ هر دو یک‌بار ثبت شدند و ماندهٔ نهایی قابل تطبیق ماند.";
        } catch (Exception failure) {
            throw new IllegalStateException("آزمون هم‌زمانی بازپرداخت و refund شکست خورد: " + failure.getMessage(), failure);
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID buy(UUID customer, String result, String key) {
        return uuid(buyResult(customer, result, key).get("orderId"));
    }

    private Map<String, Object> buyResult(UUID customer, String result, String key) {
        return service.purchaseFor(customer, PURCHASE_TOMAN, "سناریوی آزمایشی", "buy-" + key + "-" + UUID.randomUUID(), result, SandboxService.DEMO_BRANCH);
    }

    private UUID requestRefund(UUID customer, UUID order, long amountToman, String key) {
        return uuid(service.requestRefundFor(customer, order, amountToman, "درخواست سناریوی آزمایشی", key).get("refundId"));
    }

    private UUID intentFor(UUID order) {
        return jdbc.queryForObject("SELECT id FROM payment_intents WHERE order_id=?", UUID.class, order);
    }

    private String orderStatus(UUID order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order);
    }

    private String planStatus(UUID order) {
        return jdbc.queryForObject("SELECT status FROM installment_plans WHERE order_id=?", String.class, order);
    }

    private long customerNumber(UUID customer, String field) {
        if (!List.of("outstanding_irr", "reserved_irr", "limit_irr").contains(field)) throw new IllegalArgumentException("ستون پشتیبانی نمی‌شود.");
        Long result = jdbc.queryForObject("SELECT " + field + " FROM credit_accounts WHERE customer_id=?", Long.class, customer);
        return result == null ? 0 : result;
    }

    private long fundingNumber(String field) {
        if (!List.of("committed_irr", "reserved_irr", "budget_irr").contains(field)) throw new IllegalArgumentException("ستون پشتیبانی نمی‌شود.");
        Long result = jdbc.queryForObject("SELECT " + field + " FROM funding_control WHERE singleton_id=1", Long.class);
        return result == null ? 0 : result;
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private void assertFinancialIntegrity(UUID customer) {
        check(count("""
                SELECT count(*) FROM (
                    SELECT e.id FROM journal_entries e LEFT JOIN journal_lines l ON l.journal_id=e.id
                    GROUP BY e.id HAVING count(l.id)<2 OR COALESCE(sum(l.debit_irr),0)<>COALESCE(sum(l.credit_irr),0)
                ) unbalanced
                """) == 0, "دفتر کل دارای سند نامتوازن یا ناقص است.");
        long customerOutstanding = customerNumber(customer, "outstanding_irr");
        Long planOutstandingValue = jdbc.queryForObject("""
                SELECT COALESCE(sum(outstanding_irr),0) FROM installment_plans p JOIN orders o ON o.id=p.order_id
                WHERE o.customer_id=?
                """, Long.class, customer);
        check(customerOutstanding == (planOutstandingValue == null ? 0 : planOutstandingValue), "ماندهٔ حساب اعتباری با ماندهٔ طرح اقساط یکی نیست.");
        check(count("""
                SELECT count(*) FROM (
                    SELECT p.id FROM installment_plans p JOIN orders o ON o.id=p.order_id
                    LEFT JOIN installments i ON i.plan_id=p.id WHERE o.customer_id=?
                    GROUP BY p.id, p.outstanding_irr
                    HAVING p.outstanding_irr<>COALESCE(sum(i.adjusted_amount_irr-i.paid_irr),0)
                ) mismatched_plans
                """, customer) == 0, "ماندهٔ برنامه با اقساط تعدیل‌شده تطبیق ندارد.");
        check(count("""
                SELECT CASE WHEN (SELECT COALESCE(sum(outstanding_irr),0) FROM credit_accounts) =
                    (SELECT committed_irr FROM funding_control WHERE singleton_id=1) THEN 0 ELSE 1 END
                """) == 0, "تعهد شرکت با مجموع اصل بدهی حساب‌ها یکی نیست.");
        check(count("""
                SELECT CASE WHEN (SELECT COALESCE(sum(reserved_irr),0) FROM credit_accounts) =
                    (SELECT reserved_irr FROM funding_control WHERE singleton_id=1) THEN 0 ELSE 1 END
                """) == 0, "رزرو شرکت با مجموع رزرو حساب‌ها یکی نیست.");
    }

    private static long toman(long value) { return Math.multiplyExact(value, 10); }
    private static UUID uuid(Object value) { return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value)); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static void expectStatus(HttpStatus expected, Runnable action) {
        try {
            action.run();
        } catch (ResponseStatusException exception) {
            check(exception.getStatusCode().equals(expected), "HTTP " + expected.value() + " انتظار می‌رفت، اما " + exception.getStatusCode().value() + " دریافت شد.");
            return;
        }
        throw new AssertionError("عملیات باید با HTTP " + expected.value() + " رد می‌شد.");
    }
}
