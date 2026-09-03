---
title: Release guide
sidebar_position: 5
---

# Release guide

How to cut a release of the DPDP accelerator, and what the pipeline does on your behalf.

Releases are built by the **Release builder** workflow
([`.github/workflows/release-builder.yml`](https://github.com/wso2/dpdp-accelerator/blob/main/.github/workflows/release-builder.yml)). It is
dispatched by hand — nothing releases on a push or a merge.

## What a release produces

| | |
|---|---|
| Tag | `vX.Y.Z`, pointing at a `[Release] X.Y.Z` commit |
| Release assets | `wso2-dpdpiam-accelerator-X.Y.Z.zip`, plus the source archives GitHub attaches to every release automatically. GitHub publishes a SHA-256 digest for each asset itself, so no checksum sidecar is uploaded |
| Follow-up | A commit on `main` raising the reactor to the next `-SNAPSHOT` |

The root `pom.xml` is the single source of truth for the version — there is deliberately no
`version.txt` to drift from it. Every child pom inherits the version through `<parent>` and
declares none of its own, so `mvn versions:set` fans one number out to all 17 poms.

Tags carry a `v` prefix; poms and filenames use bare semver. Maven cannot resolve a
`v`-prefixed version, so the two are never conflated — the pipeline rejects a `version`
input that starts with `v`.

## Before the first release

The **Run workflow** button only appears once `release-builder.yml` is on the default
branch (`main`). Until then there is nothing to dispatch.

## Cutting a release

### 1. Put `main` on the commit you want to ship

The pipeline releases whatever `main` currently points at. It does no merging of its own.

### 2. Optionally write the highlights

Commit `release-notes/X.Y.Z.md` and its contents become the **What's new** section of the
release body. Leave it out and that section is simply omitted; you still get the
auto-generated changelog of merged PRs.

This is the one part no API can infer, and it is where the narrative belongs — what changed
and why it matters, rather than a list of commit subjects.

### 3. Dispatch a dry run

**Actions → Release builder → Run workflow**, with branch `main`.

| Input | Default | Set it when |
|---|---|---|
| `version` | Root pom version minus `-SNAPSHOT` | Releasing something other than what the pom says |
| `next_version` | Minor bump (`1.0.0` → `1.1.0`) | You want a patch or major bump instead |
| `prerelease` | off | Cutting an RC, **or releasing from any branch other than `main`** |
| `run_e2e` | on | Turn off only to make a dry run quick |
| `dry_run` | off | **On for the first run** |

With `dry_run` on, everything builds and the release notes render, but nothing is committed,
tagged or published. Download the `release-preview` artifact and check `release-body.md`,
the zip. Pairing it with `run_e2e: off` finishes in a few minutes.

### 4. Dispatch the real run

Same inputs, `dry_run` off.

Budget roughly **two hours** with the E2E gate on: it builds Identity Server from
`product-is` master, because the published-release + U2 update path is currently blocked
upstream (the public release zip is missing the `migration-resources/` tree the update tool
needs).

### 5. Nothing — the version bump is automatic

`post-release` commits the next `-SNAPSHOT` straight to `main`. It is pushed rather than raised
as a PR because `GITHUB_TOKEN` cannot open one unless an admin has enabled *"Allow GitHub Actions
to create and approve pull requests"*, which is off by default. Since there is no PR to carry
checks, the job runs `mvn validate` on the bumped reactor before pushing.

If that push fails, **the release itself has already succeeded** — do not re-run it, or the
duplicate-tag guard will reject it. The job summary says so and gives the `versions:set` command
to run by hand.

## How the pipeline is put together

```
prepare ─┬─ e2e ──┐
         └─ build ─┴─ release ── post-release
```

- **prepare** — resolves and validates the version, rejects an existing tag, and refuses a
  non-prerelease off `main`. Everything downstream reads its outputs rather than
  re-deriving them.
- **e2e** — the same suite that gates a PR, via the reusable
  [`e2e.yml`](https://github.com/wso2/dpdp-accelerator/blob/main/.github/workflows/e2e.yml). Skippable with `run_e2e: off`.
- **build** — `versions:set`, then `mvn clean install`, then asserts the zip exists at the
  exact expected path. That assertion is also what proves `versions:set` reached every
  module.
- **release** — makes the release commit, renders the notes, pushes the tag, publishes.
- **post-release** — commits the next `-SNAPSHOT` to `main`.

### Why `main` never moves

The release commit is published by pushing **only the tag**. `git push origin refs/tags/vX.Y.Z`
carries the commit's objects with it, so the commit is reachable through the tag without any
branch being written to.

The consequence to be aware of: **the release commit is not on `main`**. `git checkout vX.Y.Z`
gives exactly the tree that produced the artifact, and `git log main` does not show the release
commit. `main` moves to the next `-SNAPSHOT` through the post-release commit instead.

This is what keeps the whole pipeline inside `GITHUB_TOKEN`'s reach — it needs no repository
secret and no admin-flipped setting, only the `contents: write` permission declared in the
workflow.

## If a run fails

Nothing is published until the tag push, which is the second-to-last step of `release`. A
failure in `prepare`, `build` or the E2E gate leaves no tag, no release and no commit — fix
the cause and dispatch again.

Two guards fail fast, before any expensive work:

- an existing tag for the requested version is rejected;
- a non-prerelease dispatched from any branch other than `main` is rejected.

## Rehearsing in a fork

A fork is a reasonable place to exercise the pipeline end to end, with two caveats:

- **`workflow_dispatch` needs the workflow on the fork's default branch** before the Run
  workflow button appears.
- **Releasing from a branch other than `main` requires `prerelease: on`**, or `prepare`
  rejects the run by design.

`post-release` pushes its version bump to the fork's own `main`, so a rehearsal moves the fork
forward and never touches `wso2/dpdp-accelerator`.
