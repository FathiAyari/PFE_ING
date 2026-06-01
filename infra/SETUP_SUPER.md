# PFE DevSecOps — Clean-Slate Setup (Step by Step)

This is the **only doc you need** if you have to start from zero on Azure
again. It captures everything we did to get the GitHub Actions →
Terraform → Azure pipeline working end-to-end.

> **Region:** `swedencentral` — your *Azure for Students* subscription has
> quota there for `Standard_D2s_v3` (verified by creating a VM manually).
> `francecentral` is blocked (`SkuNotAvailable` on every B/D/F-series).

---

## Overview of what gets created

| Layer | Resource | Purpose |
|---|---|---|
| State | `tfstate-rg` + storage account `pfetfstate<rand>` + container `tfstate` | Remote Terraform state, accessed via Azure AD (no account keys) |
| Identity | Service principal `pfe-monitor-sp` | Used by GitHub Actions to talk to Azure |
| App infra (managed by Terraform in `infra/`) | RG `pfe-devsecops-rg`, VNet, Subnet, NSG, Public IP, NIC, ACR `pfedevsecopsacr<rand>`, VM `pfe-devsecops-vm` (`Standard_D2s_v3`, Ubuntu 22.04) | The actual DevSecOps target environment |

---

## Architecture (very short)

```
GitHub push to main
   │
   ▼
GitHub Actions (.github/workflows/terraform.yml)
   │  uses AZURE_* + TF_BACKEND_* + TF_SSH_PUBLIC_KEY + AZURE_LOCATION secrets
   ▼
Terraform (infra/*.tf)
   │  state in Azure Storage (AAD auth)
   ▼
Azure: RG → VNet/NSG/PIP/NIC → ACR → Linux VM
```

---

## 0) Prerequisites

You need:
- An Azure subscription you fully own (student is fine)
- A GitHub repo (this one)
- Azure Cloud Shell (bash) — the browser shell at <https://shell.azure.com>

> ⚠️ Cloud Shell **disconnects often**. Every step below is self-contained:
> if you reconnect, just re-run the small "set vars" block at the top of
> the step you're on, then continue.

---

## 1) Wipe Azure (if anything is left over)

In Cloud Shell:

```bash
SUB_ID=$(az account show --query id -o tsv)
echo "Sub: $SUB_ID"

# Fire off deletes for every RG (async)
for rg in $(az group list --query "[].name" -o tsv); do
  echo "deleting $rg"
  az group delete -n "$rg" --yes --no-wait
done
```

Then re-run this until it prints `0`:

```bash
az group list --query 'length(@)' -o tsv
```

---

## 2) Generate (or reuse) an SSH key

```bash
[ -f ~/.ssh/id_rsa.pub ] || ssh-keygen -t rsa -b 4096 -N "" -f ~/.ssh/id_rsa
cat ~/.ssh/id_rsa.pub
```

Copy the entire `ssh-rsa AAAA…` line — this becomes the GitHub secret
`TF_SSH_PUBLIC_KEY`.

---

## 3) Create the Terraform state backend (Sweden Central)

```bash
LOCATION="swedencentral"
TF_RG="tfstate-rg"
CONT="tfstate"
SA="pfetfstate$RANDOM"
echo "SA name: $SA      <-- WRITE THIS DOWN"

az group create -n "$TF_RG" -l "$LOCATION" -o none
az storage account create -n "$SA" -g "$TF_RG" -l "$LOCATION" \
  --sku Standard_LRS --kind StorageV2 --allow-blob-public-access false -o none
```

> If `RequestDisallowedByAzure` appears, your subscription policy blocks
> the region. Sweden Central works on student subs; if it doesn't, try
> `francecentral` *for the storage account only* — but keep
> `AZURE_LOCATION=swedencentral` for the VM/ACR.

---

## 4) Create the GitHub Actions service principal

The old SP got deleted, so create a fresh one:

```bash
SUB_ID=$(az account show --query id -o tsv)

SP_JSON=$(az ad sp create-for-rbac \
  --name "pfe-monitor-sp" \
  --role "Contributor" \
  --scopes "/subscriptions/$SUB_ID" \
  --years 2 -o json)

echo "$SP_JSON"      # appId / password / tenant — copy NOW, password shown once
```

Capture the values:

```bash
SP_APP_ID=$(echo "$SP_JSON" | jq -r .appId)
SP_PASSWORD=$(echo "$SP_JSON" | jq -r .password)
SP_TENANT=$(echo "$SP_JSON" | jq -r .tenant)

# Wait ~20s for AAD to propagate, then resolve the SP object id
sleep 20
SP_OBJECT_ID=$(az ad sp show --id "$SP_APP_ID" --query id -o tsv)
echo "ObjectId: $SP_OBJECT_ID"
```

---

## 5) Grant the SP rights on the state storage account

(So Terraform can read/write the tfstate **using Azure AD** — no account
keys involved, no `Storage Blob Data *` complaints.)

If you reconnected, set these again first:
```bash
TF_RG="tfstate-rg"
SA="pfetfstateXXXXX"   # the SA from step 3
```

Then:

```bash
SA_ID=$(az storage account show -n "$SA" -g "$TF_RG" --query id -o tsv)

az role assignment create \
  --assignee-object-id "$SP_OBJECT_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Storage Blob Data Contributor" \
  --scope "$SA_ID"
```

> Subscription-wide `Contributor` was already granted in step 4 via
> `--scopes`, so the SP can also create the RG/VNet/VM/ACR.

---

## 6) Create the blob container (using AAD)

```bash
az storage container create -n "$CONT" --account-name "$SA" --auth-mode login
```

If you get *"You do not have the required permissions"* here, your **user**
(not the SP) needs `Storage Blob Data Contributor` on the SA. Quick fix:

```bash
ME=$(az ad signed-in-user show --query id -o tsv)
az role assignment create --assignee-object-id "$ME" \
  --assignee-principal-type User \
  --role "Storage Blob Data Contributor" --scope "$SA_ID"
# wait 30s, retry the container create
```

---

## 7) Set GitHub repository secrets

GitHub → repo → **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
|---|---|
| `AZURE_SUBSCRIPTION_ID` | output of `az account show --query id -o tsv` |
| `AZURE_TENANT_ID` | `$SP_TENANT` from step 4 |
| `AZURE_CLIENT_ID` | `$SP_APP_ID` from step 4 |
| `AZURE_CLIENT_SECRET` | `$SP_PASSWORD` from step 4 |
| `TF_BACKEND_RG` | `tfstate-rg` |
| `TF_BACKEND_SA` | the `$SA` from step 3 |
| `TF_BACKEND_CONTAINER` | `tfstate` |
| `AZURE_LOCATION` | `swedencentral` |
| `TF_SSH_PUBLIC_KEY` | the `ssh-rsa …` line from step 2 |

> Print them in Cloud Shell with `echo "$VAR"` — never paste them into chat
> or commit them to the repo.

---

## 8) Code layout (already in this repo)

- **`infra/providers.tf`** — `azurerm` ~> 3.116, backend uses
  `use_azuread_auth = true` (no account key needed).
- **`infra/variables.tf`** — defaults: `location=swedencentral`,
  `vm_size=Standard_D2s_v3`, `acr_sku=Basic`. `ssh_public_key` is required.
- **`infra/main.tf`** — RG, VNet, Subnet, NSG (22/80/8080), Public IP,
  NIC, ACR (admin enabled), Linux VM (Ubuntu 22.04 jammy, zone 1) with
  `cloud-init.yaml` that installs Docker and logs in to ACR.
- **`infra/outputs.tf`** — `vm_public_ip`, `acr_login_server`, etc.
- **`.github/workflows/terraform.yml`** — `init` with the four
  `TF_BACKEND_*` secrets, `plan` on PR, `apply` on push to `main`,
  `destroy` via `workflow_dispatch`. Passes
  `-var "ssh_public_key=…"` and `-var "location=…"`.

### Key gotchas baked in

- VM size **`Standard_D2s_v3`** in **`swedencentral`** — the only combo
  with available student-sub quota.
- VM and Public IP both pinned to **zone `"1"`** to avoid
  `AllocationFailed` in non-zonal capacity.
- Backend uses **AAD auth**, so the workflow does **not** need
  `ARM_ACCESS_KEY` and the SA can keep `allow-blob-public-access=false`.

---

## 9) Trigger the deploy

From your laptop:

```powershell
cd C:\Users\fathi\OneDrive\Desktop\PFE
git add infra .github/workflows/terraform.yml
git commit -m "infra: clean-slate AAD backend + swedencentral"
git push
```

Then watch **GitHub → Actions → Terraform**. Expected:

1. **Init** — connects to `tfstate` container via AAD ✓
2. **Plan** — `Plan: 10 to add, 0 to change, 0 to destroy.`
3. **Apply** — ~3–5 min, creates the VM + ACR.

In the apply log, look for:

```
acr_login_server = "pfedevsecopsacrXXXXXX.azurecr.io"
resource_group   = "pfe-devsecops-rg"
vm_name          = "pfe-devsecops-vm"
vm_public_ip     = "X.X.X.X"
```

---

## 10) Wire the backend to the live VM (optional)

Set these on the Spring Boot side (env vars or
`pfe_back/src/main/resources/application.properties`) so the dashboard
reads live data instead of mock:

```
AZURE_RESOURCE_GROUP=pfe-devsecops-rg
AZURE_VM_NAME=pfe-devsecops-vm
AZURE_ACR_NAME=<acr_name from output>
AZURE_SUBSCRIPTION_ID=<same as GitHub secret>
AZURE_TENANT_ID=<same>
AZURE_CLIENT_ID=<same>
AZURE_CLIENT_SECRET=<same>
```

---

## 11) Tear it all down

GitHub → **Actions → Terraform → Run workflow → action: `destroy`**.

That removes the app RG (`pfe-devsecops-rg`). The state RG
(`tfstate-rg`) stays — delete it manually if you also want to wipe the
state backend:

```bash
az group delete -n tfstate-rg --yes
```

---

## Troubleshooting cheat-sheet

| Error | Cause | Fix |
|---|---|---|
| `SkuNotAvailable: Standard_Bxxx / Dxxx_v4` | Region/quota | Stay on `swedencentral` + `Standard_D2s_v3`. |
| `OperationNotAllowed: standardDSv5Family quota = 0` | Family quota 0 on student sub | Use `Dsv3` family (quota = 4 vCPUs), not v4/v5. |
| `Resource '<appId>' does not exist` on `az role assignment` | Old SP was deleted | Recreate via step 4. |
| `Failed to get existing workspaces … storage account not found` | `TF_BACKEND_SA` secret points to a deleted SA | Update the secret to the new `$SA`. |
| `You do not have the required permissions… Storage Blob Data *` | Your user lacks RBAC on the SA | Run the user-RBAC snippet in step 6. |
| `Provider produced inconsistent result after apply` | Transient azurerm bug | Re-run the workflow; if it persists, bump azurerm version in `providers.tf`. |
| `InvalidResourceGroupLocation … already exists in location X` | RG exists in another region | `az group delete -n <rg> --yes` then recreate. |

---

## TL;DR — fastest restart

1. Cloud Shell → wipe RGs (step 1).
2. Cloud Shell → create state SA (step 3) + SP (step 4) + RBAC (step 5) + container (step 6).
3. GitHub → update the 9 secrets (step 7).
4. `git push` → workflow applies. Done.
