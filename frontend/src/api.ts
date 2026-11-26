import { supabase } from "./auth/client";
import { delegatedFetch, supportMode } from "./support/transport";
export async function request(path: string, init?: RequestInit) {
  let response: Response;
  if (supportMode) response = await delegatedFetch(path, init);
  else {
    const { data } = await supabase!.auth.getSession();
    if (!data.session) throw new Error("Sign in to continue.");
    response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL || ""}/api/v1${path}`,
      {
        ...init,
        signal: AbortSignal.any([
          AbortSignal.timeout(path.endsWith("/stream") ? 420000 : 60000),
          ...(init?.signal ? [init.signal] : []),
        ]),
        headers: {
          ...init?.headers,
          Authorization: `Bearer ${data.session.access_token}`,
        },
      },
    );
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(
      response.status === 413
        ? "Choose a file no larger than 5 MB."
        : body?.detail || "Unable to complete this request. Please retry.",
    );
  }
  return response;
}
