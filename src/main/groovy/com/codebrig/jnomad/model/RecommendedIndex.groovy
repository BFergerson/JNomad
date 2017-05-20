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

    public RecommendedIndex() {
        indexAffectMap = new HashMap<>()
    }

    RecommendedIndex(String indexCreateSQL, double indexPriority, String indexTable, String indexCondition) {
        this.indexCreateSQL = indexCreateSQL
        this.indexPriority = indexPriority
        this.indexTable = indexTable
        this.indexCondition = indexCondition
        indexAffectMap = new HashMap<>()
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

    public void addToIndexAffectMap(File file, Range range) {
        indexAffectMap.put(file, range)
    }

    Map<File, Range> getIndexAffectMap() {
        return indexAffectMap
    }

}
