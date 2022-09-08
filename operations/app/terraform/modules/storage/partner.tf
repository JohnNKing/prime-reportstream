resource "azurerm_storage_container" "storage_container_hhsprotect" {
  name                 = "hhsprotect"
  storage_account_name = azurerm_storage_account.storage_partner.name
}

resource "azurerm_storage_container" "storage_container_dcipher" {
  name                 = "dcipher"
  storage_account_name = azurerm_storage_account.storage_partner.name
  queue_properties {
    logging {
      read = true
    }
  }
}




# queue_properties  {
#     logging {
#         delete                = true
#         read                  = true
#         write                 = true
#         version               = "1.0"
#         retention_policy_days = 10
#     }
#     hour_metrics {
#         enabled               = true
#         include_apis          = true
#         version               = "1.0"
#         retention_policy_days = 10
#     }
#     minute_metrics {
#         enabled               = true
#         include_apis          = true
#         version               = "1.0"
#         retention_policy_days = 10
#     }
#   }