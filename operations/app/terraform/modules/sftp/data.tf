# Fetch names of related SSH keys
# SSH key names determine SFTP instances and users
# terraform -chdir=operations/app/terraform/vars/<env> apply -target=module.sftp.data.external.sftp_ssh_query
data "external" "sftp_ssh_query" {
  program = ["bash", "${local.sftp_dir}/get_ssh_list.sh"]

  query = {
    environment = "${var.environment}"
  }

  depends_on = [
    azurerm_storage_share.sftp_scripts
  ]
}

data "azurerm_subnet" "container_subnet" {
  name                 = "container"
  virtual_network_name = "${var.resource_prefix}-East-vnet"
  resource_group_name  = var.resource_group
}

data "azurerm_virtual_network" "sftp_vnet" {
  name                = "${var.resource_prefix}-East-vnet"
  resource_group_name = var.resource_group
}

data "azurerm_subnet" "endpoint_subnet" {
  name                 = "endpoint"
  virtual_network_name = "${var.resource_prefix}-East-vnet"
  resource_group_name  = var.resource_group
}
