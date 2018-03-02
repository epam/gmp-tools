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

package com.epam.esp.vcs.dto

import com.epam.dep.esp.common.json.JsonMapper
import com.epam.esp.jira.dto.JiraIssue

/**
 * Commit data object
 */
class Commit {
    String author
    String date
    String comment
    String hash
    List<JiraIssue> jiraIssues
    List<String> files

    @Override
    public String toString() {
        return JsonMapper.getInstance().map(true, true, this);
    }
}
