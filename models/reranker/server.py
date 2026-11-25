"""Private local cross-encoder. Never expose this unauthenticated service publicly."""
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from sentence_transformers import CrossEncoder

MODEL = os.getenv('RERANKER_MODEL', 'cross-encoder/ms-marco-MiniLM-L6-v2')
model = CrossEncoder(MODEL, device='cpu', max_length=512, trust_remote_code=False)

class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, *_):
        pass  # Never log customer text.

    def reply(self, code, value):
        body = json.dumps(value, allow_nan=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.reply(200 if self.path == '/health' else 404, {'model': MODEL})

    def do_POST(self):
        if self.path != '/rerank':
            return self.reply(404, {'error': 'Not found'})
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 600_000:
                return self.reply(413, {'error': 'Invalid request size'})
            body = json.loads(self.rfile.read(size))
            query, passages = body['query'], body['passages']
            if not isinstance(query, str) or not 0 < len(query) <= 2000:
                raise ValueError('Invalid query')
            if not isinstance(passages, list) or not 0 < len(passages) <= 100:
                raise ValueError('Invalid candidate count')
            if any(not isinstance(p, str) or len(p) > 5000 for p in passages):
                raise ValueError('Invalid passage')
            scores = model.predict([(query, p) for p in passages], batch_size=16).tolist()
            self.reply(200, {'model': MODEL, 'scores': scores})
        except (ValueError, KeyError, TypeError):
            self.reply(400, {'error': 'Invalid reranking request'})
        except Exception:
            self.reply(503, {'error': 'Reranking unavailable'})

if __name__ == '__main__':
    HTTPServer((os.getenv('BIND_ADDRESS', '127.0.0.1'), int(os.getenv('PORT', '8092'))), Handler).serve_forever()
