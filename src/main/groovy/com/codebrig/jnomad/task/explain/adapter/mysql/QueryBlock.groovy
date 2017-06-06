package com.codebrig.jnomad.task.explain.adapter.mysql

import com.codebrig.jnomad.task.explain.ExplainPlan
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryBlock extends ExplainPlan {

    @JsonProperty("select_id")
    public Integer selectId
    @JsonProperty("r_loops")
    public Integer rLoops
    @JsonProperty("r_total_time_ms")
    public Double rTotalTimeMs
    @JsonProperty("table")
    public Table table

    Integer getSelectId() {
        return selectId
    }

    void setSelectId(Integer selectId) {
        this.selectId = selectId
    }

    Integer getrLoops() {
        return rLoops
    }

    void setrLoops(Integer rLoops) {
        this.rLoops = rLoops
    }

    Double getrTotalTimeMs() {
        return rTotalTimeMs
    }

    void setrTotalTimeMs(Double rTotalTimeMs) {
        this.rTotalTimeMs = rTotalTimeMs
    }

    Table getTable() {
        return table
    }

    void setTable(Table table) {
        this.table = table
    }

    @Override
    String getTableName() {
        return table.tableName
    }

    @Override
    String getConditionClause() {
        return table.attachedCondition
    }

}
