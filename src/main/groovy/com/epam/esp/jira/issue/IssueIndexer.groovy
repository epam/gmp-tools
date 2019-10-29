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

import com.atlassian.jira.rest.client.api.domain.Issue
import com.epam.esp.elasticsearch.ElasticSearchHelper
import com.epam.esp.jira.JiraHelper
import com.epam.esp.jira.dto.JiraIssueProcessCounter
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicInteger

/**
 * Class is used for issue indexing
 * TODO need to be re-factored
 */
class IssueIndexer implements Runnable {
    static final String EXPAND_PARAMS = '?expand=changelog'
    static final String SUB_ISSUES_SEARCH_QUERY = "parent=%s";
    static final String URL_DEV_STATUS = 'rest/dev-status/latest/issue/detail'

    JiraHelper jiraHelper
    ElasticSearchHelper elasticSearchHelper
    Issue issue
    def fields
    boolean force
    JiraIssueProcessCounter counter
    def issueDocType
    def indexName
    boolean isIssueDetailStashAdding = false

    final static Logger logger = LoggerFactory.getLogger(IssueIndexer.class)

    def logMessage = '\n'

    /**
     *
     * @param jiraHelper
     * @param elasticSearchHelper
     * @param issue
     * @param fields - list of fields used
     * @param force - do not check if the issue is up to date in Elasticsearch index, force re-index
     * @param counter - JiraIssueProcessCounter
     * @param issueDocType
     * @param indexName
     */
    IssueIndexer(JiraHelper jiraHelper, ElasticSearchHelper elasticSearchHelper, Issue issue, fields, boolean force, JiraIssueProcessCounter counter, String issueDocType, String indexName) {
        this.jiraHelper = jiraHelper
        this.elasticSearchHelper = elasticSearchHelper
        this.issue = issue
        this.fields = fields
        this.force = force
        this.counter = counter
        this.issueDocType = issueDocType
        this.indexName = indexName
    }

    /**
     *
     * @param jiraHelper
     * @param elasticSearchHelper
     * @param issue
     * @param fields - list of fields used
     * @param force - do not check if the issue is up to date in Elasticsearch index, force re-index
     * @param counter - JiraIssueProcessCounter
     * @param issueDocType
     * @param indexName
     * @param isIssueDetailStashAdding - true if need to index detailStash
     */
    IssueIndexer(JiraHelper jiraHelper, ElasticSearchHelper elasticSearchHelper, Issue issue, fields, boolean force, JiraIssueProcessCounter counter, boolean isIssueDetailStashAdding, String issueDocType, String indexName) {
        this.jiraHelper = jiraHelper
        this.elasticSearchHelper = elasticSearchHelper
        this.issue = issue
        this.fields = fields
        this.force = force
        this.counter = counter
        this.issueDocType = issueDocType
        this.indexName = indexName
        this.isIssueDetailStashAdding = isIssueDetailStashAdding
    }

    @Override
    void run() {
        logMessage += "${issue.key} : "
        counter.processIssue(issue.key)
        if (force || !elasticSearchHelper.isJiraIssueInSync(indexName, issue.key, issue.updateDate)) {
            try {
                def item = jiraHelper.getJsonFromUri(issue.self.toString() + EXPAND_PARAMS);
                if (isIssueDetailStashAdding && issue.id != null) {
                    try {
                        def detailStash = jiraHelper.getJsonFromUri("${jiraHelper.jiraContext.url}/${URL_DEV_STATUS}?issueId=${issue.id}&applicationType=stash&dataType=repository")
                        item = item.substring(0, item.length()-1) + ', \"detailStash\":' + detailStash + '}'
                    } catch (Exception e) {
                        logMessage += "$e.message $issue.self  unable to index issue detail stash"
                    }
                }

                updateIssue(item)
                counter.updateIssue()
            } catch (Exception e) {
                logger.error(e.getMessage(), e)
                logMessage += "$e.message $issue.self  unable to index issue"
            }
        } else {
            counter.skipIssue()
            logMessage += "$issue.key is up to date, skipping...\n"
        }

        def subIssues = jiraHelper.findIssuesByJql(String.format(SUB_ISSUES_SEARCH_QUERY, issue.key), null, null, fields)
        def iterator = subIssues.issues.iterator()
        while (iterator.hasNext()) {
            Issue subtask = iterator.next()
            counter.processSubIssue(subtask.key)
            logMessage += " |- ${subtask.key} : "
            if (force || !elasticSearchHelper.isJiraIssueInSync(indexName, subtask.key, subtask.updateDate)) {
                try {
                    def item = jiraHelper.getJsonFromUri(subtask.self.toString() + EXPAND_PARAMS);
                    updateIssue(item)
                    counter.updateSubIssue()
                } catch (Exception e) {
                    logger.error(e.getMessage(), e)
                    logMessage += " |- ${subtask.self.toString()} unable to get JSON for issue"
                }
            } else {
                counter.skipSubIssue()
                logMessage += " |- $subtask.key is up to date, skipping...\n"
            }
        }
        logMessage += "\n"
        logger.info(logMessage)
        logger.info("\n" + counter.getUpdatedReport())
    }

    private void updateIssue(String issue) {
        def issueJson = new JsonSlurper().parseText(issue)
        issueJson << [docType: issueDocType]
        logMessage += ' got result from API -> '
        logMessage += elasticSearchHelper.updateItem(indexName, issueJson['key'], JsonOutput.toJson(issueJson))
    }
}
