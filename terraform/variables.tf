variable "project" {
  default = "tdr"
}

variable "domain" {
  description = "domain, e.g. example.com"
  default     = "nationalarchives.gov.uk"
}

variable "tdr_account_number" {
  description = "The AWS account number where the TDR environment is hosted"
  type        = string
}