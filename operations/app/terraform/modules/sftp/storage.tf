# Storage account to host file shares
resource "azurerm_storage_account" "sftp" {
  name                     = "${var.resource_prefix}sftp"
  resource_group_name      = var.resource_group
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "GRS"
  min_tls_version          = "TLS1_2"

  network_rules {
    default_action = "Allow"
    bypass         = ["AzureServices"]

    ip_rules = var.terraform_caller_ip_address
    #virtual_network_subnet_ids = var.subnets.primary_subnets
  }

  tags = {
    environment = var.environment
  }
}

# SSH host keys share
resource "azurerm_storage_share" "sftp_admin" {
  name                 = "${var.resource_prefix}-sftp-admin-share"
  storage_account_name = azurerm_storage_account.sftp.name
  quota                = 1
}

# SFTP startup scripts share
resource "azurerm_storage_share" "sftp_scripts" {
  name                 = "${var.resource_prefix}-sftp-scripts-share"
  storage_account_name = azurerm_storage_account.sftp.name
  quota                = 1
}

# SFTP startup script
resource "azurerm_storage_share_file" "sftp" {
  name             = "startup.sh"
  storage_share_id = azurerm_storage_share.sftp_scripts.id
  source           = "${local.sftp_dir}/startup.sh"
}

# Storage account to host file shares
resource "azurerm_storage_account" "sftp_v2" {
  name                      = "${var.resource_prefix}sftpv2"
  resource_group_name       = var.resource_group
  location                  = var.location
  account_tier              = "Standard"
  account_replication_type  = "GRS"
  min_tls_version           = "TLS1_2"
  enable_https_traffic_only = true
  allow_blob_public_access  = false

  network_rules {
    default_action = "Deny"
    #bypass         = ["AzureServices"]

    ip_rules = var.terraform_caller_ip_address
    #virtual_network_subnet_ids = [var.sftp_subnet.id]
  }

  tags = {
    environment = var.environment
  }
}

# SSH host keys share
resource "azurerm_storage_share" "sftp_admin_v2" {
  name                 = "${var.resource_prefix}-sftp-admin-v2-share"
  storage_account_name = azurerm_storage_account.sftp_v2.name
  quota                = 1
}

# SFTP startup scripts share
resource "azurerm_storage_share" "sftp_scripts_v2" {
  name                 = "${var.resource_prefix}-sftp-scripts-v2-share"
  storage_account_name = azurerm_storage_account.sftp_v2.name
  quota                = 1
}

# SFTP startup script
resource "azurerm_storage_share_file" "sftp_v2" {
  name             = "startup.sh"
  storage_share_id = azurerm_storage_share.sftp_scripts_v2.id
  source           = "${local.sftp_dir}/startup.sh"
}
