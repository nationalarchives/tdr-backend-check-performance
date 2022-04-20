provider "aws" {
  region = "eu-west-2"
  assume_role {
    role_arn     = "arn:aws:iam::${var.tdr_account_number}:role/TDRTerraformRoleSbox"
    session_name = "terraform"
  }
}

provider "aws" {
  region = "us-east-1"
  alias  = "useast1"

  assume_role {
    role_arn     = "arn:aws:iam::${var.tdr_account_number}:role/TDRTerraformRoleSbox"
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
  auth_url                       = local.auth_url
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
  environment = local.environment
  common_tags = local.common_tags
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
  depends_on                  = [module.encryption_key]
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
  depends_on                   = [module.encryption_key]
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
  depends_on                             = [module.encryption_key, module.checksum_sqs_queue]
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
  depends_on                             = [module.encryption_key, module.file_format_sqs_queue]
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
  depends_on                             = [module.encryption_key, module.antivirus_sqs_queue]
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
  auth_url                               = local.auth_url
  api_url                                = module.consignment_api.api_url
  backend_checks_efs_root_directory_path = module.backend_checks_efs.root_directory_path
  private_subnet_ids                     = module.backend_checks_efs.private_subnets
  backend_checks_client_secret           = module.keycloak_ssm_parameters.params[local.keycloak_backend_checks_secret_name].value
  kms_key_arn                            = module.encryption_key.kms_key_arn
  efs_security_group_id                  = module.backend_checks_efs.security_group_id
  reserved_concurrency                   = 3
  depends_on                             = [module.encryption_key, module.download_files_sqs_queue]
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
  kms_key_id  = module.encryption_key.kms_key_arn
  depends_on  = [module.mock_transform_engine_parameter]
}

module "antivirus_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "antivirus"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["antivirus"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
  depends_on               = [module.mock_transform_engine_parameter]
}

module "download_files_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "download-files"
  sns_topic_arns           = [module.dirty_upload_sns_topic.sns_arn]
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["download_files"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
  depends_on               = [module.mock_transform_engine_parameter]
}

module "checksum_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "checksum"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["checksum"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
  depends_on               = [module.mock_transform_engine_parameter]
}

module "file_format_sqs_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "file-format"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["file_format"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
  depends_on               = [module.mock_transform_engine_parameter]
}

module "api_update_queue" {
  source                   = "./tdr-terraform-modules/sqs"
  common_tags              = local.common_tags
  project                  = var.project
  function                 = "api-update"
  dead_letter_queue        = module.backend_check_failure_sqs_queue.sqs_arn
  redrive_maximum_receives = 3
  visibility_timeout       = local.file_check_lambda_timeouts_in_seconds["api_update"] * 3
  kms_key_id               = module.encryption_key.kms_key_arn
  depends_on               = [module.mock_transform_engine_parameter]
}

module "api_update_lambda" {
  source                                = "./tdr-terraform-modules/lambda"
  project                               = var.project
  common_tags                           = local.common_tags
  lambda_api_update                     = true
  timeout_seconds                       = local.file_check_lambda_timeouts_in_seconds["api_update"]
  auth_url                              = local.auth_url
  api_url                               = module.consignment_api.api_url
  keycloak_backend_checks_client_secret = module.keycloak_ssm_parameters.params[local.keycloak_backend_checks_secret_name].value
  kms_key_arn                           = module.encryption_key.kms_key_arn
  private_subnet_ids                    = module.backend_checks_efs.private_subnets
  vpc_id                                = module.shared_vpc.vpc_id
  depends_on                            = [module.encryption_key, module.api_update_queue]
}

module "ecr_consignment_api_repository" {
  source           = "./tdr-terraform-modules/ecr"
  name             = "consignment-api"
  image_source_url = "https://github.com/nationalarchives/tdr-consignment-api/blob/master/Dockerfile"
  policy_name      = "sandbox_performance_check_policy"
  policy_variables = { management_account = data.aws_ssm_parameter.management_account.value, sandbox_account = data.aws_caller_identity.current.account_id }
  common_tags      = local.common_tags
}

module "ecr_auth_server_repository" {
  source           = "./tdr-terraform-modules/ecr"
  name             = "auth-server"
  image_source_url = "https://github.com/nationalarchives/tdr-auth-server/blob/master/Dockerfile"
  policy_name      = "sandbox_performance_check_policy"
  policy_variables = { management_account = data.aws_ssm_parameter.management_account.value, sandbox_account = data.aws_caller_identity.current.account_id }
  common_tags      = local.common_tags
}

module "ecr_file_format_build_repository" {
  source           = "./tdr-terraform-modules/ecr"
  name             = "file-format-build"
  image_source_url = "https://github.com/nationalarchives/tdr-auth-server/blob/master/Dockerfile"
  policy_name      = "sandbox_performance_check_policy"
  policy_variables = { management_account = data.aws_ssm_parameter.management_account.value, sandbox_account = data.aws_caller_identity.current.account_id }
  common_tags      = local.common_tags
}

module "keycloak_cloudwatch" {
  source      = "./tdr-terraform-modules/cloudwatch_logs"
  common_tags = local.common_tags
  name        = "/ecs/keycloak-auth-${local.environment}"
}

module "keycloak_ecs_execution_policy" {
  source        = "./tdr-terraform-modules/iam_policy"
  name          = "KeycloakECSExecutionPolicy${title(local.environment)}"
  policy_string = templatefile("./tdr-terraform-modules/iam_policy/templates/keycloak_ecs_execution_policy.json.tpl", { cloudwatch_log_group = module.keycloak_cloudwatch.log_group_arn, ecr_account_number = data.aws_caller_identity.current.account_id })
}

module "keycloak_ecs_task_policy" {
  source        = "./tdr-terraform-modules/iam_policy"
  name          = "KeycloakECSTaskPolicy${title(local.environment)}"
  policy_string = templatefile("./tdr-terraform-modules/iam_policy/templates/keycloak_ecs_task_role_policy.json.tpl", { account_id = data.aws_caller_identity.current.account_id, environment = local.environment, kms_arn = module.encryption_key.kms_key_arn })
}

module "keycloak_execution_role" {
  source             = "./tdr-terraform-modules/iam_role"
  assume_role_policy = templatefile("./tdr-terraform-modules/ecs/templates/ecs_assume_role_policy.json.tpl", {})
  common_tags        = local.common_tags
  name               = "KeycloakECSExecutionRole${title(local.environment)}"
  policy_attachments = {
    ssm_policy       = "arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess",
    execution_policy = module.keycloak_ecs_execution_policy.policy_arn
  }
}

module "keycloak_task_role" {
  source             = "./tdr-terraform-modules/iam_role"
  assume_role_policy = templatefile("./tdr-terraform-modules/ecs/templates/ecs_assume_role_policy.json.tpl", {})
  common_tags        = local.common_tags
  name               = "KeycloakECSTaskRole${title(local.environment)}"
  policy_attachments = { task_policy = module.keycloak_ecs_task_policy.policy_arn }
}

module "keycloak_ssm_parameters" {
  source      = "./tdr-terraform-modules/ssm_parameter"
  common_tags = local.common_tags
  random_parameters = [
    { name = local.keycloak_backend_checks_secret_name, description = "The Keycloak backend checks secret", value = random_uuid.backend_checks_client_secret.result, type = "SecureString" },
    { name = local.keycloak_tdr_client_secret_name, description = "The Keycloak tdr client secret", value = random_uuid.client_secret.result, type = "SecureString" },
    { name = local.keycloak_user_password_name, description = "The Keycloak user password", value = random_password.keycloak_password.result, type = "SecureString" },
    { name = local.keycloak_admin_password_name, description = "The Keycloak admin password", value = random_password.password.result, type = "SecureString" },
    { name = local.keycloak_govuk_notify_api_key_name, description = "The GovUK Notify API key", value = "to_be_manually_added", type = "SecureString" },
    { name = local.keycloak_govuk_notify_template_id_name, description = "The GovUK Notify Template ID", value = "to_be_manually_added", type = "SecureString" },
    { name = local.keycloak_admin_user_name, description = "The Keycloak admin user", value = "tdr-keycloak-admin-${local.environment}", type = "SecureString" },
    { name = local.keycloak_configuration_properties_name, description = "The Keycloak configuration properties file ", value = "${local.environment}_properties.json", type = "SecureString" },
    { name = local.keycloak_user_admin_client_secret_name, description = "The Keycloak user admin secret", value = random_uuid.backend_checks_client_secret.result, type = "SecureString" },
    { name = local.keycloak_reporting_client_secret_name, description = "The Keycloak reporting client secret", value = random_uuid.reporting_client_secret.result, type = "SecureString" },
    { name = local.keycloak_realm_admin_client_secret_name, description = "The Keycloak realm admin secret", value = random_uuid.backend_checks_client_secret.result, type = "SecureString" }
  ]
}

module "keycloak_ecs_security_group" {
  source      = "./tdr-terraform-modules/security_group"
  description = "Controls access within our network for the Keycloak ECS Task"
  name        = "tdr-keycloak-ecs-security-group"
  vpc_id      = module.shared_vpc.vpc_id
  common_tags = local.common_tags
  ingress_security_group_rules = [
    { port = 8080, security_group_id = module.keycloak_alb_security_group.security_group_id, description = "Allow the load balancer to access the task" }
  ]
  egress_cidr_rules = [{ port = 0, cidr_blocks = ["0.0.0.0/0"], description = "Allow outbound access on all ports", protocol = "-1" }]
}

module "keycloak_alb_security_group" {
  source      = "./tdr-terraform-modules/security_group"
  description = "Controls access to the keycloak load balancer"
  name        = "keycloak-load-balancer-security-group"
  vpc_id      = module.shared_vpc.vpc_id
  common_tags = local.common_tags
  ingress_cidr_rules = [
    { port = 443, cidr_blocks = ["0.0.0.0/0"], description = "Allow all IPs over HTTPS" }
  ]
  egress_cidr_rules = [{ port = 0, cidr_blocks = ["0.0.0.0/0"], description = "Allow outbound access on all ports", protocol = "-1" }]
}

module "keycloak_database_security_group" {
  source      = "./tdr-terraform-modules/security_group"
  description = "Controls access to the keycloak database"
  name        = "keycloak-database-security-group-${local.environment}"
  vpc_id      = module.shared_vpc.vpc_id
  common_tags = local.common_tags
  ingress_security_group_rules = [
    { port = 5432, security_group_id = module.keycloak_ecs_security_group.security_group_id, description = "Allow Postgres port from the ECS task" },
    { port = 5432, security_group_id = module.create_keycloak_db_users_lambda.create_keycloak_user_lambda_security_group[0], description = "Allow Postgres port from the create user lambda" }
  ]
  egress_security_group_rules = [{ port = 5432, security_group_id = module.keycloak_ecs_security_group.security_group_id, description = "Allow Postgres port from the ECS task", protocol = "-1" }]
}

module "tdr_keycloak" {
  source               = "./tdr-terraform-modules/generic_ecs"
  alb_target_group_arn = module.keycloak_tdr_alb.alb_target_group_arn
  cluster_name         = "keycloak_${local.environment}"
  common_tags          = local.common_tags
  container_definition = templatefile("./tdr-terraform-environments/templates/ecs_tasks/keycloak.json.tpl", {
    app_image                         = "${data.aws_caller_identity.current.account_id}.dkr.ecr.eu-west-2.amazonaws.com/auth-server:${local.environment}"
    app_port                          = 8080
    app_environment                   = local.environment
    aws_region                        = local.region
    url_path                          = module.keycloak_database.db_url_parameter_name
    username                          = "keycloak_user"
    password_path                     = local.keycloak_user_password_name
    admin_user_path                   = local.keycloak_admin_user_name
    admin_password_path               = local.keycloak_admin_password_name
    client_secret_path                = local.keycloak_tdr_client_secret_name
    backend_checks_client_secret_path = local.keycloak_backend_checks_secret_name
    realm_admin_client_secret_path    = local.keycloak_realm_admin_client_secret_name
    frontend_url                      = "https://tdr-sandbox.nationalarchives.gov.uk"
    configuration_properties_path     = local.keycloak_configuration_properties_name
    user_admin_client_secret_path     = local.keycloak_user_admin_client_secret_name
    govuk_notify_api_key_path         = local.keycloak_govuk_notify_api_key_name
    govuk_notify_template_id_path     = local.keycloak_govuk_notify_template_id_name
    reporting_client_secret_path      = local.keycloak_reporting_client_secret_name
    sns_topic_arn                     = module.notifications_topic.sns_arn
  })
  container_name               = "keycloak"
  cpu                          = 1024
  environment                  = local.environment
  execution_role               = module.keycloak_execution_role.role.arn
  load_balancer_container_port = 8080
  memory                       = 3072
  private_subnets              = module.shared_vpc.private_subnets
  security_groups              = [module.keycloak_ecs_security_group.security_group_id]
  service_name                 = "keycloak_${local.environment}"
  task_family_name             = "keycloak-${local.environment}"
  task_role                    = module.keycloak_task_role.role.arn
}

module "keycloak_tdr_alb" {
  source                = "./tdr-terraform-modules/alb"
  project               = var.project
  function              = "keycloak"
  environment           = local.environment
  alb_log_bucket        = module.alb_logs_s3.s3_bucket_id
  alb_security_group_id = module.keycloak_alb_security_group.security_group_id
  alb_target_group_port = 8080
  alb_target_type       = "ip"
  certificate_arn       = module.keycloak_certificate.certificate_arn
  health_check_matcher  = "200,303"
  health_check_path     = ""
  http_listener         = false
  public_subnets        = module.shared_vpc.public_subnets
  vpc_id                = module.shared_vpc.vpc_id
  common_tags           = local.common_tags
  own_host_header_only  = true
  host                  = "auth.${local.environment_domain}"
}

module "keycloak_database" {
  source                      = "./tdr-terraform-modules/rds"
  admin_username              = "keycloak_admin"
  common_tags                 = local.common_tags
  database_availability_zones = local.database_availability_zones
  database_name               = "keycloak"
  environment                 = local.environment
  kms_key_id                  = module.encryption_key.kms_key_arn
  private_subnets             = module.shared_vpc.private_subnets
  security_group_ids          = [module.keycloak_database_security_group.security_group_id]
  engine_version              = "11.13"
}

module "create_keycloak_db_users_lambda" {
  source                           = "./tdr-terraform-modules/lambda"
  project                          = var.project
  common_tags                      = local.common_tags
  lambda_create_keycloak_db_users  = true
  vpc_id                           = module.shared_vpc.vpc_id
  private_subnet_ids               = module.shared_vpc.private_subnets
  db_admin_user                    = module.keycloak_database.db_username
  db_admin_password                = module.keycloak_database.db_password
  db_url                           = module.keycloak_database.db_url
  kms_key_arn                      = module.encryption_key.kms_key_arn
  keycloak_password                = module.keycloak_ssm_parameters.params[local.keycloak_user_password_name].value
  keycloak_database_security_group = module.keycloak_database_security_group.security_group_id
  depends_on                       = [module.encryption_key]
}

module "keycloak_route53" {
  source                = "./tdr-terraform-modules/route53"
  common_tags           = local.common_tags
  environment_full_name = local.environment_full_name
  project               = "tdr"
  a_record_name         = "auth"
  alb_dns_name          = module.keycloak_tdr_alb.alb_dns_name
  alb_zone_id           = module.keycloak_tdr_alb.alb_zone_id
  create_hosted_zone    = false
  hosted_zone_id        = data.aws_route53_zone.tdr_dns_zone.id
}

module "mock_transform_engine_role" {
  source             = "./tdr-terraform-modules/iam_role"
  assume_role_policy = templatefile("./tdr-terraform-modules/ecs/templates/ecs_assume_role_policy.json.tpl", {})
  common_tags        = local.common_tags
  name               = "MockTransformEngineRole"
  policy_attachments = {}
}

module "mock_transform_engine_parameter" {
  source      = "./tdr-terraform-modules/ssm_parameter"
  common_tags = local.common_tags
  parameters = [{
    name        = "/${local.environment}/transform_engine/retry_role_arn",
    type        = "String"
    value       = module.mock_transform_engine_role.role.arn
    description = "The mock transform engine role"
    }
  ]
}
