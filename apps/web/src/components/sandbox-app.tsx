"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

type ApiRow = Record<string, unknown>;
type Installment = { id: string; sequence_number: number; due_date: string; original_amount_irr: number; adjusted_amount_irr: number; paid_irr: number; status: string; order_id: string; description: string };
type Application = { id: string; state: string; requested_limit_irr: number; policy_version: string; reason_codes: string[]; created_at: string };
type Order = { id: string; amount_irr: number; description: string; status: string; created_at: string };
type Refund = { id: string; order_id: string; amount_irr: number; status: string; reason: string; description: string };
type Activity = { action: string; entity_type: string; entity_id: string; reason: string; occurred_at: string };
type SandboxState = {
  customer: { id: string; mobile: string; display_name: string };
  credit: { tier: string; limit_irr: number; outstanding_irr: number; reserved_irr: number; available_irr: number; status: string };
  funding: { budget_irr: number; committed_irr: number; reserved_irr: number; available_irr: number; new_financing_enabled: boolean };
  orders: Order[];
  installments: Installment[];
  applications: Application[];
  refunds: Refund[];
  activity: Activity[];
};
type ScenarioResult = { key: string; name: string; status: "PASS" | "FAIL"; detail: string; durationMs: number };
type PageKey = "home" | "credit" | "payments" | "orders" | "operations" | "scenarios";
type NavItem = { id: PageKey; label: string; glyph: string };

const scenarioList: Array<[string, string, string]> = [
  ["four-installment-purchase", "خرید موفق چهارقسطی", "قسط اول همان لحظه دریافت و سه سررسید بعدی ساخته می‌شود."],
  ["failed-first-payment", "ناموفق بودن پرداخت اول", "رزرو اعتبار و بودجه بعد از رد پرداخت آزاد می‌شود."],
  ["duplicate-callback", "تکرار callback", "رویداد تکراری فقط یک‌بار سفارش و دفتر را نهایی می‌کند."],
  ["provider-timeout", "قطع ارتباط در پرداخت", "وضعیت مبهم می‌ماند و رزرو تا استعلام حفظ می‌شود."],
  ["crash-after-provider-success", "بازیابی پس از توقف برنامه", "callback موفق پس از توقف، ثبت را کامل و تکرارپذیر نگه می‌دارد."],
  ["concurrent-purchases", "دو خرید هم‌زمان", "قفل حساب اجازه نمی‌دهد دو خرید از یک سقف دوبار خرج کنند."],
  ["partial-repayment", "بازپرداخت بخشی از بدهی", "مبلغ به قدیمی‌ترین قسط‌های باز تخصیص می‌یابد."],
  ["early-settlement", "تسویه زودتر از موعد", "کل مانده تسویه و اعتبار مصرف‌شده آزاد می‌شود."],
  ["partial-refund", "بازپرداخت بخشی از خرید", "قسط‌های آینده تعدیل و اثر مالی ثبت می‌شود."],
  ["full-cancellation", "لغو کامل خرید", "مانده اقساط، وضعیت سفارش و سند معکوس اصلاح می‌شود."],
  ["overdue-installment", "قسط سررسیدگذشته", "قسط پرداخت‌نشده به‌صورت قطعی معوق علامت می‌خورد."],
  ["credit-suspension", "توقف اعتبار", "حساب متوقف‌شده نمی‌تواند تعهد جدید بگیرد."],
  ["reconciliation-mismatch", "مغایرت تطبیق", "مغایرت ذخیره و برای بررسی مالی صف‌بندی می‌شود."],
  ["unauthorized-credit-override", "تغییر اعتبار بدون نقش", "نقش مشتری امکان تصمیم‌گیری اعتباری ندارد."],
  ["cross-branch-isolation", "محدودیت دسترسی شعبه", "شعبه دیگر نمی‌تواند از اعتبار مشتری خرید بزند."],
  ["repayment-refund-race", "هم‌زمانی قسط و بازپرداخت وجه", "قفل مشترک مانع دوبار کم‌شدن مانده می‌شود."],
  ["late-success-after-timeout", "رسیدن نتیجه پس از timeout", "پاسخ دیرهنگام موفق فقط یک‌بار ثبت نهایی می‌شود."],
  ["duplicate-refund", "درخواست تکراری بازپرداخت وجه", "کلید idempotency از ایجاد درخواست دوم جلوگیری می‌کند."],
  ["exhausted-funding-budget", "تمام‌شدن بودجه", "بودجه کافی نبودن، تعهد جدید را متوقف می‌کند."],
  ["database-worker-recovery", "بازیابی کار پس‌زمینه", "کار آماده پس از راه‌اندازی worker دوباره پردازش می‌شود."],
];

const navItems: NavItem[] = [
  { id: "home", label: "خانه", glyph: "خ" },
  { id: "credit", label: "اعتبار", glyph: "ا" },
  { id: "payments", label: "اقساط", glyph: "ق" },
  { id: "orders", label: "خریدها", glyph: "ر" },
  { id: "operations", label: "عملیات", glyph: "م" },
  { id: "scenarios", label: "آزمون‌ها", glyph: "ت" },
];
const roleOptions = [
  ["CUSTOMER", "مشتری"], ["CASHIER", "صندوقدار"], ["BRANCH_MANAGER", "مدیر شعبه"],
  ["CREDIT_REVIEWER", "بازبین اعتبار"], ["FINANCE_OFFICER", "کارشناس مالی"],
  ["CUSTOMER_SUPPORT", "پشتیبانی"], ["SUPER_ADMIN", "مدیر کل آزمایشی"],
] as const;

function toman(rial: number | string | null | undefined) {
  return `${new Intl.NumberFormat("fa-IR").format(Number(rial ?? 0) / 10)} تومان`;
}
function jalaliDate(value?: string) {
  if (!value) return "";
  const date = new Date(value.length === 10 ? `${value}T12:00:00` : value);
  return new Intl.DateTimeFormat("fa-IR-u-ca-persian", { day: "numeric", month: "long" }).format(date);
}
function dueAmount(item: Installment) { return Math.max(0, item.adjusted_amount_irr - item.paid_irr); }
function stateName(value: string) {
  const labels: Record<string, string> = {
    ACTIVE: "فعال", SUSPENDED: "متوقف", PAYMENT_PENDING: "در انتظار پرداخت", SUCCEEDED: "موفق", FAILED: "ناموفق",
    CANCELLED: "لغو شده", REFUNDED: "بازپرداخت کامل", PARTIALLY_REFUNDED: "بازپرداخت بخشی", DUE: "سررسید آینده",
    PARTIAL: "پرداخت بخشی", PAID: "پرداخت شده", OVERDUE: "معوق", ADJUSTED: "تعدیل شده", SUBMITTED: "ثبت شده",
    MANUAL_REVIEW: "نیازمند بررسی", MORE_INFO: "نیازمند اطلاعات", APPROVED: "تأیید شده", DECLINED: "رد شده",
    REQUESTED: "درخواست شده", PENDING_PROVIDER: "در انتظار تأیید درگاه", COMPLETED: "تکمیل شده", MATCHED: "تطبیق شده", MISMATCH: "مغایرت",
  };
  return labels[value] ?? value;
}
function badge(status: string) {
  if (["PAID", "SUCCEEDED", "APPROVED", "COMPLETED", "ACTIVE", "MATCHED"].includes(status)) return "badge badge-success";
  if (["OVERDUE", "FAILED", "DECLINED", "SUSPENDED", "MISMATCH", "REFUNDED"].includes(status)) return "badge badge-danger";
  if (["PAYMENT_PENDING", "REQUESTED", "PENDING_PROVIDER", "MANUAL_REVIEW", "PARTIAL"].includes(status)) return "badge badge-warning";
  return "badge";
}
function actionLabel(action: string) {
  const labels: Record<string, string> = { SANDBOX_LOGIN: "ورود آزمایشی", APPLICATION_SUBMITTED: "درخواست اعتبار", PAYMENT_FINALIZED: "تکمیل خرید", PAYMENT_FAILED: "پرداخت ناموفق", PAYMENT_TIMEOUT: "پاسخ نامشخص پرداخت", REPAYMENT_POSTED: "ثبت بازپرداخت", REFUND_REQUESTED: "درخواست بازپرداخت وجه", REFUND_CONFIRMED: "تأیید بازپرداخت وجه" };
  return labels[action] ?? action.replaceAll("_", " ");
}

export default function SandboxApp() {
  const [data, setData] = useState<SandboxState | null>(null);
  const [loggedIn, setLoggedIn] = useState(false);
  const [role, setRole] = useState("CUSTOMER");
  const [page, setPage] = useState<PageKey>("home");
  const [busy, setBusy] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [mobile, setMobile] = useState("00000000000");
  const [otp, setOtp] = useState("246810");
  const [amount, setAmount] = useState("4000000");
  const [description, setDescription] = useState("خرید آزمایشی از فروشگاه مرکزی");
  const [providerResult, setProviderResult] = useState("SUCCESS");
  const [repayAmount, setRepayAmount] = useState("100000");
  const [refundOrderId, setRefundOrderId] = useState("");
  const [refundAmount, setRefundAmount] = useState("100000");
  const [refundReason, setRefundReason] = useState("درخواست آزمایشی مشتری");
  const [requestedLimit, setRequestedLimit] = useState("5000000");
  const [scenarioResults, setScenarioResults] = useState<Record<string, ScenarioResult>>({});
  const [scenarioBusy, setScenarioBusy] = useState(false);

  const api = useCallback(async (path: string, body?: unknown, method = "POST"): Promise<ApiRow> => {
    const response = await fetch(`/api/sandbox/${path}`, {
      method,
      headers: { "content-type": "application/json", "x-demo-role": role },
      body: method === "GET" ? undefined : JSON.stringify(body ?? {}),
      cache: "no-store",
    });
    const result = await response.json().catch(() => ({})) as ApiRow;
    if (!response.ok) throw new Error(String(result.detail ?? result.message ?? "عملیات انجام نشد."));
    return result;
  }, [role]);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await api("state", undefined, "GET") as unknown as SandboxState;
      setData(result);
      setLoggedIn(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "ارتباط با سرویس آزمایشی برقرار نشد.");
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    if (window.localStorage.getItem("privatepay-sandbox-demo-entry")) void refresh();
  }, [refresh]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    if (!loggedIn || role !== "SUPER_ADMIN" || page !== "scenarios") return;
    let active = true;
    void api("scenarios/results", undefined, "GET").then((value) => {
      const saved = value as unknown as ScenarioResult[];
      if (active && Array.isArray(saved)) setScenarioResults(Object.fromEntries(saved.map((item) => [item.key, item])));
    }).catch(() => {
      // The page still allows a fresh run; the API error is shown on explicit actions.
    });
    return () => { active = false; };
  }, [api, loggedIn, page, role]);

  async function act(key: string, fn: () => Promise<ApiRow>, success: string) {
    setBusy(key);
    setError("");
    try {
      const result = await fn();
      setToast(result.status === "FAILED" ? "پرداخت ناموفق شد و رزرو اعتبار آزاد شد." : success);
      if (typeof result.orderId === "string") setRefundOrderId(result.orderId);
      await refresh();
      return result;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "عملیات انجام نشد.");
      return null;
    } finally {
      setBusy(null);
    }
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy("login");
    setError("");
    try {
      await api("login", { mobile, code: otp });
      window.localStorage.setItem("privatepay-sandbox-demo-entry", "active");
      await refresh();
      setToast("ورود آزمایشی انجام شد. هیچ پیامکی ارسال نشده است.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "ورود انجام نشد.");
    } finally {
      setBusy(null);
    }
  }
  async function purchase(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await act("purchase", () => api("purchases", { amountToman: Number(amount), description, providerResult, idempotencyKey: crypto.randomUUID() }), "خرید و برنامهٔ چهارقسطی ثبت شد.");
    setPage("payments");
  }
  async function repay(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await act("repay", () => api("repayments", { amountToman: Number(repayAmount), providerResult, idempotencyKey: crypto.randomUUID() }), "بازپرداخت در دفتر آزمایشی ثبت شد.");
  }
  async function applyCredit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = await act("application", () => api("applications", { requestedLimitToman: Number(requestedLimit) }), "درخواست برای بررسی انسانی ارسال شد.");
    if (result) setPage("credit");
  }
  async function refund(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = await act("refund", () => api("refunds", { orderId: refundOrderId, amountToman: Number(refundAmount), reason: refundReason, idempotencyKey: crypto.randomUUID() }), "درخواست بازپرداخت وجه برای بررسی مالی ثبت شد.");
    if (result) setPage("operations");
  }
  async function runScenario(key: string) {
    setScenarioBusy(true);
    setError("");
    try {
      const result = await api(`scenarios/${key}`) as unknown as ScenarioResult;
      setScenarioResults((current) => ({ ...current, [key]: result }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "اجرای سناریو ناموفق بود.");
    } finally {
      setScenarioBusy(false);
    }
  }
  async function runAllScenarios() {
    setScenarioBusy(true);
    setError("");
    try {
      const result = await api("scenarios/run", {}) as unknown as { results: ScenarioResult[] };
      setScenarioResults(Object.fromEntries(result.results.map((item) => [item.key, item])));
      setToast(`${result.results.filter((item) => item.status === "PASS").length} از ${result.results.length} سناریو موفق بود.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "اجرای سناریوها ناموفق بود.");
    } finally {
      setScenarioBusy(false);
    }
  }
  async function processRefund(id: string, action: string) {
    const messages: Record<string, string> = { APPROVE: "درخواست بازپرداخت تأیید شد.", DECLINE: "درخواست بازپرداخت رد شد.", CONFIRM_SANDBOX_PROVIDER: "پاسخ قطعی ارائه‌دهندهٔ آزمایشی ثبت شد." };
    await act(`refund-${id}`, () => api(`refunds/${id}/review`, { action, reason: "بازبینی در فضای آزمایشی" }), messages[action]);
  }
  function logout() {
    window.localStorage.removeItem("privatepay-sandbox-demo-entry");
    setLoggedIn(false); setData(null); setPage("home"); setRole("CUSTOMER");
  }

  if (!loggedIn) {
    return <main className="login-screen">
      <section className="login-aside" aria-labelledby="login-title">
        <div className="brand"><span className="brand-mark">P</span><span className="brand-word">PrivatePay</span></div>
        <div className="login-pitch"><span className="sandbox-mark"><span className="dot" />فضای آزمایشی</span><h1 id="login-title">قسط‌ها و خریدهایتان را یک‌جا ببینید</h1><p>یک خرید نمونه بسازید، نتیجهٔ پرداخت را ببینید و برنامهٔ چهارقسطی را دنبال کنید.</p>
          <div className="sandbox-flow" aria-label="مراحل خرید چهارقسطی">
            <div className="sandbox-flow-step"><span>۱</span><div><strong>خرید</strong><small>انتخاب مبلغ نمونه</small></div></div>
            <div className="sandbox-flow-step"><span>۲</span><div><strong>پرداخت اول</strong><small>نتیجهٔ شبیه‌سازی‌شده</small></div></div>
            <div className="sandbox-flow-step"><span>۳</span><div><strong>سه قسط بعدی</strong><small>پیگیری در حساب</small></div></div>
          </div>
        </div>
        <p className="login-aside-note">موجودی، اعتبار و پرداخت‌ها ساختگی و فقط برای آزمایش هستند. هیچ پیامک یا تراکنش واقعی انجام نمی‌شود.</p>
      </section>
      <section className="login-panel-wrap"><div className="login-panel">
        <span className="sandbox-mark"><span className="dot" />ورود مشتری آزمایشی</span><h2>به حساب نمونه وارد شوید</h2><p>کد ثابت محیط آزمایشی است و پیامکی ارسال نمی‌شود.</p>
        {error && <div className="notice notice-danger" role="alert">{error}</div>}
        <form className="form-stack" onSubmit={login}>
          <div className="field"><label htmlFor="mobile">شمارهٔ همراه نمونه</label><input id="mobile" inputMode="numeric" value={mobile} onChange={(event) => setMobile(event.target.value)} autoComplete="tel" required /></div>
          <div className="field"><label htmlFor="otp">کد یک‌بارمصرف آزمایشی</label><input id="otp" inputMode="numeric" value={otp} onChange={(event) => setOtp(event.target.value)} autoComplete="one-time-code" required /></div>
          <button className="button button-primary" disabled={busy === "login"}>{busy === "login" ? "در حال ورود…" : "ورود به حساب آزمایشی"}</button>
        </form>
        <div className="sandbox-code"><span>شمارهٔ نمونه</span><strong>00000000000</strong><span>کد ورود</span><strong>246810</strong></div>
        <p className="field-hint" style={{ marginTop: 14 }}>در صورت خطای اتصال، Docker را اجرا و دستور محلی پروژه را بزنید.</p>
      </div></section>
    </main>;
  }

  const pages = role === "CUSTOMER" ? navItems.filter((item) => !["operations", "scenarios"].includes(item.id)) : navItems;
  const titles: Record<PageKey, [string, string]> = {
    home: ["سلام، کاربر آزمایشی", "وضعیت اعتبار و نزدیک‌ترین پرداخت شما"],
    credit: ["اعتبار من", "درخواست‌ها و سقف اعتباری تأییدشده"],
    payments: ["برنامهٔ پرداخت", "هر قسط، سررسید و ماندهٔ روشن و مشخص"],
    orders: ["خریدها و رسیدها", "سفارش‌های ثبت‌شده در محیط آزمایشی"],
    operations: ["مرکز عملیات", "بازبینی درخواست‌ها، بازپرداخت و کنترل ریسک"],
    scenarios: ["آزمایش‌های مالی", "۲۰ سناریوی یکپارچگی مالی در بک‌اند واقعی"],
  };
  const nav = pages;
  const next = data?.installments.find((item) => dueAmount(item) > 0);
  const utilization = data?.credit.limit_irr ? Math.min(100, Math.round(data.credit.outstanding_irr / data.credit.limit_irr * 100)) : 0;
  const outstandingInstallments = data?.installments.reduce((sum, item) => sum + dueAmount(item), 0) ?? 0;

  return <div className="app-shell">
    <a className="skip-link" href="#main-content">رفتن به محتوای اصلی</a>
    <aside className="sidebar"><div className="brand"><span className="brand-mark">P</span><span className="brand-word">PrivatePay</span></div>
      <nav className="nav-list" aria-label="ناوبری اصلی">{nav.map((item) => <NavButton key={item.id} item={item} active={page === item.id} onClick={() => setPage(item.id)} />)}</nav>
      <div className="sidebar-bottom"><span className="sandbox-mark"><span className="dot" />تراکنش واقعی غیرفعال است</span><div className="mini-profile"><span className="avatar">ک</span><span>کاربر آزمایشی<small>اعتبار {data ? stateName(data.credit.status) : ""}</small></span></div></div>
    </aside>
    <div className="main-column">
      <header className="topbar"><div className="brand"><span className="brand-mark">P</span><span className="brand-word">PrivatePay</span></div><div className="topbar-tools"><span className="sandbox-mark"><span className="dot" />محیط آزمایشی</span><label className="visually-hidden" htmlFor="demo-role">نقش نمایشی</label><select id="demo-role" className="role-select" value={role} onChange={(event) => setRole(event.target.value)} aria-label="نمایش با نقش">{roleOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><button className="button button-secondary button-small" onClick={logout}>خروج</button></div></header>
      <main id="main-content" className="content">
        <div className="page-heading"><div><p className="eyebrow">{roleOptions.find(([value]) => value === role)?.[1]}</p><h1>{titles[page][0]}</h1><p>{titles[page][1]}</p></div><button className="button button-secondary button-small" onClick={() => void refresh()} disabled={loading}>{loading ? "در حال تازه‌سازی…" : "تازه‌سازی اطلاعات"}</button></div>
        {error && <div className="notice notice-danger" role="alert">{error}</div>}
        {!data ? <div className="panel"><div className="empty-state"><strong>اطلاعات حساب بارگذاری نشد</strong>اتصال API را بررسی و صفحه را تازه کنید.</div></div> : <>
          {page === "home" && <HomeContent data={data} utilization={utilization} next={next} amount={amount} setAmount={setAmount} description={description} setDescription={setDescription} providerResult={providerResult} setProviderResult={setProviderResult} onPurchase={purchase} busy={busy} setPage={setPage} />}
          {page === "credit" && <CreditContent data={data} requestedLimit={requestedLimit} setRequestedLimit={setRequestedLimit} onSubmit={applyCredit} busy={busy} />}
          {page === "payments" && <PaymentsContent data={data} outstanding={outstandingInstallments} repayAmount={repayAmount} setRepayAmount={setRepayAmount} providerResult={providerResult} setProviderResult={setProviderResult} onSubmit={repay} busy={busy} />}
          {page === "orders" && <OrdersContent data={data} setRefundOrderId={setRefundOrderId} setPage={setPage} />}
          {page === "operations" && <OperationsContent data={data} role={role} api={api} act={act} processRefund={processRefund} refundOrderId={refundOrderId} setRefundOrderId={setRefundOrderId} refundAmount={refundAmount} setRefundAmount={setRefundAmount} refundReason={refundReason} setRefundReason={setRefundReason} onRefund={refund} busy={busy} />}
          {page === "scenarios" && <ScenariosContent role={role} results={scenarioResults} busy={scenarioBusy} runAll={runAllScenarios} runOne={runScenario} />}
        </>}
      </main>
    </div>
    <nav className="bottom-nav" aria-label="ناوبری موبایل">{nav.filter((item) => item.id !== "scenarios").slice(0, 5).map((item) => <NavButton key={item.id} item={item} active={page === item.id} onClick={() => setPage(item.id)} />)}</nav>
    {toast && <div className="toast" role="status">{toast}</div>}
  </div>;
}

function NavButton({ item, active, onClick }: { item: NavItem; active: boolean; onClick: () => void }) {
  return <button className="nav-button" aria-current={active ? "page" : undefined} onClick={onClick}><span className="nav-glyph" aria-hidden="true">{item.glyph}</span><span>{item.label}</span></button>;
}

function HomeContent({ data, utilization, next, amount, setAmount, description, setDescription, providerResult, setProviderResult, onPurchase, busy, setPage }: {
  data: SandboxState; utilization: number; next?: Installment; amount: string; setAmount: (value: string) => void; description: string; setDescription: (value: string) => void;
  providerResult: string; setProviderResult: (value: string) => void; onPurchase: (event: FormEvent<HTMLFormElement>) => void; busy: string | null; setPage: (value: PageKey) => void;
}) {
  const remaining = data.installments.reduce((sum, item) => sum + dueAmount(item), 0);
  return <div className="grid-main"><div className="stack">
    <section className="credit-feature"><p className="eyebrow">اعتبار قابل استفاده</p><p className="credit-amount">{toman(data.credit.available_irr)}</p><div className="credit-track" aria-label={`مصرف اعتبار ${utilization} درصد`}><span style={{ width: `${utilization}%` }} /></div><div className="credit-subline"><span>سقف {data.credit.tier} · {toman(data.credit.limit_irr)}</span><span>مصرف‌شده {toman(data.credit.outstanding_irr)}</span></div></section>
    <div className="metric-row"><Metric label="ماندهٔ اقساط" value={toman(remaining)} note="تخصیص از قدیمی‌ترین قسط" /><Metric label="پرداخت بعدی" value={next ? toman(dueAmount(next)) : "ندارید"} note={next ? jalaliDate(next.due_date) : "پس از خرید اینجا نمایش داده می‌شود"} /></div>
    <section className="panel"><PanelHeading title="برنامهٔ اقساط" subtitle="چهار پرداخت با سررسیدهای ثبت‌شده" action={<button className="button button-secondary button-small" onClick={() => setPage("payments")}>همهٔ اقساط</button>} />
      {data.installments.length === 0 ? <Empty title="هنوز خرید اقساطی ندارید" detail="پس از خرید آزمایشی، برنامهٔ اقساط اینجا ذخیره می‌شود." /> : <div className="timeline">{data.installments.slice(0, 4).map((item) => <Timeline key={item.id} item={item} />)}</div>}
    </section>
  </div><div className="stack">
    <section className="panel"><PanelHeading title="خرید آزمایشی" subtitle="پرداخت اول با نتیجهٔ شبیه‌سازی‌شده" />
      <form className="form-stack" onSubmit={onPurchase}><Field id="purchase-amount" label="مبلغ خرید به تومان"><input id="purchase-amount" inputMode="numeric" min="4" max="40000000" type="number" value={amount} onChange={(event) => setAmount(event.target.value)} required /></Field><Field id="purchase-description" label="شرح خرید"><input id="purchase-description" maxLength={240} value={description} onChange={(event) => setDescription(event.target.value)} required /></Field><Field id="provider-result" label="پاسخ شبیه‌ساز پرداخت"><select id="provider-result" value={providerResult} onChange={(event) => setProviderResult(event.target.value)}><option value="SUCCESS">موفق</option><option value="FAILURE">ناموفق</option><option value="TIMEOUT">پاسخ نامشخص</option></select><span className="field-hint">این انتخاب فقط شبیه‌ساز محلی را کنترل می‌کند.</span></Field><button className="button button-primary" disabled={busy === "purchase"}>{busy === "purchase" ? "در حال ثبت…" : "بررسی اعتبار و خرید"}</button></form>
    </section>
    <section className="panel"><PanelHeading title="آخرین فعالیت‌ها" subtitle="سوابق ثبت‌شده از سمت سرور" />{data.activity.length === 0 ? <p className="field-hint">هنوز فعالیتی ثبت نشده است.</p> : <div className="list">{data.activity.slice(0, 5).map((item, index) => <div className="list-row" key={`${item.entity_id}-${index}`}><div><div className="row-title">{actionLabel(item.action)}</div><div className="row-meta">{jalaliDate(item.occurred_at)} · {item.reason}</div></div><span className="badge">ثبت‌شده</span></div>)}</div>}</section>
  </div></div>;
}

function CreditContent({ data, requestedLimit, setRequestedLimit, onSubmit, busy }: { data: SandboxState; requestedLimit: string; setRequestedLimit: (value: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; busy: string | null }) {
  return <div className="grid-main"><div className="stack">
    <section className="panel"><PanelHeading title="وضعیت اعتبار" subtitle="فقط تصمیم ثبت‌شده در سرور اعتبار را فعال می‌کند." action={<span className={badge(data.credit.status)}>{stateName(data.credit.status)}</span>} /><div className="metric-row"><Metric label="سقف تأییدشده" value={toman(data.credit.limit_irr)} note={`سطح ${data.credit.tier}`} /><Metric label="اعتبار رزروشده" value={toman(data.credit.reserved_irr)} note="تا تعیین وضعیت پرداخت حفظ می‌شود" /></div></section>
    <section className="panel"><PanelHeading title="درخواست‌های شما" subtitle="نسخهٔ سیاست و نتیجه برای هر درخواست نگهداری می‌شود." />{data.applications.length === 0 ? <Empty title="درخواستی ثبت نشده است" detail="مبلغ درخواستی را انتخاب کنید تا به صف بررسی بیاید." /> : <div className="list">{data.applications.map((item) => <div className="list-row" key={item.id}><div><div className="row-title">{toman(item.requested_limit_irr)}</div><div className="row-meta">سیاست {item.policy_version} · {item.reason_codes?.join("، ")}</div></div><span className={badge(item.state)}>{stateName(item.state)}</span></div>)}</div>}</section>
  </div><section className="panel"><PanelHeading title="درخواست اعتبار" subtitle="بازبین مجاز در بخش عملیات تصمیم را ثبت می‌کند." /><form className="form-stack" onSubmit={onSubmit}><Field id="limit-request" label="سقف درخواستی به تومان"><input id="limit-request" type="number" inputMode="numeric" min="1" max="40000000" value={requestedLimit} onChange={(event) => setRequestedLimit(event.target.value)} required /></Field><div className="notice">این سقف‌ها فقط مقدارهای آزمایشی هستند و پیشنهاد اعتباری واقعی نیستند.</div><button className="button button-primary" disabled={busy === "application"}>{busy === "application" ? "در حال ارسال…" : "ارسال برای بررسی"}</button></form></section></div>;
}

function PaymentsContent({ data, outstanding, repayAmount, setRepayAmount, providerResult, setProviderResult, onSubmit, busy }: { data: SandboxState; outstanding: number; repayAmount: string; setRepayAmount: (value: string) => void; providerResult: string; setProviderResult: (value: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; busy: string | null }) {
  return <div className="grid-main"><section className="panel"><PanelHeading title="اقساط فعال" subtitle="تاریخ‌ها از برنامهٔ ثبت‌شده خوانده می‌شوند." />{data.installments.length === 0 ? <Empty title="قسطی برای نمایش نیست" detail="یک خرید آزمایشی انجام دهید تا برنامه ساخته شود." /> : <div className="timeline">{data.installments.map((item) => <Timeline key={item.id} item={item} />)}</div>}</section>
    <div className="stack"><section className="panel"><PanelHeading title="بازپرداخت" subtitle="مبلغ به قدیمی‌ترین قسط باز تخصیص داده می‌شود." /><form className="form-stack" onSubmit={onSubmit}><Metric label="کل مانده" value={toman(outstanding)} note="تسویه زودتر از موعد مجاز است" /><Field id="repay-amount" label="مبلغ به تومان"><input id="repay-amount" type="number" inputMode="numeric" min="1" value={repayAmount} onChange={(event) => setRepayAmount(event.target.value)} required /></Field><Field id="repay-provider" label="نتیجهٔ درگاه آزمایشی"><select id="repay-provider" value={providerResult} onChange={(event) => setProviderResult(event.target.value)}><option value="SUCCESS">موفق</option><option value="FAILURE">ناموفق</option><option value="TIMEOUT">پاسخ نامشخص</option></select></Field><button className="button button-primary" disabled={busy === "repay" || outstanding === 0}>{busy === "repay" ? "در حال ثبت…" : "پرداخت مبلغ انتخاب‌شده"}</button></form></section><div className="notice">هزینه و جریمهٔ دیرکرد در این محیط فعال نیست.</div></div></div>;
}

function OrdersContent({ data, setRefundOrderId, setPage }: { data: SandboxState; setRefundOrderId: (value: string) => void; setPage: (value: PageKey) => void }) {
  return <section className="panel"><PanelHeading title="سوابق خرید" subtitle="وضعیت هر سفارش از بک‌اند خوانده می‌شود." />{data.orders.length === 0 ? <Empty title="خریدی ثبت نشده است" detail="از خانه یک خرید آزمایشی بسازید." /> : <div className="list">{data.orders.map((order) => <div className="list-row" key={order.id}><div><div className="row-title">{order.description}</div><div className="row-meta">{jalaliDate(order.created_at)} · کد {order.id.slice(0, 8)}</div><div className="row-actions"><button className="button button-secondary button-small" disabled={!(["SUCCEEDED", "PARTIALLY_REFUNDED"].includes(order.status))} onClick={() => { setRefundOrderId(order.id); setPage("operations"); }}>درخواست بازپرداخت وجه</button></div></div><div style={{ textAlign: "left" }}><div className="row-amount">{toman(order.amount_irr)}</div><span className={badge(order.status)}>{stateName(order.status)}</span></div></div>)}</div>}</section>;
}

function OperationsContent({ data, role, api, act, processRefund, refundOrderId, setRefundOrderId, refundAmount, setRefundAmount, refundReason, setRefundReason, onRefund, busy }: {
  data: SandboxState; role: string; api: (path: string, body?: unknown, method?: string) => Promise<ApiRow>;
  act: (key: string, fn: () => Promise<ApiRow>, success: string) => Promise<ApiRow | null>;
  processRefund: (id: string, action: string) => Promise<void>; refundOrderId: string; setRefundOrderId: (value: string) => void;
  refundAmount: string; setRefundAmount: (value: string) => void; refundReason: string; setRefundReason: (value: string) => void;
  onRefund: (event: FormEvent<HTMLFormElement>) => void; busy: string | null;
}) {
  const pendingApps = data.applications.filter((item) => ["MANUAL_REVIEW", "SUBMITTED", "MORE_INFO"].includes(item.state));
  const appReview = role === "CREDIT_REVIEWER" || role === "SUPER_ADMIN";
  return <div className="stack">
    <div className="notice notice-warning">تعویض نقش فقط برای دیدن مسیرهای محیط آزمایشی است و احراز هویت پرسنل محسوب نمی‌شود.</div>
    <div className="metric-row"><Metric label="بودجهٔ قابل تخصیص" value={toman(data.funding.available_irr)} note="بودجهٔ شبیه‌سازی‌شدهٔ شرکت" /><Metric label="اصل مطالبات" value={toman(data.funding.committed_irr)} note="تعهد جاری مشتریان نمونه" /></div>
    <div className="grid-main">
      <section className="panel"><PanelHeading title="بازبینی درخواست اعتبار" subtitle="تصمیم‌ها با دلیل در سابقه ثبت می‌شوند." />{pendingApps.length === 0 ? <Empty title="درخواست معوقی نیست" detail="در نقش مشتری یک درخواست تازه ارسال کنید." /> : <div className="list">{pendingApps.map((item) => <div className="list-row" key={item.id}><div><div className="row-title">درخواست {toman(item.requested_limit_irr)}</div><div className="row-meta">{item.id.slice(0, 8)} · سیاست {item.policy_version}</div><div className="row-actions"><button className="button button-soft button-small" disabled={!appReview || busy === `app-${item.id}`} onClick={() => void act(`app-${item.id}`, () => api(`applications/${item.id}/review`, { decision: "APPROVE", reason: "تأیید در فضای آزمایشی" }), "درخواست اعتبار تأیید شد.")}>تأیید</button><button className="button button-danger button-small" disabled={!appReview || busy === `app-${item.id}`} onClick={() => void act(`app-${item.id}`, () => api(`applications/${item.id}/review`, { decision: "DECLINE", reason: "نیازمند بررسی بیشتر" }), "تصمیم ثبت شد.")}>رد</button></div></div><span className={badge(item.state)}>{stateName(item.state)}</span></div>)}</div>}</section>
      <section className="panel"><PanelHeading title="درخواست‌های بازپرداخت وجه" subtitle="تکمیل پس از تأیید مالی و پاسخ قطعی ارائه‌دهنده است." />{data.refunds.length === 0 ? <Empty title="درخواستی ثبت نشده است" detail="مشتری از صفحهٔ خریدها درخواست می‌دهد." /> : <div className="list">{data.refunds.map((item) => <div className="list-row" key={item.id}><div><div className="row-title">{item.description}</div><div className="row-meta">{item.reason} · {toman(item.amount_irr)}</div><div className="row-actions">{item.status === "REQUESTED" && <><button className="button button-soft button-small" disabled={role !== "FINANCE_OFFICER" && role !== "SUPER_ADMIN"} onClick={() => void processRefund(item.id, "APPROVE")}>تأیید مالی</button><button className="button button-danger button-small" disabled={role !== "FINANCE_OFFICER" && role !== "SUPER_ADMIN"} onClick={() => void processRefund(item.id, "DECLINE")}>رد درخواست</button></>}{item.status === "APPROVED" && <button className="button button-primary button-small" disabled={role !== "FINANCE_OFFICER" && role !== "SUPER_ADMIN"} onClick={() => void processRefund(item.id, "CONFIRM_SANDBOX_PROVIDER")}>ثبت پاسخ قطعی آزمایشی</button>}</div></div><span className={badge(item.status)}>{stateName(item.status)}</span></div>)}</div>}</section>
    </div>
    <div className="grid-main">
      <section className="panel"><PanelHeading title="کنترل‌های ریسک" subtitle="توقف تأمین مالی فقط تعهدهای تازه را می‌بندد." /><div className="button-row"><button className="button button-danger" disabled={role !== "SUPER_ADMIN"} onClick={() => void act("credit-suspend", () => api("admin/credit/SUSPENDED", { reason: "توقف آزمایشی" }), "اعتبار مشتری متوقف شد.")}>توقف اعتبار</button><button className="button button-secondary" disabled={role !== "SUPER_ADMIN"} onClick={() => void act("credit-resume", () => api("admin/credit/ACTIVE", { reason: "فعال‌سازی آزمایشی" }), "اعتبار فعال شد.")}>فعال‌سازی اعتبار</button><button className="button button-danger" disabled={role !== "SUPER_ADMIN"} onClick={() => void act("fund-stop", () => api("admin/funding-circuit", { enabled: false, reason: "مدار اضطراری آزمایشی" }), "مدار توقف تأمین مالی فعال شد.")}>مدار توقف تأمین</button><button className="button button-soft" disabled={role !== "SUPER_ADMIN"} onClick={() => void act("fund-start", () => api("admin/funding-circuit", { enabled: true, reason: "ادامهٔ آزمایشی" }), "تأمین مالی آزمایشی از سر گرفته شد.")}>ادامهٔ تأمین</button></div></section>
      <section className="panel"><PanelHeading title="تطبیق و بازیابی" subtitle="مغایرت‌ها و کارهای مانده در پایگاه داده ذخیره می‌شوند." /><div className="button-row"><button className="button button-secondary" disabled={role !== "FINANCE_OFFICER" && role !== "SUPER_ADMIN"} onClick={() => void act("reconcile", () => api("admin/reconcile", { reference: `sandbox-${Date.now()}`, expectedToman: 100000, observedToman: 99000 }), "مغایرت برای بررسی ثبت شد.")}>ایجاد مغایرت آزمایشی</button><button className="button button-secondary" disabled={role !== "FINANCE_OFFICER" && role !== "SUPER_ADMIN"} onClick={() => void act("overdue", () => api("admin/overdue"), "وضعیت سررسیدها بررسی شد.")}>بررسی سررسیدها</button><button className="button button-secondary" disabled={role !== "SUPER_ADMIN"} onClick={() => void act("recover", () => api("admin/jobs/recover"), "کارهای آماده پردازش شدند.")}>بازیابی کارهای پس‌زمینه</button></div></section>
    </div>
    <section className="panel"><PanelHeading title="ثبت درخواست بازپرداخت وجه" subtitle="مشتری درخواست می‌دهد، مالی بررسی می‌کند و شبیه‌ساز پاسخ می‌دهد." /><form className="form-grid" onSubmit={onRefund}><Field id="refund-order" label="شمارهٔ سفارش"><select id="refund-order" value={refundOrderId} onChange={(event) => setRefundOrderId(event.target.value)} required><option value="">انتخاب سفارش</option>{data.orders.filter((order) => ["SUCCEEDED", "PARTIALLY_REFUNDED"].includes(order.status)).map((order) => <option key={order.id} value={order.id}>{order.description} · {order.id.slice(0, 8)}</option>)}</select></Field><Field id="refund-amount" label="مبلغ به تومان"><input id="refund-amount" type="number" min="1" value={refundAmount} onChange={(event) => setRefundAmount(event.target.value)} required /></Field><div className="field span-2"><label htmlFor="refund-reason">علت درخواست</label><input id="refund-reason" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} required /></div><div className="button-row span-2"><button className="button button-primary" disabled={busy === "refund"}>ثبت درخواست بازپرداخت</button><span className="action-note">نقش جاری: {roleOptions.find(([value]) => value === role)?.[1]}</span></div></form></section>
  </div>;
}

function ScenariosContent({ role, results, busy, runAll, runOne }: { role: string; results: Record<string, ScenarioResult>; busy: boolean; runAll: () => Promise<void>; runOne: (key: string) => Promise<void> }) {
  const completed = Object.values(results);
  const passed = completed.filter((result) => result.status === "PASS").length;
  return <div className="stack"><section className="panel"><PanelHeading title="سناریوهای یکپارچگی مالی" subtitle="هر مورد در پایگاه دادهٔ آزمایشی اجرا و نتیجه ذخیره می‌شود." action={<span className="result-count">{passed} / {completed.length} موفق</span>} /><div className="notice notice-warning">این آزمون‌ها فقط داده‌های ساختگی محلی می‌سازند. آزمون‌های هم‌زمانی و بازیابی، سفارش‌های آزمایشی تسویه‌شده در تاریخچه نگه می‌دارند.</div><button className="button button-primary" disabled={busy || role !== "SUPER_ADMIN"} onClick={() => void runAll()}>{busy ? "در حال اجرای آزمون‌ها…" : role === "SUPER_ADMIN" ? "اجرای هر ۲۰ سناریو" : "برای اجرای آزمون‌ها نقش مدیر کل آزمایشی را انتخاب کنید"}</button></section>
    <section className="panel"><div className="scenario-list">{scenarioList.map(([key, title, description], index) => { const result = results[key]; return <div className="scenario-row" key={key}><span className="scenario-index">{new Intl.NumberFormat("fa-IR").format(index + 1)}</span><div><div className="row-title">{title}</div><div className="scenario-description">{result?.detail ?? description}{result && <span className="scenario-duration"> · {new Intl.NumberFormat("fa-IR").format(result.durationMs)} ms</span>}</div></div><div className="scenario-status">{result ? <><span className={result.status === "PASS" ? "badge badge-success" : "badge badge-danger"}>{result.status === "PASS" ? "موفق" : "ناموفق"}</span><button className="button button-secondary button-small" disabled={busy || role !== "SUPER_ADMIN"} onClick={() => void runOne(key)}>تکرار</button></> : <button className="button button-secondary button-small" disabled={busy || role !== "SUPER_ADMIN"} onClick={() => void runOne(key)}>اجرا</button>}</div></div>; })}</div></section></div>;
}

function Timeline({ item }: { item: Installment }) {
  const paid = item.status === "PAID";
  return <div className="timeline-item"><span className={`timeline-dot ${paid ? "timeline-dot-paid" : ""}`} aria-hidden="true" /><div><div className="timeline-title">قسط {new Intl.NumberFormat("fa-IR").format(item.sequence_number)} · {item.description}</div><div className="timeline-date">سررسید {jalaliDate(item.due_date)} <span className={badge(item.status)}>{stateName(item.status)}</span></div></div><div className="row-amount">{toman(item.adjusted_amount_irr)}</div></div>;
}
function PanelHeading({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) { return <div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>; }
function Metric({ label, value, note }: { label: string; value: string; note: string }) { return <div className="metric"><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></div>; }
function Field({ id, label, children }: { id: string; label: string; children: React.ReactNode }) { return <div className="field"><label htmlFor={id}>{label}</label>{children}</div>; }
function Empty({ title, detail }: { title: string; detail: string }) { return <div className="empty-state"><strong>{title}</strong>{detail}</div>; }
