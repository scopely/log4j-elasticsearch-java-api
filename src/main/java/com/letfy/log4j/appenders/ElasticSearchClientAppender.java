/*
 * Copyright 2012 Letfy Team <admin@letfy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.letfy.log4j.appenders;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.util.EC2MetadataUtils;
import com.google.common.base.Supplier;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Index;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import vc.inreach.aws.request.AWSSigner;
import vc.inreach.aws.request.AWSSigningRequestInterceptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Using ElasticSearch store LoggingEvent for insert the document log4j.
 *
 * @author Tran Anh Tuan <tk1cntt@gmail.com>
 */
public class ElasticSearchClientAppender extends AppenderSkeleton {

    private ExecutorService threadPool = Executors.newSingleThreadExecutor();
    private JestClient client;
    private String applicationName = "application";
    private String sourceId = "unknown";
    private String hostName = getInitialHostname();
    private String elasticIndex = getInitialIndex();
    private String elasticType = "logging";
    private String elasticHost = "http://localhost:9200";
    private String awsRegion = null;

    protected String getInitialHostname() {
        String host = "localhost";
        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException ex) {
        }
        return host;
    }

    protected String getInitialIndex() {
        final LocalDate localDate = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return String.format("log4j-elastic-%d-%02d", localDate.getYear(), localDate.getMonthValue());
    }

    /**
     * Submits LoggingEvent for insert the document if it reaches severity
     * threshold.
     *
     * @param loggingEvent
     */
    @Override
    protected void append(LoggingEvent loggingEvent) {
        if (isAsSevereAsThreshold(loggingEvent.getLevel())) {
            loggingEvent.getMDCCopy();
            threadPool.submit(new AppenderTask(loggingEvent));
        }
    }

    /**
     * Create ElasticSearch Client.
     *
     * @see AppenderSkeleton
     */
    @Override
    public void activateOptions() {
        // Need to do this if the cluster name is changed, probably need to set this and sniff the cluster
        try {
            // Configuration
            HttpClientConfig clientConfig = new HttpClientConfig.Builder(elasticHost).multiThreaded(true)
                    .connTimeout(5000)
                    .readTimeout(5000)
                    .build();

            // Construct a new Jest client according to configuration via factory
            JestClientFactory factory;

            if (awsRegion != null) {
                @SuppressWarnings("Guava") final Supplier<LocalDateTime> clock = () -> LocalDateTime.now(ZoneOffset.UTC);
                final AWSSigner awsSigner = new AWSSigner(new DefaultAWSCredentialsProviderChain(), awsRegion, "es", clock);
                final AWSSigningRequestInterceptor requestInterceptor = new AWSSigningRequestInterceptor(awsSigner);

                factory = new JestClientFactory() {
                    @Override
                    protected HttpClientBuilder configureHttpClient(HttpClientBuilder builder) {
                        builder.addInterceptorLast(requestInterceptor);
                        return builder;
                    }

                    @Override
                    protected HttpAsyncClientBuilder configureHttpClient(HttpAsyncClientBuilder builder) {
                        builder.addInterceptorLast(requestInterceptor);
                        return builder;
                    }
                };
            } else {
                factory = new JestClientFactory();
            }

            factory.setHttpClientConfig(clientConfig);
            client = factory.getObject();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        super.activateOptions();
    }

    public String getElasticHost() {
        return elasticHost;
    }

    public void setElasticHost(String elasticHost) {
        this.elasticHost = elasticHost;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public String getElasticIndex() {
        return elasticIndex;
    }

    public void setElasticIndex(String elasticIndex) {
        this.elasticIndex = elasticIndex;
    }

    public String getElasticType() {
        return elasticType;
    }

    public void setElasticType(String elasticType) {
        this.elasticType = elasticType;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Close Elastic Search client.
     */
    @Override
    public void close() {
        client.shutdownClient();
    }

    /**
     * Ensures that a Layout property is not required
     *
     * @return
     */
    @Override
    public boolean requiresLayout() {
        return false;
    }

    /**
     * Simple Callable class that insert the document into ElasticSearch
     */
    class AppenderTask implements Callable<LoggingEvent> {

        LoggingEvent loggingEvent;

        AppenderTask(LoggingEvent loggingEvent) {
            this.loggingEvent = loggingEvent;
        }

        protected void writeBasic(Map<String, Object> json, LoggingEvent event) {
            json.put("@timestamp", new Date(event.getTimeStamp()).toInstant().toString());
            json.put("hostName", getHostName());
            json.put("applicationName", getApplicationName());
            json.put("sourceId", getSourceId());
            json.put("logger", event.getLoggerName());
            json.put("level", event.getLevel().toString());
            json.put("message", event.getMessage());
        }

        protected void writeMDC(Map<String, Object> json, LoggingEvent event) {
            for (Object key : event.getProperties().keySet())
                // key cannot contain '.'
                json.put(String.format("mdc_%s", key.toString()), event.getProperties().get(key).toString());
        }

        protected void writeThrowable(Map<String, Object> json, LoggingEvent event) {
            ThrowableInformation ti = event.getThrowableInformation();
            if (ti != null) {
                Throwable t = ti.getThrowable();
                json.put("className", t.getClass().getCanonicalName());
                json.put("stackTrace", getStackTrace(t));
            }
        }

        protected String getStackTrace(Throwable aThrowable) {
            final Writer result = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(result);
            aThrowable.printStackTrace(printWriter);
            return result.toString();
        }


        private void writeExtras(Map<String, Object> json) {
            json.put("instanceId", EC2MetadataUtils.getInstanceId());
        }

        /**
         * Method is called by ExecutorService and insert the document into
         * ElasticSearch
         *
         * @return
         * @throws Exception
         */
        @Override
        public LoggingEvent call() {
            try {
                if (client != null) {
                    // Set up the es index response 
                    String uuid = UUID.randomUUID().toString();
                    Map<String, Object> data = new HashMap<String, Object>();

                    writeBasic(data, loggingEvent);
                    writeThrowable(data, loggingEvent);
                    writeMDC(data, loggingEvent);
                    writeExtras(data);
                    // insert the document into elasticsearch
                    Index index = new Index.Builder(data).index(getElasticIndex()).type(getElasticType()).id(uuid).build();
                    client.execute(index);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return loggingEvent;
        }
    }
}
