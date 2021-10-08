#!/usr/bin/bash

mapfile -t cluster_ids < <(aws rds describe-db-clusters --query 'DBClusters[].DBClusterIdentifier' --output text --profile sandbox) 
echo $myarr 
 
for cluster_id in $cluster_ids 
do 
  echo $cluster_id 
  aws rds modify-db-cluster --db-cluster-identifier $cluster_id --apply-immediately --no-deletion-protection | jq
done



aws elbv2 describe-load-balancers --query 'LoadBalancers[].LoadBalancerArn' | jq -r '.[]'| xargs -L 1 -I{} aws elbv2 modify-load-balancer-attributes --load-balancer-arn {} --attributes Key=deletion_protection.enabled,Value=false | jq

cd terraform
aws2-wrap --profile sandbox terraform13 destroy --auto-approve
