#!/usr/bin/bash
FOLDER_NAME=$1
latest_version() {
  echo $(git -c 'versionsort.suffix=-' ls-remote --tags --sort='v:refname' https://github.com/nationalarchives/tdr-$1.git | tail -1 | cut --delimiter='/' --fields=3 | tr -d '^{}0.0')
}

docker_login() {
  aws ecr get-login-password --profile $1 --region eu-west-2 | docker login --username AWS --password-stdin $2.dkr.ecr.eu-west-2.amazonaws.com
}

build_lambda() {
#  local latest_version=$(latest_version $1)
#  aws s3 cp s3://tdr-backend-code-mgmt/$latest_version/$2 . --profile management
  aws s3 cp $2 s3://tdr-backend-checks-sbox/$2 --profile sandbox
  aws lambda update-function-code --profile sandbox --function-name tdr-${3:-$1}-sbox --s3-bucket tdr-backend-checks-sbox --s3-key $2 | jq
}

file_check_results() {
  local json_name=$(echo $1 | sed -E 's/-(.)/\U\1/g')
  mapfile -t log_stream_names < <(aws logs describe-log-streams --log-group-name /aws/lambda/tdr-$1-sbox --query "logStreams[].logStreamName" --output text)
  for log_stream_name in $log_stream_names
  do
    local message=$(aws logs get-log-events --log-group-name /aws/lambda/tdr-$1-sbox --log-stream-name $log_stream_name --query 'events[].message' | grep timeTaken)
    performance-checks/performance/bin/performance-checks save-results -c downloadFiles -m "$message"
  done

}

build_docker_image() {
  local management_account=$(aws sts get-caller-identity  --query 'Account' --output text --profile management)
  local sandbox_account=$(aws sts get-caller-identity  --query 'Account' --output text --profile sandbox)
  docker_login management $management_account
  docker pull $management_account.dkr.ecr.eu-west-2.amazonaws.com/$1:intg
  docker tag $management_account.dkr.ecr.eu-west-2.amazonaws.com/$1:intg $sandbox_account.dkr.ecr.eu-west-2.amazonaws.com/$1:sbox
  docker_login sandbox $sandbox_account
  docker push $sandbox_account.dkr.ecr.eu-west-2.amazonaws.com/$1:sbox
}

run_task() {
  export AWS_PROFILE=sandbox
  local security_group_id=$(aws ec2 describe-security-groups --filter Name=group-name,Values=allow-ecs-mount-efs --query "SecurityGroups[0].GroupId" --output text)
  local subnet_ids=$(aws ec2 describe-subnets --filter Name=tag:Name,Values=tdr-private-subnet-0-sbox,tdr-private-subnet-1-sbox --query "Subnets[].SubnetId" --output text | tr '\t' ',')
  aws ecs run-task --cluster file_format_build_sbox --task-definition file-format-build-sbox --launch-type FARGATE --platform-version 1.4.0 --network-configuration awsvpcConfiguration="{subnets=[$subnet_ids],securityGroups=[$security_group_id],assignPublicIp=DISABLED}" | jq
  unset AWS_PROFILE
}
#
#sbt universal:packageBin
#unzip target/universal/performance.zip

#build_docker_image auth-server
#build_docker_image consignment-api
#build_docker_image file-format-build

#cd terraform
#aws2-wrap --profile sandbox terraform13 init
#aws2-wrap --profile sandbox terraform13 apply --auto-approve
#cd ..

#build_lambda checksum checksum.jar
#build_lambda antivirus function.zip yara-av
#build_lambda file-format file-format.jar
#build_lambda download-files download-files.jar
#build_lambda api-update api-update.jar
#build_lambda create-db-users create-db-users.jar
#build_lambda create-db-users create-db-users.jar create-keycloak-db-user
#build_lambda consignment-api-data consignment-api-data.jar database-migrations
#TODO Deploy built jars to github

#aws lambda invoke --function-name tdr-create-db-users-sbox out1 --profile sandbox | jq
#aws lambda invoke --function-name tdr-create-keycloak-db-user-sbox out2 --profile sandbox | jq
#aws lambda invoke --function-name tdr-database-migrations-sbox out3 --profile sandbox | jq
#rm -f out

#run_task
#TODO Wait for task to complete


#TODO Loop to check if load balancers are up
#export ADMIN_SECRET=$(aws ssm get-parameter --name /sbox/keycloak/user_admin_client/secret --with-decryption --profile sandbox --query "Parameter.Value" --output text)
# performance/bin/performance $1

#TODO Wait for file checks to finish

file_check_results download-files
#file_check_results yara-av
#file_check_results file-format
#file_check_results checksumfile_check_results api-update

#jq -s '.[0] * .[1] * .[2] * .[3] * .[4]'  download-files.json yara-av.json checksum.json file-format.json api-update.json > all_checks.json
#rm download-files.json yara-av.json checksum.json file-format.json api-update.json