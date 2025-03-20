FROM ubuntu:20.04

ARG SYNAPSEML_VERSION=1.0.10
ARG DEBIAN_FRONTEND=noninteractive

ENV SPARK_VERSION=3.4.1
ENV HADOOP_VERSION=3
ENV SYNAPSEML_VERSION=${SYNAPSEML_VERSION}
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

# Combine apt-get update, install, and clean steps
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      curl \
      bzip2 \
      wget \
      openmpi-bin \
      unzip \
      zip \
      openjdk-8-jre && \
    rm -rf /var/lib/apt/lists/*

# Install SDKMAN! and sbt in one go
RUN curl -s "https://get.sdkman.io" | bash && \
    bash -c "source /root/.sdkman/bin/sdkman-init.sh && sdk install sbt"

# Install Miniconda and required Python packages
RUN curl -sSL https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -o /tmp/miniconda.sh && \
    bash /tmp/miniconda.sh -bfp /opt/conda && \
    rm -rf /tmp/miniconda.sh && \
    /opt/conda/bin/conda update -y conda && \
    /opt/conda/bin/conda install -y python=3 jupyter pyspark && \
    /opt/conda/bin/conda clean --all --yes

ENV PATH=/opt/conda/bin:$PATH

# Copy environment file and create the conda environment
COPY environment.yml /tmp/environment.yml
RUN conda env create -f /tmp/environment.yml && conda clean -afy

# Download and install Spark, then clean up the tarball immediately
RUN wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
    tar -xzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
    mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} /opt/spark && \
    rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz

ENV SPARK_HOME=/opt/spark
ENV PYTHONPATH=$SPARK_HOME/python/:$SPARK_HOME/python/lib/py4j*:$PYTHONPATH
ENV PATH=$SPARK_HOME/bin/:$SPARK_HOME/python/:$PATH
