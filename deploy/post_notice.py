#!/usr/bin/env python3
"""릴리스 노트를 메인 공지사항에 올린다 (BRD 메인 공지, D-108).

서버 안에서 앱(127.0.0.1:APP_PORT)에 직접 붙어 관리자 계정으로 로그인하고,
노트 폴더의 그림을 공지 이미지로 올린 뒤 그 주소로 바꿔 공지를 만든다.

사용: python3 post_notice.py <노트 폴더>
  노트 폴더/notice.md 첫 줄 "# v1.00 제목"(버전으로 시작), 나머지는 본문(마크다운). 그림은 ![설명](파일.png)처럼 같은 폴더 파일을 가리킨다.
환경 변수: APP_PORT, NOTICE_ADMIN_EMAIL, NOTICE_ADMIN_PASSWORD
표준 라이브러리만 쓴다(서버에 따로 설치하지 않게).
"""
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.request
import uuid

PORT = os.environ.get("APP_PORT", "8080")
BASE = f"http://127.0.0.1:{PORT}"
cookies = {}  # 운영 쿠키는 Secure라 http로는 cookiejar가 보내지 않아 직접 들고 다닌다


def request(method, path, body=None, content_type=None):
    req = urllib.request.Request(BASE + path, data=body, method=method)
    if content_type:
        req.add_header("Content-Type", content_type)
    if cookies:
        req.add_header("Cookie", "; ".join(f"{k}={v}" for k, v in cookies.items()))
    if "XSRF-TOKEN" in cookies:
        req.add_header("X-XSRF-TOKEN", cookies["XSRF-TOKEN"])
    try:
        res = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as e:
        res = e
    for header in res.headers.get_all("Set-Cookie") or []:
        name, _, rest = header.partition("=")
        cookies[name.strip()] = rest.split(";", 1)[0]
    data = res.read()
    return res.status, data


def json_call(method, path, payload):
    status, data = request(method, path, json.dumps(payload).encode(), "application/json")
    if status >= 300:
        sys.exit(f"{method} {path} 실패: {status} {data[:300].decode(errors='replace')}")
    return json.loads(data) if data else None


def upload(path):
    boundary = uuid.uuid4().hex
    name = os.path.basename(path)
    mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
    with open(path, "rb") as f:
        content = f.read()
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"files\"; filename=\"{name}\"\r\n"
            f"Content-Type: {mime}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    status, data = request("POST", "/api/notices/images", body, f"multipart/form-data; boundary={boundary}")
    if status >= 300:
        sys.exit(f"그림 올리기 실패({name}): {status} {data[:300].decode(errors='replace')}")
    return json.loads(data)["images"][0]["url"]


def main():
    folder = sys.argv[1]
    with open(os.path.join(folder, "notice.md"), encoding="utf-8") as f:
        text = f.read().strip()
    first, _, content = text.partition("\n")
    title = first.lstrip("#").strip()
    if not title:
        sys.exit("notice.md 첫 줄에 '# 제목'이 필요합니다")
    if not re.match(r"^v\d+\.\d{2} ", title):  # 버전 표시로 시작(예: "v1.02 이메일 인증", D-108)
        sys.exit(f"제목은 'v1.00 ' 같은 버전으로 시작해야 합니다: {title}")
    if len(title) > 30:  # 글 제목 최대 30자(FR-003)
        sys.exit(f"제목이 30자를 넘습니다({len(title)}자): {title}")

    request("GET", "/api/auth/session")  # XSRF-TOKEN 쿠키 받기
    json_call("POST", "/api/auth/login", {
        "email": os.environ["NOTICE_ADMIN_EMAIL"],
        "password": os.environ["NOTICE_ADMIN_PASSWORD"],
        "rememberMe": False,
    })

    def replace(match):
        alt, src = match.group(1), match.group(2)
        if re.match(r"^(https?:)?/", src):
            return match.group(0)
        return f"![{alt}]({upload(os.path.join(folder, src))})"

    content = re.sub(r"!\[([^\]]*)\]\(([^)\s]+)\)", replace, content.strip())
    body = {"title": title, "content": content, "categoryId": None, "tags": []}
    id_file = os.path.join(folder, "notice-id")
    if os.path.exists(id_file):
        with open(id_file, encoding="utf-8") as f:
            notice_id = int(f.read().strip())
        json_call("PUT", f"/api/posts/{notice_id}", body)
        print(f"공지 수정: id={notice_id} 제목={title}")
    else:
        notice = json_call("POST", "/api/notices", body)
        print(f"공지 등록: id={notice.get('id')} 제목={title}")
    request("POST", "/api/auth/logout")


if __name__ == "__main__":
    main()
