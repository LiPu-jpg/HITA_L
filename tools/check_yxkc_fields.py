#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import json
import sys
from typing import Any, Dict, List, Optional

import requests

BASE_URL = "https://mjw.hitsz.edu.cn/incoSpringBoot"
BASIC_AUTH = "Basic aW5jb246MTIzNDU="


def form_post(session: requests.Session, path: str, rolecode: str, data: Optional[Dict[str, str]] = None):
    headers = {
        "authorization": BASIC_AUTH,
        "rolecode": rolecode,
        "_lang": "cn",
        "Accept": "*/*",
        "User-Agent": "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/144.0 Mobile Safari/537.36 uni-app",
    }
    return session.post(f"{BASE_URL}{path}", headers=headers, data=data or {})


def json_post(session: requests.Session, path: str, token: str, rolecode: str, body: Dict[str, Any]):
    headers = {
        "authorization": f"bearer {token}",
        "rolecode": rolecode,
        "_lang": "cn",
        "Accept": "*/*",
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/144.0 Mobile Safari/537.36 uni-app",
    }
    return session.post(f"{BASE_URL}{path}", headers=headers, data=json.dumps(body, ensure_ascii=False))


def extract_yxkc_list(payload: Dict[str, Any]) -> Optional[List[Dict[str, Any]]]:
    keys = ["yxkcList", "content", "data", "rows", "list"]
    candidates: List[List[Dict[str, Any]]] = []

    def is_course_item(obj: Dict[str, Any]) -> bool:
        for k in ("kcmc", "kcdm", "kclbmc", "kcxzmc", "xklbmc", "dgjsmc", "DGJSMC"):
            if k in obj:
                return True
        return False

    for key in keys:
        arr = payload.get(key)
        if isinstance(arr, list) and arr:
            if isinstance(arr[0], dict) and is_course_item(arr[0]):
                return arr
            candidates.append(arr)
    data_obj = payload.get("data")
    if isinstance(data_obj, dict):
        for key in keys:
            arr = data_obj.get(key)
            if isinstance(arr, list) and arr:
                if isinstance(arr[0], dict) and is_course_item(arr[0]):
                    return arr
                candidates.append(arr)
    content_obj = payload.get("content")
    if isinstance(content_obj, dict):
        for key, arr in content_obj.items():
            if not isinstance(arr, list) or not arr:
                continue
            if isinstance(arr[0], dict) and is_course_item(arr[0]):
                return arr
            candidates.append(arr)

    # fallback: pick the first candidate list
    for arr in candidates:
        if isinstance(arr, list) and arr:
            return arr
    return None


def print_array_summary(payload: Dict[str, Any]) -> None:
    def dump_array(path: str, arr: Any):
        if not isinstance(arr, list) or not arr:
            return
        sample = arr[0]
        if isinstance(sample, dict):
            keys = ", ".join(sorted(sample.keys()))
            print(f"[{path}] len={len(arr)} keys={keys}")
        else:
            print(f"[{path}] len={len(arr)} sample_type={type(sample).__name__}")

    for key, val in payload.items():
        if isinstance(val, list):
            dump_array(key, val)
        elif isinstance(val, dict):
            for sub_key, sub_val in val.items():
                if isinstance(sub_val, list):
                    dump_array(f"{key}.{sub_key}", sub_val)


def main() -> int:
    parser = argparse.ArgumentParser(description="Check fields in /app/Xsxk/queryYxkc response")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--xn", help="学年，例如 2025-2026 或 2025")
    parser.add_argument("--xq", help="学期，例如 1/2")
    parser.add_argument("--dump", help="保存最后一次响应 JSON 到文件")
    args = parser.parse_args()

    session = requests.Session()

    form_post(session, "/component/queryApplicationSetting/rsa", rolecode="01")
    form_post(session, "/c_raskey", rolecode="06")
    ldap_resp = form_post(
        session,
        "/authentication/ldap",
        rolecode="06",
        data={"username": args.username, "password": args.password},
    )
    try:
        payload = ldap_resp.json()
    except Exception:
        print("login failed: invalid json response")
        return 1
    token = payload.get("access_token")
    if not token:
        print(f"login failed: {payload.get('msg')}")
        return 1

    pylx_raw = ""
    data_obj = payload.get("data") or {}
    if isinstance(data_obj, dict):
        pylx_raw = str(data_obj.get("pylx") or "")
    pylx_candidates = [pylx_raw, pylx_raw.zfill(2)] if pylx_raw else ["1", "01", "2", "02"]

    xn = args.xn or ""
    xq = args.xq or ""
    if not xn or not xq:
        # try to read from term list
        term_resp = json_post(session, "/app/commapp/queryxnxqlist", token, "06", {})
        try:
            term_obj = term_resp.json()
        except Exception:
            term_obj = {}
        content = term_obj.get("content") or []
        if content:
            term = content[0]
            xn = str(term.get("XN") or xn)
            xq = str(term.get("XQ") or xq)

    if not xn or not xq:
        print("missing --xn/--xq and cannot infer term list")
        return 1

    xq_pad = xq.zfill(2)
    xnxq_candidates = [
        f"{xn}{xq}",
        f"{xn}-{xq}",
        f"{xn}{xq_pad}",
        f"{xn}-{xq_pad}",
    ]
    role_candidates = ["01", "06"]
    xkfs_candidates = ["yixuan", ""]

    found = None
    last_json = None
    for rolecode in role_candidates:
        for pylx in pylx_candidates:
            for xnxq in xnxq_candidates:
                for xkfs in xkfs_candidates:
                    body = {
                        "RoleCode": rolecode,
                        "p_pylx": pylx,
                        "p_xn": xn,
                        "p_xq": xq,
                        "p_xnxq": xnxq,
                        "p_gjz": "",
                        "p_kc_gjz": "",
                        "p_xkfsdm": xkfs,
                    }
                    resp = json_post(session, "/app/Xsxk/queryYxkc?_lang=zh_CN", token, rolecode, body)
                    try:
                        jo = resp.json()
                    except Exception:
                        continue
                    last_json = jo
                    arr = extract_yxkc_list(jo)
                    if arr:
                        found = arr
                        break
                if found:
                    break
            if found:
                break
        if found:
            break

    if args.dump and last_json is not None:
        with open(args.dump, "w", encoding="utf-8") as f:
            json.dump(last_json, f, ensure_ascii=False, indent=2)

    if not found:
        print("no yxkc list found")
        if last_json is not None:
            print_array_summary(last_json)
        return 1

    sample = found[0]
    if not isinstance(sample, dict):
        print("unexpected yxkc format")
        return 1

    print("keys:", ", ".join(sorted(sample.keys())))
    for key in ["xklbmc", "kclbmc", "kcxzmc", "rwlxmc", "kcmc", "kcdm"]:
        if key in sample:
            print(f"{key}: {sample.get(key)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
