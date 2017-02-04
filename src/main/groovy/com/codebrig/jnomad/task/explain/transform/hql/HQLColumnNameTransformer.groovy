package com.codebrig.jnomad.task.explain.transform.hql

import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.HierarchicalHQLVisitor
import net.sf.jsqlparser.expression.Function
import net.sf.jsqlparser.expression.operators.relational.InExpression
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.select.*
import net.sf.jsqlparser.statement.update.Update

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLColumnNameTransformer extends HierarchicalHQLVisitor {

    private QueryIndexReport queryReport
    private String tableName
    private PlainSelect plainSelect
    private Map<String, String> queryTableAliasMap = new HashMap<>()
    private Map<String, String> queryTableTranslationMap = new HashMap<>()

    HQLColumnNameTransformer(QueryIndexReport queryReport) {
        this.queryReport = queryReport
    }

    def getQueryTableTranslationMap() {
        return queryTableTranslationMap
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
            tableName = queryReport.getSQLTableName(delete.table.name)

            if (delete.table.alias != null) {
                queryTableAliasMap.put(delete.table.alias.name, delete.table.name)
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
            if (update.tables.get(0) instanceof Table) {
                def table = (Table) update.tables.get(0)
                tableName = queryReport.getSQLTableName(table.name)

                if (table.alias != null) {
                    queryTableAliasMap.put(table.alias.name, table.name)
                }
            }
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
    void visit(PlainSelect plainSelect) {
        super.visit(plainSelect)

        this.plainSelect = plainSelect
        if (plainSelect.fromItem != null) {
            if (plainSelect.fromItem instanceof Table) {
                def table = (Table) plainSelect.fromItem
                tableName = queryReport.getSQLTableName(table.name)

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

    @Override
    void visit(SubSelect subSelect) {
        super.visit(subSelect)

        def subQueryTransformer = new HQLColumnNameTransformer(queryReport)
        if (subSelect.selectBody instanceof PlainSelect) {
            subQueryTransformer.visit((PlainSelect) subSelect.selectBody)
        }
    }

    @Override
    void visit(Column column) {
        super.visit(column)

        //do any necessary table name translations
        if (!queryTableTranslationMap.isEmpty()) {
            if (column.table != null && column.table.name != null && queryTableTranslationMap.containsKey(column.table.name)) {
                column.table.name = queryTableTranslationMap.get(column.table.name)
                queryTableAliasMap.each {
                    if (it.value == column.table.name) {
                        column.table.name = it.key
                    }
                }
            }
        }

        def columnTableName = tableName
        if (column.table != null && column.table.name != null) {
            def name = queryTableAliasMap.get(column.table.name)
            if (name != null) {
                columnTableName = name
            } else {
                //hibernate translation
                columnTableName = queryReport.getSQLTableName(column.table.name)
            }
        }
        def sqlColumnName = queryReport.getSQLColumnName(columnTableName, column.columnName)

        //extreme failsafe; todo: explain how it works and why it was needed
        if (column.table != null && column.table.schemaName != null) {
            def tableName = column.table.schemaName
            if (queryTableAliasMap.containsKey(tableName)) {
                tableName = queryTableAliasMap.get(tableName)
            }

            def possibleLinkColumn
            def extract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, tableName)
            if (extract != null && extract.getQueryColumnJoinExtractor().columnJoinMap.containsKey(columnTableName)) {
                possibleLinkColumn = extract.getQueryColumnJoinExtractor().columnJoinMap.get(columnTableName)
                extract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, columnTableName)
                if (extract != null && !queryTableAliasMap.containsKey(column.columnName) && "*" != sqlColumnName) {
                    def excludeList = new ArrayList<>()
                    excludeList.add(extract)

                    while ((extract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, columnTableName, excludeList)) != null) {
                        def columnExtractor = extract.getQueryColumnAliasExtractor()
                        def joinExtractor = extract.getQueryColumnJoinExtractor()
                        if (columnExtractor.columnNameAliasMap.containsKey(possibleLinkColumn) || joinExtractor.columnJoinMap.containsValue(possibleLinkColumn)) {
                            sqlColumnName = possibleLinkColumn
                            break
                        }
                        excludeList.add(extract)
                    }
                }
            }
        }

        def extract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, columnTableName)
        if (extract != null) {
            if (extract.getQueryColumnJoinExtractor().columnJoinMap.containsKey(sqlColumnName)) {
                sqlColumnName = extract.getQueryColumnJoinExtractor().columnJoinMap.get(sqlColumnName)
            }
        }

        column.columnName = sqlColumnName
        if (column.table == null || column.table.name == null || column.table.name.isEmpty()) {
            queryTableAliasMap.each {
                if (it.value == columnTableName) {
                    columnTableName = it.key
                }
            }
            if (columnTableName != column.columnName && (column.columnName != "true" && column.columnName != "false")) {
                if (column.columnName.startsWith("\"")) {
                    if (!column.columnName.startsWith("\"" + columnTableName)) {
                        column.table = new Table(columnTableName)
                    }
                } else if (plainSelect != null) {
                    column.table = new Table(columnTableName)
                }
            }

            //add asterisk if applicable
            if (queryTableAliasMap.containsKey(column.columnName) || queryReport.getSQLTableName(sqlColumnName) == tableName) {
                column.table = new Table(columnTableName)
                column.columnName = "*"
            }
        }
    }

}
