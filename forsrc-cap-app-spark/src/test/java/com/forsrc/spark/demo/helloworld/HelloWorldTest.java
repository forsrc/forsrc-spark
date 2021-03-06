/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.forsrc.spark.demo.helloworld;

import co.cask.cdap.api.metrics.RuntimeMetrics;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.FlowManager;
import co.cask.cdap.test.ServiceManager;
import co.cask.cdap.test.StreamManager;
import co.cask.cdap.test.TestBase;
import co.cask.cdap.test.TestConfiguration;

import com.forsrc.spark.cdap.spark.SparkWordCountApplication;
import com.forsrc.spark.cdap.spark.SparkWordCountConfig;
import com.forsrc.spark.cdap.spark.SparkWordCountService;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Test for {@link HelloWorld}.
 */
public class HelloWorldTest extends TestBase {

    @ClassRule
    public static final TestConfiguration CONFIG = new TestConfiguration(Constants.Explore.EXPLORE_ENABLED, false);

    @Test
    public void test() throws Exception {
        // Deploy the HelloWorld application
        ApplicationManager appManager = deployApplication(SparkWordCountApplication.class);

        SparkWordCountConfig config = new SparkWordCountConfig();

        // Start WhoFlow
        FlowManager flowManager = appManager.getFlowManager(config.getFlowName()).start();
        flowManager.waitForStatus(true);

        // Send stream events to the "who" Stream
        StreamManager streamManager = getStreamManager(config.getStream());
        streamManager.send("A");
        streamManager.send("A B");
        streamManager.send("A C");
        streamManager.send("D");
        streamManager.send("E");

        try {
            // Wait for the last Flowlet processing 5 events, or at most 5 seconds
            RuntimeMetrics metrics = flowManager.getFlowletMetrics(config.getSaver());
            metrics.waitForProcessed(5, 5, TimeUnit.SECONDS);
        } finally {
            flowManager.stop();
            Assert.assertFalse(flowManager.isRunning());
        }

        // Start Greeting service and use it
        ServiceManager serviceManager = appManager.getServiceManager(SparkWordCountService.SERVICE_NAME).start();

        // Wait service startup
        serviceManager.waitForStatus(true);

        URL url = new URL(serviceManager.getServiceURL(), "sparkWordCount/A");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        Assert.assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
        String response;
        try {
            response = new String(ByteStreams.toByteArray(connection.getInputStream()), Charsets.UTF_8);
        } finally {
            connection.disconnect();
        }
        System.err.println(response);
        Gson gson = new Gson();
        Map<String, Integer> map = gson.fromJson(response, new TypeToken<Map<String, Integer>>(){}.getType());
        Assert.assertEquals(3, map.get("count").intValue());

        // serviceManager.stop();
        appManager.stopAll();
    }
}
