# Zillow Price Prediction Using Spark MLlib Random Forest

Data sets are generally quite large, taxing capacity of main memory, local disk, and even remote disk.​
A single machine can no more be used to train such big datasets using libraries like sci-kit learn​
With the popularity of open source tools such as Spark MLlib, it has become relatively simple to implement distributed
machine learning models such as Random forest which is massively parallelizable. ​In this project,
we would mine Zillow's dataset to estimate log error of Zestimate using Spark MLlib's Random Forest Regressor.​


## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development purposes.


### Prerequisites

1) AWS account with administrative privileges.
2) AWS CLI
3) Linux OS from which you could run your Makefile command.
4) aws-access-key-id and access aws-secret-access-key.

### Installing

1) Install AWS CLI:
    https://docs.aws.amazon.com/cli/latest/userguide/installing.html

2) Set up your ec2 key pair:
    https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html

3) Set up ec2 subnet with master and slave security groups:
    https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/default-vpc.html
    https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-man-sec-groups.html

4) Set up service role and job flow role for your account.
    https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/concepts-roles.html

5) Set up ssh tunneling and foxy proxy for your AWS account:
    https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-ssh-tunnel.html
    https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-connect-master-node-proxy.html

6) Create your s3 bucket, inside the bucket you may create a dataset folder in which you place
   your zillow's dataset files: properties_2017.csv,train_2016_v2.csv and train_2017.csv.

7) You would like to refer below link to access spark history server and various Web UI
    https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-web-interfaces.html

8) Configure your AWS credentials:
    https://docs.aws.amazon.com/cli/latest/userguide/cli-config-files.html

## Deployment

You would run the tool in two modes (configured via app.mode in Makefile):
    train: To train the model on Zillow's dataset and persist it on S3 for further use
    predict: To predict log error of Zestimate from previously trained model

You could also configure the cluster capacity and spark tuning options from the Makefile.
You need to configure information regarding your AWS account such as ec2.subnet,ec2.master.sec.group,
ec2.slave.sec.group etc

Command to train on AWS EMR cluster:
make cloud

Command to train locally:
make local

Command to download app folder:
make download-app

## Authors

* **Rutul Patel**

## License

This project is licensed under the Apache License - see the [LICENSE.md](LICENSE.md) file for details