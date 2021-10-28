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
}
