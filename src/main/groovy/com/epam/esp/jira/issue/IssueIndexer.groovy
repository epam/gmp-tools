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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Class is used for issue indexing
 * TODO need to be re-factored
 */
class IssueIndexer implements Runnable {
    static final def BATCH_SIZE = 100
    static final String EXPAND_PARAMS = '?expand=changelog'
    static final String SUB_ISSUES_SEARCH_QUERY = "parent=%s";
    static final String URL_DEV_STATUS = 'rest/dev-status/latest/issue/detail'

    JiraHelper jiraHelper
    ElasticSearchHelper elasticSearchHelper
    Issue issue
    def fields
    boolean force
    def issuesCount
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
     * @param issuesCount
     * @param issueDocType
     * @param indexName
     */
    IssueIndexer(JiraHelper jiraHelper, ElasticSearchHelper elasticSearchHelper, Issue issue, fields, boolean force, issuesCount, String issueDocType, String indexName) {
        this.jiraHelper = jiraHelper
        this.elasticSearchHelper = elasticSearchHelper
        this.issue = issue
        this.fields = fields
        this.force = force
        this.issuesCount = issuesCount
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
     * @param issuesCount
     * @param issueDocType
     * @param indexName
     * @param isIssueDetailStashAdding - true if need to index detailStash
     */
    IssueIndexer(JiraHelper jiraHelper, ElasticSearchHelper elasticSearchHelper, Issue issue, fields, boolean force, issuesCount, boolean isIssueDetailStashAdding, String issueDocType, String indexName) {
        this.jiraHelper = jiraHelper
        this.elasticSearchHelper = elasticSearchHelper
        this.issue = issue
        this.fields = fields
        this.force = force
        this.issuesCount = issuesCount
        this.issueDocType = issueDocType
        this.indexName = indexName
        this.isIssueDetailStashAdding = isIssueDetailStashAdding
    }

    @Override
    void run() {
        logMessage += "$issuesCount : "
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

                def issueJson = new JsonSlurper().parseText(item)
                issueJson << [docType: issueDocType]

                logMessage += ' got result from API -> '
                logMessage += elasticSearchHelper.updateItem(indexName, issue.key, JsonOutput.toJson(issueJson))
            } catch (Exception e) {
                logger.error(e.getMessage(), e)
                logMessage += "$e.message $issue.self  unable to index issue"
            }
        } else {
            logMessage += "$issue.key is up to date, skipping...\n"
        }

        def subIssues = jiraHelper.findIssuesByJql(String.format(SUB_ISSUES_SEARCH_QUERY, issue.key), BATCH_SIZE, 0, fields);

        def iterator = subIssues.issues.iterator()
        while (iterator.hasNext()) {
            Issue subtask = iterator.next()
            if (force || !elasticSearchHelper.isJiraIssueInSync(indexName, subtask.key, subtask.updateDate)) {
                try {
                    def item = jiraHelper.getJsonFromUri(subtask.self.toString() + EXPAND_PARAMS);
                    logMessage += ' | -' + elasticSearchHelper.updateItem(indexName, subtask.key, item)
                } catch (Exception e) {
                    logMessage += " |- ${subtask.self.toString()} unable to get JSON for issue"
                }
            } else {
                logMessage += " |- $subtask.key is up to date, skipping...\n"
            }
        }
        logMessage += "\n"
        logger.info(logMessage)
    }
}
