package com.codebrig.jnomad.task.explain

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
abstract class ExplainPlan {

    abstract String getTableName()
    abstract String getConditionClause()

}
