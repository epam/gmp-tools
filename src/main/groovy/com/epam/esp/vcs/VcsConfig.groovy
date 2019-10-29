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

package com.epam.esp.vcs

import com.epam.esp.vcs.git.GitException;

class VcsConfig {
    Map projectPathMap
    def url
    def user
    def password

    final static String DEFAULT_PATH = 'default'

    VcsConfig(String path) {
        this.projectPathMap = [DEFAULT_PATH: path]
    }

    VcsConfig(Map projectPathMap)    {
        this.projectPathMap = projectPathMap
    }

    VcsConfig(String path, url, user, password) {
        this.projectPathMap = [DEFAULT_PATH: path]
        this.url = url
        this.user = user
        this.password = password
    }

    String getPath(String project) {
        String path = projectPathMap.getOrDefault(project.toLowerCase(), projectPathMap.get(DEFAULT_PATH))
        if (path == null) {
            throw new GitException("Path to repository must not be null")
        }
        return path
    }
}
