package com.codebrig.jnomad.utils

import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.select.*
import net.sf.jsqlparser.statement.update.Update

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLTableAliasWalker extends HQLQueryWalker implements FromItemVisitor {

    private Map<String, String> queryTableAliasMap = new HashMap<>()

    Map<String, String> getQueryTableAliasMap() {
        return queryTableAliasMap
    }

    @Override
    void visit(Delete delete) {
        super.visit(delete)

        if (delete.table != null) {
            if (delete.table.alias != null) {
                queryTableAliasMap.put(delete.table.alias.name, delete.table.name)
            }
        }
    }

    @Override
    void visit(Update update) {
        super.visit(update)

        if (update.tables != null) {
            if (update.tables.get(0) instanceof Table) {
                def table = (Table) update.tables.get(0)
                if (table.alias != null) {
                    queryTableAliasMap.put(table.alias.name, table.name)
                }
            }
        }
        if (update.fromItem != null && update.fromItem instanceof Table) {
            def table = (Table) update.fromItem
            if (table.alias != null) {
                queryTableAliasMap.put(table.alias.name, table.name)
            }
        }
    }

    @Override
    void visit(PlainSelect plainSelect) {
        super.visit(plainSelect)

        if (plainSelect.fromItem != null) {
            if (plainSelect.fromItem instanceof Table) {
                def table = (Table) plainSelect.fromItem
                if (plainSelect.fromItem.alias != null) {
                    queryTableAliasMap.put(table.alias.name, table.name)
                }
            }
        }
        if (plainSelect.joins != null) {
            for (Join join : plainSelect.joins) {
                if (join.rightItem instanceof Table && join.rightItem.alias != null) {
                    def table = (Table) join.rightItem
                    queryTableAliasMap.put(table.alias.name, table.name)
                }
            }
        }
    }

    @Override
    void visit(Table table) {
        if (table.alias != null) {
            queryTableAliasMap.put(table.alias.name, table.name)
        }
    }

    @Override
    void visit(Column column) {
        super.visit(column)

        if (column.table != null) {
            visit(column.table)
        }
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
