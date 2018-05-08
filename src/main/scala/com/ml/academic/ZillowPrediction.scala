package com.ml.academic

import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.feature.{VectorAssembler, VectorIndexer}
import org.apache.spark.ml.regression.RandomForestRegressor
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{SaveMode, SparkSession}

object ZillowPrediction {
  val spark = SparkSession.builder().appName("ZillowPrediction").getOrCreate()

  def main(args: Array[String]): Unit = {
    val base_path = args(0)
    val out_path = args(1)
    val (properties, train1, train2) = PreProcessingUtils.getrawdf(base_path, spark)
    val p = PreProcessingUtils.getNumericFeatures(properties)
    val p_months = p.withColumn("months",
      array(lit(201610).cast("double"), lit(201611).cast("double"),
        lit(201612).cast("double"), lit(201710).cast("double"),
        lit(201711).cast("double"), lit(201712).cast("double")))
    val p_eval = p_months.withColumn("transactiondate", explode(p_months("months"))).drop("months")

    val model = args(2) match {
      case "predict" => {
        val pipelineModel = PipelineModel.load(s"$base_path/model")
        val cols = Array("201610", "201611", "201612", "201710", "201711", "201712").reverse
        val t = pipelineModel.transform(p_eval)
        val p = t.select(t("parcelid").cast("Integer"), t("transactiondate"), t("prediction"))
        val predictions = p.repartition(4000, p("parcelid")).cache()

        val results = predictions.select(predictions("parcelid").cast("Integer")).distinct()
        val resultsdf = cols.foldLeft(results) { (tempDF, col) => {
            val p = predictions.where(predictions("transactiondate") === col)
            p.select(round(p("prediction"), 4).alias(col), p("parcelid")).join(tempDF, Seq("parcelid"))
          }
        }
        resultsdf.withColumnRenamed("parcelid", "ParcelId").repartition(1)
          .write.format("com.databricks.spark.csv")
          .mode(SaveMode.Overwrite)
          .option("header", "true")
          .save(s"$out_path/results")
      }
      case _ => {
        val train = PreProcessingUtils.get_XY_raw(properties, train1, train2).cache()

        val featuresCols = train.drop("logerror").columns
        // This concatenates all feature columns into a single feature vector in a new column "rawFeatures".
        val assembler = new VectorAssembler().setInputCols(featuresCols).setOutputCol("rawFeatures")

        // This identifies categorical features and indexes them.
        val vectorIndexer = new VectorIndexer().setInputCol("rawFeatures")
          .setOutputCol("features").setMaxCategories(20)
          .fit(assembler.transform(train union p_eval))

        val rf = new RandomForestRegressor().setLabelCol("logerror")
          .setCacheNodeIds(true)
          .setImpurity("variance")

        // Define a grid of hyperparameters to test:
        //  - maxDepth: max depth of each decision tree in the ensemble
        //  - numTrees: no of trees in ensemble
        val paramGrid = new ParamGridBuilder()
          .addGrid(rf.maxDepth, 10 to 30 by 5)
          .addGrid(rf.numTrees, 20 to 100 by 10)
          .addGrid(rf.subsamplingRate, 0.5 to 1.0 by 0.1)
          .addGrid(rf.maxBins, Array(600))
          .build()
        // We define an evaluation metric.
        // This tells CrossValidator how well we are doing by comparing the true labels with predictions.
        val evaluator = new RegressionEvaluator().setMetricName("rmse").
          setLabelCol(rf.getLabelCol).setPredictionCol(rf.getPredictionCol)
        // Declare the CrossValidator, which runs model tuning for us.
        val cv = new CrossValidator().setEstimator(rf).setEvaluator(evaluator)
          .setEstimatorParamMaps(paramGrid).setNumFolds(15)
        val pipeline = new Pipeline().setStages(Array(assembler, vectorIndexer, cv))
        val pipelineModel = pipeline.fit(train)
        pipelineModel.write.overwrite().save(s"$out_path/model")
      }
    }
  }
}