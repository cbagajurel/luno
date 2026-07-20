# Releasing the `@luno-oss/*` packages

The nine backend SDK packages under `packages/` publish to npm under the
[`luno-oss`](https://www.npmjs.com/org/luno-oss) scope. Versioning is driven by
[Changesets](https://github.com/changesets/changesets); publishing is driven by
npm **trusted publishing** (OIDC).

**There is no npm token in this repository, and there must never be one.** CI
authenticates to npm with a short-lived credential minted from the GitHub
Actions workflow identity. Nothing to store, nothing to leak, nothing to rotate.

## Day-to-day: shipping a change

1. Make the change.
2. Describe it: `pnpm changeset` — pick the affected packages and a bump
   (patch/minor/major). This writes a small markdown file under `.changeset/`.
3. Commit the changeset alongside the code and open a PR.

CI fails a PR that changes a package without a changeset, because the release
workflow publishes exactly what has one — a package changed without a changeset
ships nothing and the fix goes silently missing.

Once merged to `master`, the release workflow opens (or updates) a **"chore:
version packages"** PR that applies every pending changeset: bumping versions,
bumping dependents, and writing each package's `CHANGELOG.md`. Nothing is
published while that PR sits open.

**Merging that PR publishes.** That is the release button.

Version bumps cascade automatically: bumping `@luno-oss/core` bumps the adapters
that peer-depend on it. Published dependency ranges are carets (`^0.1.0`), not
exact pins — `workspace:^` in a `package.json` is rewritten at pack time.

## One-time bootstrap

npm cannot configure a trusted publisher for a package that does not exist yet
(unlike PyPI — see [npm/cli#8544](https://github.com/npm/cli/issues/8544)). So
the very first version of each package must be published with a token, once.
After that the token is never needed again.

### 1. Publish once from a machine, with a token

```bash
pnpm install --frozen-lockfile
pnpm typecheck && pnpm test && pnpm build

# Authenticate without writing the token to ~/.npmrc:
printf '//registry.npmjs.org/:_authToken=%s\n' "$NPM_ACCESS_TOKEN" > /tmp/luno-npmrc
chmod 600 /tmp/luno-npmrc

pnpm -r --filter './packages/*' publish \
  --access public --no-git-checks --userconfig /tmp/luno-npmrc

rm -f /tmp/luno-npmrc
```

### 2. Configure the trusted publisher for each package

On npmjs.com, for **each** of the nine packages:

> Package → Settings → Trusted publishing → GitHub Actions

| Field             | Value         |
| ----------------- | ------------- |
| Organization/user | `cbagajurel`  |
| Repository        | `luno`        |
| Workflow filename | `release.yml` |
| Environment       | _(blank)_     |

### 3. Revoke the token

Once every package has a trusted publisher, the token has no remaining purpose.
Delete it at npmjs.com → Access Tokens. CI does not use it, and leaving a
publish-capable token alive is the only real credential risk left in the setup.

### 4. Allow Actions to open PRs

The "version packages" PR needs:

> GitHub repo → Settings → Actions → General → Workflow permissions →
> **Allow GitHub Actions to create and approve pull requests**

## Why OIDC rather than an `NPM_TOKEN` secret

- Nothing long-lived is stored, so nothing can leak from the repo, from CI logs,
  or from a compromised fork.
- The credential is scoped to this repository _and_ this workflow file. A token
  in `secrets` is scoped to whoever can run any workflow.
- npm attaches [provenance](https://docs.npmjs.com/generating-provenance-statements)
  automatically, so each published tarball is cryptographically linked to the
  commit and workflow that built it. Consumers can verify where it came from.
