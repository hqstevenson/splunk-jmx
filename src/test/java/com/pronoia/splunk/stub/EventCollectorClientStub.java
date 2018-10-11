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
package com.pronoia.splunk.stub;

import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;

import java.util.Map;


public class EventCollectorClientStub implements EventCollectorClient {
    public String lastEvent;

    @Override
    public String getClientId() {
        return "stubbed-client";
    }

    @Override
    public boolean hasEventHost() {
        return false;
    }

    @Override
    public String getEventHost() {
        return null;
    }

    @Override
    public boolean hasEventIndex() {
        return false;
    }

    @Override
    public String getEventIndex() {
        return null;
    }

    @Override
    public boolean hasEventSource() {
        return false;
    }

    @Override
    public String getEventSource() {
        return null;
    }

    @Override
    public boolean hasEventSourcetype() {
        return false;
    }

    @Override
    public String getEventSourcetype() {
        return null;
    }

    @Override
    public boolean hasConstantFields() {
        return false;
    }

    @Override
    public Map<String, String> getConstantFields() {
        return null;
    }

    @Override
    public Map<String, String> getConstantFields(boolean copy) {
        return null;
    }

    @Override
    public boolean hasIncludedSystemProperties() {
        return false;
    }

    @Override
    public Map<String, String> getIncludedSystemProperties() {
        return null;
    }

    @Override
    public Map<String, String> getIncludedSystemProperties(boolean copy) {
        return null;
    }

    @Override
    public boolean hasIncludedEnvironmentVariables() {
        return false;
    }

    @Override
    public Map<String, String> getIncludedEnvironmentVariables() {
        return null;
    }

    @Override
    public Map<String, String> getIncludedEnvironmentVariables(boolean copy) {
        return null;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void sendEvent(String event) throws EventDeliveryException {
        lastEvent = event;
    }
}