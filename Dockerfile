FROM mcr.microsoft.com/oss/mirror/docker.io/library/ubuntu:20.04

ARG SYNAPSEML_VERSION=1.0.10
ARG DEBIAN_FRONTEND=noninteractive

# Configure Spark version and other env variables
ENV SPARK_VERSION=3.4.1
ENV HADOOP_VERSION=3
ENV SYNAPSEML_VERSION=${SYNAPSEML_VERSION}
# Use OpenJDK 8 for SynapseML
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

# Install base packages (curl, bzip2, wget, openmpi-bin, unzip, zip)
RUN apt-get -qq update \
    && apt-get -qq -y install \
      curl \
      bzip2 \
      wget \
      openmpi-bin \
      unzip \
      zip \
    && apt-get upgrade -y \
    && apt-get clean -y \
    && apt-get -qq -y autoremove \
    && apt-get autoclean \
    && rm -rf /var/lib/apt/lists/* /var/log/dpkg.log

# Install OpenJDK 8
RUN apt-get update && \
    apt-get install -y openjdk-8-jre && \
    apt-get clean -y && rm -rf /var/lib/apt/lists/*

# Install SDKMAN! non-interactively
RUN curl -s "https://get.sdkman.io" | bash

# Set up environment variables for SDKMAN! to work in non-login shells
ENV SDKMAN_DIR="/root/.sdkman"
ENV PATH="${SDKMAN_DIR}/bin:${PATH}"

# Ensure SDKMAN! is available for use in all subsequent commands
RUN bash -c "source ${SDKMAN_DIR}/bin/sdkman-init.sh && sdk version"

# Use SDKMAN! to install sbt
RUN bash -c "source ${SDKMAN_DIR}/bin/sdkman-init.sh && sdk install sbt"

# Install Miniconda and required Python packages
RUN curl -sSL https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -o /tmp/miniconda.sh \
    && bash /tmp/miniconda.sh -bfp /opt/conda \
    && rm -rf /tmp/miniconda.sh \
    && /opt/conda/bin/conda update -y conda \
    && /opt/conda/bin/conda install -y python=3 jupyter pyspark \
    && /opt/conda/bin/conda clean --all --yes

ENV PATH=/opt/conda/bin:$PATH

# Copy your environment.yml to the container and create the 'synapseml' environment
COPY environment.yml /tmp/environment.yml
RUN conda env create -f /tmp/environment.yml && conda clean -afy

# # Download and install Spark
# RUN wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
#     && tar -xzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
#     && mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} /opt/spark \
#     && rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz

# ENV SPARK_HOME=/opt/spark
# ENV PYTHONPATH=$SPARK_HOME/python/:$SPARK_HOME/python/lib/py4j*:$PYTHON_PATH
# ENV PATH=$SPARK_HOME/bin/:$SPARK_HOME/python/:$PATH

# Remove packages not needed anymore
RUN apt-get remove --purge -y \
        curl \
        bzip2 \
        wget \
        unzip \
        zip \
    && apt-get -qq -y autoremove \
    && apt-get autoclean \
    && rm -rf /var/lib/apt/lists/* /var/log/dpkg.log
