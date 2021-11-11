data "aws_route53_zone" "tdr_dns_zone" {
  name = "tdr-sandbox.nationalarchives.gov.uk"
}

data "aws_ssm_parameter" "management_account" {
  name = "/mgmt/management_account"
}

data "aws_caller_identity" "current" {}

resource "random_password" "password" {
  length  = 16
  special = false
}

resource "random_password" "keycloak_password" {
  length  = 16
  special = false
}

resource "random_uuid" "client_secret" {}
resource "random_uuid" "backend_checks_client_secret" {}
resource "random_uuid" "reporting_client_secret" {}
