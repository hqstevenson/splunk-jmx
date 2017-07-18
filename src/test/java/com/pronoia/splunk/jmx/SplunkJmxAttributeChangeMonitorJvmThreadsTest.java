/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pronoia.splunk.jmx;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.pronoia.splunk.stub.EventCollectorClientStub;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SplunkJmxAttributeChangeMonitorJvmThreadsTest {
  SplunkJmxAttributeChangeMonitor instance;

  @Before
  public void setUp() throws Exception {
    instance = new SplunkJmxAttributeChangeMonitor();

    instance.setObservedObjects("java.lang:type=Threading");
  }

  @Test
  public void testStartStop() throws Exception {
    EventCollectorClientStub clientStub = new EventCollectorClientStub();
    Logger log = LoggerFactory.getLogger(this.getClass());
    log.info("starting start stop test case....");
    instance.setSplunkClient(clientStub);
    instance.setGranularityPeriod(1);

    instance.start();
    Thread.sleep(1500);
    instance.stop();

    assertNotNull(clientStub.lastEvent);
  }
}