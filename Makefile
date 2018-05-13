#emr configs
emr.region.name=your_aws_region
emr.service.role=your_service_role
emr.job.flow.role=your_job_flow_role.
emr.release.label=emr-5.5.2
emr.cluster.name=Zillow

# ec2 configs
ec2.key.name= your_ec2_key
ec2.subnet= your_subnet_id
ec2.master.sec.group=your_master_sec_group
ec2.slave.sec.group=your_slave_sec_group
ec2.master.instance.type=m4.large
ec2.slave.instance.type=m4.large
ec2.slave.instance.count=2
ec2.master.ebs.vol.gb=32
ec2.slave.ebs.vol.gb=32
ec2.slave.ebs.vol.per.inst=1

# app configs
app.class.name=com.ml.academic.ZillowPrediction
app.aws.bucket=your_aws_bucket
app.aws.folder=your_app_folder
#app.mode=predict
app.mode=train
app.jar.name=ZillowPrediction-assembly-0.1.jar
app.scala.version=scala-2.11
app.jar.path=target/${app.scala.version}/${app.jar.name}
app.local.input=your_local_input_path
# dataset must contain zillow dataset files: properties_2017.csv,train_2016_v2.csv,train_2017.csv
app.dataset.s3.loc=your_dataset_loc_on_s3


#spark tuning configs
spark.executor.memory=5g
spark.driver.memory=4g
spark.driver.overhead=1g
spark.executor.overhead=3g
spark.shuffle.partitions=4000
spark.num.executors=2
spark.executor.cores=3


jar:
	sbt clean assembly

local : jar
	spark-submit --packages com.databricks:spark-csv_2.10:1.2.0 \
		 --class ${app.class.name} \
                 --conf spark.driver.extraJavaOptions=-XX:+UseG1GC \
                 --conf spark.sql.shuffle.partitions=${spark.shuffle.partitions} \
		 --conf spark.driver.memoryOverhead=${spark.driver.overhead} \
                 --conf spark.driver.memory=${spark.driver.memory} \
		 --master local[2] \
                 ${app.jar.path} \
                 ${app.local.input} \
		 ${app.local.input} \
		 ${app.local.input} \
		 ${app.mode}


make-bucket:
	aws s3 mb s3://${app.aws.bucket} --region ${emr.region.name}

upload-app-jar-aws:
	aws s3 cp ${app.jar.path} s3://${app.aws.bucket}/${app.aws.folder}/

clean-s3:
	aws s3 rm s3://${app.aws.bucket}/${app.aws.folder} --recursive

download-app: 
	aws s3 cp s3://${app.aws.bucket}/${app.aws.folder} ${app.aws.folder} --recursive 

cloud: jar upload-app-jar-aws  
	aws emr create-cluster \
		--name 'ZillowAnalysis' \
		--release-label ${emr.release.label} \
		--instance-groups '[{ "InstanceGroupType": "MASTER","InstanceType": "${ec2.master.instance.type}","InstanceCount": 1,"EbsConfiguration": {"EbsBlockDeviceConfigs": [{"VolumeSpecification": {"VolumeType": "gp2","SizeInGB": ${ec2.master.ebs.vol.gb}},"VolumesPerInstance": 1}]}},{"InstanceGroupType": "CORE","InstanceType": "${ec2.slave.instance.type}","InstanceCount": ${ec2.slave.instance.count},"EbsConfiguration": {"EbsBlockDeviceConfigs": [{"VolumeSpecification": {"VolumeType": "gp2","SizeInGB": ${ec2.slave.ebs.vol.gb}},"VolumesPerInstance":${ec2.slave.ebs.vol.per.inst}}]}}]' \
		--applications '[{"Name": "Spark"},{"Name": "Zeppelin"},{"Name": "Hadoop"}]' \
		--steps '[{"Name": "Spark Submit","ActionOnFailure": "TERMINATE_JOB_FLOW","Jar": "command-runner.jar","Args": ["spark-submit","--executor-cores","${spark.executor.cores}","--num-executors","${spark.num.executors}","--class","${app.class.name}","--deploy-mode","cluster","--master","yarn","--executor-memory","${spark.executor.memory}","--driver-memory","${spark.driver.memory}","--packages","com.databricks:spark-csv_2.10:1.2.0","s3://${app.aws.bucket}/${app.aws.folder}/${app.jar.name}","${app.dataset.s3.loc}","hdfs:///${app.aws.folder}","${app.mode}","s3://${app.aws.bucket}/${app.aws.folder}"]},{"Name": "S3DistCp","ActionOnFailure": "TERMINATE_JOB_FLOW","Jar": "command-runner.jar","Args": ["s3-dist-cp","--src=hdfs:///${app.aws.folder}/","--dest=s3://${app.aws.bucket}/${app.aws.folder}"]}]' \
		--log-uri 's3://${app.aws.bucket}/${app.aws.folder}/emr-logs' \
		--service-role ${emr.service.role} \
		--ec2-attributes InstanceProfile=${emr.job.flow.role},SubnetId=${ec2.subnet},KeyName=${ec2.key.name},EmrManagedMasterSecurityGroup=${ec2.master.sec.group},EmrManagedSlaveSecurityGroup=${ec2.slave.sec.group} \
		--configurations '[{"Classification": "spark","Properties": {"maximizeResourceAllocation": "false"}},{"Classification": "spark-defaults","Properties": {"spark.dynamicAllocation.enabled": "true","spark.shuffle.service.enabled":"true","spark.executor.memoryOverhead":"${spark.executor.overhead}","spark.driver.memoryOverhead":"${spark.driver.overhead}","spark.executor.extraJavaOptions":"-XX:+UseG1GC","spark.shuffle.partitions":"${spark.shuffle.partitions}"},"Configurations": []},{"Classification": "capacity-scheduler","Properties": {"yarn.scheduler.capacity.resource-calculator": "org.apache.hadoop.yarn.util.resource.DominantResourceCalculator"}},{"Classification": "yarn-site","Properties": {"yarn.log-aggregation-enable": "true","yarn.log-aggregation.retain-seconds": "-1","yarn.nodemanager.remote-app-log-dir": "s3://${app.aws.bucket}/${app.aws.folder}/yarn-logs"}}]' \
		--region ${emr.region.name} \
		--enable-debugging \
		--auto-terminate

# comment --auto-terminate if you want to keep cluster alive after all steps are done.	
# ActionOnFailure can be:	 
# TERMINATE_JOB_FLOW, CONTINUE
