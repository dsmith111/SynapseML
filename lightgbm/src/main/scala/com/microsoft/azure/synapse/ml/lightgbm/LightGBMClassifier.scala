package com.microsoft.azure.synapse.ml.lightgbm

import com.microsoft.azure.synapse.ml.lightgbm.booster.LightGBMBooster
import com.microsoft.azure.synapse.ml.lightgbm.params.{BaseTrainParams, ClassifierTrainParams,
  LightGBMModelParams, LightGBMPredictionParams}
import com.microsoft.azure.synapse.ml.logging.{FeatureNames, SynapseMLLogging}
import org.apache.spark.ml.classification.{ProbabilisticClassificationModel, ProbabilisticClassifier}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.param._
import org.apache.spark.ml.util._
import org.apache.spark.ml.{ComplexParamsReadable, ComplexParamsWritable}
import org.apache.spark.sql._
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.types.StructField

object LightGBMClassifier extends DefaultParamsReadable[LightGBMClassifier]

class LightGBMClassifier(override val uid: String)
  extends ProbabilisticClassifier[Vector, LightGBMClassifier, LightGBMClassificationModel]
  with LightGBMBase[LightGBMClassificationModel] with SynapseMLLogging with JavaMLReadable[LightGBMClassifier] {
  logClass(FeatureNames.LightGBM)

  def this() = this(Identifiable.randomUID("LightGBMClassifier"))

  setDefault(objective -> LightGBMConstants.BinaryObjective)

  val isUnbalance = new BooleanParam(this, "isUnbalance",
    "Set to true if training data is unbalanced in binary classification scenario")
  setDefault(isUnbalance -> false)
  def getIsUnbalance: Boolean = $(isUnbalance)
  def setIsUnbalance(value: Boolean): this.type = set(isUnbalance, value)

  def getTrainParams(numTasks: Int, featuresSchema: StructField, numTasksPerExec: Int): BaseTrainParams = {
    ClassifierTrainParams(
      get(passThroughArgs),
      getIsUnbalance,
      getBoostFromAverage,
      get(isProvideTrainingMetric),
      getDelegate,
      getGeneralParams(numTasks, featuresSchema),
      getDatasetParams,
      getDartParams,
      getExecutionParams(numTasksPerExec),
      getObjectiveParams,
      getSeedParams,
      getCategoricalParams)
  }

  override protected def addCustomTrainParams(params: BaseTrainParams, dataset: Dataset[_]): BaseTrainParams = {
    val classifierParams = params.asInstanceOf[ClassifierTrainParams]
    if (classifierParams.isBinary) params
    else classifierParams.setNumClass(getNumClasses(dataset, getMaxNumClasses))
  }

  def getModel(trainParams: BaseTrainParams, lightGBMBooster: LightGBMBooster): LightGBMClassificationModel = {
    val classifierTrainParams = trainParams.asInstanceOf[ClassifierTrainParams]
    val model = new LightGBMClassificationModel(uid)
      .setLightGBMBooster(lightGBMBooster)
      .setFeaturesCol(getFeaturesCol)
      .setPredictionCol(getPredictionCol)
      .setProbabilityCol(getProbabilityCol)
      .setRawPredictionCol(getRawPredictionCol)
      .setLeafPredictionCol(getLeafPredictionCol)
      .setFeaturesShapCol(getFeaturesShapCol)
      .setActualNumClasses(classifierTrainParams.numClass)
      .setNumIterations(lightGBMBooster.bestIteration)
    if (isDefined(thresholds)) model.setThresholds(getThresholds) else model
  }

  def stringFromTrainedModel(model: LightGBMClassificationModel): String = {
    model.getModel.modelStr.get
  }

  override def copy(extra: ParamMap): LightGBMClassifier = defaultCopy(extra)

  @classmethod
  def _from_java(cls, java_stage):
    stage_name = java_stage.getClass().getName().replace("org.apache.spark", "pyspark")
    stage_name = stage_name.replace("com.microsoft.azure.synapse.ml", "synapse.ml")
    return from_java(java_stage, stage_name)
}

trait HasActualNumClasses extends Params {
  val actualNumClasses = new IntParam(this, "actualNumClasses",
    "Inferred number of classes based on dataset metadata or, if there is no metadata, unique count")
  def getActualNumClasses: Int = $(actualNumClasses)
  def setActualNumClasses(value: Int): this.type = set(actualNumClasses, value)
}

class LightGBMClassificationModel(override val uid: String)
    extends ProbabilisticClassificationModel[Vector, LightGBMClassificationModel]
      with LightGBMModelParams with LightGBMModelMethods with LightGBMPredictionParams
      with HasActualNumClasses with ComplexParamsWritable with SynapseMLLogging {
  logClass(FeatureNames.LightGBM)

  def this() = this(Identifiable.randomUID("LightGBMClassificationModel"))

  override protected lazy val pyInternalWrapper = true

  override def transform(dataset: Dataset[_]): DataFrame = {
    logTransform[DataFrame]({
      updateBoosterParamsBeforePredict()
      transformSchema(dataset.schema, logging = true)
      if (isDefined(thresholds)) {
        require(getThresholds.length == numClasses, this.getClass.getSimpleName +
          ".transform() called with non-matching numClasses and thresholds.length." +
          s" numClasses=$numClasses, but thresholds has length ${getThresholds.length}")
      }

      var outputData = dataset
      var numColsOutput = 0
      if (getRawPredictionCol.nonEmpty) {
        val predictRawUDF = udf(predictRaw _)
        outputData = outputData.withColumn(getRawPredictionCol, predictRawUDF(col(getFeaturesCol)))
        numColsOutput += 1
      }
      if (getProbabilityCol.nonEmpty) {
        val probabilityUDF = udf(predictProbability _)
        outputData = outputData.withColumn(getProbabilityCol, probabilityUDF(col(getFeaturesCol)))
        numColsOutput += 1
      }
      if (getPredictionCol.nonEmpty) {
        val predUDF = predictColumn
        outputData = outputData.withColumn(getPredictionCol, predUDF)
        numColsOutput += 1
      }
      if (getLeafPredictionCol.nonEmpty) {
        val predLeafUDF = udf(predictLeaf _)
        outputData = outputData.withColumn(getLeafPredictionCol, predLeafUDF(col(getFeaturesCol)))
        numColsOutput += 1
      }
      if (getFeaturesShapCol.nonEmpty) {
        val featureShapUDF = udf(featuresShap _)
        outputData = outputData.withColumn(getFeaturesShapCol, featureShapUDF(col(getFeaturesCol)))
        numColsOutput += 1
      }

      if (numColsOutput == 0) {
        this.logWarning(s"$uid: LightGBMClassificationModel.transform() was called as NOOP" +
          " since no output columns were set.")
      }
      outputData.toDF
    }, dataset.columns.length)
  }

  override protected def raw2probabilityInPlace(rawPrediction: Vector): Vector = {
    throw new NotImplementedError("Unexpected error in LightGBMClassificationModel:" +
      " raw2probabilityInPlace should not be called!")
  }

  override def numClasses: Int = getActualNumClasses

  override def predictRaw(features: Vector): Vector = {
    Vectors.dense(getModel.score(features, raw = true, classification = true, getPredictDisableShapeCheck))
  }

  override def predictProbability(features: Vector): Vector = {
    Vectors.dense(getModel.score(features, raw = false, classification = true, getPredictDisableShapeCheck))
  }

  override def copy(extra: ParamMap): LightGBMClassificationModel = defaultCopy(extra)

  protected def predictColumn: Column = {
    if (getRawPredictionCol.nonEmpty && !isDefined(thresholds)) {
      udf(raw2prediction _).apply(col(getRawPredictionCol))
    } else if (getProbabilityCol.nonEmpty) {
      udf(probability2prediction _).apply(col(getProbabilityCol))
    } else {
      udf(predict _).apply(col(getFeaturesCol))
    }
  }
}

object LightGBMClassificationModel extends ComplexParamsReadable[LightGBMClassificationModel] {
  def loadNativeModelFromFile(filename: String): LightGBMClassificationModel = {
    val uid = Identifiable.randomUID("LightGBMClassificationModel")
    val session = SparkSession.builder().getOrCreate()
    val textRdd = session.read.text(filename)
    val text = textRdd.collect().map { row => row.getString(0) }.mkString("\n")
    val lightGBMBooster = new LightGBMBooster(text)
    val actualNumClasses = lightGBMBooster.numClasses
    new LightGBMClassificationModel(uid).setLightGBMBooster(lightGBMBooster).setActualNumClasses(actualNumClasses)
  }

  def loadNativeModelFromString(model: String): LightGBMClassificationModel = {
    val uid = Identifiable.randomUID("LightGBMClassificationModel")
    val lightGBMBooster = new LightGBMBooster(model)
    val actualNumClasses = lightGBMBooster.numClasses
    new LightGBMClassificationModel(uid).setLightGBMBooster(lightGBMBooster).setActualNumClasses(actualNumClasses)
  }

  @classmethod
  def _from_java(cls, java_stage):
    stage_name = java_stage.getClass().getName().replace("org.apache.spark", "pyspark")
    stage_name = stage_name.replace("com.microsoft.azure.synapse.ml", "synapse.ml")
    return from_java(java_stage, stage_name)
}
