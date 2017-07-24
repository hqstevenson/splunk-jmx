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

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectedExcludedAttributeTest {
  Logger log = LoggerFactory.getLogger(this.getClass());

  Set<String> observedAttributes = new TreeSet<>();
  Set<String> excludedObservedAttributes = new TreeSet<>();
  Set<String> collectedAttributes = new TreeSet<>();

  public boolean canAddToObservedAttributes(String attributeName) {
    boolean canAdd = true;
    if (excludedObservedAttributes.contains(attributeName)) {
      canAdd = false;
    }
    if (collectedAttributes.contains(attributeName)) {
      canAdd = true;
    }
    return canAdd;
  }

  @Test
  public void testData() throws Exception {
    final String[] testValues = new String[] {"String 0", "String 1", "String 2", "String 3", "String 4"};

    excludedObservedAttributes.add(testValues[1]);
    excludedObservedAttributes.add(testValues[3]);
    collectedAttributes.add(testValues[1]);
    collectedAttributes.add(testValues[2]);

    if (canAddToObservedAttributes(testValues[1])) {
      observedAttributes.add(testValues[1]);
    }

    if (canAddToObservedAttributes(testValues[2])) {
      observedAttributes.add(testValues[2]);
    }

    if (canAddToObservedAttributes(testValues[3])) {
      observedAttributes.add(testValues[3]);
    }

    if (canAddToObservedAttributes(testValues[4])) {
      observedAttributes.add(testValues[4]);
    }

    log.info("Final observedAttributes Set: {}", observedAttributes);
  }

}
