# Data-center infrastructure

Terraform provisions two Ubuntu 24.04 VMs on Harvester using the upstream
[`wso2/open-cloud-datacenter` `workloads/vm` module](https://github.com/wso2/open-cloud-datacenter/tree/main/modules/workloads/vm),
pinned to `v0.8.0`. The configuration follows that module's documented
provider, network, cloud-init, and console-password conventions.

| VM | Intended role | vCPU | RAM | Root disk |
| --- | --- | ---: | ---: | ---: |
| `dpdp-is` | Identity Server | 6 | 12 GiB | 50 GiB |
| `dpdp-db` | Database | 2 | 4 GiB | 50 GiB |

The split stays within the quoted total of 8 vCPU, 16 GiB RAM, and 100 GiB
storage. This PR only provisions infrastructure and serial-console access. It does not
install or configure WSO2 Identity Server, a database, or the accelerator.

## Prerequisites

- Terraform 1.7 or newer
- a Rancher-generated Harvester kubeconfig with rights to create and manage VMs
- the tenant namespace, Ubuntu image, and VM network
- a temporary console password stored only in ignored local Terraform variables

### Download the Harvester kubeconfig

1. Sign in to Rancher and open **Virtualization Management**.
2. Select the target Harvester cluster and choose **Download Kubeconfig**.
3. Store the downloaded file locally with owner-only permissions, for example
   `~/.kube/us-dc-harvester-cluster.yaml`, and set `harvester_kubeconfig_path`
   to that path in ignored `terraform.tfvars`.

## Provision the VMs

Copy the variables file, set `harvester_kubeconfig_path` to the local,
owner-readable kubeconfig file, and set `vm_console_password` locally.

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# Set the kubeconfig path, tenant values, and temporary console password.
make init
make plan
# Review the plan before applying it.
make apply
```

No infrastructure is created until `terraform apply` is run with a valid
Harvester kubeconfig.

## Console access

When the VM subnet is not reachable from the local network, use the serial
console through a separately installed `virtctl` binary. Run the console target:

```bash
make console-is
make console-db
```

These commands set `KUBECONFIG` to `HARVESTER_KUBECONFIG` and invoke `virtctl
console` for the relevant VM. Log in as `ubuntu` with the temporary password
stored in ignored local `terraform.tfvars`; never commit it. Exit a console
session with `Ctrl+]`.

Keep in mind that the serial console is a single-user session: only one person
can be connected at a time, unlike SSH, which supports multiple sessions.

## Deferred SSH access

SSH access is intentionally deferred because there is no site-to-site network
connectivity from developer machines to the VM subnet. Revisit SSH keys and
network reachability when that connectivity is available.
