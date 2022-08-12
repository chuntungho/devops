# https://registry.hub.docker.com/_/elasticsearch
# After ES 7.11, license changed.
FROM elasticsearch:7.9.3

# fix log4j vulnerability issue
# https://logging.apache.org/log4j/2.x/security.html
RUN cd /usr/share/elasticsearch/lib/ && rm log4j-* \
    && curl -O https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.17.2/log4j-core-2.17.2.jar \
    && curl -O https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.17.2/log4j-api-2.17.2.jar

# https://github.com/medcl/elasticsearch-analysis-ik/releases
RUN cd /usr/share/elasticsearch/plugins \
    && curl -O https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.9.3/elasticsearch-analysis-ik-7.9.3.zip \
    && mkdir analysis-ik \
    && unzip elasticsearch-analysis-ik-7.9.3.zip -d analysis-ik \
    && rm -rf elasticsearch-analysis-ik-7.9.3.zip

WORKDIR /usr/share/elasticsearch
