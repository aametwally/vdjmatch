/*
 * Copyright 2015 Mikhail Shugay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.antigenomics.vdjdb

import com.antigenomics.vdjdb.db.Column
import com.antigenomics.vdjdb.db.Database
import com.antigenomics.vdjdb.impl.ClonotypeDatabase
import com.antigenomics.vdjdb.sequence.SequenceColumn
import com.antigenomics.vdjdb.sequence.SequenceFilter
import com.antigenomics.vdjdb.text.TextColumn
import com.antigenomics.vdjdb.text.TextFilter

class VDJdb {
    protected static final String DEFAULT_DB_RESOURCE_NAME = "vdjdb_legacy.txt",
                                  DEFAULT_META_RESOURCE_NAME = "vdjdb_legacy.meta"

    private static final Database dbInstance = new Database(Util.resourceAsStream(DEFAULT_META_RESOURCE_NAME))

    static {
        dbInstance.addEntries(Util.resourceAsStream(DEFAULT_DB_RESOURCE_NAME))
    }

    static List<Column> getHeader() {
        dbInstance.columns.collect {
            it instanceof SequenceColumn ? new SequenceColumn(it.name, it.metadata) :
                    new TextColumn(it.name, it.metadata)
        }
    }

    static Database getDatabase(List<TextFilter> textFilters = [],
                                List<SequenceFilter> sequenceFilters = []) {
        Database.create(dbInstance.search(textFilters, sequenceFilters), dbInstance)
    }

    static ClonotypeDatabase asClonotypeDatabase(Database db, boolean matchV = false, boolean matchJ = false,
                                                 int maxMismatches = 2, int maxInsertions = 1,
                                                 int maxDeletions = 1, int maxMutations = 2, int depth = -1) {
        new ClonotypeDatabase(db.columns, matchV, matchJ,
                maxMismatches, maxInsertions,
                maxDeletions, maxMutations, depth)
    }
}