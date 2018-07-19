/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pronoia.splunk.jmx;

import com.pronoia.splunk.stub.EventCollectorClientStub;

import org.junit.Before;
import org.junit.Test;

public class SplunkJmxNotificationListenerTest {
    SplunkJmxNotificationListener instance;

    /**
     * Setup the instance for the test.
     *
     * @throws Exception raised in the event of a test error
     */
    @Before
    public void setUp() throws Exception {
        instance = new SplunkJmxNotificationListener();
        instance.setSplunkClient(new EventCollectorClientStub());
        // instance.addSourceMBeans("java.lang:type=GarbageCollector,name=PS MarkSweep");
        instance.addSourceMBeans("java.lang:type=GarbageCollector,name=*");
    }

    @Test
    public void testStart() throws Exception {
        instance.start();

        Thread.sleep(1000);
        System.gc();
        Thread.sleep(1000);

        instance.stop();
    }

}