# `infra/` — file map

This folder contains everything Terraform needs to provision the project's
Azure infrastructure. After cleaning up the debug artefacts, here's what
each file is for.

> For the full **clean-slate setup walkthrough** (Cloud Shell commands,
> service principal, GitHub secrets, etc.) see **`SETUP_SUPER.md`** in
> this same folder.

---

## Tracked files

| File | Type | Purpose |
|---|---|---|
| `providers.tf` | Terraform | Declares required providers (`azurerm ~> 3.116`, `random ~> 3.6`), the minimum Terraform version, and the **remote backend** (`azurerm` with `use_azuread_auth = true`, so the workflow doesn't need a storage account key). Configures the `azurerm` provider. |
| `variables.tf` | Terraform | All input variables and their defaults: `project=pfe-devsecops`, `location=swedencentral`, `vm_size=Standard_D2s_v3`, `vm_admin_username=azureuser`, `acr_sku=Basic`, `allowed_ssh_cidr=0.0.0.0/0`, `tags`, plus the required `ssh_public_key`. |
| `main.tf` | Terraform | The actual resources: random suffix, resource group, VNet + subnet, NSG (open ports 22/80/8080), public IP, NIC, Azure Container Registry, and the Linux VM (Ubuntu 22.04, zone 1) with cloud-init wired in. |
| `outputs.tf` | Terraform | Outputs surfaced after `apply`: `resource_group`, `vm_name`, `vm_public_ip`, `vm_location`, `vm_size`, `acr_name`, `acr_login_server`. The GitHub Actions log prints these. |
| `cloud-init.yaml` | YAML (cloud-init) | First-boot script for the VM. Installs Docker + Docker Compose, enables the service, adds the `azureuser` to the `docker` group, and runs `docker login` against the ACR using the templated `acr_login_server`. |
| `.terraform.lock.hcl` | Terraform | Provider dependency lock file. **Commit this** so CI uses the exact same provider versions you tested with. |
| `README.md` | Docs | Short pointer + summary of the infra layer. |
| `SETUP.md` | Docs | This file — explains what every file in `infra/` does. |
| `SETUP_SUPER.md` | Docs | Step-by-step clean-slate setup: wipe Azure, create state SA, service principal, RBAC, GitHub secrets, push, deploy, troubleshoot. |

## Untracked / generated (safe to delete)

| Path | Why it exists | Action |
|---|---|---|
| `.terraform/` | Local provider plugins downloaded by `terraform init`. Regenerated automatically. | Gitignored. Leave it for local runs; delete to force a fresh `init`. |
| `terraform.tfstate*` (if present) | Local state from a non-backend run. | Should not appear — the backend is remote. If it does, delete it before pushing. |
| Earlier debug logs (removed): `init.out`, `init.stderr`, `init.stdout`, `init_all.log`, `init_reconf.out`, `plan.out`, `plan_all.log`, `plan_reconf.out`, `show.out`, `show_all.log`, `tftrace.log` | Captured during local troubleshooting. | Already deleted. Don't re-commit. |

---

## How the files fit together

```
                       ┌──────────────────────────┐
   GitHub Actions ───► │ providers.tf             │  ← backend = Azure Storage (AAD auth)
                       │ variables.tf             │  ← reads -var "ssh_public_key=…"
                       │                          │           -var "location=…"
                       │ main.tf  ────────────────┼──► creates RG, VNet, NSG, PIP,
                       │                          │      NIC, ACR, Linux VM
                       │     │                    │
                       │     │  custom_data       │
                       │     ▼                    │
                       │ cloud-init.yaml          │  ← runs on the VM at first boot
                       │                          │      (Docker + ACR login)
                       │ outputs.tf               │  ← surfaces vm_public_ip, acr_login_server, …
                       └──────────────────────────┘
```

---

## Common commands

Local plan (PowerShell):

```powershell
cd C:\Users\fathi\OneDrive\Desktop\PFE\infra
$env:TF_VAR_ssh_public_key = (Get-Content $HOME\.ssh\id_rsa.pub -Raw)
terraform init `
  -backend-config="resource_group_name=tfstate-rg" `
  -backend-config="storage_account_name=$env:TF_BACKEND_SA" `
  -backend-config="container_name=tfstate" `
  -backend-config="key=pfe-devsecops.tfstate"
terraform plan
```

CI deploy: push to `main`, the `Terraform — Azure infra` workflow runs
`init`, `plan`, then `apply`.

CI destroy: GitHub → Actions → that workflow → **Run workflow** →
`action=destroy`.
