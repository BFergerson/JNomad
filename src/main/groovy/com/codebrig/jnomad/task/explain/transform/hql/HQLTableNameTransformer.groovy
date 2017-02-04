package com.codebrig.jnomad.task.explain.transform.hql

import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.utils.HierarchicalHQLVisitor
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.SetStatement
import net.sf.jsqlparser.statement.Statements
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.create.index.CreateIndex
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.view.AlterView
import net.sf.jsqlparser.statement.create.view.CreateView
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.execute.Execute
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.merge.Merge
import net.sf.jsqlparser.statement.replace.Replace
import net.sf.jsqlparser.statement.select.*
import net.sf.jsqlparser.statement.truncate.Truncate
import net.sf.jsqlparser.statement.update.Update

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLTableNameTransformer extends HierarchicalHQLVisitor implements FromItemVisitor {

    private QueryIndexReport queryReport

    HQLTableNameTransformer(QueryIndexReport queryReport) {
        this.queryReport = queryReport
    }

    @Override
    void visit(Select select) {
        super.visit(select)

        select.selectBody.accept(this)
    }

    @Override
    void visit(Delete delete) {
        super.visit(delete)

        if (delete.table != null) {
            delete.table.accept(this)
        }
        if (delete.tables != null) {
            delete.tables.each {
                it.accept(this)
            }
        }
        if (delete.where != null) {
            delete.where.accept(this)
        }
    }

    @Override
    void visit(Update update) {
        super.visit(update)

        if (update.tables != null) {
            update.tables.each {
                it.accept(this)
            }
        }
        if (update.fromItem != null && update.fromItem instanceof Table) {
            Table fromTable = (Table) update.fromItem
            fromTable.accept(this)
        }
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
    void visit(Insert insert) {
    }

    @Override
    void visit(Replace replace) {
    }

    @Override
    void visit(Drop drop) {
    }

    @Override
    void visit(Truncate truncate) {
    }

    @Override
    void visit(CreateIndex createIndex) {
    }

    @Override
    void visit(CreateTable createTable) {
    }

    @Override
    void visit(CreateView createView) {
    }

    @Override
    void visit(AlterView alterView) {
    }

    @Override
    void visit(Alter alter) {
    }

    @Override
    void visit(Statements stmts) {
    }

    @Override
    void visit(Execute execute) {
    }

    @Override
    void visit(SetStatement set) {
    }

    @Override
    void visit(Merge merge) {
    }

    @Override
    void visit(PlainSelect plainSelect) {
        if (plainSelect.fromItem != null) {
            plainSelect.fromItem.accept(this)
        }

        if (plainSelect.where != null) {
            plainSelect.where.accept(this)
        }
    }

    @Override
    void visit(SetOperationList setOpList) {
    }

    @Override
    void visit(WithItem withItem) {
    }

    @Override
    void visit(Table table) {
        table.name = queryReport.getSQLTableName(table.name)
    }

    @Override
    void visit(SubSelect subSelect) {
        super.visit(subSelect)

        subSelect.selectBody.accept(this)
    }

    @Override
    void visit(SubJoin subjoin) {
    }

    @Override
    void visit(LateralSubSelect lateralSubSelect) {
    }

    @Override
    void visit(ValuesList valuesList) {
    }

    @Override
    void visit(TableFunction tableFunction) {
    }

}
