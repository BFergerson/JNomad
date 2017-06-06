package com.codebrig.jnomad.model

import com.github.javaparser.Range

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class RecommendedIndex {

    private String indexCreateSQL
    private double indexPriority
    private String indexTable
    private String indexCondition
    private final Map<File, Range> indexAffectMap
    private final Set<String> indexAffectOriginalQuerySet

    RecommendedIndex() {
        indexAffectMap = new HashMap<>()
        indexAffectOriginalQuerySet = new HashSet<>()
    }

    RecommendedIndex(String indexCreateSQL, double indexPriority, String indexTable, String indexCondition) {
        this.indexCreateSQL = indexCreateSQL
        this.indexPriority = indexPriority
        this.indexTable = indexTable
        this.indexCondition = indexCondition
        this.indexAffectMap = new HashMap<>()
        this.indexAffectOriginalQuerySet = new HashSet<>()
    }

    String getIndexCreateSQL() {
        return indexCreateSQL
    }

    void setIndexCreateSQL(String indexCreateSQL) {
        this.indexCreateSQL = indexCreateSQL
    }

    double getIndexPriority() {
        return indexPriority
    }

    void setIndexPriority(double indexPriority) {
        this.indexPriority = indexPriority
    }

    String getIndexTable() {
        return indexTable
    }

    void setIndexTable(String indexTable) {
        this.indexTable = indexTable
    }

    String getIndexCondition() {
        return indexCondition
    }

    void setIndexCondition(String indexCondition) {
        this.indexCondition = indexCondition
    }

    public void addToIndexAffectMap(File file, Range range, String originalQuery) {
        indexAffectMap.put(file, range)
        indexAffectOriginalQuerySet.add(originalQuery)
    }

    Map<File, Range> getIndexAffectMap() {
        return indexAffectMap
    }

    boolean isIndexAffect(String originalQuery) {
        return indexAffectOriginalQuerySet.contains(originalQuery)
    }

}
