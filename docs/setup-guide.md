# Setting up the DPDP Identity Server Accelerator

Gets the Identity Server running with the consent portal deployed. Complete
this before [`configuration-guide.md`](configuration-guide.md), which
registers the portal application.

## Prerequisites

- JDK 11 or later on the PATH
- Maven 3.6.3+ and Node.js 20.19+ (or 22.12+) with npm, only if building the
  accelerator from source

## 1. Get WSO2 Identity Server

Download and extract WSO2 Identity Server 7.3.0 from the
[WSO2 Identity Server](https://wso2.com/identity-and-access-management/)
site. The extracted directory is `<IS_HOME>` in the steps below.

## 2. Get the accelerator

Either download a released `wso2-dpdp-is-accelerator-<version>.zip`, or build
it from source:

```sh
mvn clean install
```

Run from the repository root — this produces
`dpdp-accelerator/accelerators/dpdp-is/target/wso2-dpdp-is-accelerator-<version>.zip`.
See the [repository README](../README.md#build) for details.

## 3. Extract the accelerator

Unzip the accelerator inside `<IS_HOME>` (or anywhere, passing `<IS_HOME>` to
each script in the next steps).

## 4. Copy the artifacts in

With the server stopped:

```sh
sh bin/merge.sh <IS_HOME>
```

## 5. Apply the configuration

Still stopped:

```sh
sh bin/configure.sh <IS_HOME>
```

Edit `repository/conf/configure.properties` first if your hostname, port,
administrator credentials or database differ from the defaults. This step
installs `deployment.toml`, writes `dpdp-portal.properties`, and applies the
consent schema migration.

> **`deployment.toml` is replaced, not merged.** The accelerator ships a
> complete file — `repository/resources/wso2is-7.3.0-deployment.toml`, the
> stock Identity Server 7.3.0 configuration plus the accelerator's own
> settings. Your existing file is copied to `deployment.toml.bak-<timestamp>`
> first; re-apply any local customisation from that backup before starting
> the server. To target a different Identity Server version, add a template
> beside the shipped one and point `PRODUCT_CONF_PATH` at it.

## 6. Start the Identity Server

From `<IS_HOME>`:

```sh
sh bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Wait for the console to print `WSO2 Identity Server started in ...` before
continuing.

## Next: register the portal application

Follow [`configuration-guide.md`](configuration-guide.md).
