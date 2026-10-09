---
title: Setting Up Servers
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Setting Up Servers

Complete the [Prerequisites](prerequisites.md) first.

This section explains how to install the DPDP Accelerator distribution, merge its artifacts into WSO2 Identity Server, and configure the base `deployment.toml` file.

:::note
Ensure the Identity Server is stopped before extracting files, running scripts, or applying configurations.
:::

---

## 1. Install the Accelerator

Download the latest `wso2-dpdpiam-accelerator-<version>.zip` from the [releases page](https://github.com/wso2/dpdp-accelerator/releases). To build it from source instead, see the [repository README](https://github.com/wso2/dpdp-accelerator#build).

Extract it, and copy the extracted `wso2-dpdpiam-accelerator-<version>` directory into the root directory of Identity Server `<IS_HOME>`.

The rest of this guide refers to the directories as follows:

| Directory | Placeholder |
| --- | --- |
| Identity Server | `<IS_HOME>` |
| DPDP Accelerator, inside `<IS_HOME>` | `<DPDP_ACCELERATOR_HOME>` |

---

## 2. Merge Accelerator Artifacts

Go to `<IS_HOME>/<DPDP_ACCELERATOR_HOME>/bin` and run the merge script:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./merge.sh
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./merge.sh
```

</TabItem>
<TabItem value="windows" label="Windows">

The accelerator's scripts are Bash scripts, so run them from Git Bash or WSL (Windows Subsystem for Linux):

```sh
./merge.sh
```

</TabItem>
</Tabs>

This script copies the accelerator's artifacts—including OSGi bundles, webapps, configuration templates, and database scripts—into their respective directories in `<IS_HOME>`.

---

## 3. Copy `deployment.toml`

Server-level configurations in WSO2 Identity Server are managed through `<IS_HOME>/repository/conf/deployment.toml`.

The accelerator provides a preconfigured, fully commented template for WSO2 Identity Server 7.3.0:

```text
<IS_HOME>/<DPDP_ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml
```

To apply the configuration:

1. **Back up existing configuration**:
   Back up your current `<IS_HOME>/repository/conf/deployment.toml` file.

2. **Copy the template**:
   Copy the accelerator's template to `<IS_HOME>/repository/conf/` and rename it to `deployment.toml`, replacing the existing file:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
cp <IS_HOME>/<DPDP_ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml <IS_HOME>/repository/conf/deployment.toml
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
cp <IS_HOME>/<DPDP_ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml <IS_HOME>/repository/conf/deployment.toml
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
Copy-Item "<IS_HOME>\<DPDP_ACCELERATOR_HOME>\repository\resources\wso2is-7.3.0-deployment.toml" "<IS_HOME>\repository\conf\deployment.toml"
```

</TabItem>
</Tabs>

:::info
This template contains the baseline settings for the DPDP Accelerator, including datasource definitions, Consent Portal configurations, and event notification settings. Database connection URLs and credentials will be configured in subsequent steps.
:::

---

## 4. Secure the Default Keystore

If you are using the default keystore provided with the product, it is located at:

```text
<IS_HOME>/repository/resources/security/wso2carbon.p12
```

:::tip
For a production deployment, consider [creating a new keystore](https://is.docs.wso2.com/en/latest/deploy/security/keystores/create-new-keystores/) and [configuring it](https://is.docs.wso2.com/en/latest/deploy/security/keystores/configure-keystores/) instead of using the default one.
:::

---

## Next Steps

Once the server artifacts are installed and the base `deployment.toml` template is copied, proceed to configure the databases before starting the server:

- Continue with [Setting up the database](setting-up-the-database.md) to create the required databases and execute the database scripts.
