export const supportMode =
  new URLSearchParams(window.location.search).get("support") === "1";
export const supportOrigin =
  import.meta.env.VITE_SUPPORT_ORIGIN || "http://localhost:5174";
let grant: string | null = null;
export function setSupportGrant(value: string | null) {
  grant = value;
}
export function endSupportView() {
  grant = null;
  window.dispatchEvent(new Event("support-ended"));
  window.parent.postMessage({ type: "answerbase:end-request" }, supportOrigin);
}
export async function delegatedFetch(path: string, init?: RequestInit) {
  if (!grant) throw new Error("Support access is not active.");
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || ""}/api/v1/delegated${path}`,
    {
      ...init,
      signal: AbortSignal.any([
        AbortSignal.timeout(path.endsWith("/stream") ? 420000 : 60000),
        ...(init?.signal ? [init.signal] : []),
      ]),
      headers: { ...init?.headers, "X-Support-Grant": grant },
    },
  );
  if (response.status === 401) {
    grant = null;
    window.dispatchEvent(new Event("support-ended"));
  }
  return response;
}
