import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

type RouteParams = { params: Promise<{ path: string[] }> };

async function forward(request: Request, context: RouteParams) {
  const { path } = await context.params;
  const apiBase = process.env.PRIVATEPAY_API_URL ?? "http://127.0.0.1:8080";
  const upstreamUrl = `${apiBase.replace(/\/$/, "")}/api/v1/sandbox/${path.map(encodeURIComponent).join("/")}`;
  const headers = new Headers();
  const contentType = request.headers.get("content-type");
  const role = request.headers.get("x-demo-role");
  if (contentType) headers.set("content-type", contentType);
  if (role) headers.set("x-demo-role", role);
  const timeoutMs = path[0] === "scenarios" ? 120_000 : 8_000;

  try {
    const response = await fetch(upstreamUrl, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
      cache: "no-store",
      signal: AbortSignal.timeout(timeoutMs),
    });
    const responseBody = await response.arrayBuffer();
    return new NextResponse(responseBody, {
      status: response.status,
      headers: { "content-type": response.headers.get("content-type") ?? "application/json" },
    });
  } catch {
    return NextResponse.json(
      {
        code: "SANDBOX_API_UNAVAILABLE",
        message: "سرویس آزمایشی وصل نیست. ابتدا Docker را اجرا کنید و دستور docker compose up -d --build را بزنید.",
      },
      { status: 503 },
    );
  }
}

export const GET = forward;
export const POST = forward;
