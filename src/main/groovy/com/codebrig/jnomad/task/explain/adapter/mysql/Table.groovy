package com.codebrig.jnomad.task.explain.adapter.mysql

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class Table {

    @JsonProperty("table_name")
    public String tableName
    @JsonProperty("access_type")
    public String accessType
    @JsonProperty("possible_keys")
    public List<String> possibleKeys
    @JsonProperty("key")
    public String key
    @JsonProperty("key_length")
    public String keyLength
    @JsonProperty("used_key_parts")
    public List<String> usedKeyParts
    @JsonProperty("ref")
    public List<String> ref
    @JsonProperty("r_loops")
    public Integer rLoops
    @JsonProperty("rows")
    public Integer rows
    @JsonProperty("r_rows")
    public Object rRows
    @JsonProperty("filtered")
    public Integer filtered
    @JsonProperty("r_filtered")
    public Object rFiltered
    @JsonProperty("r_total_time_ms")
    public Double rTotalTimeMs
    @JsonProperty("attached_condition")
    public String attachedCondition
    @JsonProperty("index_condition")
    public String indexCondition;

    String getTableName() {
        return tableName
    }

    void setTableName(String tableName) {
        this.tableName = tableName
    }

    String getAccessType() {
        return accessType
    }

    void setAccessType(String accessType) {
        this.accessType = accessType
    }

    List<String> getPossibleKeys() {
        return possibleKeys
    }

    void setPossibleKeys(List<String> possibleKeys) {
        this.possibleKeys = possibleKeys
    }

    String getKey() {
        return key
    }

    void setKey(String key) {
        this.key = key
    }

    String getKeyLength() {
        return keyLength
    }

    void setKeyLength(String keyLength) {
        this.keyLength = keyLength
    }

    List<String> getUsedKeyParts() {
        return usedKeyParts
    }

    void setUsedKeyParts(List<String> usedKeyParts) {
        this.usedKeyParts = usedKeyParts
    }

    List<String> getRef() {
        return ref
    }

    void setRef(List<String> ref) {
        this.ref = ref
    }

    Integer getrLoops() {
        return rLoops
    }

    void setrLoops(Integer rLoops) {
        this.rLoops = rLoops
    }

    Integer getRows() {
        return rows
    }

    void setRows(Integer rows) {
        this.rows = rows
    }

    Object getrRows() {
        return rRows
    }

    void setrRows(Object rRows) {
        this.rRows = rRows
    }

    Integer getFiltered() {
        return filtered
    }

    void setFiltered(Integer filtered) {
        this.filtered = filtered
    }

    Object getrFiltered() {
        return rFiltered
    }

    void setrFiltered(Object rFiltered) {
        this.rFiltered = rFiltered
    }

    Double getrTotalTimeMs() {
        return rTotalTimeMs
    }

    void setrTotalTimeMs(Double rTotalTimeMs) {
        this.rTotalTimeMs = rTotalTimeMs
    }

    String getAttachedCondition() {
        return attachedCondition
    }

    void setAttachedCondition(String attachedCondition) {
        this.attachedCondition = attachedCondition
    }

    String getIndexCondition() {
        return indexCondition
    }

    void setIndexCondition(String indexCondition) {
        this.indexCondition = indexCondition
    }

}