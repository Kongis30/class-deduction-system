#!/usr/bin/env python3
"""从 V1 只读快照生成仅含共有业务列的本地 V2 数据导入文件。"""

import argparse
import gzip
import json
import os
import re
from collections import Counter
from pathlib import Path


TARGET_DATABASE = "class_deduction_system_v2"
SOURCE_ONLY_TABLES = {"course_price_option_backup_20260905"}
OBSOLETE_COLUMNS = {"course_product": {"default_total_units", "default_lesson_units"}}
INSERT_PATTERN = re.compile(r"^INSERT INTO `([^`]+)` \((.*?)\) VALUES (.*);\n?$", re.DOTALL)


def load_columns(path: Path) -> dict[str, dict[str, list[str]]]:
    """读取 information_schema 列清单；导入前据此拒绝未知的 schema 差异。"""
    columns: dict[str, dict[str, list[str]]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split("\t")
        if len(fields) != 7:
            raise ValueError(f"Invalid column metadata in {path.name}")
        columns.setdefault(fields[0], {})[fields[1]] = fields
    return columns


def split_sql_values(value: str) -> list[str]:
    """按顶层逗号切分 MySQL 值，保留字符串和转义的原始 SQL 文本。"""
    parts: list[str] = []
    start = 0
    depth = 0
    in_string = False
    escaped = False
    for index, char in enumerate(value):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "'":
                in_string = False
        elif char == "'":
            in_string = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth < 0:
                raise ValueError("Unbalanced SQL row")
        elif char == "," and depth == 0:
            parts.append(value[start:index])
            start = index + 1
    if in_string or depth != 0:
        raise ValueError("Unterminated SQL value")
    parts.append(value[start:])
    return parts


def prepare(source: Path, source_metadata: Path, target_metadata: Path, output: Path, report: Path) -> None:
    """只写本地文件；源库快照不变，目标数据库也由后续独立步骤操作。"""
    source_columns = load_columns(source_metadata)
    target_columns = load_columns(target_metadata)
    source_tables = set(source_columns)
    target_tables = set(target_columns)
    if source_tables - target_tables != SOURCE_ONLY_TABLES:
        raise ValueError(f"Unexpected source-only tables: {sorted(source_tables - target_tables)}")
    for table in source_tables & target_tables:
        missing = set(source_columns[table]) - set(target_columns[table])
        if missing != OBSOLETE_COLUMNS.get(table, set()):
            raise ValueError(f"Unexpected missing V2 columns in {table}: {sorted(missing)}")
        for column, metadata in target_columns[table].items():
            if column not in source_columns[table] and metadata[3] == "NO" \
                    and metadata[4] == "<NULL>" and "auto_increment" not in metadata[5]:
                raise ValueError(f"V2 column requires a value: {table}.{column}")

    counts: Counter[str] = Counter({table: 0 for table in source_tables & target_tables})
    output.parent.mkdir(parents=True, exist_ok=True)
    output_fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with gzip.open(source, "rt", encoding="utf-8") as source_file, os.fdopen(output_fd, "w", encoding="utf-8") as target_file:
        target_file.write(f"USE `{TARGET_DATABASE}`;\n")
        target_file.write("SET NAMES utf8mb4;\nSET TIME_ZONE='+00:00';\n")
        target_file.write("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\nSET FOREIGN_KEY_CHECKS=0;\n")
        for line in source_file:
            if not line.startswith("INSERT INTO `"):
                continue
            match = INSERT_PATTERN.fullmatch(line)
            if not match:
                raise ValueError("Unexpected INSERT format in V1 snapshot")
            table, columns_text, values_text = match.groups()
            if table in SOURCE_ONLY_TABLES:
                continue
            if table not in target_columns:
                raise ValueError(f"Unknown destination table: {table}")
            columns = re.findall(r"`([^`]+)`", columns_text)
            expected_columns = {column for column, metadata in source_columns[table].items()
                                if not metadata[5].upper().startswith(("STORED GENERATED", "VIRTUAL GENERATED"))}
            if set(columns) != expected_columns:
                raise ValueError(f"Snapshot columns do not match source metadata: {table}")
            rows = split_sql_values(values_text)
            counts[table] += len(rows)
            removed = OBSOLETE_COLUMNS.get(table, set())
            if not removed:
                target_file.write(line)
                continue
            retained_indexes = [index for index, column in enumerate(columns) if column not in removed]
            retained_columns = ", ".join(f"`{columns[index]}`" for index in retained_indexes)
            transformed_rows = []
            for row in rows:
                if not row.startswith("(") or not row.endswith(")"):
                    raise ValueError(f"Unexpected row shape: {table}")
                fields = split_sql_values(row[1:-1])
                if len(fields) != len(columns):
                    raise ValueError(f"Column/value count mismatch: {table}")
                transformed_rows.append("(" + ",".join(fields[index] for index in retained_indexes) + ")")
            target_file.write(f"INSERT INTO `{table}` ({retained_columns}) VALUES {','.join(transformed_rows)};\n")
        target_file.write("SET FOREIGN_KEY_CHECKS=1;\n")
    output.chmod(0o600)
    report_fd = os.open(report, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(report_fd, "w", encoding="utf-8") as report_file:
        report_file.write(json.dumps(dict(sorted(counts.items())), indent=2) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-columns", type=Path, required=True)
    parser.add_argument("--target-columns", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.source, args.source_columns, args.target_columns, args.output, args.report)
