# Step-by-step: Wire Azure + GitHub Actions

End goal: pushing to `main` (or clicking **Run workflow**) makes GitHub Actions
run `terraform apply` against your Azure subscription and create the VM + ACR
+ networking defined in `infra/`.

You'll do this **once** (≈ 10 minutes). Run the commands in **Azure Cloud Shell**
(easiest) or in a local PowerShell / bash after `az login`.

---

## Step 0 — Prerequisites

- An Azure subscription you can deploy to. Get its ID:
  ```bash
  az account show --query id -o tsv
  ```
  Copy that value — you'll need it as `<SUB_ID>`.

- An SSH public key (we'll inject it into the VM). Print yours:
  ```bash
  cat ~/.ssh/id_rsa.pub          # bash / Cloud Shell
  ```
  ```powershell
  Get-Content $env:USERPROFILE\.ssh\id_rsa.pub   # Windows PowerShell
  ```
  If you don't have one: `ssh-keygen -t rsa -b 4096 -N ""` (just press Enter).
  Copy the whole `ssh-rsa AAAA…` line — you'll need it as `<SSH_PUB>`.

---

## Step 1 — Create the service principal (Azure credentials)

This is the identity GitHub Actions uses to call Azure.

```bash
az ad sp create-for-rbac \
  --name "pfe-devsecops-gh" \
  --role Contributor \
  --scopes "/subscriptions/<SUB_ID>"
```

You get JSON like:

```json
{
  "appId":       "11111111-1111-1111-1111-111111111111",
  "displayName": "pfe-devsecops-gh",
  "password":    "redacted-secret-value",
  "tenant":      "22222222-2222-2222-2222-222222222222"
}
```

Map these to the **GitHub Secrets** we'll create:

| GitHub secret name        | Value from above       |
| ------------------------- | ---------------------- |
| `AZURE_CLIENT_ID`         | `appId`                |
| `AZURE_CLIENT_SECRET`     | `password`             |
| `AZURE_TENANT_ID`         | `tenant`               |
| `AZURE_SUBSCRIPTION_ID`   | `<SUB_ID>` from Step 0 |

> Keep this JSON open — secrets are shown **only once**.

---

## Step 2 — Create the remote Terraform state backend

Terraform must store its state somewhere all CI runs can share. We use an
Azure Storage account.

```bash
# pick any region; storage account name must be globally unique and lowercase
LOC=westeurope
RG=tfstate-rg
SA=pfetfstate$RANDOM      # e.g. pfetfstate12345
CONT=tfstate

az group create -n "$RG" -l "$LOC"
az storage account create -n "$SA" -g "$RG" -l "$LOC" --sku Standard_LRS --kind StorageV2
az storage container create -n "$CONT" --account-name "$SA"
```

Record three values for GitHub Secrets:

| GitHub secret name      | Value             |
| ----------------------- | ----------------- |
| `TF_BACKEND_RG`         | `$RG` (`tfstate-rg`)            |
| `TF_BACKEND_SA`         | `$SA` (your unique storage acct) |
| `TF_BACKEND_CONTAINER`  | `$CONT` (`tfstate`)              |

---

## Step 3 — Add the SSH public key as a secret

| GitHub secret name | Value |
| ------------------ | ----- |
| `TF_SSH_PUBLIC_KEY`| The full `ssh-rsa AAAA…` line from Step 0 |

---

## Step 4 — Put the secrets in GitHub

In the repo on GitHub:

**Settings → Secrets and variables → Actions → "New repository secret"**

Add all **8** secrets:

```
AZURE_CLIENT_ID          (Step 1)
AZURE_CLIENT_SECRET      (Step 1)
AZURE_TENANT_ID          (Step 1)
AZURE_SUBSCRIPTION_ID    (Step 1)
TF_BACKEND_RG            (Step 2)
TF_BACKEND_SA            (Step 2)
TF_BACKEND_CONTAINER     (Step 2)
TF_SSH_PUBLIC_KEY        (Step 3)
```

---

## Step 5 — Trigger the workflow

Two ways:

### A. Push a change to `infra/`

```powershell
git add infra .github/workflows/terraform.yml
git commit -m "Provision Azure infra via Terraform + GitHub Actions"
git push
```

The push hits `paths: ['infra/**']` and triggers a full `terraform apply`.

### B. Manually run from the Actions tab

GitHub → **Actions** → "Terraform — Azure infra" → **Run workflow** →
pick `plan`, `apply`, or `destroy`.

---

## Step 6 — Verify

After the run finishes:

1. Open the Actions run → look at the **job summary** → "Terraform outputs"
   block shows the `vm_public_ip`, `acr_login_server`, etc.
2. In Azure:
   ```bash
   az resource list -g pfe-devsecops-rg -o table
   ```
3. SSH into the new VM:
   ```bash
   ssh azureuser@<vm_public_ip>
   docker --version    # cloud-init installed Docker
   ```

---

## Step 7 — Cleanup (when you're done testing)

Either click **Run workflow → destroy** in the Actions tab, or locally:

```powershell
cd infra
terraform destroy -var "ssh_public_key=$(Get-Content $env:USERPROFILE\.ssh\id_rsa.pub -Raw)"
```

---

## What's running where

| Layer                       | Where                                                    |
| --------------------------- | -------------------------------------------------------- |
| Terraform code              | `infra/*.tf`                                             |
| Workflow                    | `.github/workflows/terraform.yml`                        |
| Terraform state             | Azure Storage container `$CONT` in `$SA`                 |
| Azure SP secrets            | GitHub repo secrets                                      |
| VM bootstrap                | `infra/cloud-init.yaml` (installs Docker)                |
| Web UI visualization        | `/infra` page — reads `infra/resources.json` via backend |
