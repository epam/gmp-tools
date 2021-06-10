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

package com.epam.esp.confluence.dto

class NewConfPageDto {
    String type = 'page'
    String title
    List<ConfPageAncestors> ancestors
    ConfSpace space
    ConfPageBody body
    ConfPageMeta metadata

    NewConfPageDto(String space, Long ancestor, String title, String body) {
        this.title = title
        this.body = new ConfPageBody(body)
        this.space = new ConfSpace(space)
        def ancestors = new ArrayList<ConfPageAncestors>()
        ancestors.add(new ConfPageAncestors(ancestor))
        this.ancestors = ancestors
        this.metadata =
                ['properties':
                         [
                                 'editor'                      : ['value': 'v2'],
                                 'content-appearance-draft'    : ['key': 'content-appearance-draft', 'value': 'full-width'],
                                 'content-appearance-published': ['key': 'content-appearance-published', 'value': 'full-width']
                         ]
                ]
    }

    NewConfPageDto(String space, Long ancestor, String title, String body, Map<String, Object> meta) {
        this.title = title
        this.body = new ConfPageBody(body)
        this.space = new ConfSpace(space)
        def ancestors = new ArrayList<ConfPageAncestors>()
        ancestors.add(new ConfPageAncestors(ancestor))
        this.ancestors = ancestors
        this.metadata = new ConfPageMeta(meta)
    }

}
