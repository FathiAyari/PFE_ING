output "resource_group" {
  value = azurerm_resource_group.rg.name
}

output "vm_name" {
  value = azurerm_linux_virtual_machine.vm.name
}

output "vm_public_ip" {
  value = azurerm_public_ip.pip.ip_address
}

output "vm_size" {
  value = azurerm_linux_virtual_machine.vm.size
}

output "vm_location" {
  value = azurerm_linux_virtual_machine.vm.location
}

output "acr_name" {
  value = azurerm_container_registry.acr.name
}

output "acr_login_server" {
  value = azurerm_container_registry.acr.login_server
}
