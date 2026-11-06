#!/usr/bin/env python3
"""
Сборка APK для OTA-обновлений TBox Launcher и подготовка version.json.

Имена APK:
  tbox_launcher-v.0.1.0-ru.apk, tbox_launcher-v.0.1.0-en.apk

Папки по умолчанию:
  <repo>/ota-out/launcher-dev
  <repo>/ota-out/launcher-release

Публичные ссылки Облака Mail.ru (чтение с ГУ) в local.properties:
  launcher.update.devPublicKey=https://cloud.mail.ru/public/yT4V/JC5C1FwA9
  launcher.update.releasePublicKey=https://cloud.mail.ru/public/yT4V/JC5C1FwA9

Автозагрузка на Диск (--upload) — OAuth + пути записи:
  yandex.disk.oauth=AQAAAA...
  yandex.disk.launcher.devPath=/dashing/launcher-dev
  yandex.disk.launcher.releasePath=/dashing/launcher-release

Запуск из корня репозитория:
  python tools/build_ota_launcher.py --channel dev --upload
  python tools/build_ota_launcher.py --channel release --changelog "Первый OTA" --upload

Из WSL удобнее собирать через Windows Gradle (gradlew.bat), либо:
  python3 tools/build_ota_launcher.py --channel dev --skip-build --upload
после сборки в PowerShell / Android Studio.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

GRADLE_FILE = Path("launcher/build.gradle.kts")
FLAVORS = ("ru", "en")


def default_output_base() -> Path:
    return project_root() / "ota-out"

@dataclass(frozen=True)
class ChannelConfig:
    key: str
    label: str
    build_type: str
    output_dir_name: str
    gradle_tasks: tuple[str, ...]
    disk_path_property: str
    default_disk_path: str


CHANNELS: dict[str, ChannelConfig] = {
    "dev": ChannelConfig(
        key="dev",
        label="Разработка (debug APK -> launcher-dev)",
        build_type="debug",
        output_dir_name="launcher-dev",
        gradle_tasks=(":launcher:assembleRuDebug", ":launcher:assembleEnDebug"),
        disk_path_property="yandex.disk.launcher.devPath",
        default_disk_path="/dashing/launcher-dev",
    ),
    "dev_release": ChannelConfig(
        key="dev_release",
        label="Разработка (release APK -> launcher-dev)",
        build_type="release",
        output_dir_name="launcher-dev",
        gradle_tasks=(":launcher:assembleRuRelease", ":launcher:assembleEnRelease"),
        disk_path_property="yandex.disk.launcher.devPath",
        default_disk_path="/dashing/launcher-dev",
    ),
    "release": ChannelConfig(
        key="release",
        label="Релиз (release APK -> launcher-release)",
        build_type="release",
        output_dir_name="launcher-release",
        gradle_tasks=(":launcher:assembleRuRelease", ":launcher:assembleEnRelease"),
        disk_path_property="yandex.disk.launcher.releasePath",
        default_disk_path="/dashing/launcher-release",
    ),
}


@dataclass(frozen=True)
class AppVersion:
    version_code: int
    version_name: str


@dataclass(frozen=True)
class BuiltApk:
    flavor: str
    source_path: Path
    file_name: str
    sha256: str
    size_bytes: int


def project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def gradle_wrapper(project_dir: Path) -> Path:
    bat = project_dir / "gradlew.bat"
    sh = project_dir / "gradlew"
    in_wsl = False
    proc_version = Path("/proc/version")
    if proc_version.is_file():
        in_wsl = "microsoft" in proc_version.read_text(encoding="utf-8", errors="ignore").lower()
    if os.name == "nt" or in_wsl:
        if bat.is_file():
            return bat
    if sh.is_file():
        return sh
    raise FileNotFoundError(f"Gradle wrapper not found in {project_dir}")


def ota_apk_file_name(version_name: str, flavor: str) -> str:
    return f"tbox_launcher-v.{version_name}-{flavor}.apk"


def read_app_version(gradle_path: Path) -> AppVersion:
    text = gradle_path.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        raise ValueError(f"Could not parse versionCode/versionName from {gradle_path}")
    return AppVersion(
        version_code=int(code_match.group(1)),
        version_name=name_match.group(1),
    )


def choose_channel_interactive() -> ChannelConfig:
    print("Выберите канал обновлений лаунчера:")
    print("  1 — Разработка (debug APK -> launcher-dev)")
    print("  2 — Разработка (release APK -> launcher-dev)")
    print("  3 — Релиз (release APK -> launcher-release)")
    while True:
        choice = input("Введите 1, 2 или 3: ").strip()
        if choice == "1":
            return CHANNELS["dev"]
        if choice == "2":
            return CHANNELS["dev_release"]
        if choice == "3":
            return CHANNELS["release"]
        print("Неверный ввод, попробуйте снова.")


def resolve_channel(args: argparse.Namespace) -> ChannelConfig:
    if args.channel:
        key = args.channel.lower().replace("-", "_")
        if key in ("development", "debug"):
            key = "dev"
        if key not in CHANNELS:
            raise ValueError(f"Unknown channel: {args.channel}")
        return CHANNELS[key]
    return choose_channel_interactive()


def run_gradle(project_dir: Path, tasks: tuple[str, ...]) -> None:
    wrapper = gradle_wrapper(project_dir)
    in_wsl = Path("/proc/version").is_file() and "microsoft" in Path("/proc/version").read_text(
        encoding="utf-8", errors="ignore"
    ).lower()
    if wrapper.suffix.lower() == ".bat" and in_wsl:
        win_dir = subprocess.check_output(
            ["wslpath", "-w", str(project_dir)],
            text=True,
        ).strip()
        win_cmd = f'cd /d {win_dir} && gradlew.bat {" ".join(tasks)}'
        print(f"Running (WSL->cmd): cmd.exe /c {win_cmd}")
        subprocess.run(["cmd.exe", "/c", win_cmd], check=True)
        return
    command = [str(wrapper), *tasks]
    print(f"Running: {' '.join(command)}")
    subprocess.run(command, cwd=project_dir, check=True)


def expected_apk_path(project_dir: Path, flavor: str, build_type: str) -> Path:
    return (
        project_dir
        / "launcher/build/outputs/apk"
        / flavor
        / build_type
        / f"launcher-{flavor}-{build_type}.apk"
    )


def find_apk(project_dir: Path, flavor: str, build_type: str) -> Path:
    expected = expected_apk_path(project_dir, flavor, build_type)
    if expected.is_file():
        return expected

    search_dir = project_dir / "launcher/build/outputs/apk" / flavor / build_type
    candidates = sorted(search_dir.glob("*.apk")) if search_dir.is_dir() else []
    if len(candidates) == 1:
        return candidates[0]
    if not candidates:
        raise FileNotFoundError(f"APK not found for flavor={flavor}, buildType={build_type}")
    raise FileNotFoundError(
        f"Multiple APK files found in {search_dir}; expected {expected.name}"
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as apk_file:
        for chunk in iter(lambda: apk_file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_apk_metadata(
    project_dir: Path,
    flavor: str,
    build_type: str,
    version_name: str,
) -> BuiltApk:
    source = find_apk(project_dir, flavor, build_type)
    file_name = ota_apk_file_name(version_name, flavor)
    return BuiltApk(
        flavor=flavor,
        source_path=source,
        file_name=file_name,
        sha256=sha256_file(source),
        size_bytes=source.stat().st_size,
    )


def copy_apk(source: Path, destination_dir: Path, destination_name: str) -> Path:
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / destination_name
    shutil.copy2(source, destination)
    return destination


def prompt_changelog(default: str = "") -> str:
    text = input("Changelog (Enter — пропустить): ").strip()
    return text or default


def build_version_json(
    version: AppVersion,
    apks: list[BuiltApk],
    changelog: str,
    min_supported_version_code: int,
) -> dict:
    published_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    releases = []
    for apk in apks:
        releases.append(
            {
                "versionCode": version.version_code,
                "versionName": version.version_name,
                "flavor": apk.flavor,
                "apkFileName": apk.file_name,
                "sha256": apk.sha256,
                "apkSizeBytes": apk.size_bytes,
                "minSupportedVersionCode": min_supported_version_code,
                "changelog": changelog,
                "publishedAt": published_at,
            }
        )
    return {
        "schemaVersion": 1,
        "releases": releases,
    }


def write_version_json(destination_dir: Path, manifest: dict) -> Path:
    destination = destination_dir / "version.json"
    destination.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return destination


def resolve_disk_folder(channel: ChannelConfig, props: dict[str, str]) -> str:
    path = props.get(channel.disk_path_property, "").strip() or channel.default_disk_path
    if not path.startswith("/"):
        path = f"/{path}"
    return path.rstrip("/") or "/"


def upload_ota_artifacts(
    root: Path,
    channel: ChannelConfig,
    output_dir: Path,
    apks: list[BuiltApk],
) -> None:
    from yandex_disk import YandexDiskError, load_local_properties, upload_directory_files

    props = load_local_properties(root)
    token = props.get("yandex.disk.oauth", "").strip()
    if not token:
        raise YandexDiskError(
            "Не задан yandex.disk.oauth в local.properties. "
            "См. комментарий в tools/yandex_disk.py"
        )
    disk_folder = resolve_disk_folder(channel, props)
    names = [apk.file_name for apk in apks] + ["version.json"]
    upload_directory_files(token, output_dir, disk_folder, names)
    print(f"Uploaded to Yandex Disk folder: {disk_folder}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build TBox Launcher OTA APKs and generate version.json",
    )
    parser.add_argument(
        "--channel",
        choices=["dev", "development", "debug", "dev-release", "dev_release", "release"],
        help=(
            "Канал: dev (debug -> launcher-dev), "
            "dev-release (release -> launcher-dev), "
            "release (release -> launcher-release)."
        ),
    )
    parser.add_argument(
        "--output-base",
        type=Path,
        default=None,
        help="Базовая папка (по умолчанию: <repo>/ota-out)",
    )
    parser.add_argument("--changelog", default=None, help="Текст изменений для version.json")
    parser.add_argument(
        "--min-supported-version-code",
        type=int,
        default=0,
        help="minSupportedVersionCode в version.json",
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Не запускать Gradle, использовать уже собранные APK",
    )
    parser.add_argument(
        "--upload",
        action="store_true",
        help="Загрузить version.json и APK на Яндекс.Диск (нужен yandex.disk.oauth)",
    )
    return parser.parse_args()


def main() -> int:
    tools_dir = Path(__file__).resolve().parent
    if str(tools_dir) not in sys.path:
        sys.path.insert(0, str(tools_dir))

    # Late import after sys.path tweak for sibling module
    from yandex_disk import YandexDiskError  # noqa: WPS433

    args = parse_args()
    root = project_root()
    gradle_file = root / GRADLE_FILE
    if not gradle_file.is_file():
        print(f"Error: {gradle_file} not found. Run from the TBox repository.", file=sys.stderr)
        return 1

    try:
        channel = resolve_channel(args)
        version = read_app_version(gradle_file)
        output_base = args.output_base or default_output_base()
        output_dir = output_base / channel.output_dir_name

        changelog = args.changelog
        if changelog is None and sys.stdin.isatty():
            changelog = prompt_changelog()
        changelog = changelog or ""

        print()
        print(f"Канал: {channel.label}")
        print(f"Версия: {version.version_name} ({version.version_code})")
        print(f"Папка назначения: {output_dir}")
        print()

        if not args.skip_build:
            run_gradle(root, channel.gradle_tasks)
        else:
            print("Пропуск сборки (--skip-build)")

        apks: list[BuiltApk] = []
        for flavor in FLAVORS:
            metadata = build_apk_metadata(root, flavor, channel.build_type, version.version_name)
            copied = copy_apk(metadata.source_path, output_dir, metadata.file_name)
            print(f"Copied {metadata.source_path.name} -> {copied}")
            apks.append(metadata)

        manifest = build_version_json(
            version=version,
            apks=apks,
            changelog=changelog,
            min_supported_version_code=args.min_supported_version_code,
        )
        version_json_path = write_version_json(output_dir, manifest)

        print()
        print("Локально готово.")
        print(f"version.json -> {version_json_path}")
        for apk in apks:
            size_mb = apk.size_bytes / (1024 * 1024)
            print(f"  {apk.file_name}: {size_mb:.1f} MB, sha256={apk.sha256}")

        if args.upload:
            print()
            upload_ota_artifacts(root, channel, output_dir, apks)
            print("Загрузка на Яндекс.Диск завершена.")
        return 0
    except subprocess.CalledProcessError as error:
        print(f"Gradle build failed with exit code {error.returncode}", file=sys.stderr)
        return error.returncode or 1
    except (FileNotFoundError, ValueError, OSError, YandexDiskError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
