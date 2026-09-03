variable "harvester_kubeconfig_path" {
  type        = string
  description = "Path to the Rancher-generated Harvester kubeconfig used to provision and manage these VMs."
}

variable "prefix" {
  type        = string
  description = "Prefix for the VM names, giving <prefix>-is and <prefix>-db."
  default     = "dpdp"
}

variable "vm_namespace" {
  type        = string
  description = "Harvester namespace (tenant project namespace) to create the VMs in."
}

variable "vm_image" {
  type        = string
  description = "Harvester OS image in namespace/name form. Ask the platform team which images your namespace can read."
}

variable "vm_network" {
  type        = string
  description = "Harvester network attachment definition in namespace/name form. Ask the platform team which subnet your namespace is on."
}

variable "vm_user" {
  type        = string
  description = "OS user created by cloud-init on both VMs."
  default     = "ubuntu"
}

variable "vm_console_password" {
  type        = string
  description = "Temporary password for vm_user console access."
  nullable    = false
  sensitive   = true

  validation {
    condition     = trimspace(var.vm_console_password) != ""
    error_message = "Set vm_console_password so the VMs receive cloud-init and can be accessed through the serial console."
  }
}

# The quoted 8 vCPU / 16 GiB / 100 GiB allocation is split across both VMs.
# IS receives most compute because it runs the JVM, Carbon, and the portal; the
# database receives half the disk because it owns the persistent application data.

variable "is_vm_cpu" {
  type        = number
  description = "vCPUs for the Identity Server VM."
  default     = 6
}

variable "is_vm_memory" {
  type        = string
  description = "RAM for the Identity Server VM, in Gi."
  default     = "12Gi"
}

variable "is_vm_disk_size" {
  type        = string
  description = "Root disk for the Identity Server VM."
  default     = "50Gi"
}

variable "db_vm_cpu" {
  type        = number
  description = "vCPUs for the MySQL VM."
  default     = 2
}

variable "db_vm_memory" {
  type        = string
  description = "RAM for the MySQL VM, in Gi."
  default     = "4Gi"
}

variable "db_vm_disk_size" {
  type        = string
  description = "Root disk for the MySQL VM. Holds the MySQL data directory."
  default     = "50Gi"
}
