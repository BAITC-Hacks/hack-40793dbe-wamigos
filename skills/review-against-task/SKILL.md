---
name: review-against-task
description: Use as the mandatory final Code Rules quality gate after any code-changing task, and after the official HackAlem task is released to compare implementation with mandatory requirements and primary demo flows.
---

# Review Against Task and Code Rules

Use the mode required by the current stage. Code Rules mode is mandatory after every code-changing task. Task-compliance mode applies when reviewing implementation against the released official task.

## Code Rules Mode

Inspect the changed code and its directly affected boundaries against the `Code rules` section of `AGENTS.md` and the applicable frontend, backend, project-structure, and Git rules.

Check at minimum:

- nested classes without a necessary technical reason;
- independent DTOs, mappers, validators, configuration, services, repositories, utilities, or helpers hidden inside other classes;
- oversized classes or methods with multiple unrelated responsibilities;
- misplaced behavior and unclear ownership between layers or components;
- duplicated logic that should reuse an existing owner;
- dead code and commented-out implementations;
- unused imports, fields, methods, parameters, or dependencies;
- debug output, temporary flags, hardcoded diagnostics, and other development leftovers;
- abstractions, classes, and interfaces that add complexity without a real requirement;
- TODO and FIXME markers that leave the current task incomplete;
- names that obscure the purpose of classes, methods, variables, fields, or files;
- obvious violations of the existing project structure and local patterns.

Use cohesion, responsibility, and readability rather than arbitrary line-count thresholds when judging size. Limit inspection to the change and its necessary dependency surface unless evidence shows a broader problem.

Fix a violation when the correction is behavior-preserving, localized, and safe. After a safe fix, repeat the focused Code Rules check. Do not perform a blind refactor when a change could alter business behavior, external contracts, persistence semantics, or another developer's work; report the finding with its risk and affected location instead.

Do not create automated tests unless explicitly requested. Code Rules mode is the last code-quality check before the final response.

## Task-Compliance Mode

Evaluate the actual implementation against the official task, not assumptions or preferred architecture.

1. Read the official task and applicable rules.
2. Read the current implementation.
3. Extract every explicit mandatory requirement.
4. Compare each requirement with observable implementation behavior.
5. Identify missing, incomplete, or incorrect requirements; broken primary flows; and work that does not help satisfy the task.
6. Prioritize findings by submission impact, demo impact, and dependency order.
7. When fixes are requested, address the highest-priority gaps first and preserve working behavior.

Do not invent requirements, replace official answers with assumptions, spend time on style unless it materially affects functionality, or create test suites unless explicitly requested.

Return a concise, actionable comparison of task requirements versus the current implementation.
