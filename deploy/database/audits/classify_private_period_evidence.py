#!/usr/bin/env python3
"""汇总两份只读快照证据；分类只用于复核，不能自动生成生产回填。"""

import argparse
import csv
import sys


def rows(path):
    with open(path, encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", help="规则证据 TSV")
    parser.add_argument("comparison", help="跨快照对照 TSV")
    args = parser.parse_args()
    evidence = {row["rule_id"]: row for row in rows(args.evidence)}
    comparisons = rows(args.comparison)
    if len({row["rule_id"] for row in comparisons}) != len(comparisons):
        raise ValueError("快照比较中存在重复规则，停止分类")
    fields = ["rule_id", "student_name", "package_no", "evidence_category",
              "rule_created_at", "previous_rule_id", "deleted_slot_count",
              "confirmed_record_count", "oct19_comparison", "oct08_comparison",
              "disposition"]
    writer = csv.DictWriter(sys.stdout, fieldnames=fields, delimiter="\t", lineterminator="\n")
    writer.writeheader()
    for comparison in comparisons:
        rule_id = comparison["rule_id"]
        rule = evidence.get(rule_id)
        if not rule or rule["allocation_period_started_at"] != "NULL":
            raise ValueError(f"规则 {rule_id} 的证据缺失或与比较快照不一致")
        linked = rule["supersedes_rule_id"] != "NULL"
        rebuilt = int(rule["deleted_slot_count"]) > 0
        category = ("versioned_rebuilt" if rebuilt else "versioned") if linked else (
            "legacy_rebuilt" if rebuilt else "legacy_no_rebuild_evidence")

        def compare(prefix):
            if comparison[f"{prefix}_rule_id"] == "NULL":
                return "not_in_snapshot"
            unchanged = (comparison[f"{prefix}_start_date"] == comparison["start_date"]
                         and comparison[f"{prefix}_times"] == comparison["current_times"])
            return "same_observed_schedule" if unchanged else "different_observed_schedule"

        writer.writerow({
            "rule_id": rule_id, "student_name": rule["student_name"],
            "package_no": rule["package_no"], "evidence_category": category,
            "rule_created_at": rule["rule_created_at"],
            "previous_rule_id": rule["supersedes_rule_id"],
            "deleted_slot_count": rule["deleted_slot_count"],
            "confirmed_record_count": rule["confirmed_record_count"],
            "oct19_comparison": compare("oct19"), "oct08_comparison": compare("oct08"),
            "disposition": "requires_individual_review",
        })


if __name__ == "__main__":
    main()
