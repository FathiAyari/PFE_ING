# `infra/` — Infrastructure as Code (Terraform)

Defines the Azure cloud infrastructure for the PFE DevSecOps stack:

| Resource              | Type                          |
| --------------------- | ----------------------------- |
| `azurerm_resource_group.rg`              | Resource Group                |
| `azurerm_virtual_network.vnet`           | VNet `10.20.0.0/16`           |
| `azurerm_subnet.subnet`                  | Subnet `10.20.1.0/24`         |
| `azurerm_network_security_group.nsg`     | NSG (ssh / http / 8080)       |
| `azurerm_public_ip.pip`                  | Static public IP              |
| `azurerm_network_interface.nic`          | VM NIC                        |
| `azurerm_container_registry.acr`         | Container Registry (Basic)    |
| `azurerm_linux_virtual_machine.vm`       | Ubuntu 24.04 + Docker         |

## Files

| File                | Purpose                                                  |
| ------------------- | -------------------------------------------------------- |
| `providers.tf`      | Provider versions (`azurerm`, `random`)                  |
| `variables.tf`      | Inputs (`project`, `location`, `vm_size`, `ssh_public_key`, …) |
| `main.tf`           | All resources                                            |
| `outputs.tf`        | Useful outputs (`vm_public_ip`, `acr_login_server`, …)   |
| `cloud-init.yaml`   | VM bootstrap: installs Docker, exports ACR login server  |
| `resources.json`    | Static manifest used by the web app's **Infra** page     |

## Visualization in the web app

The Spring Boot backend serves `GET /api/iac/resources` which reads
`infra/resources.json` and returns a list of resources. The Angular SPA renders
them as cards at **`/infra`** (sidebar → "Infra (IaC)"). This works **without
Azure credentials** — the page shows the *defined* infrastructure straight from
the manifest.

If `infra/terraform.tfstate` exists (after a `terraform apply`), the backend
also exposes its resource addresses via `GET /api/iac/state` so the UI can mark
each resource as **PLANNED** or **APPLIED**.

## Local usage

```powershell
cd C:\Users\fathi\OneDrive\Desktop\PFE\infra

# Provide Azure SP credentials (or `az login` and skip these)
$env:ARM_TENANT_ID       = "<tenant>"
$env:ARM_CLIENT_ID       = "<appId>"
$env:ARM_CLIENT_SECRET   = "<secret>"
$env:ARM_SUBSCRIPTION_ID = "<subscription>"

terraform init
terraform plan  -var "ssh_public_key=$(Get-Content $env:USERPROFILE\.ssh\id_rsa.pub -Raw)"
terraform apply -var "ssh_public_key=$(Get-Content $env:USERPROFILE\.ssh\id_rsa.pub -Raw)"

# When done
terraform destroy -var "ssh_public_key=$(Get-Content $env:USERPROFILE\.ssh\id_rsa.pub -Raw)"
```

`terraform.tfstate` is gitignored — the standard pattern is to use a remote
backend (Azure Storage / Terraform Cloud) for shared state. Backend config is
intentionally omitted from `providers.tf` so each environment can supply its
own via `-backend-config=...` at init time.

## Conventions

- Resource names use the `${var.project}-<role>` pattern (`pfe-devsecops-vm`,
  `pfe-devsecops-nsg`, …).
- ACR names must be globally unique → `random_string.suffix` keeps it valid
  across re-applies in different subscriptions.
- The VM admin user is `azureuser`; the SSH public key must be passed as a var.
- Every resource carries the `tags` map (`project`, `managed=terraform`).
