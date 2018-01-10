# Scopely's fork

This fork sings the HTTP requests with AWS credentials which allows us
to send the data to Amazon's ElasticSearch clusters.

It also replaced Maven with Gradle.

log4j-elasticsearch-java-api
============================

Using log4j insert log info into ElasticSearch.

### Build the lib ###

```
./gradlew shadowJar
```

Copy log4j-elasticsearch.jar and all lib depend in target/lib to using.

### The configuration is simple Properties Configuration ###
<pre><code>
# RootLogger
log4j.rootLogger=INFO,stdout,elastic

# Logging Threshold
log4j.threshhold=ALL

#
# stdout
# Add *stdout* to rootlogger above if you want to use this
#
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=%d{ISO8601} %-5p %c{2} (%F:%M(%L)) - %m%n

# ElasticSearch log4j appender for application
log4j.appender.elastic=com.letfy.log4j.appenders.ElasticSearchClientAppender
</code></pre>

### The configuration is advance Properties Configuration ###
<pre><code>
....

# ElasticSearch log4j appender for application
log4j.appender.elastic=com.letfy.log4j.appenders.ElasticSearchClientAppender
log4j.appender.elastic.elasticHost=http://localhost:9200
log4j.appender.elastic.hostName=my_laptop
log4j.appender.elastic.applicationName=demo
log4j.appender.elastic.elasticIndex=logging-index
log4j.appender.elastic.elasticType=logging

# defaults to null. if provided, requests are signed for use against an AWS ES cluster
log4j.appender.elastic.awsRegion=us-east-1
</code></pre>
