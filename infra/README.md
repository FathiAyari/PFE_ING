# `infra/` — Terraform Infrastructure

This folder defines Azure infrastructure and the workflow integration that feeds
the backend real-time inventory.

## What Terraform Creates

- Resource Group
- VNet + Subnet
- NSG
- Public IP
- NIC
- Azure Container Registry (ACR)
- Linux VM

See concrete outputs in `infra/outputs.tf`:

- `resource_group`
- `vm_name`
- `vm_public_ip`
- `vm_size`
- `vm_location`
- `acr_name`
- `acr_login_server`

## How It Connects to the Platform

1. GitHub Actions runs `.github/workflows/terraform.yml`.
2. After successful apply/destroy, workflow signs payload with
   `INFRA_WEBHOOK_HMAC_SECRET`.
3. Workflow posts to backend webhook:
   `POST /api/infra/sync/trigger`.
4. Backend runs Azure Resource Graph full sync and stores resources in MySQL
   (`azure_resource`, `sync_run`, `azure_resource_history`).
5. Frontend `infra-live` page reflects updates via REST + WebSocket.

## Key Secrets for Workflow

- `AZURE_TENANT_ID`
- `AZURE_CLIENT_ID`
- `AZURE_CLIENT_SECRET`
- `AZURE_SUBSCRIPTION_ID`
- `TF_BACKEND_RG`
- `TF_BACKEND_SA`
- `TF_BACKEND_CONTAINER`
- `TF_SSH_PUBLIC_KEY`
- `AZURE_LOCATION`
- `BACKEND_WEBHOOK_URL`
- `INFRA_WEBHOOK_HMAC_SECRET`

`BACKEND_WEBHOOK_URL` can be base URL or full endpoint. Workflow normalizes it.

## Local Terraform Commands

```powershell
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\infra"

terraform init `
  -backend-config="resource_group_name=<TF_BACKEND_RG>" `
  -backend-config="storage_account_name=<TF_BACKEND_SA>" `
  -backend-config="container_name=<TF_BACKEND_CONTAINER>" `
  -backend-config="key=pfe-devsecops.tfstate"

terraform plan  -var "ssh_public_key=$(Get-Content $HOME\.ssh\id_rsa.pub -Raw)" -var "location=<AZURE_LOCATION>"
terraform apply -auto-approve -var "ssh_public_key=$(Get-Content $HOME\.ssh\id_rsa.pub -Raw)" -var "location=<AZURE_LOCATION>"
```

For full clean setup/reset steps, see `infra/SETUP_SUPER.md`.

## File Map

| File | Purpose |
|---|---|
| `providers.tf` | Required providers (`azurerm`, `random`) and remote backend (Azure Storage with `use_azuread_auth = true`). |
| `variables.tf` | Inputs and defaults: `project`, `location`, `vm_size`, `vm_admin_username`, `acr_sku`, `allowed_ssh_cidr`, `tags`, plus the required `ssh_public_key`. |
| `main.tf` | Core resources: random suffix, Resource Group, VNet + Subnet, NSG, Public IP, NIC, ACR, Linux VM with cloud-init. |
| `outputs.tf` | Outputs surfaced after apply (`vm_public_ip`, `acr_login_server`, ...). |
| `cloud-init.yaml` | First-boot VM script (Docker install, ACR login). |
| `.terraform.lock.hcl` | Provider dependency lock file. Commit it. |
| `README.md` | This file. |
| `SETUP_SUPER.md` | Full clean-slate Azure setup runbook. |

## Generated / Untracked

| Path | Purpose | Action |
|---|---|---|
| `.terraform/` | Local provider plugins from `terraform init`. | Gitignored. Delete to force fresh init. |
| `terraform.tfstate*` | Local state from a non-backend run. Should not appear since backend is remote. | Delete before pushing. |

