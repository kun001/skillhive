#!/usr/bin/env python3
"""Validate namespace deletion through real authenticated APIs using disposable fixtures.

Usage: SMOKE_ADMIN_USERNAME=... SMOKE_ADMIN_PASSWORD=... python3 scripts/namespace-knowledge-delete-smoke-test.py http://localhost:8080
Creates uniquely named namespaces and deletes only those fixtures. Requires an OWNER-capable administrator.
"""

import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Client:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, method, path, payload=None, content_type="application/json", expected=200):
        headers = {"Accept": "application/json"}
        token = next((cookie.value for cookie in self.cookies if cookie.name == "XSRF-TOKEN"), None)
        if token:
            headers["X-XSRF-TOKEN"] = urllib.parse.unquote(token)
        if payload is not None:
            headers["Content-Type"] = content_type
            if not isinstance(payload, bytes):
                payload = json.dumps(payload).encode()
        req = urllib.request.Request(self.base_url + path, data=payload, headers=headers, method=method)
        try:
            response = self.opener.open(req, timeout=30)
        except urllib.error.HTTPError as exc:
            response = exc
        status = response.code
        body = json.loads(response.read())
        assert status == expected, (method, path, status, body.get("message"))
        if expected == 200:
            assert body["code"] == 0, body.get("message")
        return body.get("data")

    def login(self):
        self.request("GET", "/api/v1/auth/me", expected=401)
        self.request("POST", "/api/v1/auth/local/login", {
            "username": os.environ["SMOKE_ADMIN_USERNAME"],
            "password": os.environ["SMOKE_ADMIN_PASSWORD"],
        })
        self.request("GET", "/api/v1/auth/me")

    def upload(self, namespace):
        boundary = "smoke-" + uuid.uuid4().hex
        content = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"check.md\"\r\n"
                   "Content-Type: text/markdown\r\n\r\n# Disposable namespace deletion fixture\r\n"
                   f"--{boundary}--\r\n").encode()
        return self.request("POST", f"/api/web/knowledge/bases/{namespace}/check/documents", content,
                            "multipart/form-data; boundary=" + boundary)


def main():
    client = Client(sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080")
    client.login()
    prefix = "delete-check-" + uuid.uuid4().hex[:10]
    fixtures = [prefix, prefix + "-other"]
    documents = {}
    for namespace in fixtures:
        client.request("POST", "/api/web/namespaces", {"slug": namespace, "displayName": "Deletion regression fixture"})
        client.request("POST", "/api/web/knowledge/bases", {"namespace": namespace, "slug": "check", "displayName": "Check"})
        documents[namespace] = client.upload(namespace)

    mine = client.request("GET", "/api/web/me/namespaces")
    assert not next(item for item in mine if item["slug"] == prefix)["canDelete"]
    client.request("DELETE", f"/api/web/namespaces/{prefix}", expected=400)
    print("PASS: active knowledge files hide deletion and reject direct deletion with HTTP 400")

    client.request("DELETE", f"/api/web/knowledge/documents/{documents[prefix]['id']}")
    mine = client.request("GET", "/api/web/me/namespaces")
    assert next(item for item in mine if item["slug"] == prefix)["canDelete"]
    client.request("POST", f"/api/web/namespaces/{prefix}/archive", {})
    client.request("DELETE", f"/api/web/namespaces/{prefix}")
    mine = client.request("GET", "/api/web/me/namespaces")
    assert all(item["slug"] != prefix for item in mine)
    print("PASS: owner can delete the archived namespace after deleting its files")

    detail = client.request("GET", f"/api/web/knowledge/documents/{documents[fixtures[1]]['id']}")
    other = detail["document"]
    assert other["id"] == documents[fixtures[1]]["id"]
    print("PASS: another namespace's knowledge file remains accessible")
    client.request("DELETE", f"/api/web/knowledge/documents/{other['id']}")
    client.request("DELETE", f"/api/web/namespaces/{fixtures[1]}")
    print("PASS: disposable fixtures removed")


if __name__ == "__main__":
    main()
