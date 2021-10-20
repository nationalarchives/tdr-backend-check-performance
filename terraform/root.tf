provider "aws" {
  region = "eu-west-2"
  assume_role {
    role_arn     = "arn:aws:iam::${var.tdr_account_number}:role/TDRPerformanceChecksRole"
    session_name = "terraform"
  }
}

module "consignment_api" {
  source                         = "./tdr-terraform-environments/modules/consignment-api"
  dns_zone_id                    = data.aws_route53_zone.tdr_dns_zone.zone_id
  alb_dns_name                   = module.consignment_api_alb.alb_dns_name
  alb_target_group_arn           = module.consignment_api_alb.alb_target_group_arn
  alb_zone_id                    = module.consignment_api_alb.alb_zone_id
  app_name                       = "consignmentapi"
  common_tags                    = local.common_tags
  database_availability_zones    = local.database_availability_zones
  environment                    = local.environment
  environment_full_name          = local.environment_full_name
  private_subnets                = module.shared_vpc.private_subnets
  public_subnets                 = module.shared_vpc.public_subnets
  vpc_id                         = module.shared_vpc.vpc_id
  region                         = local.region
  db_migration_sg                = module.database_migrations.db_migration_security_group
  auth_url                       = module.keycloak.auth_url
  kms_key_id                     = module.encryption_key.kms_key_arn
  frontend_url                   = "https://tdr-sandbox.nationalarchives.gov.uk"
  dns_zone_name_trimmed          = local.dns_zone_name_trimmed
  create_users_security_group_id = flatten([module.create_db_users_lambda.create_users_lambda_security_group_id])
}

module "consignment_api_alb" {
  source                = "./tdr-terraform-modules/alb"
  project               = "tdr"
  function              = "consignmentapi"
  environment           = local.environment
  alb_log_bucket        = module.alb_logs_s3.s3_bucket_id
  alb_security_group_id = module.consignment_api.alb_security_group_id
  alb_target_group_port = 8080
  alb_target_type       = "ip"
  certificate_arn       = module.consignment_api_certificate.certificate_arn
  health_check_matcher  = "200,303"
  health_check_path     = "healthcheck"
  http_listener         = false
  public_subnets        = module.shared_vpc.public_subnets
  vpc_id                = module.shared_vpc.vpc_id
  common_tags           = local.common_tags
}

module "keycloak" {
  app_name                      = "keycloak"
  source                        = "./tdr-terraform-environments/modules/keycloak"
  alb_dns_name                  = module.keycloak_alb.alb_dns_name
  alb_target_group_arn          = module.keycloak_alb.alb_target_group_arn
  alb_zone_id                   = module.keycloak_alb.alb_zone_id
  dns_zone_id                   = local.dns_zone_id
  dns_zone_name_trimmed         = local.dns_zone_name_trimmed
  environment                   = local.environment
  environment_full_name         = local.environment_full_name
  common_tags                   = local.common_tags
  database_availability_zones   = local.database_availability_zones
  az_count                      = 2
  region                        = local.region
  frontend_url                  = ""
  kms_key_id                    = module.encryption_key.kms_key_arn
  create_user_security_group_id = module.create_keycloak_db_users_lambda.create_keycloak_user_lambda_security_group
  notification_sns_topic        = module.notifications_topic.sns_arn
  kms_key_arn                   = module.encryption_key.kms_key_arn
}

module "keycloak_alb" {
  source                = "./tdr-terraform-modules/alb"
  project               = var.project
  function              = "keycloak"
  environment           = local.environment
  alb_log_bucket        = module.alb_logs_s3.s3_bucket_id
  alb_security_group_id = module.keycloak.alb_security_group_id
  alb_target_group_port = 8080
  alb_target_type       = "ip"
  certificate_arn       = module.keycloak_certificate.certificate_arn
  health_check_matcher  = "200,303"
  health_check_path     = ""
  http_listener         = false
  public_subnets        = module.keycloak.public_subnets
  vpc_id                = module.keycloak.vpc_id
  common_tags           = local.common_tags
  own_host_header_only  = true
  host                  = module.keycloak.auth_host
}

module "keycloak_certificate" {
  source      = "./tdr-terraform-modules/certificatemanager"
  project     = var.project
  function    = "keycloak"
  dns_zone    = local.environment_domain
  domain_name = "auth.${local.environment_domain}"
  common_tags = local.common_tags
}

module "alb_logs_s3" {
  source        = "./tdr-terraform-modules/s3"
  project       = var.project
  function      = "alb-logs"
  access_logs   = false
  bucket_policy = "alb_logging_euwest2"
  common_tags   = local.common_tags
  kms_key_id    = 1
}

module "encryption_key" {
  source      = "./tdr-terraform-modules/kms"
  project     = var.project
  function    = "encryption"
  key_policy  = "message_system_access"
  environment = local.environment
  common_tags = local.common_tags
}

module "create_keycloak_db_users_lambda" {
  source                           = "./tdr-terraform-modules/lambda"
  project                          = var.project
  common_tags                      = local.common_tags
  lambda_create_keycloak_db_users  = true
  vpc_id                           = module.keycloak.vpc_id
  private_subnet_ids               = module.keycloak.private_subnets
  db_admin_user                    = module.keycloak.db_username
  db_admin_password                = module.keycloak.db_password
  db_url                           = module.keycloak.db_url
  kms_key_arn                      = module.encryption_key.kms_key_arn
  keycloak_password                = module.keycloak.keycloak_user_password
  keycloak_database_security_group = module.keycloak.database_security_group
}

module "notifications_topic" {
  source      = "./tdr-terraform-modules/sns"
  common_tags = local.common_tags
  function    = "notifications"
  project     = var.project
  sns_policy  = "notifications"
  kms_key_arn = module.encryption_key.kms_key_arn
}

module "shared_vpc" {
  source                      = "./tdr-terraform-environments/modules/shared-vpc"
  az_count                    = 2
  common_tags                 = local.common_tags
  environment                 = local.environment
  database_availability_zones = local.database_availability_zones
}

module "consignment_api_certificate" {
  source      = "./tdr-terraform-modules/certificatemanager"
  project     = var.project
  function    = "consignment-api"
  dns_zone    = local.environment_domain
  domain_name = "api.${local.environment_domain}"
  common_tags = local.common_tags
}

module "create_db_users_lambda" {
  source                      = "./tdr-terraform-modules/lambda"
  project                     = var.project
  common_tags                 = local.common_tags
  lambda_create_db_users      = true
  vpc_id                      = module.shared_vpc.vpc_id
  private_subnet_ids          = module.backend_checks_efs.private_subnets
  consignment_database_sg_id  = module.consignment_api.consignment_db_security_group_id
  db_admin_user               = module.consignment_api.database_username
  db_admin_password           = module.consignment_api.database_password
  db_url                      = module.consignment_api.database_url
  kms_key_arn                 = module.encryption_key.kms_key_arn
  api_database_security_group = module.consignment_api.database_security_group
  lambda_name                 = "create-db-users"
  database_name               = "consignmentapi"
}

module "database_migrations" {
  source          = "./tdr-terraform-environments/modules/database-migrations"
  environment     = local.environment
  vpc_id          = module.shared_vpc.vpc_id
  private_subnets = module.shared_vpc.private_subnets
  common_tags     = local.common_tags
  db_url          = module.consignment_api.database_url
  db_cluster_id   = module.consignment_api.database_cluster_id
}

module "backend_checks_efs" {
  source                       = "./tdr-terraform-modules/efs"
  common_tags                  = local.common_tags
  function                     = "backend-checks-efs"
  project                      = var.project
  access_point_path            = "/backend-checks"
  policy                       = "efs_access_no_bastion"
  policy_roles                 = jsonencode(flatten([module.file_format_build_task.file_format_build_role, module.checksum_lambda.checksum_lambda_role, module.antivirus_lambda.antivirus_lambda_role, module.download_files_lambda.download_files_lambda_role, module.file_format_lambda.file_format_lambda_role]))
  mount_target_security_groups = flatten([module.file_format_lambda.file_format_lambda_sg_id, module.download_files_lambda.download_files_lambda_sg_id, module.file_format_build_task.file_format_build_sg_id, module.antivirus_lambda.antivirus_lambda_sg_id, module.checksum_lambda.checksum_lambda_sg_id])
  nat_gateway_ids              = module.shared_vpc.nat_gateway_ids
  vpc_cidr_block               = module.shared_vpc.vpc_cidr_block
  vpc_id                       = module.shared_vpc.vpc_id
}

module "checksum_lambda" {
  source                                 = "./tdr-terraform-modules/lambda"
  project                                = var.project
  common_tags                            = local.common_tags
  lambda_checksum                        = true
  timeout_seconds                        = local.file_check_lambda_timeouts_in_seconds["checksum"]
  file_system_id                         = module.backend_checks_efs.file_system_id
  backend_checks_efs_access_point        = module.backend_checks_efs.access_point
  vpc_id                                 = module.shared_vpc.vpc_id
  use_efs                                = true
  backend_checks_efs_root_directory_path = module.backend_checks_efs.root_directory_path
  private_subnet_ids                     = module.backend_checks_efs.private_subnets
  mount_target_zero                      = module.backend_checks_efs.mount_target_zero
  mount_target_one                       = module.backend_checks_efs.mount_target_one
  kms_key_arn                            = module.encryption_key.kms_key_arn
  efs_security_group_id                  = module.backend_checks_efs.security_group_id
}


module "file_format_lambda" {
  source                                 = "./tdr-terraform-modules/lambda"
  project                                = var.project
  common_tags                            = local.common_tags
  lambda_file_format                     = true
  timeout_seconds                        = local.file_check_lambda_timeouts_in_seconds["file_format"]
  file_system_id                         = module.backend_checks_efs.file_system_id
  backend_checks_efs_access_point        = module.backend_checks_efs.access_point
  vpc_id                                 = module.shared_vpc.vpc_id
  use_efs                                = true
  backend_checks_efs_root_directory_path = module.backend_checks_efs.root_directory_path
  private_subnet_ids                     = module.backend_checks_efs.private_subnets
  mount_target_zero                      = module.backend_checks_efs.mount_target_zero
  mount_target_one                       = module.backend_checks_efs.mount_target_one
  kms_key_arn                            = module.encryption_key.kms_key_arn
  efs_security_group_id                  = module.backend_checks_efs.security_group_id
}

module "antivirus_lambda" {
  source                                 = "./tdr-terraform-modules/lambda"
  backend_checks_efs_access_point        = module.backend_checks_efs.access_point
  backend_checks_efs_root_directory_path = module.backend_checks_efs.root_directory_path
  common_tags                            = local.common_tags
  file_system_id                         = module.backend_checks_efs.file_system_id
  lambda_yara_av                         = true
  timeout_seconds                        = local.file_check_lambda_timeouts_in_seconds["antivirus"]
  project                                = var.project
  use_efs                                = true
  vpc_id                                 = module.shared_vpc.vpc_id
  private_subnet_ids                     = module.backend_checks_efs.private_subnets
  mount_target_zero                      = module.backend_checks_efs.mount_target_zero
  mount_target_one                       = module.backend_checks_efs.mount_target_one
  kms_key_arn                            = module.encryption_key.kms_key_arn
  efs_security_group_id                  = module.backend_checks_efs.security_group_id
}

module "download_files_lambda" {
  source                                 = "./tdr-terraform-modules/lambda"
  common_tags                            = local.common_tags
  project                                = var.project
  lambda_download_files                  = true
  timeout_seconds                        = local.file_check_lambda_timeouts_in_seconds["download_files"]
  s3_sns_topic                           = module.dirty_upload_sns_topic.sns_arn
  file_system_id                         = module.backend_checks_efs.file_system_id
  backend_checks_efs_access_point        = module.backend_checks_efs.access_point
  vpc_id                                 = module.shared_vpc.vpc_id
  use_efs                                = true
  auth_url                               = module.keycloak.auth_url
  api_url                                = module.consignment_api.api_url
  backend_checks_efs_root_directory_path = module.backend_checks_efs.root_directory_path
  private_subnet_ids                     = module.backend_checks_efs.private_subnets
  backend_checks_client_secret           = module.keycloak.backend_checks_client_secret
  kms_key_arn                            = module.encryption_key.kms_key_arn
  efs_security_group_id                  = module.backend_checks_efs.security_group_id
  reserved_concurrency                   = 3
}

module "dirty_upload_sns_topic" {
  source      = "./tdr-terraform-modules/sns"
  common_tags = local.common_tags
  project     = var.project
  function    = "s3-dirty-upload"
  sns_policy  = "s3_upload"
  kms_key_arn = module.encryption_key.kms_key_arn
}

module "upload_file_cloudfront_dirty_s3" {
  source                   = "./tdr-terraform-modules/s3"
  project                  = var.project
  function                 = "upload-files-cloudfront-dirty"
  common_tags              = local.common_tags
  sns_topic_arn            = module.dirty_upload_sns_topic.sns_arn
  sns_notification         = true
  abort_incomplete_uploads = true
}

module "upload_bucket" {
  source      = "./tdr-terraform-modules/s3"
  project     = var.project
  function    = "upload-files"
  common_tags = local.common_tags
}

module "file_format_build_task" {
  source            = "./tdr-terraform-modules/ecs"
  common_tags       = local.common_tags
  file_system_id    = module.backend_checks_efs.file_system_id
  access_point      = module.backend_checks_efs.access_point
  file_format_build = true
  project           = var.project
  vpc_id            = module.shared_vpc.vpc_id
}

module "backend_check_failure_sqs_queue" {
  source      = "./tdr-terraform-modules/sqs"
  common_tags = local.common_tags
  project     = var.project
  function    = "backend-check-failure"
  sqs_policy  = "failure_queue"
  kms_key_id  = module.encryption_key.kms_key_arn
}

module "antivirus_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "antivirus"
  sqs_policy               = "file_checks"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["antivirus"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
}

module "download_files_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "download-files"
  sns_topic_arns           = [module.dirty_upload_sns_topic.sns_arn]
  sqs_policy               = "sns_topic"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["download_files"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
}

module "checksum_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "checksum"
  sqs_policy               = "file_checks"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["checksum"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
}

module "file_format_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "file-format"
  sqs_policy               = "file_checks"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["file_format"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
}

module "api_update_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "api-update"
  sqs_policy               = "api_update_antivirus"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["api_update"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
}

module "api_update_lambda" {
  source                                = "./tdr-terraform-modules/lambda"
  project                               = var.project
  common_tags                           = local.common_tags
  lambda_api_update                     = true
  timeout_seconds                       = local.file_check_lambda_timeouts_in_seconds["api_update"]
  auth_url                              = module.keycloak.auth_url
  api_url                               = module.consignment_api.api_url
  keycloak_backend_checks_client_secret = module.keycloak.backend_checks_client_secret
  kms_key_arn                           = module.encryption_key.kms_key_arn
  private_subnet_ids                    = module.backend_checks_efs.private_subnets
  vpc_id                                = module.shared_vpc.vpc_id
}

module "ecr_consignment_api_repository" {
  source           = "./tdr-terraform-modules/ecr"
  name             = "consignment-api"
  image_source_url = "https://github.com/nationalarchives/tdr-consignment-api/blob/master/Dockerfile"
  policy_name      = "sandbox_performance_check_policy"
  policy_variables = { management_account = data.aws_ssm_parameter.management_account.value, sandbox_account=data.aws_caller_identity.current.account_id }
  common_tags      = local.common_tags
}

module "ecr_auth_server_repository" {
  source           = "./tdr-terraform-modules/ecr"
  name             = "auth-server"
  image_source_url = "https://github.com/nationalarchives/tdr-auth-server/blob/master/Dockerfile"
  policy_name      = "sandbox_performance_check_policy"
  policy_variables = { management_account = data.aws_ssm_parameter.management_account.value, sandbox_account=data.aws_caller_identity.current.account_id }
  common_tags      = local.common_tags
}

