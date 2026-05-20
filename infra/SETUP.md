# Clean-slate setup — PFE DevSecOps infra

This document explains, from a fresh start, how to provision the project's Azure infrastructure using Terraform and how to wire it into GitHub Actions so CI can apply/destroy the environment.

High-level summary
- This repo contains Terraform under `infra/` that creates a resource group, a VM (with cloud-init to install Docker), an Azure Container Registry, networking (VNet/subnet/NSG) and a storage account/container for Terraform state.
- You can run Terraform locally (recommended for first-time testing) or let the GitHub Actions workflow run `apply`.
- The Terraform state backend is an Azure Storage container; we create that first.

Assumptions and conventions
- Default Azure region used in this guide: Sweden Central (`swedencentral`). Change as needed.
- The GitHub Actions workflow expects these repository secrets (names below) — we create them in step 3.
- Commands below assume you use Azure Cloud Shell (bash) or any environment with the `az` CLI and `terraform` installed. Where PowerShell is used locally, adapt commands accordingly.

Checklist (quick)
1. Create an SSH key if you don't have one
2. Create an Azure Service Principal (GitHub Actions identity)
3. Create storage account + container for Terraform state (backend)
4. Add GitHub repository secrets
5. (Optional) Initialize & apply Terraform locally to test
6. Trigger the GitHub Actions workflow (Terraform - Azure infra → apply)


---

## 0) Prereqs

- Install the Azure CLI and login (`az login`) or use Azure Cloud Shell.
- Install Terraform locally if you plan to run Terraform commands locally.
- Have `git` and a GitHub repo with this code.

If you don't already have an SSH key pair (you'll paste the public key into a GitHub secret):

```bash
[ -f ~/.ssh/id_rsa.pub ] || ssh-keygen -t rsa -b 4096 -N "" -f ~/.ssh/id_rsa
cat ~/.ssh/id_rsa.pub
```

Copy the printed public key (contents of `~/.ssh/id_rsa.pub`).


## 1) Create the Service Principal (used by GitHub Actions)

This Service Principal will be stored as GitHub repository secrets and used by the workflow to authenticate against your Azure subscription.

Run (in Cloud Shell or a shell where you're logged in as the subscription admin):

```bash
SUB=$(az account show --query id -o tsv)
az ad sp create-for-rbac \
  --name "pfe-monitor-sp" \
  --role "Contributor" \
  --scopes "/subscriptions/$SUB" \
  -o json
```

Save these four values from the JSON output and add them as GitHub repository secrets (see step 3):
- appId -> AZURE_CLIENT_ID
- password -> AZURE_CLIENT_SECRET
- tenant -> AZURE_TENANT_ID
- subscription id (from `az account show`) -> AZURE_SUBSCRIPTION_ID

Notes:
- Granting the `Contributor` role to the subscription is broad but convenient for student/demo scenarios; in production you should scope this to only required resource groups.


## 2) Create the Terraform state backend (Azure Storage)

We store Terraform state in an Azure Storage container so multiple runs (and GitHub Actions) share the same state.

Change these variables if you want a different location or names:

```bash
LOC=swedencentral
RG=tfstate-rg
SA=pfetfstate$RANDOM
CONT=tfstate
```

Commands to create the resource group, storage account and container:

```bash
az group create -n "$RG" -l "$LOC"
az storage account create -n "$SA" -g "$RG" -l "$LOC" \
  --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2
az storage container create -n "$CONT" --account-name "$SA" --auth-mode login
```

After the storage container exists, the workflow will read the storage account key using the Service Principal and export it as `ARM_ACCESS_KEY` when needed. You will want these values as repository secrets too (see step 3).

Print the values to save them:

```bash
echo "TF_BACKEND_RG=$RG"
echo "TF_BACKEND_SA=$SA"
echo "TF_BACKEND_CONTAINER=$CONT"
```


## 3) Add required GitHub repository secrets

Open your repo on GitHub → Settings → Secrets and variables → Actions → New repository secret.

Add these secrets (names are important because the Actions workflow expects them):

- AZURE_CLIENT_ID — Service Principal `appId` from step 1
- AZURE_CLIENT_SECRET — Service Principal `password` from step 1
- AZURE_TENANT_ID — Service Principal `tenant` from step 1
- AZURE_SUBSCRIPTION_ID — subscription id (from `az account show`)
- TF_BACKEND_RG — resource group name where the state storage account lives (from step 2)
- TF_BACKEND_SA — storage account name (from step 2)
- TF_BACKEND_CONTAINER — `tfstate` (or whatever container you created)
- TF_SSH_PUBLIC_KEY — contents of your `~/.ssh/id_rsa.pub`

Optional local-only secrets (not required by GitHub Actions if you run locally):
- For local `terraform` runs you can set environment variables instead of entering credentials interactively:
  - ARM_CLIENT_ID, ARM_CLIENT_SECRET, ARM_TENANT_ID, ARM_SUBSCRIPTION_ID
  - Or run `az login` and `az account set --subscription <SUB_ID>` to authenticate the Azure CLI-backed provider.


## 4) (Optional but recommended) Initialize and test Terraform locally

Before you rely on the GitHub Actions workflow, validate that Terraform runs locally and your backend is reachable.

Change directory into the infra folder and run `terraform init` with backend config values — this instructs Terraform to use the Azure storage account and container you created:

```bash
# Bash / Cloud Shell example (recommended for Linux/macOS/Cloud Shell)
cd infra
terraform init \
  -backend-config="resource_group_name=$TF_BACKEND_RG" \
  -backend-config="storage_account_name=$TF_BACKEND_SA" \
  -backend-config="container_name=$TF_BACKEND_CONTAINER" \
  -backend-config="key=terraform.tfstate"
```

```powershell
# PowerShell example (Windows) - this sets TF var for the SSH public key so terraform won't prompt
cd infra
$env:TF_BACKEND_RG = 'tfstate-rg'
$env:TF_BACKEND_SA = 'pfetfstate21474'
$env:TF_BACKEND_CONTAINER = 'tfstate'
# Set your ARM_* env vars (Service Principal) or run `az login` first
$env:ARM_CLIENT_ID = '<AZURE_CLIENT_ID>'
$env:ARM_CLIENT_SECRET = '<AZURE_CLIENT_SECRET>'
$env:ARM_TENANT_ID = '<AZURE_TENANT_ID>'
$env:ARM_SUBSCRIPTION_ID = '<AZURE_SUBSCRIPTION_ID>'
# Prevent interactive prompt for the ssh public key variable
$env:TF_VAR_ssh_public_key = '<CONTENTS_OF_YOUR_ID_RSA.PUB>'

# Option A: use Azure AD auth (you have an interactive az login or a SP with proper RBAC):
terraform init `
  -backend-config="resource_group_name=$env:TF_BACKEND_RG" `
  -backend-config="storage_account_name=$env:TF_BACKEND_SA" `
  -backend-config="container_name=$env:TF_BACKEND_CONTAINER" `
  -backend-config="key=terraform.tfstate" `
  -backend-config="use_azuread_auth=true"

# Option B: pass the storage account access key (non-interactive / CI-friendly)
# Fetch the key with az and pass it into init (example uses the Azure CLI to fetch the key):
$key = az storage account keys list -g $env:TF_BACKEND_RG -n $env:TF_BACKEND_SA --query "[0].value" -o tsv
terraform init `
  -backend-config="resource_group_name=$env:TF_BACKEND_RG" `
  -backend-config="storage_account_name=$env:TF_BACKEND_SA" `
  -backend-config="container_name=$env:TF_BACKEND_CONTAINER" `
  -backend-config="key=terraform.tfstate" `
  -backend-config="access_key=$key"
```

Notes about backend changes and non-interactive runs
- If Terraform reports "Backend configuration changed" during init, run `terraform init -reconfigure` (or include `-reconfigure` above) to accept the new backend settings or to migrate state.
- If Terraform prompts for any variables, set them via environment variables (TF_VAR_<name>) or a `terraform.tfvars` file. E.g. `TF_VAR_ssh_public_key` avoids an interactive prompt for the SSH key.

Plan and apply locally (safe test)

```bash
# create a plan file (read-only) and inspect it locally
cd infra
terraform plan -out=plan.tfplan
terraform show plan.tfplan
```

```powershell
# PowerShell: non-interactive plan (uses TF_VAR_ssh_public_key set above)
cd infra
terraform plan -input=false -out=plan.tfplan
terraform show -no-color plan.tfplan
```

If you want me to run these commands for you here, I can — but note that running them will use the credentials you provide and may create resources when you later run `terraform apply`. I ran an init/plan attempt earlier in this workspace and saw the init detect a backend change; the fix is `-reconfigure` or supplying the storage account access key as shown above.

## 5) Use GitHub Actions to deploy (recommended for team/CI)

This repo contains a preconfigured Actions workflow named `Terraform — Azure infra`. To run it:
- Push any change under `infra/` to the `main` branch (this triggers the workflow).
- Or in GitHub: Actions → select `Terraform — Azure infra` → Run workflow → choose `apply` or `destroy` as needed.

The workflow expects the repository secrets added in step 3 and will:
- Initialize Terraform using the backend secrets and `az` creds from the SP
- Run `terraform plan` and `terraform apply` (or `destroy`)
- Output values such as `vm_public_ip` and `acr_login_server` in the workflow summary


## 6) Verify your resources

- VM public IP (output `vm_public_ip`) — connect via SSH using the private key you generated earlier.
  Example:
  ```bash
  ssh -i ~/.ssh/id_rsa ubuntu@<vm_public_ip>
  ```
  The cloud-init in `infra/cloud-init.yaml` installs Docker on first boot.

- ACR (output `acr_login_server`) — you can `docker login` using the `az acr` commands or push images from your CI/CD pipelines.


## 7) Destroy / Tear down

From GitHub Actions: run the `Terraform — Azure infra` workflow and choose `destroy`.

From Cloud Shell (full cleanup example from the top-level):

```bash
az group delete -n pfe-devsecops-rg --yes --no-wait
az group delete -n tfstate-rg       --yes --no-wait
az ad sp delete  --id $(az ad sp list --display-name pfe-monitor-sp --query "[0].appId" -o tsv)
```


## Troubleshooting

- Authentication errors when running `terraform init` locally:
  - Ensure `az login` is successful and you set the correct subscription with `az account set --subscription <SUB_ID>`.
  - Or export the service principal environment variables locally:
    ```bash
    export ARM_CLIENT_ID="$AZURE_CLIENT_ID"
    export ARM_CLIENT_SECRET="$AZURE_CLIENT_SECRET"
    export ARM_TENANT_ID="$AZURE_TENANT_ID"
    export ARM_SUBSCRIPTION_ID="$AZURE_SUBSCRIPTION_ID"
    ```

- Storage account/container not found during backend init:
  - Double-check `TF_BACKEND_SA`, `TF_BACKEND_RG` and `TF_BACKEND_CONTAINER` values and that the SP has access to read the storage keys.

- GitHub Actions failing with permission denied while reading storage key:
  - Confirm the Service Principal has the `Contributor` role on the subscription (or at least permission to list/get keys on that storage account).

- SSH fails to connect to the VM:
  - Confirm NSG allows port 22 and that the VM's cloud-init finished. Check the VM's boot diagnostics in the Azure portal if needed.


## Notes and best-practices

- This repo uses a storage backend for Terraform state; never store state locally for shared environments.
- For real projects, scope the SP to a resource group, and use finer-grained RBAC.
- Consider enabling ACR's admin user off and using managed identities/Service Principals for CI pushes.


## Additional references
- Azure CLI: https://docs.microsoft.com/cli/azure
- Terraform on Azure: https://learn.hashicorp.com/tutorials/terraform/azure-build


If you'd like, I can also:
- Run a local `terraform init -backend-config=...` (dry run) in `infra/` to show the exact local output (I won't apply anything without your explicit go-ahead).
- Update the GitHub Actions workflow to be more restrictive/safe (e.g. require approvals before apply).


---

Last updated: automated edit
