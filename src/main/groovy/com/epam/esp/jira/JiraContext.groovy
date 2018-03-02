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

package com.epam.esp.jira

/**
 * Is used to store jira credential and instance specific data
 */
class JiraContext {
    String url,
           user,
           password,
           epicLink

    List<String> customFields = [].asList()

    JiraContext setUrl(String url) {
        this.url = url
        return this
    }

    JiraContext setUser(String user) {
        this.user = user
        return this
    }

    JiraContext setPassword(String password) {
        this.password = password
        return this
    }

    JiraContext setEpicLink(String epicLink) {
        this.epicLink = epicLink
        return this
    }

    JiraContext setCustomFields(List<String> customFields) {
        this.customFields = customFields
        return this
    }
}
