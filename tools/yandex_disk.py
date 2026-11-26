#!/usr/bin/env python3
"""
Загрузка файлов на Яндекс.Диск через REST API (OAuth).

Документация: https://yandex.ru/dev/disk-api/doc/ru/reference/upload

Токен (один раз):
  1. Создать приложение: https://oauth.yandex.ru/
     Права: cloud_api:disk.write, cloud_api:disk.read (или «Яндекс.Диск REST API»)
  2. Открыть в браузере:
     https://oauth.yandex.ru/authorize?response_type=token&client_id=<CLIENT_ID>
  3. Скопировать access_token в local.properties:
     yandex.disk.oauth=AQAAAA...

Пути папок на Диске (куда писать) — не публичные ссылки:
  yandex.disk.launcher.devPath=/dashing/launcher-dev
  yandex.disk.launcher.releasePath=/dashing/launcher-release
"""

from __future__ import annotations

import json
import mimetypes
import ssl
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import BinaryIO

API_BASE = "https://cloud-api.yandex.net/v1/disk"


class YandexDiskError(RuntimeError):
    pass


def load_local_properties(project_root: Path) -> dict[str, str]:
    props_path = project_root / "local.properties"
    result: dict[str, str] = {}
    if not props_path.is_file():
        return result
    for raw in props_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        # Unescape common gradle local.properties escaping for Windows paths
        result[key.strip()] = value.strip().replace(r"\:", ":").replace(r"\\", "\\")
    return result


def _request(
    method: str,
    url: str,
    *,
    token: str | None = None,
    data: bytes | BinaryIO | None = None,
    content_type: str | None = None,
    content_length: int | None = None,
    timeout: float = 120.0,
) -> tuple[int, bytes]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"OAuth {token}"
    if content_type:
        headers["Content-Type"] = content_type
    if content_length is not None:
        headers["Content-Length"] = str(content_length)
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    context = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=context) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        body = error.read()
        message = body.decode("utf-8", errors="replace")
        raise YandexDiskError(f"HTTP {error.code} {method} {url}: {message}") from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise YandexDiskError(f"Network error {method} {url}: {error}") from error


def ensure_folder(token: str, disk_path: str) -> None:
    """Создаёт папку на Диске (игнорирует уже существующую)."""
    normalized = disk_path if disk_path.startswith("/") else f"/{disk_path}"
    # Create parents from root down.
    parts = [p for p in normalized.split("/") if p]
    current = ""
    for part in parts:
        current = f"{current}/{part}"
        url = (
            f"{API_BASE}/resources?"
            + urllib.parse.urlencode({"path": current})
        )
        try:
            _request("PUT", url, token=token)
        except YandexDiskError as error:
            if "DiskPathPointsToExistentDirectoryError" in str(error) or "HTTP 409" in str(error):
                continue
            raise


def upload_file(token: str, local_file: Path, disk_path: str, *, overwrite: bool = True) -> None:
    """
    Загружает файл на Диск.
    disk_path — полный путь на Диске, например /launcher-dev/version.json
    """
    if not local_file.is_file():
        raise FileNotFoundError(local_file)
    normalized = disk_path if disk_path.startswith("/") else f"/{disk_path}"
    parent = str(Path(normalized).parent).replace("\\", "/")
    if parent not in ("", ".", "/"):
        ensure_folder(token, parent if parent.startswith("/") else f"/{parent}")

    query = urllib.parse.urlencode(
        {
            "path": normalized,
            "overwrite": "true" if overwrite else "false",
        }
    )
    status, body = _request("GET", f"{API_BASE}/resources/upload?{query}", token=token)
    if status != 200:
        raise YandexDiskError(f"upload href failed: HTTP {status}")
    payload = json.loads(body.decode("utf-8"))
    href = payload.get("href")
    method = payload.get("method", "PUT")
    if not href:
        raise YandexDiskError(f"upload href missing: {payload}")

    content_type = mimetypes.guess_type(local_file.name)[0] or "application/octet-stream"
    file_size = local_file.stat().st_size
    with local_file.open("rb") as fh:
        put_status, _ = _request(
            method,
            href,
            data=fh,
            content_type=content_type,
            content_length=file_size,
            timeout=600.0,
        )
    if put_status not in (201, 202):
        raise YandexDiskError(f"upload PUT failed: HTTP {put_status}")


def upload_directory_files(
    token: str,
    local_dir: Path,
    disk_folder: str,
    file_names: list[str],
) -> list[str]:
    """Загружает перечисленные файлы из local_dir в disk_folder. Возвращает загруженные имена."""
    folder = disk_folder.rstrip("/") or "/"
    uploaded: list[str] = []
    for name in file_names:
        local = local_dir / name
        if not local.is_file():
            raise FileNotFoundError(local)
        remote = f"{folder}/{name}"
        print(f"Uploading {local.name} -> disk:{remote} ({local.stat().st_size / (1024 * 1024):.1f} MB)")
        upload_file(token, local, remote, overwrite=True)
        uploaded.append(name)
    return uploaded
