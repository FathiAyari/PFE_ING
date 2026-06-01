#!/usr/bin/env bash
# Run this in Azure Cloud Shell (bash). It will:
#   1) Delete every resource group in your subscription (full wipe)
#   2) Recreate the Terraform state storage account in Sweden Central
#   3) Grant the GitHub Actions service principal "Storage Blob Data Contributor"
#      so the workflow can read/write the tfstate via Azure AD auth.
#   4) Print the GitHub secrets you must update.
#
# Prereqs (set these to YOUR values once):
SP_APP_ID="24fa570e-2f88-4575-b395-65c8a7c1ca42"   # AZURE_CLIENT_ID of pfe-monitor-sp
LOCATION="swedencentral"                            # confirmed working region
TF_RG="tfstate-rg"
CONT="tfstate"

set -euo pipefail

SUB_ID=$(az account show --query id -o tsv)
echo "Subscription: $SUB_ID"

# -------- 1) Wipe ALL resource groups --------
echo ">>> Deleting ALL resource groups in subscription (async)..."
for rg in $(az group list --query "[].name" -o tsv); do
  echo "  - deleting $rg"
  az group delete -n "$rg" --yes --no-wait || true
done

echo ">>> Waiting for deletions to finish..."
while [ "$(az group list --query 'length(@)' -o tsv)" != "0" ]; do
  echo "  still $(az group list --query 'length(@)' -o tsv) RGs left..."
  sleep 20
done
echo "All resource groups deleted."

# -------- 2) Recreate state backend --------
SA="pfetfstate$RANDOM"
echo ">>> Creating $TF_RG / $SA in $LOCATION ..."
az group create -n "$TF_RG" -l "$LOCATION" -o none
az storage account create -n "$SA" -g "$TF_RG" -l "$LOCATION" \
  --sku Standard_LRS --kind StorageV2 \
  --allow-blob-public-access false -o none

# -------- 3) RBAC for the SP (Azure AD auth on the blob) --------
SA_ID=$(az storage account show -n "$SA" -g "$TF_RG" --query id -o tsv)
SP_OBJECT_ID=$(az ad sp show --id "$SP_APP_ID" --query id -o tsv)

echo ">>> Granting Storage Blob Data Contributor to SP on $SA ..."
az role assignment create \
  --assignee-object-id "$SP_OBJECT_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Storage Blob Data Contributor" \
  --scope "$SA_ID" -o none

# Also give the SP Contributor at subscription scope so it can create RG/VM/ACR etc.
echo ">>> Granting Contributor to SP at subscription scope ..."
az role assignment create \
  --assignee-object-id "$SP_OBJECT_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Contributor" \
  --scope "/subscriptions/$SUB_ID" -o none || true

# Create the blob container (using AAD)
az storage container create -n "$CONT" --account-name "$SA" --auth-mode login -o none

cat <<EOF

=========================================================
 UPDATE these GitHub repo secrets (Settings > Secrets):
=========================================================
TF_BACKEND_RG=$TF_RG
TF_BACKEND_SA=$SA
TF_BACKEND_CONTAINER=$CONT
AZURE_LOCATION=$LOCATION
=========================================================
EOF
