---
name: spec-self-eval
description: Validate `.specs/<feature>/requirements.md`, `design.md`, and `tasks.md` against the project checklist and emit a strict `PASS` / `WEAK` / `FAIL` report saved to `.specs/<feature>/eval-report-YYYY-MM-DD.md`. Use when the user asks to self-evaluate a spec, audit implementation readiness, review spec quality, or generate an eval report for the latest feature spec.
---

# Spec Self Eval

Use this skill to audit a feature spec bundle before implementation begins.

## Scope

Review exactly these files inside one feature folder:

- `.specs/<feature>/requirements.md`
- `.specs/<feature>/design.md`
- `.specs/<feature>/tasks.md`

If the user names a feature, use that folder.

If the user does not name a feature, auto-detect the most recently edited `.specs/<feature>/` folder. Determine recency from the newest modified time among `requirements.md`, `design.md`, and `tasks.md`. Ignore the top-level checklist file, nested `plans/` folders, and existing `eval-report-*.md` files.

## Checklist Source

Read the checklist from the first path that exists:

1. `.specs/_ eval-checklist.md`
2. `references/_eval-checklist.md`

Treat each non-empty line as one required evaluation point.

## Required Behavior

Read all three spec files before judging any checklist item.

If one or more required files are missing:

- mark the missing file check as `FAIL`
- still evaluate every checklist item using the files that do exist
- do not invent missing evidence

Cross-check requirements, design, and tasks together. Prefer concrete gaps, contradictions, and missing traceability over style feedback.

## Strict Thresholds

- `PASS`: The criterion is clearly satisfied, with direct evidence in the spec files.
- `WEAK`: The criterion is partially satisfied, implied, or too ambiguous to trust during implementation.
- `FAIL`: The criterion is missing, contradicted, or unsupported by the spec files.

Use a strict overall verdict:

- `PASS` only if all three required files exist and every checklist item is `PASS`
- `FAIL` if any required file is missing or any checklist item is `FAIL`
- `WEAK` otherwise

## Report Requirements

Save the report to `.specs/<feature>/eval-report-YYYY-MM-DD.md`.

Use the current date in `YYYY-MM-DD` format. If the same-day report already exists, overwrite it instead of creating duplicates.

For every checklist item:

- keep the original checklist wording
- assign exactly one status: `PASS`, `WEAK`, or `FAIL`
- include direct evidence with file names and section names or line references when practical
- include a short "Fix" note explaining what should be improved

## Suggested Report Shape

```md
# Evaluation Report

- Feature: `.specs/<feature>/`
- Date: `YYYY-MM-DD`
- Checklist source: `...`
- Overall verdict: `PASS | WEAK | FAIL`

## Required Files

- `PASS | FAIL` `requirements.md`: ...
- `PASS | FAIL` `design.md`: ...
- `PASS | FAIL` `tasks.md`: ...

## Checklist Results

1. `PASS | WEAK | FAIL` Checklist item text
   Evidence: ...
   Fix: ...

## Summary

- `PASS`: N
- `WEAK`: N
- `FAIL`: N
- Highest-risk gaps: ...
```

## Output In Chat

After saving the file, briefly report:

- the selected feature folder
- the overall verdict
- the saved report path
