# Copyright (C) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License. See LICENSE in project root for information.

from synapse.ml.lightgbm._LightGBMClassificationModel import (
    _LightGBMClassificationModel,
)
from synapse.ml.lightgbm.mixin import LightGBMModelMixin
from pyspark import SparkContext
from pyspark.ml.common import inherit_doc
from pyspark.ml.wrapper import JavaParams
from synapse.ml.core.serialize.java_params_patch import *


@inherit_doc
class LightGBMClassificationModel(LightGBMModelMixin, _LightGBMClassificationModel):
    @staticmethod
    def loadNativeModelFromFile(filename):
        """
        Load the model from a native LightGBM text file.
        """
        ctx = SparkContext._active_spark_context
        loader = (
            ctx._jvm.com.microsoft.azure.synapse.ml.lightgbm.LightGBMClassificationModel
        )
        java_model = loader.loadNativeModelFromFile(filename)
        return JavaParams._from_java(java_model)

    @staticmethod
    def loadNativeModelFromString(model):
        """
        Load the model from a native LightGBM model string.
        """
        ctx = SparkContext._active_spark_context
        loader = (
            ctx._jvm.com.microsoft.azure.synapse.ml.lightgbm.LightGBMClassificationModel
        )
        java_model = loader.loadNativeModelFromString(model)
        return JavaParams._from_java(java_model)

    @classmethod
    def _from_java(cls, java_stage):
        """
        Given a Java object, create and return a Python wrapper of it.
        Used for ML persistence.

        Args:
            java_stage (JavaObject): The Java object to convert.

        Returns:
            object: The Python wrapper.
        """
        stage_name = java_stage.getClass().getName().replace("org.apache.spark", "pyspark")
        stage_name = stage_name.replace("com.microsoft.azure.synapse.ml", "synapse.ml")
        return from_java(java_stage, stage_name)

    def getBoosterNumClasses(self):
        """Get the number of classes from the booster.

        Returns:
            The number of classes.
        """
        return self._java_obj.getBoosterNumClasses()
