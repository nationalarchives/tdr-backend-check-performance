## Backend checks performance tests
There are two separate parts to this repository. Terraform files and a Scala project.

### Terraform
This project contains terraform to create a subset of the TDR system, so we can run the backend checks in the sandbox account. The main components created by terraform are:
* ECR repositories for the API and Keycloak.
* API and keycloak ECS services and load balancers.
* API and keycloak databases.
* Backend check lambdas and queues.

### Scala
The `setupResources` method in the `Main` object deploys the TDR code once terraform has created the resources.
* Download the latest lambda code from Github.
* Upload this to S3 on the sandbox.
* Update the newly created lambda with this code.
* Run the create database uses lambda.
* Run the database migrations lambda.
* Run the file format task to install Droid on EFS.
* Wait for the API and Keycloak target health checks to be healthy.

The `createFileCheckResults` in the `Main` object creates the report.
* Create a user in Keycloak
* Create the tables in the sqlite3
* Clear out any existing log streams from previous runs.
* Download the files based on the input arguments from the S3 test files in Sandbox.
* Call the API to create the consignment and files.
* Insert the file IDs into the sqlite database.
* Upload the files to the S3 upload bucket.
* Repeatedly call the  API until the checks have finished.
* Get the file format information from the API.
* Parse the logs to get the times taken.
* Insert the file format and time taken data into the sqlite database.
* Create the HTML and CSV reports based on the data in the sqlite database.

The `destroyResources` method in the `Main` object prepares the environment before being destroyed.
* Removes deletion protection from the load balancers.
* Removes the deletion protection from the databases.

### Jenkins job
There are three parameters for the Jenkins job.
#### CREATE_RESOURCES
This runs terraform and the setup code. 
Set this to true if you need to create the environment. 
Set it to false if the environment already exists and you want to run further file checks against it.

#### FILES
A space separated list of folder names as found in the `tdr-upload-test-data` bucket in the Sandbox environment. Unless you are running with 'DESTROY_RESOURCES' set, this is mandatory.

#### DESTROY_RESOURCES
Setting this to true runs `destroyResources` and then `terraform destroy`

### Running locally
You will need to use management account credentials. There are arguments that can be passed to `sbt run`. For example `sbt 'run -cr 10Images'` will create the environment and run the file checks against the `10Images` directory in `tdr-upload-test-data`

The available options are:
```
--help
Display this help text.
--create-resources, -c
Whether to run terraform and deploy lambdas and docker images
--create-results, -r
Whether to run the performance checks and output the results
--destroy-resources, -d
Whether to remove deletion protection to allow resource destruction
```