package com.codebrig.jnomad.task.resolve

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class ResolvedVariable {

    private boolean dynamicVariable
    private long solveTime

    boolean getDynamicVariable() {
        return dynamicVariable
    }

    void setDynamicVariable(boolean dynamicVariable) {
        this.dynamicVariable = dynamicVariable
    }

    long getSolveTime() {
        return solveTime
    }

    void setSolveTime(long solveTime) {
        this.solveTime = solveTime
    }

}
