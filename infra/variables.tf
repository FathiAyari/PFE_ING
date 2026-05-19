variable "project" {
  description = "Project name prefix used for all resources."
  type        = string
  default     = "pfe-devsecops"
}

variable "location" {
  description = "Azure region."
  type        = string
  default     = "francecentral"
}

variable "vm_size" {
  description = "Azure VM size."
  type        = string
  default     = "Standard_B1ms"
}

variable "vm_admin_username" {
  description = "Linux admin username for the VM."
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key" {
  description = "OpenSSH public key used for the VM admin account."
  type        = string
}

variable "acr_sku" {
  description = "Azure Container Registry SKU."
  type        = string
  default     = "Basic"
}

variable "allowed_ssh_cidr" {
  description = "CIDR allowed to SSH into the VM."
  type        = string
  default     = "0.0.0.0/0"
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default = {
    project = "pfe-devsecops"
    managed = "terraform"
  }
}
