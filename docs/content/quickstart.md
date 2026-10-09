# Quickstart

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

## Overview

The WSO2 DPDP Accelerator helps you build solutions for India's [Digital Personal Data Protection Act, 2023](introduction.md). 
Built on top of WSO2 Identity Server,Solution provides consent management, grievance handling, consent audit
history, and event notifications, along with a Consent Portal for end users and
administrators.

This guide sets up the accelerator in a local environment with the default
**embedded H2 databases**, so you can quickly try it out.

:::info Setting up for production?

This quickstart is for local evaluation only. For a production deployment,
follow [Install and Set Up](install-and-setup/prerequisites.md) instead.

:::

## Prerequisites

1. **Java Development Kit:** JDK 21.
2. **Environment variables:** set `JAVA_HOME` to the JDK 21 folder and add its
   `bin` folder to your `PATH`.

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
export JAVA_HOME="<JDK_LOCATION>"
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$env:JAVA_HOME = "<JDK_LOCATION>"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

</TabItem>
</Tabs>

`java -version` should report version 21.

## Install the base product

[Download WSO2 Identity Server 7.3.0](https://wso2.com/products/downloads/?product=wso2is)
and extract the ZIP.

## Install the accelerator

Download the latest `wso2-dpdpiam-accelerator-<version>.zip` from the
[releases page](https://github.com/wso2/dpdp-accelerator/releases). To build it
from source instead, see the
[repository README](https://github.com/wso2/dpdp-accelerator#build).

Extract it, and copy the extracted `wso2-dpdpiam-accelerator-<version>`
directory into the root directory of Identity Server.

The rest of this guide refers to the directories as follows:

| Directory | Placeholder |
| --- | --- |
| Identity Server | `<IS_HOME>` |
| DPDP Accelerator, inside `<IS_HOME>` | `<DPDP_ACCELERATOR_HOME>` |

## Apply updates

The accelerator needs Identity Server at U2 update level 17 or later. A freshly
downloaded Identity Server doesn't include the
[update tool](https://updates.docs.wso2.com/en/latest/updates/update-tool/)
yet, so get it first:

Go to `<IS_HOME>/bin` and run the setup script. It downloads the update tool
that matches your operating system and processor into the same folder:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./update_tool_setup.sh
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./update_tool_setup.sh
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
.\update_tool_setup.ps1
```

</TabItem>
</Tabs>

Then, in the same folder, run the update tool it downloaded:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./wso2update_linux        # ARM64: ./wso2update_linux_arm64
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./wso2update_darwin_arm64  # Intel: ./wso2update_darwin
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
.\wso2update_windows.exe   # ARM64: .\wso2update_windows_arm64.exe
```

</TabItem>
</Tabs>

If the tool reports that it updated itself, run the same command again to
update Identity Server.

For more information about WSO2 updates and the update tool, see
[updating WSO2 products](https://updates.docs.wso2.com/en/latest/).

## Configure the accelerator

Go to `<IS_HOME>/<DPDP_ACCELERATOR_HOME>/bin` and run the merge script, then
the configure script:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./merge.sh
./configure.sh
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./merge.sh
./configure.sh
```

</TabItem>
<TabItem value="windows" label="Windows">

The accelerator's scripts are Bash scripts, so run them from Git Bash or
WSL (Windows Subsystem for Linux):

```sh
./merge.sh
./configure.sh
```

</TabItem>
</Tabs>

## Start the server

Go to `<IS_HOME>/bin` and start Identity Server:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./wso2server.sh
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./wso2server.sh
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
.\wso2server.bat
```

</TabItem>
</Tabs>

Once the server starts, open the Console at `https://localhost:9443/console`
and sign in with the default administrator account: username `admin@wso2.com`,
password `wso2123`.

## Set up portal users

To fully try out the accelerator, create users and assign them these roles by
following the [Configuring Users and Roles](install-and-setup/configuring-users-and-roles.md)

| User | Role | What they can do in the portal |
| --- | --- | --- |
| Portal administrator | `dpdp-consent-admin` | Manage purposes and elements, view and revoke any user's consents, view consent history, manage Event Notifications, and handle all complaints. Use this user to check the portal in the next step. |
| Data Principal | `dpdp-consent-user` | Exercise their data protection rights: review the history of their consents, raise and track grievances, and delete their own account. |
| Data Protection Officer | `dpdp-consent-dpo` | View and respond to every complaint in the organization, without access to consents or other administration. |

## Open the Consent Portal

Open `https://localhost:9443/consent-portal/` and sign in as the user holding
`dpdp-consent-admin`.

## Next steps

- [Learn through real stories](learn/managing-access.md) — see how the main
  features fit together from each participant's point of view
- [Try Out](try-out/consent.md) — walk through the catalog, consent lifecycle,
  complaint, and event flows
- [Install and Set Up](install-and-setup/prerequisites.md) — move to a
  production deployment with an external MySQL or PostgreSQL database
