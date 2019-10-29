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

package com.epam.esp.vcs.svn

import com.epam.dep.esp.common.OS
import com.epam.esp.vcs.VcsConfig
import com.epam.esp.vcs.VcsHelper
import com.epam.esp.vcs.dto.Commit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SvnHelper extends VcsHelper {
    final static Logger logger = LoggerFactory.getLogger(SvnHelper.class)

    SvnHelper(vcsConfig) {
        super(
                new VcsConfig(
                        vcsConfig.folder.toString(),
                        vcsConfig.url.toString(),
                        vcsConfig.user.toString(),
                        vcsConfig.password.toString()))
    }

    SvnHelper(VcsConfig config) {
        super(config)
    }

    @Override
    List<Commit> getCommitDiff(project, srcBranch, destBranch) {
        OS os = OS.getOs()
        List<String> params = ['svn', 'log', config.url, '--xml', "-r${revisionFrom}:${revisionTo}".toString(), '--username', config.user, '--password', config.password].asList()
        List<String> processOut = new ArrayList<String>()
        def result = os.execCommandLine(params, processOut, config.getPath(project), 600)   //.join()
        def commitList = new ArrayList<Commit>()

        if (result == 0) {
            if (logger.isInfoEnabled()) {
                logger.info("Processing result. ${processOut.size()} lines to process.")
            }
            def rootNode = new XmlParser().parseText(processOut.join(" "))
            rootNode.logentry.each { element ->
                def commit = new Commit()
                commit.date = element.date.text()
                commit.hash = element.attribute("revision")
                commit.author = element.author.text()
                commit.comment = element.msg.text()
                commitList.add(commit)
            }

        } else {
            throw new SvnException('Unable to execute svn command')
        }
        return commitList
    }
}
