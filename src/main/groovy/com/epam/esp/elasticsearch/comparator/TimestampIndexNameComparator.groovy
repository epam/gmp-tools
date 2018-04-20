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

package com.epam.esp.elasticsearch.comparator

class TimestampIndexNameComparator implements Comparator<String> {

    private long offset
    private String delimiter

    public TimestampIndexNameComparator(long offsetMillis, String delimiter) {
        this.offset = offsetMillis
        this.delimiter = delimiter
    }

    @Override
    int compare(String o1, String o2) {

        if(!compatibilityCheck(o1, o2)) return 0

        long ts1 = getTimestamp(o1) + offset
        long ts2
        if (o2 == null) ts2 = System.currentTimeMillis() else ts2 = getTimestamp(o2)

        if (ts1 < ts2) return -1
        if (ts1 > ts2) return 1

        return 0
    }

    private boolean compatibilityCheck(String o1, String o2) {
        return o1.substring(0, o1.lastIndexOf(delimiter)).equals(o2.substring(0, o2.lastIndexOf(delimiter)))
    }

    private long getTimestamp(String name) {
        String s = name.substring(name.lastIndexOf(delimiter) + 1)
        return Long.parseLong(s)
    }
}
