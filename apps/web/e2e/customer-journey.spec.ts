import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

async function loginAsDemoCustomer(page: import("@playwright/test").Page) {
  await page.goto("/");
  await page.getByLabel("شمارهٔ همراه نمونه").fill("00000000000");
  await page.getByLabel("کد یک‌بارمصرف آزمایشی").fill("246810");
  await page.getByRole("button", { name: "ورود به حساب آزمایشی" }).click();
  await expect(page.getByRole("heading", { name: "سلام، کاربر آزمایشی" })).toBeVisible();
}

test("login page is Persian RTL, labeled, and has no WCAG A/AA violations", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  await expect(page.getByRole("heading", { name: "قسط‌ها و خریدهایتان را یک‌جا ببینید" })).toBeVisible();
  await expect(page.getByLabel("شمارهٔ همراه نمونه")).toBeVisible();

  const audit = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  expect(audit.violations).toEqual([]);
});

test("customer completes a purchase, sees four installments, and settles early", async ({ page }) => {
  await loginAsDemoCustomer(page);
  const primaryNav = page.getByRole("navigation", { name: "ناوبری اصلی" });
  await primaryNav.getByRole("button", { name: "اقساط" }).click();
  await expect(page.getByRole("heading", { name: "برنامهٔ پرداخت" })).toBeVisible();
  const installmentsBefore = await page.locator(".timeline-item").count();
  await primaryNav.getByRole("button", { name: "خانه" }).click();

  const amount = page.getByLabel("مبلغ خرید به تومان");
  await amount.fill("3");
  expect(await amount.evaluate((input: HTMLInputElement) => input.validity.valid)).toBe(false);
  await amount.fill("4000000");
  await page.getByRole("button", { name: "بررسی اعتبار و خرید" }).click();

  await expect(page.getByRole("heading", { name: "برنامهٔ پرداخت" })).toBeVisible();
  await expect(page.locator(".timeline-item")).toHaveCount(installmentsBefore + 4);
  await expect(page.getByText("پرداخت شده").first()).toBeVisible();
  await page.getByLabel("مبلغ به تومان").fill("3000000");
  await page.getByRole("button", { name: "پرداخت مبلغ انتخاب‌شده" }).click();
  await expect(page.getByRole("status")).toContainText("بازپرداخت در دفتر آزمایشی ثبت شد");
  await expect(page.getByText("قسطی برای نمایش نیست")).toHaveCount(0);
});

test("customer applies for credit, a reviewer approves, then finance completes a full cancellation", async ({ page }) => {
  await loginAsDemoCustomer(page);
  await page.getByRole("navigation", { name: "ناوبری اصلی" })
    .getByRole("button", { name: "اعتبار" }).click();
  await page.getByLabel("سقف درخواستی به تومان").fill("5000000");
  await page.getByRole("button", { name: "ارسال برای بررسی" }).click();
  await expect(page.getByRole("status")).toContainText("درخواست برای بررسی انسانی ارسال شد");

  await page.locator("#demo-role").selectOption("CREDIT_REVIEWER");
  await page.getByRole("navigation", { name: "ناوبری اصلی" })
    .getByRole("button", { name: "عملیات" }).click();
  await page.getByRole("button", { name: "تأیید" }).last().click();
  await expect(page.getByRole("status")).toContainText("درخواست اعتبار تأیید شد");

  await page.locator("#demo-role").selectOption("CUSTOMER");
  await page.getByRole("navigation", { name: "ناوبری اصلی" })
    .getByRole("button", { name: "خانه" }).click();
  await page.getByLabel("مبلغ خرید به تومان").fill("4000000");
  await page.getByRole("button", { name: "بررسی اعتبار و خرید" }).click();
  await page.getByRole("navigation", { name: "ناوبری اصلی" })
    .getByRole("button", { name: "خریدها" }).click();
  await expect(page.getByRole("heading", { name: "خریدها و رسیدها" })).toBeVisible();
  await page.getByRole("button", { name: "درخواست بازپرداخت وجه" }).first().click();
  await page.getByLabel("مبلغ به تومان").fill("4000000");
  await page.getByRole("button", { name: "ثبت درخواست بازپرداخت" }).click();

  await page.locator("#demo-role").selectOption("FINANCE_OFFICER");
  await page.getByRole("button", { name: "تأیید مالی" }).click();
  await page.getByRole("button", { name: "ثبت پاسخ قطعی آزمایشی" }).click();
  await expect(page.getByText("تکمیل شده").last()).toBeVisible();
  await page.getByRole("navigation", { name: "ناوبری اصلی" })
    .getByRole("button", { name: "خریدها" }).click();
  await expect(page.getByRole("heading", { name: "خریدها و رسیدها" })).toBeVisible();
  await expect(page.getByText("بازپرداخت کامل").first()).toBeVisible();
});

test("mobile navigation remains available at a narrow viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loginAsDemoCustomer(page);
  await expect(page.locator(".bottom-nav")).toBeVisible();
  await expect(page.locator(".sidebar")).toBeHidden();
});

test("owner sandbox screen executes and displays all twenty financial scenarios", async ({ page }) => {
  test.setTimeout(60_000);
  await loginAsDemoCustomer(page);
  await page.locator("#demo-role").selectOption("SUPER_ADMIN");
  await page.getByRole("button", { name: "آزمون‌ها" }).click();
  await page.getByRole("button", { name: "اجرای هر ۲۰ سناریو" }).click();

  await expect(page.getByRole("status")).toContainText("20 از 20 سناریو موفق بود");
  await expect(page.locator(".scenario-row")).toHaveCount(20);
  await expect(page.locator(".scenario-status .badge-success")).toHaveCount(20);
});
