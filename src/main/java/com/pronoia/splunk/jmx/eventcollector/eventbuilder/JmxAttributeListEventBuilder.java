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

package com.pronoia.splunk.jmx.eventcollector.eventbuilder;

import static com.pronoia.splunk.eventcollector.EventCollectorInfo.EVENT_BODY_KEY;

import com.pronoia.splunk.eventcollector.EventBuilder;
import java.util.HashMap;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;

/**
 * Splunk Event Builder for JMX AttributeLists.
 */
public class JmxAttributeListEventBuilder extends JmxEventBuilderSupport<AttributeList> {

  @Override
  public EventBuilder<AttributeList> duplicate() {
    JmxAttributeListEventBuilder answer = new JmxAttributeListEventBuilder();

    answer.copyConfiguration(this);

    return answer;
  }

  @Override
  protected void addEventBodyToMap(Map<String, Object> map) {
    log.debug("{}.serializeBody() ...", this.getClass().getName());

    Map<String, Object> eventBodyObject = new HashMap<>();

    for (Object attributeObject : this.getEventBody()) {
      Attribute attribute = (Attribute) attributeObject;
      addAttribute(eventBodyObject, attribute);
    }
    map.put(EVENT_BODY_KEY, eventBodyObject);
  }


}
