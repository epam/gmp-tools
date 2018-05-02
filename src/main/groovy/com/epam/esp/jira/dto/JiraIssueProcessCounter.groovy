/*
 *  /***************************************************************************
 *  Copyright (c) 2017, EPAM SYSTEMS INC
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ***************************************************************************
 */

package com.epam.esp.jira.dto

import com.sun.javafx.binding.StringFormatter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder;

public class JiraIssueProcessCounter {

    private AtomicInteger issueCount
    private AtomicInteger subIssueCount
    private AtomicInteger issueCountUpdated
    private AtomicInteger subIssueCountUpdated
    private AtomicInteger issueCountSkipped
    private AtomicInteger subIssueCountSkipped
    private Map<String, LongAdder> duplicates

    JiraIssueProcessCounter() {
        issueCount = new AtomicInteger(0)
        subIssueCount = new AtomicInteger(0)
        issueCountUpdated = new AtomicInteger(0)
        subIssueCountUpdated = new AtomicInteger(0)
        issueCountSkipped = new AtomicInteger(0)
        subIssueCountSkipped = new AtomicInteger(0)

        duplicates = new ConcurrentHashMap<>()
    }

    private void checkDuplicates(String key) {
        duplicates.computeIfAbsent(key, { k -> new LongAdder() }).increment();
    }

    int processIssue(String key) {
        checkDuplicates(key)
        return issueCount.incrementAndGet()
    }

    int processSubIssue(String key) {
        checkDuplicates(key)
        return subIssueCount.incrementAndGet()
    }

    int updateIssue() {
        return issueCountUpdated.incrementAndGet()
    }

    int updateSubIssue() {
        return subIssueCountUpdated.incrementAndGet()
    }

    int skipIssue() {
        return issueCountSkipped.incrementAndGet()
    }

    int skipSubIssue() {
        return subIssueCountSkipped.incrementAndGet()
    }

    int getIssueCount() {
        return issueCount.get()
    }

    int getSubIssueCount() {
        return subIssueCount.get()
    }

    int getIssueCountUpdated() {
        return issueCountUpdated.get()
    }

    int getSubIssueCountUpdated() {
        return subIssueCountUpdated.get()
    }

    int getIssueCountSkipped() {
        return issueCountSkipped.get()
    }

    int getSubIssueCountSkipped() {
        return subIssueCountSkipped.get()
    }

    int getTotalProcessed() {
        return issueCount.get() + subIssueCount.get()
    }

    int getTotalUpdated() {
        return issueCountUpdated.get() + subIssueCountUpdated.get()
    }

    int getTotalSkipped() {
        return issueCountSkipped.get() + subIssueCountSkipped.get()
    }

    int getIssueErrorCount() {
        return getIssueCount() - (getIssueCountUpdated() + getIssueCountSkipped())
    }

    int getSubIssueErrorCount() {
        return getSubIssueCount() - (getSubIssueCountUpdated() + getSubIssueCountSkipped())
    }

    int getTotalErrorCount() {
        return getTotalProcessed() - (getTotalUpdated() + getTotalSkipped())
    }

    String getUpdatedReport() {
        int issues = getIssueCountUpdated()
        int subIssues = getSubIssueCountUpdated()
        return "Updated issues: $issues; sub issues: $subIssues; total:${issues + subIssues}"
    }

    String getFinalFullReport() {
        String report = "\nProcessed - \n" +
                "   issues: ${getIssueCount()} \n" +
                "   sub issues: ${getSubIssueCount()} \n" +
                "   total: ${getTotalProcessed()} \n" +
                "Updated - \n" +
                "   issues: ${getIssueCountUpdated()} \n" +
                "   sub issues: ${getSubIssueCountUpdated()} \n" +
                "   total: ${getTotalUpdated()} \n" +
                "Skipped - \n" +
                "   issues: ${getIssueCountSkipped()} \n" +
                "   sub issues: ${getSubIssueCountSkipped()} \n" +
                "   total: ${getTotalSkipped()} \n" +
                "Failed - \n" +
                "   issues: ${getIssueErrorCount()} \n" +
                "   sub issues: ${getSubIssueErrorCount()} \n" +
                "   total: ${getTotalErrorCount()} \n" +
                "Duplicates - \n"
        duplicates.each { k, v ->
            if (v.longValue() > 1l) {
                report += '      ' + k + ' appears ' + v + ' times\n'
            }
        }
        return report
    }
}
