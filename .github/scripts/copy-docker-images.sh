#!/usr/bin/env bash
aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin $1.dkr.ecr.eu-west-2.amazonaws.com
docker pull $1.dkr.ecr.eu-west-2.amazonaws.com/$3:intg
docker tag $1.dkr.ecr.eu-west-2.amazonaws.com/$3:intg $2.dkr.ecr.eu-west-2.amazonaws.com/$3:sbox
aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin $2.dkr.ecr.eu-west-2.amazonaws.com
docker push $2.dkr.ecr.eu-west-2.amazonaws.com/$3:sbox
