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

package com.epam.esp.vcs.git

import com.epam.dep.esp.common.OS
import com.epam.esp.vcs.VcsConfig
import com.epam.esp.vcs.VcsHelper
import com.epam.esp.vcs.dto.Commit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Matcher

class GitHelper extends VcsHelper {

    static def COMMIT_PATTERN = '"?\\[([^]]*)\\]-\\[([^\\]]*)\\]-@\\[([^\\]]*)\\]-(.*)'
    final static Logger logger = LoggerFactory.getLogger(GitHelper.class)

    GitHelper(VcsConfig config) {
        super(config)
    }

   /**
     *
     * @param hash
     * @return git diff-tree result strings
     */
    private List<String> getCommitInfo(project, hash) {
        OS os = OS.getOs()
        List<String> params = ['git', 'diff-tree', '--no-commit-id', '--name-only', '-r', hash].asList()
        List<String> processOut = new ArrayList<String>()
        def result = os.execCommandLine(params, processOut, config.getPath(project), 600)
        if (result == 0) {
            return processOut
        } else {
            def output = processOut.join('\n')
            throw new GitException("Unable to execute git command:\n${output}")
        }
    }

    /**
     *
     * @param srcBranch
     * @param destBranch
     * @return List < Commit >  for  "git log srcBranch..destBranch" cmdline execution or throws GitException
     */
    List<Commit> getCommitDiff(project, srcBranch, destBranch) {
        OS os = OS.getOs()
        List<String> params = ['git', 'log', '--pretty=format:"[%cD]-[%H]-@[%an]-%s"', "${srcBranch}..${destBranch}".toString()].asList()
        List<String> processOut = new ArrayList<String>()
        def result = os.execCommandLine(params, processOut, config.getPath(project), 600)
        def commitList = new ArrayList<Commit>()
        if (result == 0) {
            if (logger.isInfoEnabled()) {
                logger.info("Processing result. ${processOut.size()} lines to process.")
            }
            processOut.each {
                Matcher matcher = it =~ COMMIT_PATTERN
                if (matcher.matches()) {
                    def commit = new Commit()
                    commit.date = matcher.group(1)
                    commit.hash = matcher.group(2)
                    commit.author = matcher.group(3)
                    commit.comment = matcher.group(4)
                    commit.files = getCommitInfo(project, commit.hash)
                    if (commit.files.size() != 0) {
                        commitList.add(commit)
                    }
                } else {
                    logger.error("${it} doesn't match ${COMMIT_PATTERN}")
                }
            }
        } else {
            def output = processOut.join('\n')
            throw new GitException("Unable to execute git command:\n${output}")
        }
        return commitList
    }
}
