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

import com.atlassian.jira.rest.client.api.domain.IssueField

class JiraIssue {
    String key
    String type
    JiraIssue parent
    String summary
    String status
    Map<String, IssueField> customFields = new HashMap<>()

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        JiraIssue jiraIssue = (JiraIssue) o

        if (key != jiraIssue.key) return false
        if (parent != jiraIssue.parent) return false
        if (summary != jiraIssue.summary) return false
        if (type != jiraIssue.type) return false

        return true
    }

    int hashCode() {
        int result
        result = (key != null ? key.hashCode() : 0)
        result = 31 * result + (type != null ? type.hashCode() : 0)
        result = 31 * result + (parent != null ? parent.hashCode() : 0)
        result = 31 * result + (summary != null ? summary.hashCode() : 0)
        return result
    }

    void putCustomField(String key, IssueField value) {
        customFields.put(key, value)
    }

}
