import argparse
import json
import os
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description='Отправить состояние салфетницы на сервер')
parser.add_argument('--url', default='http://127.0.0.1:8080')
parser.add_argument('--device', default='table-01')
parser.add_argument('--state', choices=['low', 'ok'], required=True)
args = parser.parse_args()

token = os.environ.get('DEVICE_TOKEN')
if not token:
    parser.error('Задайте DEVICE_TOKEN в окружении')

data = {
    'device_id': args.device,
    'low': args.state == 'low',
}
headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token,
}
request = Request(
    args.url.rstrip('/') + '/api/telemetry',
    json.dumps(data).encode(),
    headers,
)
with urlopen(request, timeout=10) as response:
    print(response.read().decode())
