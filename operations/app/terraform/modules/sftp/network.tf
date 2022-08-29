resource "azurerm_network_profile" "sftp" {
  name                = "sftp"
  location            = var.location
  resource_group_name = var.resource_group

  container_network_interface {
    name = "sftp"
    ip_configuration {
      name      = "sftp"
      subnet_id = data.azurerm_subnet.container_subnet.id
    }
  }
}

data "azurerm_private_dns_zone" "sftpfileshare" {
  name                = "privatelink.file.core.windows.net"
  resource_group_name = var.resource_group
}

# Private endpoint to file share storage
resource "azurerm_private_endpoint" "sftpfileshare" {
  name                = "${var.resource_prefix}-sftpfile-endpoint"
  location            = var.location
  resource_group_name = var.resource_group
  subnet_id           = data.azurerm_subnet.endpoint_subnet.id

  private_service_connection {
    name                           = "${var.resource_prefix}-sftpfileshare"
    private_connection_resource_id = azurerm_storage_account.sftp_v2.id
    is_manual_connection           = false
    subresource_names              = ["file"]
  }

  private_dns_zone_group {
    name                 = "${var.resource_prefix}-privatelink-sftpfileshare"
    private_dns_zone_ids = [data.azurerm_private_dns_zone.sftpfileshare.id]
  }
}
