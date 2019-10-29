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

import com.atlassian.jira.rest.client.api.IssueRestClient
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.JiraRestClientFactory
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.IssueField
import com.atlassian.jira.rest.client.api.domain.IssueFieldId
import com.atlassian.jira.rest.client.api.domain.SearchResult
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.epam.dep.esp.common.web.Web
import com.epam.esp.jira.dto.JiraIssue
import com.epam.esp.vcs.dto.Commit
import io.atlassian.util.concurrent.Promise
import net.sf.ehcache.Cache
import net.sf.ehcache.CacheManager
import net.sf.ehcache.Element
import org.apache.http.auth.UsernamePasswordCredentials
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Matcher

class JiraHelper {
    public static def JIRA_ISSUE_PATTERN = '((?<!([A-Z]{1,10})-?)[A-Z]+-\\d+)'
    public static final String JIRA_DATE_FORMAT = "YYYY-MM-dd'T'HH:mm:ss.SSSZ"

    final int BATCH_SIZE = 200;
    final static Logger logger = LoggerFactory.getLogger(JiraHelper.class)
    JiraRestClient client
    private Cache issueCache
    JiraContext jiraContext

    protected Web web = null;

    void setWeb(Web web) {
        this.web = web
    }

    List fields = [
            IssueFieldId.SUMMARY_FIELD.id,
            IssueFieldId.ISSUE_TYPE_FIELD.id,
            IssueFieldId.CREATED_FIELD.id,
            IssueFieldId.UPDATED_FIELD.id,
            IssueFieldId.PROJECT_FIELD.id,
            IssueFieldId.STATUS_FIELD.id,
            IssueFieldId.REPORTER_FIELD.id,
            IssueFieldId.ASSIGNEE_FIELD.id,
            IssueFieldId.PRIORITY_FIELD.id,
            //IssueFieldId.ATTACHMENT_FIELD.id,
            IssueFieldId.RESOLUTION_FIELD.id,
            IssueFieldId.COMPONENTS_FIELD.id,
            IssueFieldId.LABELS_FIELD.id,
            IssueFieldId.DESCRIPTION_FIELD.id,
            IssueFieldId.UPDATED_FIELD.id,
            IssueFieldId.SUBTASKS_FIELD.id,
            IssueFieldId.COMMENT_FIELD.id,
            "parent"].asList();

    JiraHelper(JiraContext jiraContext) {
        this.jiraContext = jiraContext
        JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory()
        URI uri = new URI(jiraContext.url);
        fields.addAll(jiraContext.customFields)
        client = factory.createWithBasicHttpAuthentication(uri, jiraContext.user, jiraContext.password)
        CacheManager cacheManager = CacheManager.getInstance()
        Cache cache = cacheManager.getCache(jiraContext.url)
        if (cache == null) {
            cacheManager.addCache(jiraContext.url)
            cache = cacheManager.getCache(jiraContext.url)
        }
        issueCache = cache
    }

    static def List<String> extractIssues(comment) {
        logger.info("comment='$comment'")
        Matcher matcher = comment =~ JIRA_ISSUE_PATTERN
        def result = new ArrayList<String>()
        while (matcher.find()) {
            def issueId = matcher.group(0)
            logger.info("\tIssue detected='$issueId'")
            result.add(issueId)
        }
        return result
    }

    static def void preserveIssues(Collection<Commit> commits) {
        commits.each {
            it.jiraIssues = extractIssues(it.comment)
        }
    }

    def mapIssue(issueId, boolean onlyTasks) {
        try {
            Issue jIssue = getIssueById(issueId)

            if (jIssue != null) {
                logger.info("\tIssue $issueId: EXISTS")
                def issue = new JiraIssue()
                issue.setKey(jIssue.key)
                issue.setType(jIssue.issueType.name)
                issue.setSummary(jIssue.summary)
                issue.setStatus(jIssue.status.name)

                jiraContext.customFields.each { String fieldKey ->
                    IssueField field = jIssue.getField(fieldKey);
                    if (field != null && field.value != null) {
                        issue.putCustomField(fieldKey, field)
                    }
                }

                //Parent issue
                def parent = jIssue.getField('parent')
                if (parent != null && parent.value != null) {
                    def parentId = parent.value.get('key')
                    logger.info("\tChecking parent: $parentId")
                    def mParent = mapIssue(parentId, onlyTasks)
                    issue.setParent(mParent)
                    if (onlyTasks) {
                        return mParent
                    }
                }

                //Epic
                if (jiraContext.epicLink != null) {
                    def epic = jIssue.getField(jiraContext.epicLink)
                    if (epic != null && epic.value != null) {
                        def mEpic = mapIssue(epic.value, onlyTasks)
                        issue.setParent(mEpic)
                    }
                }
                return issue
            } else {
                logger.info("\tIssue $issueId: NOT EXISTS")
                return null
            }
        } catch (RuntimeException e) {
            logger.error("Unable to find issue: '$issueId' ", e)
            return null
        }
    }

    /**
     * Updates commit issues list with actual issues exists in jira
     * @param commits
     * @param onlyTasks if true then parent issue for sub issue is used
     * @return
     */
    def checkIssues(Collection<Commit> commits, boolean onlyTasks) {
        commits.each { Commit commit ->
            def jiraIssueIds = extractIssues(commit.comment)
            def issues = new ArrayList<JiraIssue>()
            jiraIssueIds.each { String issueId ->
                def issue = mapIssue(issueId, onlyTasks)
                if (issue != null) {
                    issues.add(issue)
                }
            }
            commit.jiraIssues = issues
        }
    }

    /**
     *
     * @param jqlString
     * @param maxResults
     * @param startAt
     * @param fields
     * @return
     */
    SearchResult findIssuesByJql(String jqlString, Integer maxResults, Integer startAt, Set<String> fields) {
        SearchResult result = null;
        while (result == null) {
            try {
                Promise<SearchResult> searchPromise = client.getSearchClient().searchJql(jqlString, maxResults, startAt, fields)
                result = searchPromise.claim()
            } catch (RuntimeException e) {
                logger.error("Got error: $e.message")
                logger.error("Will repeat in 3 sec...")
                Thread.sleep(3000)
                result = null
            }
        }
        return result;
    }

    /**
     *
     * @param jql
     * @return
     */

    def getIssues(String jql) {
        def result = new ArrayList<JiraIssue>()
        def fieldSet = new HashSet<String>()
        fieldSet.addAll(fields)
        def issues = findIssuesByJql(jql, BATCH_SIZE, 0, fieldSet)
        issues.issues.each { Issue issue ->
            JiraIssue jIssue = mapIssue(issue.key, false)
            if (jIssue != null) {
                result.add(jIssue)
            }
        }
        return result
    }

    /**
     *
     * @param issueId
     * @return
     */

    Issue getIssueById(String issueId) {
        Element cacheItem = issueCache.get(issueId)
        Issue jIssue
        if (cacheItem == null) {
            logger.info("\t\tDirect call.")
            def expand = [IssueRestClient.Expandos.CHANGELOG].asList();
            Promise<Issue> issuePromise = client.getIssueClient().getIssue(issueId, expand)
            jIssue = issuePromise.claim()
            issueCache.put(new Element(issueId, jIssue))
        } else {
            logger.info("\t\tCache hit.")
            jIssue = cacheItem.getObjectValue()
        }
        return jIssue
    }

    /**
     *
     * @param uri
     * @return
     */
    def getJsonFromUri(String uri) {
        //TODO cache credentials
        return web.get(uri, null, null, new UsernamePasswordCredentials(jiraContext.user, jiraContext.password));
    }

    public void close() {
        if (client != null) {
            client.close()
            logger.info('Closing Jira client...');
        }
        if (web != null) {
            web.close()
        }
    }
}
