locals {
  common_tags = tomap(
    {
      "Environment"     = "sandbox",
      "Owner"           = "TDR",
      "Terraform"       = true,
      "TerraformSource" = "https://github.com/nationalarchives/tdr-backend-checks-performance"
    }
  )

  environment = "sbox"

  environment_domain = "${var.project}-${local.environment_full_name}.${var.domain}"

  environment_full_name = "sandbox"

  dns_zone_id = data.aws_route53_zone.tdr_dns_zone.zone_id

  dns_zone_name_trimmed = trimsuffix(data.aws_route53_zone.tdr_dns_zone.name, ".")

  database_availability_zones = ["eu-west-2a", "eu-west-2b"]

  region = "eu-west-2"

  file_check_lambda_timeouts_in_seconds = {
    "antivirus"      = 180,
    "api_update"     = 20,
    "checksum"       = 180,
    "download_files" = 180,
    "file_format"    = 900
  }
  auth_url = "https://auth.tdr-sandbox.nationalarchives.gov.uk/auth"

  keycloak_backend_checks_secret_name     = "/${local.environment}/keycloak/backend_checks_client/secret"
  keycloak_tdr_client_secret_name         = "/${local.environment}/keycloak/client/secret"
  keycloak_user_password_name             = "/${local.environment}/keycloak/password"
  keycloak_admin_password_name            = "/${local.environment}/keycloak/admin/password"
  keycloak_admin_user_name                = "/${local.environment}/keycloak/admin/user"
  keycloak_realm_admin_client_secret_name = "/${local.environment}/keycloak/realm_admin_client/secret"
  keycloak_configuration_properties_name  = "/${local.environment}/keycloak/configuration_properties"
  keycloak_user_admin_client_secret_name  = "/${local.environment}/keycloak/user_admin_client/secret"
  keycloak_govuk_notify_api_key_name      = "/${local.environment}/keycloak/govuk_notify/api_key"
  keycloak_govuk_notify_template_id_name  = "/${local.environment}/keycloak/govuk_notify/template_id"
  keycloak_reporting_client_secret_name   = "/${local.environment}/keycloak/reporting_client/secret"
}
