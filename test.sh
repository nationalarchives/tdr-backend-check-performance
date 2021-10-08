#!/usr/bin/bash
latest_version() {
  echo $(git -c 'versionsort.suffix=-' ls-remote --tags --sort='v:refname' https://github.com/nationalarchives/tdr-$1.git | tail -1 | cut --delimiter='/' --fields=3 | tr -d '^{}0.0' )
}

docker_login() {
  aws ecr get-login-password --profile $1 --region eu-west-2 | docker login --username AWS --password-stdin $2.dkr.ecr.eu-west-2.amazonaws.com
}

build_lambda() {
#  local latest_version=$(latest_version $1)
#  aws s3 cp s3://tdr-backend-code-mgmt/$latest_version/$2 . --profile management
#  aws s3 cp $2 s3://tdr-backend-checks-sbox/$2 --profile sandbox
  aws lambda update-function-code --profile sandbox --function-name tdr-${3:-$1}-sbox --s3-bucket tdr-backend-checks-sbox --s3-key $2 | jq
}

build_docker_image() {
  local latest_version=$(latest_version $1)
  local management_account=$(aws sts get-caller-identity  --query 'Account' --output text --profile management)
  local sandbox_account=$(aws sts get-caller-identity  --query 'Account' --output text --profile sandbox)
  docker_login management $management_account
  docker pull $management_account.dkr.ecr.eu-west-2.amazonaws.com/$1:$latest_version
  docker tag $management_account.dkr.ecr.eu-west-2.amazonaws.com/$1:$latest_version $sandbox_account.dkr.ecr.eu-west-2.amazonaws.com/$1:sbox
  docker_login sandbox $sandbox_account
  docker push $sandbox_account.dkr.ecr.eu-west-2.amazonaws.com/$1:sbox
}

#build_docker_image auth-server
#build_docker_image consignment-api

#cd terraform
#aws2-wrap --profile sandbox terraform13 apply --auto-approve

build_lambda checksum checksum.jar
build_lambda antivirus yara-av.zip yara-av
build_lambda file-format file-format.jar
build_lambda download-files download-files.jar
build_lambda api-update api-update.jar
#build_lambda create-db-users create-db-users.jar
#build_lambda create-db-users create-db-users.jar create-keycloak-db-user
#Don't forget to change the deploy job for this repo
build_lambda consignment-api-data consignment-api-data.jar database-migrations

#aws lambda invoke --function-name tdr-create-db-users-sbox out1 --profile sandbox | jq
#aws lambda invoke --function-name tdr-create-keycloak-db-user-sbox out2 --profile sandbox | jq
#aws lambda invoke --function-name tdr-database-migrations-sbox out3 --profile sandbox | jq
#rm -f out

#cd create-files
#
#export ADMIN_SECRET=$(aws ssm get-parameter --name /sbox/keycloak/user_admin_client/secret --with-decryption --profile sandbox --query "Parameter.Value" --output text)
#sbt run