# Clean-slate setup — PFE DevSecOps infra

This is the only doc you need. It assumes Azure is **empty** (you wiped it).

Region default: **Sweden Central** — your student subscription has quota
there for `Standard_D2s_v3` (verified manually). France Central is blocked
by SKU/quota.

---

## 0) Prereqs

In **Azure Cloud Shell** (bash):

```bash
az account show --query id -o tsv         # confirm sub
# 282c7f52-2d8d-4976-a288-e32a57bd16be
```

If you don't already have an SSH key:

```bash
[ -f ~/.ssh/id_rsa.pub ] || ssh-keygen -t rsa -b 4096 -N "" -f ~/.ssh/id_rsa
cat ~/.ssh/id_rsa.pub
```

Copy that public key — you'll paste it into a GitHub secret later.

---

## 1) Create the Service Principal (used by GitHub Actions)

```bash
SUB=$(az account show --query id -o tsv)
az ad sp create-for-rbac \
  --name "pfe-monitor-sp" \
  --role "Contributor" \
  --scopes "/subscriptions/$SUB"
```

Save these 4 values from the output:

| Output field | GitHub secret           |
| ------------ | ----------------------- |
| `appId`      | `AZURE_CLIENT_ID`       |
| `password`   | `AZURE_CLIENT_SECRET`   |
| `tenant`     | `AZURE_TENANT_ID`       |
| (sub id)     | `AZURE_SUBSCRIPTION_ID` |

---

## 2) Create the Terraform state backend

```bash
LOC=swedencentral
RG=tfstate-rg
SA=pfetfstate$RANDOM
CONT=tfstate

az group create -n "$RG" -l "$LOC" -o none
az storage account create -n "$SA" -g "$RG" -l "$LOC" \
  --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 -o none
az storage container create -n "$CONT" --account-name "$SA" --auth-mode login -o none

echo
echo "=== GitHub secrets for the state backend ==="
echo "TF_BACKEND_RG=$RG"
echo "TF_BACKEND_SA=$SA"
echo "TF_BACKEND_CONTAINER=$CONT"
```

> No extra RBAC role needed. The workflow reads the storage account key
> via the SP's **Contributor** rights and exports it as `ARM_ACCESS_KEY`.

---

## 3) Add GitHub secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret                  | Value                                  |
| ----------------------- | -------------------------------------- |
| `AZURE_CLIENT_ID`       | from step 1                            |
| `AZURE_CLIENT_SECRET`   | from step 1                            |
| `AZURE_TENANT_ID`       | from step 1                            |
| `AZURE_SUBSCRIPTION_ID` | `282c7f52-2d8d-4976-a288-e32a57bd16be` |
| `TF_BACKEND_RG`         | from step 2                            |
| `TF_BACKEND_SA`         | from step 2                            |
| `TF_BACKEND_CONTAINER`  | `tfstate`                              |
| `TF_SSH_PUBLIC_KEY`     | contents of `~/.ssh/id_rsa.pub`        |

---

## 4) Trigger the deployment

Push any change under `infra/` to `main`, **or** run manually:
GitHub → **Actions → Terraform — Azure infra → Run workflow → apply**.

What it builds:

- Resource group `pfe-devsecops-rg` (Sweden Central)
- VNet + subnet + NSG (ports 22, 80, 8080)
- Public IP (Standard, zonal)
- Linux VM `pfe-devsecops-vm` — `Standard_D2s_v3`, Ubuntu 22.04, zone 1
- Azure Container Registry (Basic, admin enabled)
- cloud-init installs Docker on first boot

Outputs (in the workflow summary): `vm_public_ip`, `acr_login_server`, etc.

---

## 5) Destroy when finished

GitHub → **Actions → Terraform — Azure infra → Run workflow → destroy**.

Full nuke from Cloud Shell:

```bash
az group delete -n pfe-devsecops-rg --yes --no-wait
az group delete -n tfstate-rg       --yes --no-wait
az ad sp delete  --id $(az ad sp list --display-name pfe-monitor-sp --query "[0].appId" -o tsv)
```
