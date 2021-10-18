data "aws_route53_zone" "tdr_dns_zone" {
  name = "tdr-sandbox.nationalarchives.gov.uk"
}

data "aws_ssm_parameter" "management_account" {
  name = "/mgmt/management_account"
}

data "aws_caller_identity" "current" {}