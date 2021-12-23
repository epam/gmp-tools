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
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequestBuilder
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.client.Requests
import org.elasticsearch.cluster.metadata.AliasMetadata
import org.elasticsearch.cluster.metadata.IndexMetadata
import org.elasticsearch.common.collect.ImmutableOpenMap
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.Settings.Builder
import org.elasticsearch.common.transport.TransportAddress
import org.elasticsearch.xcontent.XContentType
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
    private final static Logger logger = LoggerFactory.getLogger(ElasticSearchHelper.class)
    //The preferred type name is _doc, so that index APIs have the same path as they will have in 7.0
    private String defaultType = "_doc"
    protected PreBuiltTransportClient client;

    ElasticSearchHelper(String clusterName, String login, String password, nodes) {

        def settings = assembleSettings(clusterName, login, password, nodes).build()

        if (login != null) {
            client = new PreBuiltXPackTransportClient(settings)
        } else {
            client = new PreBuiltTransportClient(settings)
        }

        nodes.each { key, value ->
            client = client.addTransportAddress(new TransportAddress(InetAddress.getByName(key), value))
        }
    }

    protected Builder assembleSettings(String clusterName, String login, String password, nodes) {
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
                    .put('request.headers.X-Found-Cluster', clusterName)

            //String token = basicAuthHeaderValue(login, new SecureString(password.toCharArray()));
            //client.filterWithHeader(Collections.singletonMap("Authorization", token)).prepareSearch().get();
        }
        return settingsBuilder
    }

    @Deprecated
    ElasticSearchHelper(String clusterName, String login, String password, String defaultIndex, String defaultType, nodes) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    /**
     *
     * @param indexName
     * @return true if given index exists
     */
    boolean isIndexExists(String indexName) {
        IndexMetadata indexMetaData = client.admin().cluster()
                .state(Requests.clusterStateRequest())
                .actionGet()
                .getState()
                .getMetadata()
                .index(indexName);
        return (indexMetaData != null);
    }

    /**
     *
     * @param documentId
     * @return true if document with given id exists
     */
    @Deprecated
    boolean isDocumentExists(String documentId) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    /**
     *
     * @param indexName
     * @param documentType
     * @param documentId
     * @return true if document exists
     */
    @Deprecated
    boolean isDocumentExists(String indexName, String documentType, String documentId) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    /**
     *
     * @param documentId
     * @return document with given ID
     */
    @Deprecated
    def getDocumentById(String documentId) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    /**
     *
     * @param indexName
     * @param documentType
     * @param documentId
     * @return document with given index, type, id
     */
    @Deprecated
    def getDocumentById(String indexName, String documentType, String documentId) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    /**
     *
     * @param indexName
     * @param documentId
     * @return document with given index, type, id
     */
    def getDocumentById(String indexName, String documentId) {
        return client.prepareGet(indexName, defaultType, documentId).get()
    }

    /**
     * JIRA 'issue' document specific
     * @param documentId
     * @param date
     * @return true if 'issue' update date is the same
     */
    boolean isInSync(String documentId, date) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    /**
     * JIRA 'issue' document specific
     * @param documentId
     * @param documentType
     * @param date
     * @return true if 'issue' update date is the same
     */
    @Deprecated
    boolean isInSync(String documentId, String documentType, DateTime date) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }
    /**
     * JIRA 'issue' document specific
     * @param indexName index name
     * @param documentId
     * @param date
     * @return true if 'issue' update date is the same
     */
    boolean isJiraIssueInSync(String indexName, String documentId, DateTime date) {
        GetResponse response = client.prepareGet().setId(documentId).setIndex(indexName).setType(defaultType).setFetchSource('fields.updated', null).execute().actionGet()
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

    @Deprecated
    String updateItem(String documentId, String documentSrc) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    @Deprecated
    String updateItem(String indexName, String documentType, String documentId, String documentSrc) {
        throw new UnsupportedOperationException("This is not supported since elasticsearch version 6.2")
    }

    String updateItem(String indexName, String documentId, String documentSrc) {
        def message = ''
        def response = client.prepareIndex(indexName, defaultType, documentId).setSource(documentSrc, XContentType.JSON).get()
        message += "version=$response.version\n"
        return message
    }

    /**
     *
     * @param templateName
     * @param templateSrc
     */
    void postIndexMappingTemplate(String templateName, String templateSrc) {
        def response = client.admin().indices().preparePutTemplate(templateName).setSource(templateSrc.getBytes("utf-8"), XContentType.JSON).execute().actionGet();
    }

    /**
     * @param aliasName
     * @return indices for alias with given name
     */
    Collection<String> getIndicesFromAliasName(String aliasName) {
        return getIndicesFromAliasName(aliasName, null, null)
    }

    /**
     * @param aliasName
     * @param comparator comparator for index names
     * @return indices for alias with given name
     */
    Collection<String> getIndicesFromAliasName(String aliasName, String indexName, Comparator<String> comparator) {
        IndicesAdminClient iac = client.admin().indices();
        GetAliasesResponse aliasResponse = iac.prepareGetAliases(aliasName).get()
        ImmutableOpenMap<String, List<AliasMetadata>> map = aliasResponse.getAliases();
        final Set<String> allIndices = new HashSet<>();

        def iterator = map.iterator()

        while (iterator.hasNext()) {
            def item = iterator.next()
            if (item.value != null && !item.value.isEmpty() || comparator != null && comparator.compare(item.key, indexName) < 0) {
                allIndices.add(item.key)
            }
        }

        return allIndices;
    }

    /**
     * Switches alias to new index and removes old indexes
     * @param aliasName
     * @param indexName
     */
    void switchAliasToIndex(String aliasName, String indexName) {
        switchAliasToIndex(aliasName, indexName, null)
    }

    /**
     * Switches alias to new index and removes old indexes
     * @param aliasName
     * @param indexName
     * @param comparator comparator for index names
     */
    void switchAliasToIndex(String aliasName, String indexName, Comparator<String> dropComporator) {

        IndicesAdminClient indices = client.admin().indices();
        try {
            Collection<String> indicesToDrop = getIndicesFromAliasName(aliasName, indexName, dropComporator);

            IndicesAliasesRequestBuilder requestBuilder = indices.prepareAliases();
            requestBuilder.addAlias(indexName, aliasName);
            for (String indexToDrop : indicesToDrop) {
                requestBuilder.removeIndex(indexToDrop);
            }

            AcknowledgedResponse response = requestBuilder.execute().actionGet();
            if (!response.isAcknowledged()) {
                logger.warn("Unable to switch alias $aliasName to use index $indexName");
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace()
            logger.error("Unable to switch alias to: $indexName cause: ${e.toString()}");
        }
        logger.info("Alias $aliasName has been switched to index $indexName")
    }
    /**
     * Creates index
     * @param name index name
     * @param settings builder for settings
     */
    void createIndex(String name, Settings.Builder settings) {
        CreateIndexResponse cir = client.admin().indices()
                .prepareCreate(name)
                .setSettings(settings)
                .execute().actionGet();
    }

    /**
     * Returns search builder for default index and doc type
     * @return search builder
     */
    SearchRequestBuilder prepareSearch(String indexName) {
        return client.prepareSearch(indexName).setTypes(defaultType)
    }

    IndexRequestBuilder prepareIndex(String name, String id, String source) {
        return client.prepareIndex(name, defaultType, id).setSource(source, XContentType.JSON)
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


