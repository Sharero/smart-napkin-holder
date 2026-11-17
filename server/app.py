import hmac
import json
import os
import re
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs


class Store:
    def __init__(self, path):
        self.path = path
        with self.connect() as db:
            db.executescript('''CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY, low INTEGER NOT NULL, seen INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT, device TEXT NOT NULL,
                low INTEGER NOT NULL, time INTEGER NOT NULL);''')

    def connect(self):
        return sqlite3.connect(self.path, timeout=10)

    def update(self, device, low):
        now = int(time.time())
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            old = db.execute('SELECT low FROM devices WHERE id=?', (device,)).fetchone()
            if old is None or old[0] != int(low):
                db.execute('INSERT INTO events(device,low,time) VALUES(?,?,?)', (device, int(low), now))
            db.execute('INSERT INTO devices VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET low=excluded.low,seen=excluded.seen', (device, int(low), now))
        return {'ok': True}

    def snapshot(self, after):
        with self.connect() as db:
            db.execute('BEGIN')
            devices = []
            for device_id, low, seen in db.execute('SELECT id,low,seen FROM devices ORDER BY id'):
                devices.append({
                    'id': device_id,
                    'low': bool(low),
                    'seen': seen,
                    'online': int(time.time()) - seen < 180,
                })

            events = []
            rows = db.execute(
                'SELECT id,device,low,time FROM events WHERE id>? ORDER BY id LIMIT 500',
                (after,),
            )
            for event_id, device_id, low, event_time in rows:
                events.append({
                    'id': event_id,
                    'device': device_id,
                    'low': bool(low),
                    'time': event_time,
                })

        cursor = after
        if events:
            cursor = events[-1]['id']
        return {'devices': devices, 'events': events, 'cursor': cursor}



def handler(store, device_token, admin_token):
    class Handler(BaseHTTPRequestHandler):
        def reply(self, status, body):
            data = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def authorized(self, token):
            return hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token)

        def do_GET(self):
            url = urlparse(self.path)
            if url.path != '/api/sync':
                return self.reply(404, {'error': 'not found'})
            if not self.authorized(admin_token):
                return self.reply(401, {'error': 'unauthorized'})
            try:
                after = int(parse_qs(url.query).get('after', ['0'])[0])
                if after < 0:
                    raise ValueError()
                self.reply(200, store.snapshot(after))
            except ValueError:
                self.reply(400, {'error': 'invalid cursor'})

        def do_POST(self):
            if self.path != '/api/telemetry':
                return self.reply(404, {'error': 'not found'})
            if not self.authorized(device_token):
                return self.reply(401, {'error': 'unauthorized'})
            try:
                size = int(self.headers.get('Content-Length', '0'))
                if not 0 < size <= 2048:
                    raise ValueError()
                body = json.loads(self.rfile.read(size))
                device, low = body['device_id'], body['low']
                if not isinstance(device, str) or not re.fullmatch(r'[a-zA-Z0-9_-]{1,48}', device) or type(low) is not bool:
                    raise ValueError()
                self.reply(200, store.update(device, low))
            except (ValueError, KeyError, TypeError):
                self.reply(400, {'error': 'expected device_id and boolean low'})
    return Handler


if __name__ == '__main__':
    device = os.environ.get('DEVICE_TOKEN', '')
    admin = os.environ.get('ADMIN_TOKEN', '')
    if min(len(device), len(admin)) < 20 or device == admin:
        raise SystemExit('Задайте разные DEVICE_TOKEN и ADMIN_TOKEN длиной минимум 20 символов')
    server = ThreadingHTTPServer((os.getenv('HOST', '127.0.0.1'), int(os.getenv('PORT', '8080'))),
                                handler(Store(os.getenv('DATABASE', 'salfetki.sqlite3')), device, admin))
    server.timeout = 15
    print('Сервер запущен:', server.server_address, flush=True)
    server.serve_forever()
