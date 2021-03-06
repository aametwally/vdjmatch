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
import com.antigenomics.vdjdb.db.ExpressionFilterBatch
import com.antigenomics.vdjdb.impl.ClonotypeDatabase
import com.antigenomics.vdjdb.impl.ScoringBundle
import com.antigenomics.vdjdb.impl.filter.DummyResultFilter
import com.antigenomics.vdjdb.impl.filter.ResultFilter
import com.antigenomics.vdjdb.impl.weights.DummyWeightFunctionFactory
import com.antigenomics.vdjdb.impl.weights.WeightFunctionFactory
import com.antigenomics.vdjdb.sequence.SearchScope
import com.antigenomics.vdjdb.sequence.SequenceColumn
import com.antigenomics.vdjdb.sequence.SequenceFilter
import com.antigenomics.vdjdb.text.EpitopeSizeFilterUtil
import com.antigenomics.vdjdb.text.ExactTextFilter
import com.antigenomics.vdjdb.text.LevelFilter
import com.antigenomics.vdjdb.text.TextColumn
import com.antigenomics.vdjdb.text.TextFilter
import com.antigenomics.vdjtools.sample.Clonotype
import com.antigenomics.vdjtools.sample.Sample

/**
 * A simple API to operate with current VDJdb database.
 * The database is loaded from a local copy. In case it doesn't exist,
 * the latest database release is downloaded from GitHub.
 *
 * This class contains methods to create, filter and wrap the database.
 *
 * @see <a href="https://github.com/antigenomics/vdjdb-db">vdjdb-db repository</a>
 */
class VdjdbInstance {
    static final String SCORE_COLUMN_DEFAULT = "vdjdb.score"

    final Database dbInstance

    VdjdbInstance(boolean useFatDb = true) {
        this(checkDbAndGetMetadata(useFatDb),
                new FileInputStream(Util.HOME_DIR + (useFatDb ? "/vdjdb.txt" : "/vdjdb.slim.txt")))
    }

    VdjdbInstance(Database dbInstance) {
        this.dbInstance = dbInstance
    }

    private static InputStream checkDbAndGetMetadata(boolean useFatDb) {
        Util.checkDatabase()
        new FileInputStream(Util.HOME_DIR + (useFatDb ? "/vdjdb.meta.txt" : "/vdjdb.slim.meta.txt"))
    }

    VdjdbInstance(InputStream metadata, InputStream entries) {
        dbInstance = new Database(metadata)
        dbInstance.addEntries(entries)
    }

    VdjdbInstance(File metadata, File entries) {
        this(new FileInputStream(metadata), new FileInputStream(entries))
    }

    /**
     * Gets the header of current VDJdb database.
     * @return a list of columns
     */
    List<Column> getHeader() {
        dbInstance.columns.collect {
            it instanceof SequenceColumn ? new SequenceColumn(it.name, it.metadata) :
                    new TextColumn(it.name, it.metadata)
        }
    }

    /**
     * Creates an instance of current VDJdb database and applies text and sequence filters if specified.
     * @param textFilters text filters to apply
     * @param sequenceFilters sequence filters to apply
     * @return a VdjdbInstance object
     */
    VdjdbInstance filter(List<TextFilter> textFilters = [],
                         List<SequenceFilter> sequenceFilters = []) {
        new VdjdbInstance(Database.create(dbInstance.search(textFilters, sequenceFilters), dbInstance))
    }

    /**
     * Creates an instance of current VDJdb database and applies text and sequence filters if specified.
     * Pre-filtering is performed using a runtime-evaluated logical expression, containing database column
     * names highlighted with '__', e.g. {@code __source__=~/(EBV|influenza)/} or
     * {@code __source__=="EBV" || __source__=="influenza"}.
     * @param source a file with database table
     * @param expression a logical expression that will be compiled to filter or (String)null
     * @return a VdjdbInstance object
     */
    VdjdbInstance filter(String expression) {
        new VdjdbInstance(Database.create(dbInstance.search(new ExpressionFilterBatch(dbInstance, expression), []), dbInstance))
    }

    /**
     * Converts database instance to a clonotype database, providing means for
     * browsing with {@link com.antigenomics.vdjtools.sample.Clonotype} and
     * {@link com.antigenomics.vdjtools.sample.Sample} vdjtools objects that facilitates
     * searching of specific clonotypes in database.
     *
     * Clonotype searcher parameters and pre-filtering of database records based on
     * species, gene, segment and VDJdb confidence score can be set here.
     *
     * @param species species name (set to 'null' for all possible species)
     * @param gene receptor gene name (set to 'null' for all possible genes)
     * @param searchScope initial CDR3 sequence matching edit distance threshold & other search parameters
     * @param scoringBundle a bundle holding rules for CDR3 alignment scoring, segment scoring and score aggregation
     * @param weightFunctionFactory a factory for database hit weight functions (computes weighting on database creation/update)
     * @param resultFilter database search result filter
     * @param matchV if true requires exact (up to allele) V segment match during search
     * @param matchJ if true requires exact (up to allele) J segment match during search
     * @param vdjdbRecordConfidenceThreshold VDJdb record confidence score threshold
     *
     * @return a clonotype database object with specified search parameters
     */
    ClonotypeDatabase asClonotypeDatabase(String species = null, String gene = null,
                                          SearchScope searchScope = SearchScope.EXACT,
                                          ScoringBundle scoringBundle = ScoringBundle.DUMMY,
                                          WeightFunctionFactory weightFunctionFactory = DummyWeightFunctionFactory.INSTANCE,
                                          ResultFilter resultFilter = DummyResultFilter.INSTANCE,
                                          boolean matchV = false, boolean matchJ = false,
                                          int vdjdbRecordConfidenceThreshold = -1,
                                          int minEpiSize = -1) {
        def cdb = new ClonotypeDatabase(header, matchV, matchJ,
                searchScope,
                scoringBundle.alignmentScoring,
                scoringBundle.segmentScoring,
                scoringBundle.aggregateScoring,
                weightFunctionFactory,
                resultFilter)

        def filters = []

        if (species) {
            filters << new ExactTextFilter(cdb.speciesColName, species, false)
        }
        if (gene) {
            filters << new ExactTextFilter(cdb.geneColName, gene, false)
        }
        if (vdjdbRecordConfidenceThreshold > 0) {
            filters << new LevelFilter(SCORE_COLUMN_DEFAULT, vdjdbRecordConfidenceThreshold.toString(), false)
        }
        if (minEpiSize > 0) {
            filters << EpitopeSizeFilterUtil.createEpitopeSizeFilter(this, species, gene, minEpiSize)
        }

        cdb.addEntries(dbInstance.rows.collect { row -> row.entries.collect { it.value } }, filters)

        cdb
    }

    /**
     * Creates a clonotype database from sample, for the purpose of performing annotation between RepSeq samples
     * and unsupervised clustering of sample(s). The database will hold clonotype counts in 'count' column,
     * frequencies in 'freq' column and index in 'id' column (strictly as in sample). Non-coding clonotypes
     * are ignored.
     *
     * N.B. allowExact should be set to false (default value here) if the database will be used for clustering
     *
     * @param sample sample to convert
     * @param searchScope initial CDR3 sequence matching edit distance threshold & other search parameters
     * @param scoringBundle a bundle holding rules for CDR3 alignment scoring, segment scoring and score aggregation
     * @param weightFunctionFactory a factory for database hit weight functions (computes weighting on database creation/update)
     * @param resultFilter database search result filter
     * @param matchV if true requires exact (up to allele) V segment match during search
     * @param matchJ if true requires exact (up to allele) J segment match during search
     * @param allowExact allows exact matches if true. Can be disabled by setting to false when querying database or sample against itself.
     *
     * @return a clonotype database object with specified search parameters
     */
    static ClonotypeDatabase fromSample(Sample sample,
                                        SearchScope searchScope = SearchScope.EXACT,
                                        ScoringBundle scoringBundle = ScoringBundle.DUMMY,
                                        WeightFunctionFactory weightFunctionFactory = DummyWeightFunctionFactory.INSTANCE,
                                        ResultFilter resultFilter = DummyResultFilter.INSTANCE,
                                        boolean matchV = false, boolean matchJ = false,
                                        boolean allowExact = false) {

        def cdb = new ClonotypeDatabase(getSampleHeader(), matchV, matchJ,
                searchScope,
                scoringBundle.alignmentScoring,
                scoringBundle.segmentScoring,
                scoringBundle.aggregateScoring,
                weightFunctionFactory,
                resultFilter,
                allowExact)

        int epitopeColId = sample.annotationHeader ? sample.annotationHeader.split("\t").findIndexOf {
            it.equalsIgnoreCase(ClonotypeDatabase.EPITOPE_COL_DEFAULT)
        } : -1

        def entries = new ArrayList<List<String>>()
        int id = 0

        sample.each { Clonotype clonotype ->
            if (clonotype.coding) {
                entries.add(
                        [id,
                         clonotype.count, clonotype.freq,
                         clonotype.cdr3aa, clonotype.v, clonotype.j,
                         epitopeColId >= 0 ? clonotype.annotation.split("\t")[epitopeColId] : ""] as List<String>
                )
            }
            id++
        }

        cdb.addEntries(entries)

        cdb
    }

    static final String CLONOTYPE_SAMPLE_ID_COL = "id",
                        CLONOTYPE_SAMPLE_COUNT_COL = "count",
                        CLONOTYPE_SAMPLE_FREQ_COL = "freq"

    private static List<Column> getSampleHeader() {
        [
                new TextColumn(CLONOTYPE_SAMPLE_ID_COL),
                new TextColumn(CLONOTYPE_SAMPLE_COUNT_COL),
                new TextColumn(CLONOTYPE_SAMPLE_FREQ_COL),
                new SequenceColumn(ClonotypeDatabase.CDR3_COL_DEFAULT),
                new TextColumn(ClonotypeDatabase.V_COL_DEFAULT),
                new TextColumn(ClonotypeDatabase.J_COL_DEFAULT),
                new TextColumn(ClonotypeDatabase.EPITOPE_COL_DEFAULT)
        ]
    }

}