import requests
import json
import os

path = os.path.join(os.path.dirname(__file__), 'handshake_test.json')
url = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline'
headers = {
    'Content-Type': 'application/json',
    'userID': 'harshvidyabamer',
    'ulcaApiKey': '129fe72a91-352c-4321-b577-a89c3d3c6060'
}

with open(path, 'r') as f:
    payload = json.load(f)

print('Sending request to', url)
try:
    resp = requests.post(url, headers=headers, json=payload, timeout=15)
    print('HTTP Status:', resp.status_code)
    print('Response Text:', resp.text)
except Exception as e:
    print('Exception:', type(e), e)
