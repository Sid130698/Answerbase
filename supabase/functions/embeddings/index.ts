// Deploy with gateway JWT verification disabled: this endpoint uses its own server-only secret.
const model = new Supabase.ai.Session('gte-small');
Deno.serve(async (request: Request) => {
  const secret = Deno.env.get('EMBEDDING_SECRET');
  if (!secret || request.headers.get('Authorization') !== `Bearer ${secret}`)
    return new Response('Unauthorized', { status: 401 });
  if (request.method !== 'POST') return new Response('Method not allowed', { status: 405 });
  const body = await request.text();
  if (body.length > 16384) return new Response('Too large', { status: 413 });
  try {
    const { text } = JSON.parse(body);
    if (typeof text !== 'string' || !text.trim() || text.length > 12000)
      return new Response('Invalid text', { status: 400 });
    const embedding = await model.run(text, { mean_pool: true, normalize: true });
    return Response.json({ embedding });
  } catch {
    return new Response('Embedding failed', { status: 503 });
  }
});
