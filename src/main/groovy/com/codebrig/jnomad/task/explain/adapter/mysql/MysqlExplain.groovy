package com.codebrig.jnomad.task.explain.adapter.mysql

import com.codebrig.jnomad.task.explain.ExplainResult
import com.codebrig.jnomad.task.explain.CalculatedExplainPlan
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class MysqlExplain extends ExplainResult {

    @JsonProperty("query_block")
    public QueryBlock queryBlock

    QueryBlock getQueryBlock() {
        return queryBlock
    }

    void setQueryBlock(QueryBlock queryBlock) {
        this.queryBlock = queryBlock
    }

    @Override
    CalculatedExplainPlan calculateCostliestNode(List<String> excludedNodeTypeList) {
        return new CalculatedExplainPlan(this, queryBlock.rTotalTimeMs, queryBlock, getExplainedStatement())
    }

    @Override
    CalculatedExplainPlan calculateSlowestNode(List<String> excludedNodeTypeList) {
        return new CalculatedExplainPlan(this, queryBlock.rTotalTimeMs, queryBlock, getExplainedStatement())
    }

    @Override
    String toString() {
        return "MysqlExplain: " + new ObjectMapper().writeValueAsString(this)
    }

}