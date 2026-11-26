/** Read SSE frames across arbitrary byte boundaries, including CRLF and UTF-8 splits. */
export async function readEvents(
  response: Response,
  receive: (kind: string, data: unknown) => void,
) {
  if (!response.body)
    throw new Error("Streaming is not available in this browser.");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      pending += decoder.decode(value, { stream: true });
      let boundary: number;
      while ((boundary = pending.search(/\r?\n\r?\n/)) >= 0) {
        const frame = pending.slice(0, boundary);
        pending = pending.slice(
          boundary + pending.slice(boundary).match(/^\r?\n\r?\n/)![0].length,
        );
        const lines = frame.split(/\r?\n/);
        const kind =
          lines
            .find((line) => line.startsWith("event:"))
            ?.slice(6)
            .trim() || "message";
        const data = lines
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).replace(/^ /, ""))
          .join("\n");
        if (data) receive(kind, JSON.parse(data));
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
