# Nomination Impersonation Validator

A WSO2 Identity Server extension. It stands in the validation line IS runs before
issuing an impersonation subject token, and does two things the built-in
validators cannot.

**1. The nomination gate.** IS's own `SubjectScopeValidator` confirms only that
the caller holds the impersonation scope — it never checks whether the caller is
actually nominated for the person being impersonated. This validator asks Nominee
Service, which owns that record, so IS needs no connection to or knowledge of the
application's database. It fails closed: if the nomination cannot be confirmed,
impersonation is denied.

**2. Scope narrowing.** IS computes the approved scopes *as the owner*, which for
a nomination is far too much — the owner grants each nominee a specific subset.
This validator intersects the approved scopes down to that subset, so the subject
token, and the access token exchanged from it, can never carry more authority
than the owner delegated.

For why it is built this way — the priority ordering, the deny-list scope map, the
OSGi packaging constraints and the startup rule — see [DESIGN.md](DESIGN.md).

## Prerequisites

- **JDK 21** — [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)
  (`java -version` should report 21)
- **Maven 3.9+** — [maven.apache.org/download](https://maven.apache.org/download.cgi)
  (`mvn -version`)
- **WSO2 Identity Server 7.3.0** — [wso2.com/identity-server](https://wso2.com/identity-server/)

On Windows, `winget install EclipseAdoptium.Temurin.21.JDK` and
`winget install Apache.Maven` also work. On macOS,
`brew install temurin@21 maven`.

## Build

```bash
mvn clean package
```

Produces `target/nomination-extension-accelerator-<version>.jar`.

## Deploy

```bash
# 1. Remove any older copy first - two versions means two validators registered
rm $IS_HOME/repository/components/dropins/nomination-extension-accelerator-*.jar

# 2. Drop in the new jar
cp target/nomination-extension-accelerator-1.5.0.jar \
   $IS_HOME/repository/components/dropins/

# 3. Remove stale entries from the OSGi bundle list
#    Deleting the jar is NOT enough. An entry pointing at a jar that no longer
#    exists stops the bundle loading, silently and with no error in the log.
#    Edit this file and delete any line naming an old version:
#    $IS_HOME/repository/components/default/configuration/\
#      org.eclipse.equinox.simpleconfigurator/bundles.info

# 4. Restart IS
```

## Configuration

Two system properties, both with development defaults:

| Property | Default | Meaning |
|---|---|---|
| `nominee.gate.url` | `http://localhost:8082` | Where Nominee Service is reachable |
| `nominee.gate.key` | `dev-impersonation-gate-key` | Shared key for the gate endpoint |

Set them on the IS JVM for anything other than local development. The key must
match Nominee Service's `impersonation-gate.internal-api-key`.

## Confirming it loaded

Run an impersonation and look in `$IS_HOME/repository/logs/wso2carbon.log`:

```
INFO {org.wso2.dpdp.nomination.extension.accelerator.NominationImpersonationValidator} - Impersonation allowed: ...
INFO {org.wso2.dpdp.nomination.extension.accelerator.NominationImpersonationValidator} - Narrowed impersonation scopes ...
```

**If nothing appears, the bundle did not resolve.** Two usual causes:

1. A stale `bundles.info` entry — see step 3 above.
2. A new `Import-Package` entry picked up a strict version range from the bundle
   plugin's wildcard. WSO2 exports these packages unversioned, so every import
   must be listed explicitly with `version="[0,9)"` in `pom.xml`. Compare the
   jar's manifest against a known-good build:
   `unzip -p target/*.jar META-INF/MANIFEST.MF | grep Import-Package`

## Why priority 50

IS sorts validators by priority **descending**, so the lowest number runs last.
`SubjectScopeValidator` sits at 80 and calls `setApprovedScope(...)`; anything
written before it is simply overwritten. This validator therefore runs below it,
where its narrowing is the final word.

## A trap worth knowing

The HTTP client is built lazily, on first use, and must stay that way.

`HttpClient.Builder#build()` resolves `SSLContext.getDefault()` when no context is
supplied, and that call initialises the JVM-wide default context permanently from
whatever truststore is configured at that instant. This bundle starts before the
server has configured its own truststore, so building a client at construction
time freezes a default context trusting only the JDK's bundled authorities — and
every later TLS client in the process inherits it, including IS's own login page,
which then fails with a PKIX error and returns 404.

Anything else added here that opens an HTTPS connection needs the same treatment.

## Tests

The validator is exercised end to end rather than in isolation, through the
nominee flow — see `portal/NOMINEE-SETUP.md`.
