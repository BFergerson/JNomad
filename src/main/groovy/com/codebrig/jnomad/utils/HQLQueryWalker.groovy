package com.codebrig.jnomad.utils

import net.sf.jsqlparser.expression.Function
import net.sf.jsqlparser.expression.operators.relational.InExpression
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.select.*
import net.sf.jsqlparser.statement.update.Update

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLQueryWalker extends HierarchicalHQLVisitor {

    @Override
    void visit(Select select) {
        super.visit(select)

        select.selectBody.accept(this)
    }

    @Override
    void visit(Delete delete) {
        super.visit(delete)

        if (delete.where != null) {
            delete.where.accept(this)
        }
    }

    @Override
    void visit(Update update) {
        super.visit(update)

        if (update.where != null) {
            update.where.accept(this)
        }
        if (update.columns != null) {
            update.columns.each {
                it.accept(this)
            }
        }
        if (update.expressions != null) {
            update.expressions.each {
                it.accept(this)
            }
        }
    }

    @Override
    void visit(PlainSelect plainSelect) {
        super.visit(plainSelect)

        if (plainSelect.selectItems != null) {
            plainSelect.selectItems.each {
                if (it instanceof SelectExpressionItem) {
                    it.expression.accept(this)
                }
            }
        }
        if (plainSelect.where != null) {
            plainSelect.where.accept(this)
        }
        if (plainSelect.groupByColumnReferences != null) {
            plainSelect.groupByColumnReferences.each {
                it.accept(this)
            }
        }
        if (plainSelect.orderByElements != null) {
            plainSelect.orderByElements.each {
                it.expression.accept(this)
            }
        }
    }

    @Override
    void visit(Function function) {
        super.visit(function)

        if (function.parameters != null) {
            function.parameters.expressions.each {
                it.accept(this)
            }
        }
    }

    @Override
    void visit(InExpression inExpression) {
        super.visit(inExpression)

        if (inExpression.leftExpression instanceof Column) {
            inExpression.leftExpression.accept(this)
        }
    }

}
