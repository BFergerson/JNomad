package com.codebrig.jnomad.task.explain.adapter.postgres

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.explain.ExplainResult
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import net.sf.jsqlparser.statement.Statement

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class PostgresExplain extends ExplainResult {

    @JsonProperty("Plan")
    private ExplainPlan plan
    @JsonProperty("Planning Time")
    private Double planningTime
    @JsonProperty("Execution Time")
    private Double executionTime
    @JsonProperty("Total Runtime")
    private Double totalRuntime
    @JsonProperty("Triggers")
    private List<PostgresTrigger> triggers

    @JsonIgnore
    private transient String originalQuery
    @JsonIgnore
    private transient String finalQuery
    @JsonIgnore
    private transient Statement explainedStatement
    @JsonIgnore
    private transient SourceCodeExtract sourceCodeExtract

    ExplainPlan getPlan() {
        return plan
    }

    Double getPlanningTime() {
        return planningTime
    }

    Double getExecutionTime() {
        return executionTime
    }

    Double getTotalRuntime() {
        return totalRuntime
    }

    List<PostgresTrigger> getTriggers() {
        return triggers
    }

    void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery
    }

    String getOriginalQuery() {
        return originalQuery
    }

    void setFinalQuery(String finalQuery) {
        this.finalQuery = finalQuery
    }

    String getFinalQuery() {
        return finalQuery
    }

    void setExplainedStatement(Statement explainedStatement) {
        this.explainedStatement = explainedStatement
    }

    Statement getExplainedStatement() {
        return explainedStatement
    }

    void setSourceCodeExtract(SourceCodeExtract sourceCodeExtract) {
        this.sourceCodeExtract = sourceCodeExtract
    }

    SourceCodeExtract getSourceCodeExtract() {
        return sourceCodeExtract
    }

    CalculatedExplainPlan calculateCostliestNode() {
        return calculateCostliestNode(null)
    }

    CalculatedExplainPlan calculateCostliestNode(List<String> excludedNodeTypeList) {
        List<ExplainPlan> flatPlanList = flattenPostgresExplain(plan, new ArrayList<>())

        Double costliestNodeValue = null
        ExplainPlan costliestPlan = null
        flatPlanList.each {
            if (it.totalCost != null) {
                def excludedNode = false
                if (excludedNodeTypeList != null) {
                    for (String excludedNodeType : excludedNodeTypeList) {
                        if (excludedNodeType == it.nodeType) {
                            excludedNode = true
                            break
                        }
                    }
                }

                if (!excludedNode) {
                    if (costliestNodeValue == null) {
                        costliestNodeValue = it.totalCost
                        costliestPlan = it
                    } else if (costliestNodeValue < it.totalCost) {
                        costliestNodeValue = it.totalCost
                        costliestPlan = it
                    }
                }
            }
        }

        if (costliestNodeValue == null) {
            costliestNodeValue = 0.0
        }
        return new CalculatedExplainPlan(costliestNodeValue, costliestPlan, explainedStatement)
    }

    CalculatedExplainPlan calculateSlowestNode() {
        return calculateSlowestNode(null)
    }

    CalculatedExplainPlan calculateSlowestNode(List<String> excludedNodeTypeList) {
        List<ExplainPlan> flatPlanList = flattenPostgresExplain(plan, new ArrayList<>())

        Double slowestNodeValue = null
        ExplainPlan slowestPlan = null
        flatPlanList.each {
            if (it.totalCost != null) {
                def excludedNode = false
                if (excludedNodeTypeList != null) {
                    for (String excludedNodeType : excludedNodeTypeList) {
                        if (excludedNodeType == it.nodeType) {
                            excludedNode = true
                            break
                        }
                    }
                }

                if (!excludedNode) {
                    if (slowestNodeValue == null) {
                        slowestNodeValue = it.actualTotalTime
                        slowestPlan = it
                    } else if (slowestNodeValue < it.actualTotalTime) {
                        slowestNodeValue = it.actualTotalTime
                        slowestPlan = it
                    }
                }
            }
        }

        if (slowestNodeValue == null) {
            slowestNodeValue = 0.0
        }
        return new CalculatedExplainPlan(slowestNodeValue, slowestPlan, explainedStatement)
    }

    private static List<ExplainPlan> flattenPostgresExplain(ExplainPlan plan, List<ExplainPlan> planList) {
        planList.add(plan)
        if (plan.plans != null) {
            plan.plans.each {
                planList.addAll(flattenPostgresExplain(it, planList))
            }
        }
        return planList
    }

    //todo: largest node

    @Override
    String toString() {
        return "PostgresExplain: " + new ObjectMapper().writeValueAsString(this)
    }

}
