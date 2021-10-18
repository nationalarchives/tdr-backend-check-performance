#!/usr/bin/bash
FOLDER_NAME=$1

docker_login() {
  aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin $2.dkr.ecr.eu-west-2.amazonaws.com
}

build_lambda() {
  local download_url=$(curl https://api.github.com/repos/nationalarchives/tdr-$1/releases/latest | jq -r '.assets[0].browser_download_url')
  wget $download_url
  aws s3 cp $2 s3://tdr-backend-checks-sbox/$2 --profile sandbox
  aws lambda update-function-code --function-name tdr-${3:-$1}-sbox --s3-bucket tdr-backend-checks-sbox --s3-key $2 | jq
}

file_check_results() {
  for check; do
    echo $check
    local json_name=$(echo $check | sed -E 's/-(.)/\U\1/g')
    local json_name=$(echo $check | sed -E 's/-(.)/\U\1/g')
    mapfile -t log_streams < <(aws logs describe-log-streams --log-group-name /aws/lambda/tdr-$check-sbox --query "logStreams[].logStreamName" --output text)

    for log_stream in $log_streams
    do
      aws logs get-log-events --log-group-name /aws/lambda/tdr-$check-sbox --log-stream-name $log_stream | jq -r '.events[].message' | jq -R 'fromjson?|select(.timeTaken != null)|{fileId : .fileId, consignmentId: .consignmentId, timeTaken: .timeTaken}' | jq -n 'reduce inputs as $row ([]; .+ [$row])' | jq "{\"results\": .}" > $check.json
      ./performance-checks save-results -c $json_name -f $check.json
      rm $check.json
    done
  done



}

#Run this as management account. If running in Jenkins, use Jenkins assumed role
build_docker_image() {
  export AWS_PROFILE=management
  local management_account=$(aws sts get-caller-identity  --query 'Account' --output text --profile management)
  local sandbox_account=$(aws ssm get-parameter --name /mgmt/sandbox_account --with-decryption --profile management --query "Parameter.Value" --output text)
  docker_login $management_account
  docker pull $management_account.dkr.ecr.eu-west-2.amazonaws.com/$1:intg
  docker tag $management_account.dkr.ecr.eu-west-2.amazonaws.com/$1:intg $sandbox_account.dkr.ecr.eu-west-2.amazonaws.com/$1:sbox
  docker_login $sandbox_account
  docker push $sandbox_account.dkr.ecr.eu-west-2.amazonaws.com/$1:sbox
  unset AWS_PROFILE
}

describe_tasks() {
  echo $(aws ecs describe-tasks --tasks $task_id --cluster file_format_build_sbox | jq '.tasks[0].containers[0].exitCode')
}

describe_target_health() {
  local load_balancer_arn=$(aws elbv2 describe-load-balancers --names tdr-$1-sbox --query 'LoadBalancers[0].LoadBalancerArn' --output text)
  local target_group_arn=$(aws elbv2 describe-target-groups --load-balancer-arn $load_balancer_arn | jq -r '.TargetGroups[0].TargetGroupArn')
  echo $(aws elbv2 describe-target-health --target-group-arn $target_group_arn --query "TargetHealthDescriptions[].TargetHealth.State" --output text)
}

check_service_health() {
  local service_status=$(describe_target_health $1)
  while [[ $service_status != *"healthy"* ]];
  do
    service_status=$(describe_target_health $1)
  done
  echo "Service $1 is healthy"
}

run_task() {
  local security_group_id=$(aws ec2 describe-security-groups --filter Name=group-name,Values=allow-ecs-mount-efs --query "SecurityGroups[0].GroupId" --output text)
  local subnet_ids=$(aws ec2 describe-subnets --filter Name=tag:Name,Values=tdr-private-subnet-0-sbox,tdr-private-subnet-1-sbox --query "Subnets[].SubnetId" --output text | tr '\t' ',')
  local task_id=$(aws ecs run-task --cluster file_format_build_sbox --task-definition file-format-build-sbox --launch-type FARGATE --platform-version 1.4.0 --network-configuration awsvpcConfiguration="{subnets=[$subnet_ids],securityGroups=[$security_group_id],assignPublicIp=DISABLED}" | jq -r '.tasks[0].containers[0].taskArn')
  exit_status=$(describe_tasks)
  echo $exit_status
  while [ "$exit_status" == "null" ];
  do
    echo $task_id
    exit_status=$(describe_tasks)
    sleep 5
    echo "Task status is $exit_status"
  done
  echo "Done"
  if [ "$exit_status" -ne "0" ]
  then
    echo "The file format build task has failed"
    exit 1
  fi
}

sbt universal:packageBin
unzip target/universal/performance.zip

cd terraform
aws2-wrap terraform13 init
aws2-wrap terraform13 apply --auto-approve
cd ..

build_docker_image auth-server
build_docker_image consignment-api
build_docker_image file-format-build

build_lambda antivirus function.zip yara-av
build_lambda api-update api-update.jar
build_lambda create-db-users create-db-users.jar
build_lambda create-db-users create-db-users.jar create-keycloak-db-user
build_lambda consignment-api-data consignment-api-data.jar database-migrations
build_lambda checksum checksum.jar
build_lambda download-files download-files.jar
build_lambda file-format file-format.jar

aws lambda invoke --function-name tdr-create-db-users-sbox out1 | jq
aws lambda invoke --function-name tdr-create-keycloak-db-user-sbox out2 | jq
aws lambda invoke --function-name tdr-database-migrations-sbox out3 | jq
rm -f out

run_task

check_service_health consignmentapi
check_service_health keycloak


#export ADMIN_SECRET=$(aws ssm get-parameter --name /sbox/keycloak/user_admin_client/secret --with-decryption --query "Parameter.Value" --output text)

for file; do
   cd performance-checks/performance/bin/
   ./performance-checks create-files -f $file
   file_check_results download-files yara-av file-format checksum api-update
done

#jq -s '.[0] * .[1] * .[2] * .[3] * .[4]'  download-files.json yara-av.json checksum.json file-format.json api-update.json > all_checks.json
#rm download-files.json yara-av.json checksum.json file-format.json api-update.json