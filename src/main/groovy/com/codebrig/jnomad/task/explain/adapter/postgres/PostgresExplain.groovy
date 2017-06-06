package com.codebrig.jnomad.task.explain.adapter.postgres

import com.codebrig.jnomad.task.explain.CalculatedExplainPlan
import com.codebrig.jnomad.task.explain.ExplainResult
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class PostgresExplain extends ExplainResult {

    @JsonProperty("Plan")
    private PostgresExplainPlan plan
    @JsonProperty("Planning Time")
    private Double planningTime
    @JsonProperty("Execution Time")
    private Double executionTime
    @JsonProperty("Total Runtime")
    private Double totalRuntime
    @JsonProperty("Triggers")
    private List<PostgresTrigger> triggers

    PostgresExplainPlan getPlan() {
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

    CalculatedExplainPlan calculateCostliestNode() {
        return calculateCostliestNode(null)
    }

    @Override
    CalculatedExplainPlan calculateCostliestNode(List<String> excludedNodeTypeList) {
        List<PostgresExplainPlan> flatPlanList = flattenPostgresExplain(plan, new ArrayList<>())

        Double costliestNodeValue = null
        PostgresExplainPlan costliestPlan = null
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
        return new CalculatedExplainPlan(this, costliestNodeValue, costliestPlan, explainedStatement)
    }

    CalculatedExplainPlan calculateSlowestNode() {
        return calculateSlowestNode(null)
    }

    @Override
    CalculatedExplainPlan calculateSlowestNode(List<String> excludedNodeTypeList) {
        List<PostgresExplainPlan> flatPlanList = flattenPostgresExplain(plan, new ArrayList<>())

        Double slowestNodeValue = null
        PostgresExplainPlan slowestPlan = null
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
        return new CalculatedExplainPlan(this, slowestNodeValue, slowestPlan, explainedStatement)
    }

    private static List<PostgresExplainPlan> flattenPostgresExplain(PostgresExplainPlan plan, List<PostgresExplainPlan> planList) {
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
