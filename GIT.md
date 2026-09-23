# Git Workflow

## Integration branch

`develop` is the shared integration branch for the hackathon. Keep it runnable and demonstrable. Do not push feature work directly to `main`; use `develop` for active integration unless the team explicitly changes this decision.

- Make small, understandable commits that describe actual changes.
- Use short-lived feature branches when they reduce overlap.
- Assign owners to high-conflict files and coordinate before editing them.
- Integrate frequently enough to avoid large merges.
- Keep developers in separate ownership areas whenever possible.
- Never commit `.env`, secrets, API keys, personal Codex configuration, local IDE files, irrelevant generated files, or unnecessary large binaries.
- Do not rewrite shared history destructively.

## Mandatory Pre-Push Workflow

Before pushing a feature branch or opening or updating a merge request:

1. Confirm that `develop` remains the active integration branch or use another branch only when the team explicitly designates it.
2. Ensure the feature branch has no unresolved conflicts and preserve or commit intended local changes before rebasing.
3. Run `git fetch origin develop` to obtain the latest remote state.
4. Update the local `develop` branch from `origin/develop` using a fast-forward-only update. Never replace uncommitted work or force the integration branch to another state.
5. Return to the feature branch.
6. Rebase the feature branch onto the updated `develop` or freshly fetched `origin/develop`.
7. If the rebase completes without conflicts, push the feature branch with `git push --force-with-lease`.

If a conflict occurs:

1. Do not push.
2. Inspect every conflicted file and understand the purpose of both changes.
3. Combine the valid changes from `develop` with the changes required by the feature task.
4. Never resolve a conflict mechanically by taking the complete `ours` or `theirs` side when both contain useful work.
5. Stage a file only after its conflict is correctly resolved.
6. Continue with `git rebase --continue` and repeat until the rebase finishes.
7. Confirm that Git reports no remaining conflicted files.
8. Push the rebased feature branch with `git push --force-with-lease` only after the repository is clean of conflicts.

Use `--force-with-lease`, never plain `--force`. Force-with-lease is allowed only for a developer's own feature branch after rebase. Never force-push `develop`, `main`, or another shared branch.

Optimize for low conflict probability, fast integration, and a continuously demonstrable build.
