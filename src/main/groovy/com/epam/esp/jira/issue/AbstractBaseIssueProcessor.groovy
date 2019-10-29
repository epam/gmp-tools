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

package com.epam.esp.jira.issue

import com.epam.esp.jira.JiraHelper
import org.elasticsearch.search.SearchHit
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter;

public abstract class AbstractBaseIssueProcessor implements IssueProcessor {
    public static final String UNKNOWN = 'unknown'
    public static final String ID_FIELD = 'id'
    public static final String PROJECT_FIELD = 'project'
    public static final String DOC_TYPE_FIELD = 'docType'
    public static final String ISSUE_TYPE_FIELD = 'issueType'
    public static final String TIMESTAMP = 'timestamp'
    public static final String PRIORITY_FIELD = 'priority'

    /**
     * @param searchHit
     * @param fieldName
     * @return concatenated document.fields[$fieldName].[0].value + document.fields[$fieldName].[n].value
     */
    def getMultiFieldValues(SearchHit searchHit, String fieldName) {
        def field = searchHit.sourceAsMap.fields[fieldName]
        if (field != null) {
            List<String> result = new ArrayList<String>()
            field.each {
                result.add(it.value)
            }
            result.sort()
            return result.join('+')
        } else return UNKNOWN
    }

    /**
     *
     * @param searchHit
     * @param fieldName
     * @param valueName
     * @return document.fields[$fieldName].$valueName
     */
    def getField(SearchHit searchHit, String fieldName, String valueName) {
        def field = searchHit.sourceAsMap.fields[fieldName]
        if (field != null) {
            return field[valueName]
        } else return UNKNOWN
    }

    /**
     *
     * @param searchHit
     * @param fieldName
     * @return document.fields[$fieldName].value
     */
    def getFieldValue(SearchHit searchHit, String fieldName) {
        getField(searchHit, fieldName, 'value')
    }

    /**
     *
     * @param searchHit
     * @return fields.priority.name
     */
    String getPriority(SearchHit searchHit) {
        return getField(searchHit, 'priority', 'name')
    }

    @Override
    Map<String, Object> process(SearchHit searchHit) {
        Map document = new HashMap()
        document.put(ID_FIELD, searchHit.id)
        document.put(PROJECT_FIELD, searchHit.sourceAsMap.fields.project.key)
        document.put(ISSUE_TYPE_FIELD, searchHit.sourceAsMap.fields.issuetype.name)
        document.put(PRIORITY_FIELD, getPriority(searchHit))
        document.put(DOC_TYPE_FIELD, getEventName())
        return document
    }

    @Override
    void postProcess() {
    }

    /**
     *
     * @param items
     * @param statuses
     * @return true if transition (items) has 'status' eq to any of the list (statuses)
     */
    public boolean isTransitionTo(items, String... statuses) {
        def result = false
        items.each { item ->
            if (item.field.equalsIgnoreCase('status')) {
                if (statuses.find { it.equalsIgnoreCase(item.toString) } != null) result = true
            }
        }
        return result
    }

    /**
     *
     * @param issue
     * @param statuses
     * @return time when the latest transition to any of statuses was performed or null if none
     */
    public def lastTransitionTo(SearchHit issue, String... statuses) {
        def resultDate = null
        def changeLog = issue.sourceAsMap.changelog
        if (changeLog != null) {
            def histories = changeLog.histories
            if (histories == null) return null
            else {

                DateTimeFormatter dtf = DateTimeFormat.forPattern(JiraHelper.JIRA_DATE_FORMAT)
                histories.each { history ->
                    if (isTransitionTo(history.items, statuses)) {
                        def date = dtf.parseDateTime((String) history.created)
                        if (resultDate == null) {
                            resultDate = date
                        } else {
                            if (resultDate.getMillis() < date.getMillis()) {
                                resultDate = date
                            }
                        }
                    }
                }
            }
        }
        return resultDate
    }

    /**
     *
     * @param searchHit
     * @param fieldName
     * @param valueName
     * @return fields[fieldName].[..].valueName as a list
     */
    def getMultiFieldAsList(SearchHit searchHit, String fieldName, String valueName) {
        def field = searchHit.sourceAsMap.fields[fieldName]
        if (field != null) {
            List<String> result = new ArrayList<String>()
            field.each {
                result.add(it[valueName])
            }
            return result
        } else return UNKNOWN
    }
}