#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SPECS_ROOT = REPO_ROOT / ".specs"
REPO_ROOT_KEY = hashlib.sha256(str(REPO_ROOT).encode("utf-8")).hexdigest()[:16]
STATE_ROOT = Path(tempfile.gettempdir()) / "codex-spec-self-eval-gate" / REPO_ROOT_KEY
BASELINE_ROOT = STATE_ROOT / "turn-baselines"
NESTED_SQLITE_HOME = STATE_ROOT / "nested-codex-sqlite"
REPORT_GLOB = "eval-report-*.md"
FAIL_STATUS_RE = re.compile(r"^\s*(?:-|\d+\.)\s+(?:`FAIL`|\[FAIL\])\s+(.*\S)\s*$")
VERDICT_RE = re.compile(r"^-\s+Overall verdict:\s+`?(PASS|WEAK|FAIL)`?\s*$", re.MULTILINE)
REPORT_PATH_RE = re.compile(r"^Report:\s*(.+?)\s*$", re.MULTILINE)


def main() -> int:
    payload = json.load(sys.stdin)
    event_name = payload.get("hook_event_name")

    if event_name == "UserPromptSubmit":
        save_turn_baseline(payload)
        return 0

    if event_name == "Stop":
        decision = evaluate_touched_specs(payload)
        if decision is None:
            return 0
        print(json.dumps(decision))
        return 0

    return 0


def save_turn_baseline(payload: dict) -> None:
    turn_id = payload.get("turn_id")
    if not turn_id:
        return

    BASELINE_ROOT.mkdir(parents=True, exist_ok=True)
    baseline_path = turn_baseline_path(turn_id)
    snapshot = collect_specs_snapshot()
    baseline_path.write_text(
        json.dumps(
            {
                "turn_id": turn_id,
                "cwd": payload.get("cwd"),
                "files": snapshot,
            },
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )


def evaluate_touched_specs(payload: dict) -> dict | None:
    turn_id = payload.get("turn_id")
    if not turn_id:
        return None

    baseline_path = turn_baseline_path(turn_id)
    if not baseline_path.exists():
        return None

    baseline_data = json.loads(baseline_path.read_text(encoding="utf-8"))
    baseline_snapshot = baseline_data.get("files", {})
    current_snapshot = collect_specs_snapshot()
    touched_features = detect_touched_features(baseline_snapshot, current_snapshot)

    if not touched_features:
        baseline_path.unlink(missing_ok=True)
        return None

    failures: list[tuple[str, list[str]]] = []
    for feature_name in touched_features:
        feature_failures = run_spec_self_eval(feature_name)
        if feature_failures:
            failures.append((feature_name, feature_failures))

    if failures:
        return {
            "decision": "block",
            "reason": build_block_reason(failures),
        }

    baseline_path.unlink(missing_ok=True)
    return None


def collect_specs_snapshot() -> dict[str, dict[str, int | str]]:
    snapshot: dict[str, dict[str, int | str]] = {}
    if not SPECS_ROOT.exists():
        return snapshot

    for path in sorted(p for p in SPECS_ROOT.rglob("*") if p.is_file()):
        stat = path.stat()
        rel_path = path.relative_to(REPO_ROOT).as_posix()
        snapshot[rel_path] = {
            "mtime_ns": stat.st_mtime_ns,
            "size": stat.st_size,
            "sha256": sha256sum(path),
        }

    return snapshot


def detect_touched_features(
    baseline_snapshot: dict[str, dict[str, int | str]],
    current_snapshot: dict[str, dict[str, int | str]],
) -> list[str]:
    touched_features: set[str] = set()
    touched_paths = set(baseline_snapshot) | set(current_snapshot)

    for rel_path in touched_paths:
        if baseline_snapshot.get(rel_path) == current_snapshot.get(rel_path):
            continue

        path_parts = Path(rel_path).parts
        if len(path_parts) < 3 or path_parts[0] != ".specs":
            continue

        touched_features.add(path_parts[1])

    return sorted(touched_features)


def run_spec_self_eval(feature_name: str) -> list[str]:
    feature_dir = SPECS_ROOT / feature_name
    if not feature_dir.is_dir():
        return [f"Feature folder `.specs/{feature_name}/` no longer exists."]

    prompt = build_eval_prompt(feature_name)
    temp_output_path = Path(tempfile.mkstemp(prefix="spec-self-eval-", suffix=".txt")[1])
    NESTED_SQLITE_HOME.mkdir(parents=True, exist_ok=True)

    try:
        result = subprocess.run(
            [
                "codex",
                "-a",
                "never",
                "exec",
                "-C",
                str(REPO_ROOT),
                "--ephemeral",
                "-c",
                f"sqlite_home={json.dumps(str(NESTED_SQLITE_HOME))}",
                "-s",
                "workspace-write",
                "--disable",
                "codex_hooks",
                "--output-last-message",
                str(temp_output_path),
                prompt,
            ],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            stdin=subprocess.DEVNULL,
            timeout=600,
        )
    except subprocess.TimeoutExpired:
        temp_output_path.unlink(missing_ok=True)
        return [f"Running `spec-self-eval` timed out for `.specs/{feature_name}/`."]

    final_message = temp_output_path.read_text(encoding="utf-8").strip() if temp_output_path.exists() else ""
    temp_output_path.unlink(missing_ok=True)

    if result.returncode != 0:
        details = compact_error_details(result.stdout, result.stderr, final_message)
        return [f"Could not run `spec-self-eval` for `.specs/{feature_name}/`: {details}"]

    report_path = resolve_report_path(feature_dir, final_message)
    if report_path is None:
        return [f"`spec-self-eval` did not produce a readable report for `.specs/{feature_name}/`."]

    report_text = report_path.read_text(encoding="utf-8")
    fail_items = extract_fail_items(report_text)
    if fail_items:
        return fail_items

    verdict = extract_overall_verdict(report_text)
    if verdict == "FAIL":
        return [f"The report at `{report_path.relative_to(REPO_ROOT).as_posix()}` has overall verdict `FAIL`."]

    return []


def build_eval_prompt(feature_name: str) -> str:
    return (
        f"Use the `spec-self-eval` skill to evaluate the spec in `.specs/{feature_name}/`.\n"
        "Read only that feature folder, save or overwrite the eval report there, and do not edit any other files.\n"
        "After saving the report, reply with exactly these three lines and replace the placeholders with real values:\n"
        f"Feature: .specs/{feature_name}/\n"
        "Verdict: PASS|WEAK|FAIL\n"
        "Report: <path-to-report>\n"
    )


def resolve_report_path(feature_dir: Path, final_message: str) -> Path | None:
    message_path_match = REPORT_PATH_RE.search(final_message)
    if message_path_match:
        raw_path = message_path_match.group(1).strip().strip("`")
        candidate = Path(raw_path)
        if not candidate.is_absolute():
            candidate = REPO_ROOT / raw_path
        if candidate.exists():
            return candidate

    report_candidates = list(feature_dir.glob(REPORT_GLOB))
    if not report_candidates:
        return None

    return max(report_candidates, key=lambda path: path.stat().st_mtime_ns)


def extract_fail_items(report_text: str) -> list[str]:
    failures: list[str] = []
    for line in report_text.splitlines():
        match = FAIL_STATUS_RE.match(line)
        if not match:
            continue

        item = match.group(1).strip()
        if item.startswith(":"):
            continue
        failures.append(item)

    return dedupe_preserving_order(failures)


def extract_overall_verdict(report_text: str) -> str | None:
    match = VERDICT_RE.search(report_text)
    if not match:
        return None
    return match.group(1)


def build_block_reason(failures: list[tuple[str, list[str]]]) -> str:
    lines = [
        "Stop hook blocked turn close because `spec-self-eval` found `FAIL` items in touched specs.",
        "Fix these spec issues first:",
    ]

    for feature_name, items in failures:
        lines.append(f"- `.specs/{feature_name}/`")
        for item in items:
            lines.append(f"  - {item}")

    return "\n".join(lines)


def compact_error_details(stdout: str, stderr: str, final_message: str) -> str:
    chunks = []
    for text in (stderr, stdout, final_message):
        text = text.strip()
        if text:
            chunks.append(text)
    if not chunks:
        return "the nested Codex run exited without output"

    merged = " | ".join(chunks)
    return " ".join(merged.split())[:600]


def turn_baseline_path(turn_id: str) -> Path:
    safe_turn_id = re.sub(r"[^A-Za-z0-9_.-]", "_", turn_id)
    return BASELINE_ROOT / f"{safe_turn_id}.json"


def sha256sum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dedupe_preserving_order(items: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in items:
        if item in seen:
            continue
        seen.add(item)
        ordered.append(item)
    return ordered


if __name__ == "__main__":
    sys.exit(main())
