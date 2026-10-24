#!/usr/bin/env python3
"""演示 AppKey/AppSecret + HMAC-SHA256 签名调用开放平台。

使用前先启动服务（默认 8081）。脚本每次生成新的 nonce，因此可重复执行。
如需换地址：BASE_URL=http://127.0.0.1:8082 python3 scripts/demo-signature.py
"""
import base64
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request
import uuid

# 演示应用信息（与 InMemoryAppRegistry 中的种子数据一致）
BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8081")
PATH = "/open/v1/enterprise/verify"
APP_ID = "app-sign"
APP_SECRET = "sign-secret"

# 请求体必须与计算摘要时使用的字节完全一致：
# 先序列化成紧凑 JSON（无多余空格），再编码成 UTF-8 字节。
body = json.dumps(
    {"enterpriseName": "示例科技有限公司", "creditCode": "91310000MA1K1234XX"},
    ensure_ascii=False,
    separators=(",", ":"),
).encode("utf-8")

# 毫秒时间戳，服务端默认允许 ±5 分钟偏差。
timestamp = str(int(time.time() * 1000))

# 一次性随机串，服务端在 nonce-ttl 内拒绝重复值。
nonce = uuid.uuid4().hex

# 请求体摘要：小写十六进制 SHA-256，放入 Content-SHA256 请求头。
content_sha256 = hashlib.sha256(body).hexdigest()

# 待签名串：字段之间用换行分隔，顺序固定，不能多也不能少。
canonical = "\n".join(["POST", PATH, "", timestamp, nonce, content_sha256])

# HMAC-SHA256 后做 Base64 编码，放入 X-Signature 请求头。
signature = base64.b64encode(
    hmac.new(APP_SECRET.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).digest()
).decode("utf-8")

request = urllib.request.Request(BASE_URL + PATH, data=body, method="POST")
request.add_header("Content-Type", "application/json")
request.add_header("X-App-Id", APP_ID)
request.add_header("X-Timestamp", timestamp)
request.add_header("X-Nonce", nonce)
request.add_header("X-Signature-Method", "HMAC-SHA256")
request.add_header("X-Signature", signature)
request.add_header("Content-SHA256", content_sha256)

try:
    with urllib.request.urlopen(request) as response:
        print(response.status, response.read().decode("utf-8"))
except urllib.error.HTTPError as error:
    # 失败时也把服务端返回的 JSON 打出来，方便对照错误码排查。
    print(error.code, error.read().decode("utf-8"))
