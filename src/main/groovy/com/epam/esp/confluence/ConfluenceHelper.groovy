/*
 *  /***************************************************************************
 *  Copyright (c) 2017,  EPAM SYSTEMS INC
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

package com.epam.esp.confluence

import com.epam.dep.esp.common.json.JsonMapper
import com.epam.esp.confluence.dto.EditorVersion
import com.epam.esp.confluence.dto.NewConfPageDto
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets

class ConfluenceHelper {
    String baseUrl
    String username
    String password
    static final String ENCODING = StandardCharsets.UTF_8.name()
    final static Logger logger = LoggerFactory.getLogger(ConfluenceHelper.class)
    private String authHeader

    /**
     * @param baseUrl
     * @param username
     * @param password
     */
    ConfluenceHelper(baseUrl, username, password) {
        this.baseUrl = baseUrl.toString()
        this.username = username.toString()
        this.password = password.toString()
        def encodedCredentials = Base64.getEncoder().encodeToString("$username:$password".getBytes())
        this.authHeader = "Basic $encodedCredentials"
    }

    /**
     * @param contentId - page id
     * @param expansions - expand parameters (see REST documentation)
     * @return url to content
     * @throws UnsupportedEncodingException
     */
    String getContentRestUrl(Long contentId, String[] expansions) throws UnsupportedEncodingException {
        final String expand = URLEncoder.encode(expansions.join(','), ENCODING)
        return String.format("%s/rest/api/content/%s?expand=%s", baseUrl, contentId, expand)
    }

    String createContentRestUrl() throws UnsupportedEncodingException {
        return String.format("%s/rest/api/content", baseUrl)
    }

    /**
     *
     * @param spaceKey - space id where new page should be created
     * @param parentPageId - parent page id
     * @param title - title foe new page
     * @param pageBody - page source
     * @param editorVersion - Confluence editor version
     * @param fullWidth - use page fill width alignment
     * @throws ConfluenceException in case of error
     * @return new page ID
     */
    Long createPage(String spaceKey, Long parentPageId, String title, String pageBody,
                    EditorVersion editorVersion = EditorVersion.V2, boolean fullWidth = false) {
        return createOrUpdatePage(spaceKey, parentPageId, title, pageBody, editorVersion, fullWidth, true)
    }

    /**
     *
     * @param spaceKey - space id where new page should be created
     * @param parentPageId - parent page id
     * @param title - title foe new page
     * @param pageBody - page source
     * @param editorVersion - Confluence editor version
     * @param fullWidth - use page fill width alignment
     * @param failIfExist - throw an exception when page with such title is already created
     * @throws com.epam.esp.confluence.ConfluenceException in case of error
     * @return new page ID
     */
    Long createOrUpdatePage(String spaceKey, Long parentPageId, String title, String pageBody,
                            EditorVersion editorVersion = EditorVersion.V2,
                            boolean fullWidth = false,
                            boolean failIfExist = false) {
        HttpEntity putPageEntity = null
        HttpClient client = HttpClients.createDefault()
        try {
            def existPageId = findPageIdByTitle(spaceKey, title)
            if (existPageId != null) {
                if (failIfExist) {
                    def errorMessage = "Page with title '${title}' already exists in space ${spaceKey} with id ${existPageId}"
                    logger.error(errorMessage)
                    throw new ConfluenceException(errorMessage)
                }
                updatePage(existPageId, pageBody)
                return existPageId
            }
            HttpPost putPageRequest = new HttpPost(createContentRestUrl())
            setAuthHeader(putPageRequest)
            def pageDto = new NewConfPageDto(spaceKey, parentPageId, title, pageBody, editorVersion, fullWidth)
            StringEntity entity = new StringEntity(JsonMapper.getInstance().map(pageDto), ContentType.APPLICATION_JSON)
            putPageRequest.setEntity(entity)
            HttpResponse putPageResponse = client.execute(putPageRequest)
            putPageEntity = putPageResponse.getEntity()
            logger.info(putPageRequest.requestLine.uri)
            def pageObj = IOUtils.toString(putPageEntity.getContent())
            if (putPageResponse.statusLine.statusCode != 200) {
                logger.error("Put Page Request returned ${putPageResponse.statusLine}")
                logger.error(pageObj)
                throw new ConfluenceException("Unexpected API response code")
            } else {
                logger.info("PUT Page Request returned " + putPageResponse.statusLine.toString())
                JSONObject jsonPage = new JSONObject(pageObj)
                def url = jsonPage.getJSONObject('_links').getString('base') + jsonPage.getJSONObject('_links').getString('tinyui')
                logger.info("page URL: $url")
                return jsonPage.getLong("id")
            }
        }
        finally {
            EntityUtils.consume(putPageEntity) F
        }
    }

    /**
     *
     * @param pageId - id of the page to be edited
     * @param body - page source
     * @throws ConfluenceException in case of error
     */
    def updatePage(Long pageId, String body) {
        HttpClient client = HttpClients.createDefault()
        // Get current page version
        String pageObj = null
        HttpEntity pageEntity = null
        try {
            HttpGet getPageRequest = new HttpGet(getContentRestUrl(pageId, 'body.storage version ancestors'.split()))
            setAuthHeader(getPageRequest)
            HttpResponse getPageResponse = client.execute(getPageRequest)
            pageEntity = getPageResponse.getEntity()

            pageObj = IOUtils.toString(pageEntity.getContent())

            logger.info("Get Page Request returned " + getPageResponse.statusLine.toString())
            if (getPageResponse.statusLine.statusCode != 200) {
                logger.error("GET Page Request returned ${getPageResponse.statusLine}")
                throw new ConfluenceException("Unexpected API response code")
            }

        }
        finally {
            if (pageEntity != null) {
                EntityUtils.consume(pageEntity)
            }
        }

        // Parse response into JSON
        JSONObject page = new JSONObject(pageObj)

        // Update page
        // The updated value must be Confluence Storage Format (https://confluence.atlassian.com/display/DOC/Confluence+Storage+Format), NOT HTML.
        page.getJSONObject("body").getJSONObject("storage").put("value", body)

        int currentVersion = page.getJSONObject("version").getInt("number")
        page.getJSONObject("version").put("number", currentVersion + 1)

        // Send update request
        HttpEntity putPageEntity = null

        try {
            HttpPut putPageRequest = new HttpPut(getContentRestUrl(pageId, new String[0]))
            setAuthHeader(putPageRequest)
            StringEntity entity = new StringEntity(page.toString(), ContentType.APPLICATION_JSON)
            putPageRequest.setEntity(entity)

            HttpResponse putPageResponse = client.execute(putPageRequest)
            putPageEntity = putPageResponse.getEntity()

            logger.info("Put Page Request returned ${putPageResponse.statusLine}")
            pageObj = IOUtils.toString(putPageEntity.getContent())
            page = new JSONObject(pageObj)
            def url = page.getJSONObject('_links').getString('base') + page.getJSONObject('_links').getString('tinyui')
            logger.info("page URL: $url")


            if (putPageResponse.statusLine.statusCode != 200) {
                logger.error("Put Page Request returned ${putPageResponse.statusLine}")
                throw new ConfluenceException("Unexpected API response code")
            }
        }
        finally {
            EntityUtils.consume(putPageEntity)
        }
    }

    /**
     * @param request
     * Set Basic Authorization header to HTTP request
     */
    def setAuthHeader(HttpRequestBase request) {
        request.setHeader('Authorization', authHeader)
    }

    /**
     * find page by spaceKey and title
     * @param spaceKey
     * @param title
     * @return founded page ID or null
     */
    Long findPageIdByTitle(String spaceKey, String title) {
        HttpClient client = HttpClients.createDefault()
        List requestParams = [new BasicNameValuePair("title", title),
                              new BasicNameValuePair("spaceKey", spaceKey),
        ]
        URI uri = new URIBuilder(createContentRestUrl())
                .addParameters(requestParams)
                .build()
        def findPageRequest = new HttpGet(uri)
        setAuthHeader(findPageRequest)
        logger.info(findPageRequest.requestLine.uri)
        def response = client.execute(findPageRequest)
        def findResult = EntityUtils.toString(response.entity)
        if (response.statusLine.statusCode != 200) {
            logger.error("Find Page Request returned ${response.statusLine}")
            logger.error(findResult)
            throw new ConfluenceException("Unexpected API response code")
        } else {
            logger.info("Find Page Request returned " + response.statusLine.toString())
            JSONObject jsonPage = new JSONObject(findResult)
            def pageList = jsonPage.getJSONArray("results")
            if (pageList != null && !pageList.empty) {
                return ((JSONObject) pageList[0]).getLong("id")
            }
            return null
        }
    }
}
