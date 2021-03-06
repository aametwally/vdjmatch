package com.antigenomics.vdjdb.cluster

import com.antigenomics.vdjdb.VdjdbInstance
import com.antigenomics.vdjdb.impl.ScoringBundle
import com.antigenomics.vdjdb.impl.filter.DummyResultFilter
import com.antigenomics.vdjdb.impl.filter.ResultFilter
import com.antigenomics.vdjdb.impl.weights.DummyWeightFunctionFactory
import com.antigenomics.vdjdb.impl.weights.WeightFunctionFactory
import com.antigenomics.vdjdb.sequence.ScoringType
import com.antigenomics.vdjdb.sequence.SearchScope
import com.antigenomics.vdjtools.sample.Sample

/**
 * Compute pairwise distance (dissimilarity) between clonotypes in the same sample / different pair of sample
 */
class ClonotypeDistanceCalculator {
    final SearchScope searchScope
    final ScoringBundle scoringBundle
    final WeightFunctionFactory weightFunctionFactory
    final ResultFilter resultFilter
    final boolean probabilisticScoring, matchV, matchJ

    /**
     *
     * @param searchScope
     * @param scoringBundle
     * @param weightFunctionFactory
     */
    ClonotypeDistanceCalculator(SearchScope searchScope = SearchScope.EXACT,
                                ScoringBundle scoringBundle = ScoringBundle.DUMMY,
                                WeightFunctionFactory weightFunctionFactory = DummyWeightFunctionFactory.INSTANCE,
                                ResultFilter resultFilter = DummyResultFilter.INSTANCE,
                                boolean matchV = false, matchJ = false) {
        this.searchScope = searchScope
        this.scoringBundle = scoringBundle
        this.weightFunctionFactory = weightFunctionFactory
        this.resultFilter = resultFilter
        this.matchV = matchV
        this.matchJ = matchJ
        this.probabilisticScoring = scoringBundle.alignmentScoring.scoringType == ScoringType.Probabilistic
    }

    /**
     *
     * @param from
     * @param to
     * @return
     */
    List<ClonotypeDistance> computeDistances(Sample from, Sample to) {
        def results = new ArrayList<ClonotypeDistance>()

        def clonotypeDatabase = VdjdbInstance.fromSample(from,
                searchScope, scoringBundle,
                weightFunctionFactory, resultFilter,
                matchV, matchJ, from.sampleMetadata != to.sampleMetadata)

        clonotypeDatabase.search(to).each { entry ->
            entry.value.each { result ->
                int idInSample = result.row[VdjdbInstance.CLONOTYPE_SAMPLE_ID_COL].value.toInteger()
                def fromClonotype = from[idInSample], toClonotype = entry.key

                results.add(new ClonotypeDistance(
                        idInSample,
                        result.id, // id in other sample
                        fromClonotype,
                        toClonotype, // clonotype in other sample
                        result.score,
                        result.weight,
                        probabilisticScoring ? -Math.log(Math.max(1e-21, result.score)) : (-result.score)
                ))
            }
        }

        results
    }

    /**
     * Compute pairwise distances between clonotypes in a given sample
     * @param sample
     * @return
     */
    List<ClonotypeDistance> computeDistances(Sample sample) {
        computeDistances(sample, sample)
    }
}
