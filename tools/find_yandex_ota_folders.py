#!/usr/bin/env python3
"""Find real Disk paths for launcher OTA public folders."""

from __future__ import annotations

import json
import sys
import urllib.parse
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from yandex_disk import API_BASE, YandexDiskError, _request, load_local_properties  # noqa: E402


def get_json(token: str | None, url: str) -> dict:
    _, body = _request("GET", url, token=token)
    return json.loads(body.decode("utf-8"))


def resolve_public(token: str, public_key: str) -> dict:
    query = urllib.parse.urlencode({"public_key": public_key})
    # Try authenticated first (populates owner fields when available), then unauthenticated fallback.
    url = f"{API_BASE}/public/resources?{query}"
    try:
        return get_json(token, url)
    except YandexDiskError:
        return get_json(None, url)


def list_dir(token: str, path: str = "/") -> list[dict]:
    items: list[dict] = []
    limit = 200
    offset = 0
    while True:
        query = urllib.parse.urlencode(
            {
                "path": path,
                "limit": limit,
                "offset": offset,
                "fields": "_embedded.items.name,_embedded.items.path,"
                "_embedded.items.type,_embedded.items.public_key,_embedded.items.public_url,"
                "_embedded.total",
            }
        )
        meta = get_json(token, f"{API_BASE}/resources?{query}")
        embedded = meta.get("_embedded", {})
        batch = embedded.get("items", [])
        if not batch:
            break
        items.extend(batch)
        offset += len(batch)
        total = embedded.get("total")
        if len(batch) < limit or (isinstance(total, int) and offset >= total):
            break
    return items


def walk_find(token: str, needle_names: set[str], max_depth: int = 4) -> dict[str, dict]:
    found: dict[str, dict] = {}
    queue: deque[tuple[str, int]] = deque([("/", 0)])
    while queue and len(found) < len(needle_names):
        path, depth = queue.popleft()
        try:
            items = list_dir(token, path)
        except YandexDiskError as error:
            print(f"list_fail {path}: {error}")
            continue
        for item in items:
            name = item.get("name") or ""
            item_path = item.get("path") or ""
            # API returns path like "disk:/foo/bar"
            if name in needle_names and name not in found:
                found[name] = item
                print(f"FOUND name={name} path={item_path} public_url={item.get('public_url')}")
            if item.get("type") == "dir" and depth < max_depth:
                # convert disk:/x to /x for next listing
                next_path = item_path
                if next_path.startswith("disk:"):
                    next_path = next_path[len("disk:") :]
                if not next_path.startswith("/"):
                    next_path = "/" + next_path
                queue.append((next_path, depth + 1))
    return found


def normalize_disk_path(path: str) -> str:
    if path.startswith("disk:"):
        path = path[len("disk:") :]
    if not path.startswith("/"):
        path = "/" + path
    return path.rstrip("/") or "/"


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    props = load_local_properties(root)
    token = props.get("yandex.disk.oauth", "").strip()
    if not token:
        print("no token")
        return 1

    pub_dev = props.get("launcher.update.devPublicKey", "").strip()
    pub_rel = props.get("launcher.update.releasePublicKey", "").strip()

    print("=== resolve by public_key ===")
    for label, pub in (("dev", pub_dev), ("release", pub_rel)):
        if not pub:
            continue
        try:
            meta = resolve_public(token, pub)
            print(
                f"{label}: name={meta.get('name')} path={meta.get('path')} "
                f"type={meta.get('type')} public_url={meta.get('public_url')}"
            )
        except YandexDiskError as error:
            print(f"{label} public_resolve_FAIL:", str(error)[:300])

    print("=== walk disk for launcher-dev / launcher-release ===")
    found = walk_find(token, {"launcher-dev", "launcher-release"})
    if not found:
        print("Nothing found by name. Listing disk root:")
        for item in list_dir(token, "/"):
            print(
                " ",
                item.get("type"),
                item.get("name"),
                item.get("path"),
                item.get("public_url") or "",
            )

    print("=== suggested local.properties ===")
    for name, key in (
        ("launcher-dev", "yandex.disk.launcher.devPath"),
        ("launcher-release", "yandex.disk.launcher.releasePath"),
    ):
        item = found.get(name)
        if item and item.get("path"):
            print(f"{key}={normalize_disk_path(item['path'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
