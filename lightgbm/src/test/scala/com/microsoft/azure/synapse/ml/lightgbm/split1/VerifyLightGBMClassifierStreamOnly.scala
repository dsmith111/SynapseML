package com.microsoft.azure.synapse.ml.lightgbm.split1

// scalastyle:off magic.number
/** Tests to validate the functionality of LightGBM module in streaming mode. */
class VerifyLightGBMClassifierStreamOnly extends LightGBMClassifierTestData {
  override def ignoreSerializationFuzzing: Boolean = true
  override def ignoreExperimentFuzzing: Boolean = true

  test("Verify LightGBMClassifier handles global sample mode correctly") {
    val df = loadBinary(breastCancerFile, "Label")
    val model = baseModel
      .setBoostingType("gbdt")
      .setSamplingMode("global")

    val fitModel = model.fit(df)
    fitModel.transform(df)
  }

  test("Verify LightGBMClassifier handles fixed sample mode correctly") {
    val df = loadBinary(breastCancerFile, "Label")
    val model = baseModel
      .setBoostingType("gbdt")
      .setSamplingMode("fixed")

    val fitModel = model.fit(df)
    fitModel.transform(df)
  }

  test("Verify LightGBMClassifier handles subset sample mode correctly") {
    boostingTypes.foreach { boostingType =>
      val df = loadBinary(breastCancerFile, "Label")
      val model = baseModel
        .setBoostingType("gbdt")
        .setSamplingMode("subset")

      val fitModel = model.fit(df)
      fitModel.transform(df)
    }
  }

  test("Verify LightGBMClassifier can use cached reference dataset") {
    val baseClassifier = baseModel
    assert(baseClassifier.getReferenceDataset.isEmpty)

    val model1 = baseClassifier.fit(pimaDF)

    // Assert the generated reference dataset was saved
    assert(baseClassifier.getReferenceDataset.nonEmpty)

    // Assert we use the same reference data and get same result
    val model2 = baseModel.fit(pimaDF)
    assert(model1.getModel.modelStr == model2.getModel.modelStr)
  }

  test("Verify loading of Spark pipeline with custom transformer and LightGBM model") {
    import org.apache.spark.ml.{Pipeline, PipelineModel}
    import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
    import org.apache.spark.sql.SparkSession

    val spark = SparkSession.builder().appName("LightGBMTest").master("local[*]").getOrCreate()
    import spark.implicits._

    // Sample data
    val data = Seq(
      (0, "a", 1.0, 1.0),
      (1, "b", 0.0, 0.0),
      (0, "a", 0.5, 0.5),
      (1, "b", 0.5, 0.5)
    ).toDF("label", "category", "feature1", "feature2")

    // Custom transformer
    val stringIndexer = new StringIndexer().setInputCol("category").setOutputCol("categoryIndex")
    val vectorAssembler = new VectorAssembler().setInputCols(Array("feature1", "feature2", "categoryIndex")).setOutputCol("features")

    // LightGBM model
    val lightGBMClassifier = new LightGBMClassifier()
      .setLabelCol("label")
      .setFeaturesCol("features")

    // Pipeline
    val pipeline = new Pipeline().setStages(Array(stringIndexer, vectorAssembler, lightGBMClassifier))

    // Fit and save pipeline
    val model = pipeline.fit(data)
    model.write.overwrite().save("/tmp/pipeline")

    // Load pipeline
    val loadedModel = PipelineModel.load("/tmp/pipeline")
    loadedModel.transform(data).show()
  }
}
