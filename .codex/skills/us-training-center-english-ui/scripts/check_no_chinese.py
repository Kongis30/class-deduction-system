#!/usr/bin/env python3

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


TEXT_EXTENSIONS = {
    ".css",
    ".csv",
    ".html",
    ".js",
    ".json",
    ".jsx",
    ".less",
    ".md",
    ".mdx",
    ".sass",
    ".scss",
    ".svelte",
    ".svg",
    ".ts",
    ".tsx",
    ".txt",
    ".vue",
    ".yaml",
    ".yml",
}

DEFAULT_FRONTEND_PATHS = (
    "src",
    "app",
    "pages",
    "components",
    "locales",
    "content",
    "public",
    "emails",
)

IGNORE_DIRS = {
    ".git",
    ".next",
    ".nuxt",
    ".output",
    ".parcel-cache",
    ".svelte-kit",
    "build",
    "coverage",
    "dist",
    "node_modules",
    "tmp",
}

CHINESE_OR_FULLWIDTH_RE = re.compile(
    r"[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF，。！？；：（）【】《》、“”‘’￥]"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scan user-facing project files for Chinese characters or Chinese punctuation."
    )
    parser.add_argument(
        "paths",
        nargs="*",
        help="Files or directories to scan. Defaults to common frontend folders when present.",
    )
    return parser.parse_args()


def resolve_targets(raw_paths: list[str]) -> list[Path]:
    if raw_paths:
        return [Path(item).resolve() for item in raw_paths]

    existing_defaults = [Path(name).resolve() for name in DEFAULT_FRONTEND_PATHS if Path(name).exists()]
    if existing_defaults:
        return existing_defaults

    return [Path(".").resolve()]


def should_scan_file(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() in TEXT_EXTENSIONS


def iter_files(target: Path) -> list[Path]:
    if should_scan_file(target):
        return [target]

    if not target.exists() or not target.is_dir():
        return []

    files: list[Path] = []
    for path in target.rglob("*"):
        if any(part in IGNORE_DIRS for part in path.parts):
            continue
        if should_scan_file(path):
            files.append(path)
    return files


def find_matches(path: Path) -> list[tuple[int, str]]:
    matches: list[tuple[int, str]] = []
    try:
        content = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return matches

    for line_number, line in enumerate(content.splitlines(), start=1):
        if CHINESE_OR_FULLWIDTH_RE.search(line):
            matches.append((line_number, line.strip()))
    return matches


def main() -> int:
    args = parse_args()
    targets = resolve_targets(args.paths)

    files_to_scan: list[Path] = []
    seen: set[Path] = set()
    for target in targets:
        for file_path in iter_files(target):
            if file_path not in seen:
                files_to_scan.append(file_path)
                seen.add(file_path)

    findings = 0
    matched_files = 0

    for file_path in sorted(files_to_scan):
        matches = find_matches(file_path)
        if not matches:
            continue

        matched_files += 1
        relative_path = file_path.relative_to(Path.cwd())
        for line_number, line in matches:
            findings += 1
            print(f"{relative_path}:{line_number}: {line}")

    if findings:
        print(
            f"\nFound {findings} line(s) with Chinese characters or punctuation in {matched_files} file(s).",
            file=sys.stderr,
        )
        return 1

    print("No Chinese characters or Chinese punctuation found in scanned files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
