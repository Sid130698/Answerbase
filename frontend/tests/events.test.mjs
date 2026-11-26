import { test } from "node:test";
import assert from "node:assert/strict";
import { readEvents } from "../src/answers/events.ts";

test("SSE decodes fragmented UTF-8, CRLF, comments and multiline data", async () => {
  const bytes = new TextEncoder().encode(
    ': keepalive\r\n\r\nevent: status\r\ndata: {"message":\r\ndata: "Reading café…"}\r\n\r\nevent: answer\ndata: {"claims":[]}\n\n',
  );
  const response = new Response(
    new ReadableStream({
      start(controller) {
        for (const byte of bytes) controller.enqueue(Uint8Array.of(byte));
        controller.close();
      },
    }),
  );
  const events = [];
  await readEvents(response, (kind, data) => events.push([kind, data]));
  assert.deepEqual(events, [
    ["status", { message: "Reading café…" }],
    ["answer", { claims: [] }],
  ]);
  assert.equal(response.body.locked, false);
});

test("Consumer failure cancels the stream and releases its reader", async () => {
  let cancelled = false;
  const response = new Response(
    new ReadableStream({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode(
            'event: failure\ndata: {"message":"Unavailable"}\n\n',
          ),
        );
      },
      cancel() {
        cancelled = true;
      },
    }),
  );
  await assert.rejects(
    readEvents(response, () => {
      throw new Error("Unavailable");
    }),
    /Unavailable/,
  );
  assert.equal(cancelled, true);
  assert.equal(response.body.locked, false);
});

test("Mixed LF/CRLF boundaries do not consume the next event", async () => {
  const events = [];
  await readEvents(new Response('event: status\ndata: {"step":1}\n\r\nevent: answer\r\ndata: {"done":true}\r\n\n'),
    (kind, data) => events.push([kind, data]));
  assert.deepEqual(events, [["status", {step: 1}], ["answer", {done: true}]]);
});
