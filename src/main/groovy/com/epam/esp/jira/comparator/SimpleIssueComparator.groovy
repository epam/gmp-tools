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

package com.epam.esp.jira.comparator

import com.epam.esp.jira.dto.JiraIssue

/**
 * Is used to sort JiraIssue
 * Order = Issue Type then Issue Key
 */
class SimpleIssueComparator implements Comparator<JiraIssue> {
    @Override
    int compare(JiraIssue o1, JiraIssue o2) {
        if (o1 == null) {
            return 1;
        }
        if (o2 == null) {
            return -1;
        }
        // compare issue type
        def typeCmp = o1.type.compareTo(o2.type)
        if (typeCmp == 0) {
            // if issue type are same compare issue keys
            return o1.key.compareTo(o2.key)
        } else {
            return typeCmp
        }
    }
}
