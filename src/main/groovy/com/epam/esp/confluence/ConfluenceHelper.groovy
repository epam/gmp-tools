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

package com.epam.esp.confluence

import com.epam.dep.esp.common.json.JsonMapper
import com.epam.esp.confluence.dto.NewConfPageDto
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
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
        final String expand = URLEncoder.encode(StringUtils.join(expansions, ","), ENCODING)
        return String.format("%s/rest/api/content/%s?expand=%s", baseUrl, contentId, expand)
    }

    String createContentRestUrl() throws UnsupportedEncodingException {
        return String.format("%s/rest/api/content", baseUrl)
    }

    /**
     *
     * @param space - space id where new page should be created
     * @param pageId - parent page id
     * @param title - title foe new page
     * @param body - page source
     * @throws ConfluenceException in case of error
     */
    def createPage(String space, Long pageId, String title, String body) {
        // Send createPage request
        HttpEntity putPageEntity = null
        HttpClient client = new DefaultHttpClient()
        try {
            HttpPost putPageRequest = new HttpPost(createContentRestUrl())
            setAuthHeader(putPageRequest)
            def pageDto = new NewConfPageDto(space, pageId, title, body)
            StringEntity entity = new StringEntity(JsonMapper.getInstance().map(pageDto), ContentType.APPLICATION_JSON)
            putPageRequest.setEntity(entity)
            HttpResponse putPageResponse = client.execute(putPageRequest)
            putPageEntity = putPageResponse.getEntity()
            logger.info(putPageRequest.requestLine.uri)
            def pageObj = IOUtils.toString(putPageEntity.getContent())
            //logger.info(IOUtils.toString(putPageEntity.getContent()))
            if (putPageResponse.getStatusLine().getStatusCode() != 200) {
                logger.error("Put Page Request returned ${putPageResponse.getStatusLine()}")
                logger.error(pageObj)
                throw new ConfluenceException("Unexpected API response code")
            } else {
                logger.info("PUT Page Request returned " + putPageResponse.getStatusLine().toString())
                JSONObject jsonPage = new JSONObject(pageObj)
                def url = jsonPage.getJSONObject('_links').getString('base') + jsonPage.getJSONObject('_links').getString('tinyui')
                logger.info("page URL: $url")
            }
        }
        finally {
            EntityUtils.consume(putPageEntity)
        }
    }

    /**
     *
     * @param pageId - id of the page to be edited
     * @param body - page source
     * @throws ConfluenceException in case of error
     */
    def updatePage(Long pageId, String body) {
        HttpClient client = new DefaultHttpClient()
        // Get current page version
        String pageObj = null
        HttpEntity pageEntity = null
        try {
            HttpGet getPageRequest = new HttpGet(getContentRestUrl(pageId, 'body.storage version ancestors'.split()))
            setAuthHeader(getPageRequest)
            HttpResponse getPageResponse = client.execute(getPageRequest)
            pageEntity = getPageResponse.getEntity()

            pageObj = IOUtils.toString(pageEntity.getContent())

            logger.info("Get Page Request returned " + getPageResponse.getStatusLine().toString())
            if (getPageResponse.getStatusLine().statusCode != 200) {
                logger.error("GET Page Request returned ${getPageResponse.getStatusLine()}")
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

            logger.info("Put Page Request returned ${putPageResponse.getStatusLine()}")
            pageObj = IOUtils.toString(putPageEntity.getContent())
            page = new JSONObject(pageObj)
            def url = page.getJSONObject('_links').getString('base') + page.getJSONObject('_links').getString('tinyui')
            logger.info("page URL: $url")


            if (putPageResponse.getStatusLine().getStatusCode() != 200) {
                logger.error("Put Page Request returned ${putPageResponse.getStatusLine()}")
                throw new ConfluenceException("Unexpected API response code")
            }
        }
        finally {
            EntityUtils.consume(putPageEntity);
        }
    }

    /**
     * @param request
     * Set Basic Authorization header to HTTP request
     */
    def setAuthHeader(HttpRequestBase request) {
        request.setHeader('Authorization', authHeader)
    }
}
