import json
import tempfile
import io
import unittest
from urllib.error import HTTPError
from server.app import Store, handler

class ServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = Store(self.temp.name+'/test.sqlite')
        self.handler = handler(self.store,'device-secret','admin-secret')
    def tearDown(self):
        self.temp.cleanup()
    def request(self,path,token,body=None):

        payload = b'' if body is None else json.dumps(body).encode()
        method = 'GET' if body is None else 'POST'
        wire = (f'{method} {path} HTTP/1.0\r\nAuthorization: Bearer {token}\r\nContent-Length: {len(payload)}\r\n\r\n').encode()+payload
        class Connection:
            def __init__(self): self.output=io.BytesIO()
            def sendall(self, data): self.output.write(data)
            def makefile(self,mode,*args): return io.BytesIO(wire) if 'r' in mode else self.output
        connection=Connection()
        self.handler(connection,('127.0.0.1',0),object())
        headers, response = connection.output.getvalue().split(b'\r\n\r\n',1)
        code=int(headers.split()[1])
        if code >= 400: raise HTTPError(path,code,'error',{},None)
        return json.loads(response)
    def test_changes_and_cursor_survive_restart(self):
        for low in (False,False,True,True,False):
            self.request('/api/telemetry','device-secret',dict(device_id='table-01',low=low))
        snapshot=self.request('/api/sync','admin-secret')
        self.assertEqual([False,True,False],[e['low'] for e in snapshot['events']])
        self.assertTrue(snapshot['devices'][0]['online'])
        self.assertEqual([],self.request('/api/sync?after=3','admin-secret')['events'])
        self.assertEqual(3,Store(self.store.path).snapshot(0)['cursor'])
    def test_validation_and_permissions(self):
        for path,token,body,code in [('/api/sync','device-secret',None,401),
            ('/api/telemetry','admin-secret',dict(device_id='x',low=True),401),
            ('/api/telemetry','device-secret',dict(device_id='x',low='true'),400),
            ('/api/sync?after=-1','admin-secret',None,400)]:
            with self.assertRaises(HTTPError) as error: self.request(path,token,body)
            self.assertEqual(code,error.exception.code)
            error.exception.close()
    def test_offline(self):
        self.store.update('x',True)
        with self.store.connect() as db: db.execute('UPDATE devices SET seen=0')
        self.assertFalse(self.store.snapshot(0)['devices'][0]['online'])

if __name__=='__main__': unittest.main()
