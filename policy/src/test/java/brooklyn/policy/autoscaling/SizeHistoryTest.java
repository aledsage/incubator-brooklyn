/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.autoscaling;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.policy.autoscaling.SizeHistory.WindowSummary;
import brooklyn.util.time.Duration;

public class SizeHistoryTest {

    @Test
    public void testSummariseEmptyHistory() throws Exception {
        SizeHistory history = new SizeHistory(Duration.THIRTY_SECONDS);
        
        assertWindowSummary(history.summarizeWindow(Duration.THIRTY_SECONDS),
                Integer.MIN_VALUE, Integer.MAX_VALUE, -1, false, false);
        assertWindowSummary(history.summarizeWindow(Duration.ZERO),
                Integer.MIN_VALUE, Integer.MAX_VALUE, -1, false, false);
    }

    @Test
    public void testSummariseWhenSingleRecentValue() throws Exception {
        long now = System.currentTimeMillis();
        SizeHistory history = new SizeHistory(Duration.THIRTY_SECONDS);
        history.add(10, now);
        
        // Not stable, because don't have 30 seconds of history;
        // therefore reports as though had extreme value until we have sufficient history.
        assertWindowSummary(history.summarizeWindow(Duration.THIRTY_SECONDS),
                Integer.MIN_VALUE, Integer.MAX_VALUE, 10, false, false);
        
        // Stable if look at window of size zero
        assertWindowSummary(history.summarizeWindow(Duration.ZERO, now),
                10, 10, 10, true, true);
    }

    @Test
    public void testSummariseWhenMultipleIdenticalRecentValues() throws Exception {
        SizeHistory history = new SizeHistory(Duration.THIRTY_SECONDS);
        long now = System.currentTimeMillis();
        history.add(10, now - 2*1000);
        history.add(10, now - 1*1000);
        history.add(10, now);
        
        // Not enough history to be stable.
        assertWindowSummary(history.summarizeWindow(Duration.THIRTY_SECONDS, now),
                Integer.MIN_VALUE, Integer.MAX_VALUE, 10, false, false);
        
        // Stable if look at window of size just two seconds
        assertWindowSummary(history.summarizeWindow(Duration.seconds(2), now),
                10, 10, 10, true, true);
    }

    @Test
    public void testSummariseWhenMultipleRecentDifferentValues() throws Exception {
        SizeHistory history = new SizeHistory(Duration.THIRTY_SECONDS);
        long now = System.currentTimeMillis();
        history.add(1, now - 2*1000);
        history.add(2, now - 1*1000);
        history.add(3, now);
        
        // Not enough history to be stable.
        assertWindowSummary(history.summarizeWindow(Duration.THIRTY_SECONDS, now),
                Integer.MIN_VALUE, Integer.MAX_VALUE, 3, false, false);

        // Stable if look at window of size just two seconds
        assertWindowSummary(history.summarizeWindow(Duration.seconds(2), now),
                1, 3, 3, false, false);

        // Stable if look with no history; 
        // TODO but will have kept "most recent expired"! Is that right?
        assertWindowSummary(history.summarizeWindow(Duration.ZERO, now),
                2, 3, 3, false, false);
    }

    @Test
    public void testSummariseWhenSingleOutOfDateValue() throws Exception {
        SizeHistory history = new SizeHistory(Duration.THIRTY_SECONDS);
        history.add(10, System.currentTimeMillis() - 31*1000);
        
        // Keeps most recent expired value, to extrapolate from.
        // therefore reports as though had extreme value until we have sufficient history.
        assertWindowSummary(history.summarizeWindow(Duration.THIRTY_SECONDS),
                10, 10, 10, true, true);
        
        // Stable if look at window of size zero
        assertWindowSummary(history.summarizeWindow(Duration.ZERO),
                10, 10, 10, true, true);
    }

    @Test
    public void testSummariseWhenMultipleOutOfDateValue() throws Exception {
        SizeHistory history = new SizeHistory(Duration.THIRTY_SECONDS);
        history.add(100, System.currentTimeMillis() - 32*1000);
        history.add(10, System.currentTimeMillis() - 31*1000);
        
        // Keeps only most recent expired value, to extrapolate from.
        // Will have discarded the "100", and assumes has been stable for last 31 seconds.
        // therefore reports as though had extreme value until we have sufficient history.
        assertWindowSummary(history.summarizeWindow(Duration.THIRTY_SECONDS),
                10, 10, 10, true, true);
        
        // Stable if look at window of size zero
        assertWindowSummary(history.summarizeWindow(Duration.ZERO),
                10, 10, 10, true, true);
    }

    private void assertWindowSummary(WindowSummary summary, long min, long max, long latest, boolean stableForGrowth, boolean stableForShrinking) {
        assertEquals(summary.min, min, "summary="+summary);
        assertEquals(summary.max, max, "summary="+summary);
        assertEquals(summary.latest, latest, "summary="+summary);
        assertEquals(summary.stableForGrowth, stableForGrowth, "summary="+summary);
        assertEquals(summary.stableForShrinking, stableForShrinking, "summary="+summary);
    }
}
