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

package com.epam.esp.elasticsearch

import com.epam.esp.jira.JiraHelper
import groovy.json.JsonSlurper
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.Client
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.client.Requests
import org.elasticsearch.cluster.metadata.AliasMetaData
import org.elasticsearch.cluster.metadata.IndexMetaData
import org.elasticsearch.common.collect.ImmutableOpenMap
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.transport.client.PreBuiltTransportClient
import org.elasticsearch.xpack.client.PreBuiltXPackTransportClient
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class ElasticSearchHelper {
    final static Logger logger = LoggerFactory.getLogger(ElasticSearchHelper.class)
    String defaultIndex = null
    String defaultType = null
    Client client;

    /**
     *
     * @param indexName
     * @return true if given index exists
     */
    boolean isIndexExists(String indexName) {
        IndexMetaData indexMetaData = client.admin().cluster()
                .state(Requests.clusterStateRequest())
                .actionGet()
                .getState()
                .getMetaData()
                .index(indexName);
        return (indexMetaData != null);
    }

    /**
     *
     * @param documentId
     * @return true if document with given id exists
     */
    boolean isDocumentExists(String documentId) {
        return isDocumentExists(documentId, defaultType, defaultIndex)
    }

    /**
     *
     * @param indexName
     * @param documentType
     * @param documentId
     * @return true if document exists
     */

    boolean isDocumentExists(String indexName, String documentType, String documentId) {
        def document = getDocumentById(indexName, documentType, documentId)
        return document.exists
    }

    /**
     *
     * @param documentId
     * @return document with given ID
     */
    def getDocumentById(String documentId) {
        getDocumentById(defaultIndex, defaultType, documentId)
    }

    /**
     *
     * @param indexName
     * @param documentType
     * @param documentId
     * @return document with given index, type, id
     */
    def getDocumentById(String indexName, String documentType, String documentId) {
        return client.prepareGet(indexName, documentType, documentId).get()
    }

    /**
     * JIRA 'issue' document specific
     * @param documentId
     * @param date
     * @return true if 'issue' update date is the same
     */
    boolean isInSync(String documentId, date) {
        return isInSync(documentId, defaultType, date);
    }

    /**
     * JIRA 'issue' document specific
     * @param documentId
     * @param documentType
     * @param date
     * @return true if 'issue' update date is the same
     */
    boolean isInSync(String documentId, String documentType, DateTime date) {
        GetResponse response = client.prepareGet().setId(documentId).setIndex(defaultIndex).setType(documentType).setFetchSource('fields.updated', null).execute().actionGet()
        if (response.exists) {
            def updated = response.sourceAsMap.fields.updated
            if (updated != null) {
                DateTimeFormatter dtf = DateTimeFormat.forPattern(JiraHelper.JIRA_DATE_FORMAT)
                DateTime eDate = dtf.parseDateTime((String) updated)
                //println key + "\n\r" + date + ">>\n\r" + eDate
                return date.getMillis() <= eDate.getMillis();
            }
        }
        return false;
    }
    /**
     *
     * @param documentId
     * @param documentSrc
     * @return
     */
    String updateItem(String documentId, documentSrc) {
        return updateItem(defaultType, documentId, documentSrc)
    }

    String updateItem(String documentType, String documentId, documentSrc) {
        return updateItem(defaultIndex, documentType, documentId, documentSrc)
    }

    String updateItem(String indexName, String documentType, String documentId, String documentSrc) {
        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(documentSrc)
        def message = ''
        if ((object instanceof Map) && !(documentId.equals(object.get("key")) || documentId.equals(object.get("id")))) {
            message += "$documentId Got wrong JSON from Jira. ERROR!\n"
            return message
        } else {
            message += "$documentId JSON is valid, "
        }
        def response = client.prepareIndex(indexName, documentType, documentId).setSource(documentSrc, XContentType.JSON).get()
        message += "version=$response.version\n"
        return message
    }

    ElasticSearchHelper(String clusterName, String login, String password, String defaultIndex, String defaultType, nodes) {
        this.defaultIndex = defaultIndex;
        this.defaultType = defaultType

        def settingsBuilder = Settings.builder()
        if (clusterName == null) {
            settingsBuilder.put('cluster.name', 'elasticsearch')
        } else {
            settingsBuilder.put('cluster.name', clusterName)
        }

        if (login != null) {
            settingsBuilder.put('transport.ping_schedule', '5s')
                    .put('xpack.security.transport.ssl.enabled', false)
                    .put('xpack.security.user', "${login}:${password}".toString())
                    .put("request.headers.X-Found-Cluster", clusterName)

            //String token = basicAuthHeaderValue(login, new SecureString(password.toCharArray()));
            //client.filterWithHeader(Collections.singletonMap("Authorization", token)).prepareSearch().get();
        }

        def settings = settingsBuilder.build()

        if (login != null) {
            client = new PreBuiltXPackTransportClient(settings)
        } else {
            client = new PreBuiltTransportClient(settings)
        }

        nodes.each { key, value ->
            client = client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(key), value))
        }
    }
    /**
     *
     * @param templateName
     * @param templateSrc
     */
    void postIndexMappingTemplate(String templateName, String templateSrc) {
        def response = client.admin().indices().preparePutTemplate(templateName).setSource(templateSrc).execute().actionGet();
    }

    /**
     * @param aliasName
     * @return indices for alias with given name
     */
    public Collection<String> getIndicesFromAliasName(String aliasName) {
        IndicesAdminClient iac = client.admin().indices();
        ImmutableOpenMap<String, List<AliasMetaData>> map = iac.getAliases(new GetAliasesRequest(aliasName)).actionGet().getAliases();
        final Set<String> allIndices = new HashSet<>();
        def iterator = map.iterator()

        while (iterator.hasNext()) {
            def item = iterator.next()
            allIndices.add(item.key)
        }
        return allIndices;
    }

    /**
     * Switches alias to new index and removes old indexes
     * @param aliasName
     * @param indexName
     */
    public void switchAliasToIndex(String aliasName, String indexName) {
        IndicesAdminClient indices = client.admin().indices();
        try {
            Collection<String> indicesToDrop = getIndicesFromAliasName(aliasName);
            IndicesAliasesRequestBuilder requestBuilder = indices.prepareAliases();
            requestBuilder.addAlias(indexName, aliasName);

            for (String indexToDrop : indicesToDrop) {
                requestBuilder.removeIndex(indexToDrop);
            }

            IndicesAliasesResponse response = requestBuilder.execute().get();
            if (!response.isAcknowledged()) {
                logger.warn("Unable to switch alias $aliasName to use index $indexName");
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Unable to switch alias to: $indexName cause: ${e.toString()}");
        }
        logger.info("Alias $aliasName has been switched to index $indexName")
    }

    void close() {
        logger.info('Shutting down...')
        client.close();
        logger.info('AwaitTermination...')
        client.threadPool().awaitTermination(5, TimeUnit.MINUTES);
        client = null;
        logger.info('Closed')
    }
}


