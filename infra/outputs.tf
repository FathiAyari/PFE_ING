output "resource_group" {
  value = azurerm_resource_group.rg.name
}

output "acr_name" {
  value = azurerm_container_registry.acr.name
}

output "acr_login_server" {
  value = azurerm_container_registry.acr.login_server
}

output "aci_name" {
  value = azurerm_container_group.app.name
}

output "aci_public_ip" {
  value = azurerm_container_group.app.ip_address
}

output "aci_fqdn" {
  value = azurerm_container_group.app.fqdn
}

output "aci_location" {
  value = azurerm_container_group.app.location
}
