package com.codebrig.jnomad.task.explain.adapter.postgres

import com.codebrig.jnomad.task.explain.ExplainPlan
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class PostgresExplainPlan extends ExplainPlan  {

    @JsonProperty("Node Type")
    private String nodeType
    @JsonProperty("Operation")
    private String operation
    @JsonProperty("Strategy")
    private String strategy
    @JsonProperty("Partial Mode")
    private String partialMode
    @JsonProperty("Parallel Aware")
    private String parallelAware
    @JsonProperty("Join Type")
    private String joinType
    @JsonProperty("Parent Relationship")
    private String parentRelationship
    @JsonProperty("Subplan Name")
    private String subplanName
    @JsonProperty("Scan Direction")
    private String scanDirection
    @JsonProperty("Index Name")
    private String indexName
    @JsonProperty("Relation Name")
    private String relationName
    @JsonProperty("Alias")
    private String alias
    @JsonProperty("Startup Cost")
    private Double startupCost
    @JsonProperty("Total Cost")
    private Double totalCost
    @JsonProperty("Plan Rows")
    private Integer planRows
    @JsonProperty("Plan Width")
    private Integer planWidth
    @JsonProperty("Recheck Cond")
    private String recheckCond
    @JsonProperty("Hash Cond")
    private String hashCond
    @JsonProperty("Group Key")
    private String[] groupKey
    @JsonProperty("Sort Key")
    private String[] sortKey
    @JsonProperty("Sort Method")
    private String sortMethod
    @JsonProperty("Sort Space Used")
    private Integer sortSpaceUsed
    @JsonProperty("Sort Space Type")
    private String sortSpaceType
    @JsonProperty("Join Filter")
    private String joinFilter
    @JsonProperty("One-Time Filter")
    private String oneTimeFilter
    @JsonProperty("Filter")
    private String filter
    @JsonProperty("Index Cond")
    private String indexCond
    @JsonProperty("Actual Startup Time")
    private Double actualStartupTime
    @JsonProperty("Actual Total Time")
    private Double actualTotalTime
    @JsonProperty("Actual Rows")
    private Integer actualRows
    @JsonProperty("Actual Loops")
    private Integer actualLoops
    @JsonProperty("Hash Buckets")
    private Integer hashBuckets
    @JsonProperty("Original Hash Buckets")
    private Integer originalHashBuckets
    @JsonProperty("Hash Batches")
    private Integer hashBatches
    @JsonProperty("Original Hash Batches")
    private Integer originalHashBatches
    @JsonProperty("Peak Memory Usage")
    private Integer peakMemoryUsage
    @JsonProperty("Rows Removed by Filter")
    private Integer rowsRemovedByFilter
    @JsonProperty("Rows Removed by Join Filter")
    private Integer rowsRemovedByJoinFilter
    @JsonProperty("Rows Removed by Index Recheck")
    private Integer rowsRemovedByIndexRecheck
    @JsonProperty("Heap Fetches")
    private Integer heapFetches
    @JsonProperty("Exact Heap Blocks")
    private Integer exactHeapBlocks
    @JsonProperty("Lossy Heap Blocks")
    private Integer lossyHeapBlocks
    @JsonProperty("Plans")
    private List<PostgresExplainPlan> plans

    String getNodeType() {
        return nodeType
    }

    String getOperation() {
        return operation
    }

    String getStrategy() {
        return strategy
    }

    String getPartialMode() {
        return partialMode
    }

    String getParallelAware() {
        return parallelAware
    }

    String getJoinType() {
        return joinType
    }

    String getParentRelationship() {
        return parentRelationship
    }

    String getSubplanName() {
        return subplanName
    }

    String getScanDirection() {
        return scanDirection
    }

    String getIndexName() {
        return indexName
    }

    String getRelationName() {
        return relationName
    }

    String getAlias() {
        return alias
    }

    Double getStartupCost() {
        return startupCost
    }

    Double getTotalCost() {
        return totalCost
    }

    Integer getPlanRows() {
        return planRows
    }

    Integer getPlanWidth() {
        return planWidth
    }

    String getRecheckCond() {
        return recheckCond
    }

    String getHashCond() {
        return hashCond
    }

    String[] getGroupKey() {
        return groupKey
    }

    String[] getSortKey() {
        return sortKey
    }

    String getSortMethod() {
        return sortMethod
    }

    Integer getSortSpaceUsed() {
        return sortSpaceUsed
    }

    String getSortSpaceType() {
        return sortSpaceType
    }

    String getJoinFilter() {
        return joinFilter
    }

    String getOneTimeFilter() {
        return oneTimeFilter
    }

    String getFilter() {
        return filter
    }

    String getIndexCond() {
        return indexCond
    }

    Double getActualStartupTime() {
        return actualStartupTime
    }

    Double getActualTotalTime() {
        return actualTotalTime
    }

    Integer getActualRows() {
        return actualRows
    }

    Integer getActualLoops() {
        return actualLoops
    }

    Integer getHashBuckets() {
        return hashBuckets
    }

    Integer getOriginalHashBuckets() {
        return originalHashBuckets
    }

    Integer getHashBatches() {
        return hashBatches
    }

    Integer getOriginalHashBatches() {
        return originalHashBatches
    }

    Integer getPeakMemoryUsage() {
        return peakMemoryUsage
    }

    Integer getRowsRemovedByFilter() {
        return rowsRemovedByFilter
    }

    Integer getRowsRemovedByJoinFilter() {
        return rowsRemovedByJoinFilter
    }

    Integer getRowsRemovedByIndexRecheck() {
        return rowsRemovedByIndexRecheck
    }

    Integer getHeapFetches() {
        return heapFetches
    }

    Integer getExactHeapBlocks() {
        return exactHeapBlocks
    }

    Integer getLossyHeapBlocks() {
        return lossyHeapBlocks
    }

    List<PostgresExplainPlan> getPlans() {
        return plans
    }

    @Override
    String getTableName() {
        return relationName
    }

    @Override
    String getConditionClause() {
        def conditionStatementString = filter
        if (conditionStatementString == null) {
            conditionStatementString = recheckCond
        }
        return conditionStatementString
    }

}
