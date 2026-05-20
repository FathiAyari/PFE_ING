terraform {
  required_version = ">= 1.6.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state in Azure Storage. Values are supplied at `init` time:
  #   terraform init \
  #     -backend-config="resource_group_name=..." \
  #     -backend-config="storage_account_name=..." \
  #     -backend-config="container_name=..." \
  #     -backend-config="key=pfe-devsecops.tfstate"
  # Local runs without these args fall back to a `terraform.tfstate` file
  # in the working dir (which is gitignored).
  backend "azurerm" {
    use_azuread_auth = true
  }
}

provider "azurerm" {
  features {}
  resource_provider_registrations = "none"
}
