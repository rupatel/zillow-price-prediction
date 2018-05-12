package com.ml.academic

import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{BooleanType, StringType}
import org.apache.spark.sql.{DataFrame, SparkSession}

object PreProcessingUtils {
  /**
    *
    * @param rawFeatures: Raw features dataframe
    * @return  Processed features dataframe with double values.
    *          Categorical features in the given dataframe are converted to numerical indices.
    */
  def getNumericFeatures(rawFeatures: DataFrame): DataFrame = {
    val cat_features = rawFeatures.schema.fields.foldLeft(List[String]()) { (cols, field) =>
      field.dataType match {
        case StringType => {
          field.name :: cols
        }
        case _ => cols
      }
    }
    val bool_features = rawFeatures.schema.fields.foldLeft(List[String]()) { (cols, field) =>
      field.dataType match {
        case BooleanType => {
          field.name :: cols
        }
        case _ => cols
      }
    }
    //For numerical features, we replace na with -1
    //For categorical features, we replace na with 'N/A'
    val XRaw = rawFeatures.na.fill(-1).na.fill("N/A", cat_features)

    //Transforming boolean features to numeric values
    val x_bool_numeric = bool_features.foldLeft(XRaw) { (tempDF, col) =>
      tempDF.schema(col).dataType match {
        case BooleanType => {
          tempDF.withColumn(col + "Numeric", when(isnull(tempDF(col)), lit(0))
            .when(tempDF(col), lit(1)).otherwise(lit(0)))
            .drop(col).withColumnRenamed(col + "Numeric", col)
        }
        case _ => tempDF
      }
    }
    //Transforming categorical features to numeric values
    val X_numeric = cat_features.foldLeft(x_bool_numeric) { (tempDF, col) =>
      tempDF.schema(col).dataType match {
        case StringType => {
          val indexer = new StringIndexer().setInputCol(col).setOutputCol(col + "Index")
          indexer.fit(tempDF).transform(tempDF).drop(col).withColumnRenamed(col + "Index", col)
        }
        case _ => tempDF
      }
    }
    //Casting numeric features to double.
    val X = X_numeric.columns.foldLeft(X_numeric) { (tempDF, col) =>
      tempDF.withColumn(col + "Double", tempDF(col).cast("Double")).drop(col).withColumnRenamed(col + "Double", col)
    }
    return X
  }


  /**
    *
    * @param properties: Raw Properties dataframe loaded from CSV file
    * @param train1: Raw train1 dataframe loaded from CSV file
    * @param train2: Raw train2 dataframe loaded from CSV file
    * @return processed features dataframe for training zillow dataset.
    */
  def get_XY_raw(properties: DataFrame, train1: DataFrame, train2: DataFrame): DataFrame = {
    // extracting year_month from transaction date in training data.
    val train1_with_yrmon = train1.withColumn("year_month",
      concat(regexp_extract(train1("transactiondate"),"""(\d\d\d\d)-(\d\d)-(\d\d)""", 1),
        regexp_extract(train1("transactiondate"),"""(\d\d\d\d)-(\d\d)-(\d\d)""", 2)).cast("Int"))
      .drop("transactiondate")

    val train2_with_yrmon = train2.withColumn("year_month",
      concat(regexp_extract(train2("transactiondate"),"""(\d\d\d\d)-(\d\d)-(\d\d)""", 1),
        regexp_extract(train2("transactiondate"),"""(\d\d\d\d)-(\d\d)-(\d\d)""", 2)).cast("Int"))
      .drop("transactiondate")


    //dropping na from training data
    val train = train1_with_yrmon.union(train2_with_yrmon).na.drop()

    val joined = properties.join(broadcast(train), "parcelid")

    val rawFeatures = joined.drop("parcelid").withColumnRenamed("year_month", "transactiondate")
    return getNumericFeatures(rawFeatures)
  }

  /**
    *
    * @param base_path: path of the zillow dataset containing:properties_2017.csv,
    *                   train_2016_v2.csv and train_2017.csv
    * @param spark: The spark session
    * @return a tuple of three dataframes loaded from three raw files in given directory.
    */
  def getrawdf(base_path: String,spark:SparkSession): (DataFrame, DataFrame, DataFrame) = {
    val properties = spark.sqlContext.read.format("csv")
      .option("header", "true")
      .option("inferSchema", "true")
      .load(s"$base_path/properties_2017.csv")
    val train1 = spark.sqlContext.read.format("csv")
      .option("header", "true")
      .option("inferSchema", "true")
      .load(s"$base_path/train_2016_v2.csv")
    val train2 = spark.sqlContext.read.format("csv")
      .option("header", "true")
      .option("inferSchema", "true")
      .load(s"$base_path/train_2017.csv")
    return (properties, train1, train2)
  }
}