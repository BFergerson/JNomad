package com.codebrig.jnomad.task.explain.transform.hql

import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.HierarchicalHQLVisitor
import net.sf.jsqlparser.expression.*
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import net.sf.jsqlparser.expression.operators.relational.InExpression
import net.sf.jsqlparser.expression.operators.relational.LikeExpression
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.select.Join
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.SubSelect
import net.sf.jsqlparser.statement.update.Update

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLColumnValueTransformer extends HierarchicalHQLVisitor {

    private QueryIndexReport queryReport
    private String tableName
    private PlainSelect plainSelect
    private Map<String, String> queryTableAliasMap = new HashMap<>()

    HQLColumnValueTransformer(QueryIndexReport queryReport) {
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
        if (update.fromItem != null && update.fromItem instanceof Table) {
            Table fromTable = (Table) update.fromItem
            if (fromTable.alias != null) {
                queryTableAliasMap.put(fromTable.alias.name, fromTable.name)
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
        if (plainSelect.where != null) {
            plainSelect.where.accept(this)
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
    void visit(SubSelect subSelect) {
        super.visit(subSelect)

        def subQueryTransformer = new HQLColumnValueTransformer(queryReport)
        if (subSelect.selectBody instanceof PlainSelect) {
            subQueryTransformer.visit((PlainSelect) subSelect.selectBody)
        }
    }

    @Override
    void visit(JdbcParameter jdbcParameter) {
        super.visit(jdbcParameter)

        //get proper table and column names
        Column column = (Column) getColumn(jdbcParameter)
        def queryTableName = tableName
        def columnName = null
        if (column != null) {
            columnName = column.columnName
            if (column.table != null && column.table.name != null) {
                def name = queryTableAliasMap.get(column.table.name)
                if (name != null) {
                    queryTableName = name
                } else {
                    //hibernate translation
                    queryTableName = queryReport.getSQLTableName(column.table.name)
                }
            }
        }

        def tableColumnName = queryReport.getSQLTableColumnName(queryTableName, columnName)

        def joinTableValue = null
        def extract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, queryTableName)
        if (extract != null) {
            def possibleDataType = extract.getQueryColumnDataTypeExtractor().columnDataTypeMap.get(columnName)
            boolean missingDataType = possibleDataType == null
            boolean knownDataType = false
            if (!missingDataType) {
                knownDataType = queryReport.databaseDataType.isKnownDataType(possibleDataType)
            }
            if ((missingDataType || !knownDataType) && extract.getQueryColumnJoinExtractor().columnJoinMap.containsValue(columnName)) {
                //column is used for join; use data type of id on join table
                def joinType = null
                extract.getQueryColumnJoinExtractor().columnJoinMap.each {
                    if (it.value == columnName) {
                        joinType = extract.getQueryColumnJoinExtractor().fieldMappedByTypeMap.get(it.key)
                    }
                }

                if (joinType != null) {
                    def joinTableName = queryReport.getSQLTableName(joinType)
                    def joinExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, joinTableName)
                    if (joinExtract != null) {
                        def joinTableColumnName = queryReport.getSQLTableColumnName(joinTableName, joinExtract.getQueryColumnAliasExtractor().idName)
                        joinTableValue = queryReport.databaseDataType.generateNomadSQLValue(joinTableColumnName)
                    }
                }
            }
        }

        def value
        if (joinTableValue != null) {
            value = joinTableValue
        } else {
            value = queryReport.databaseDataType.generateNomadSQLValue(tableColumnName)
        }
        def parent = getParent(jdbcParameter)
        if (parent instanceof BinaryExpression) {
            if (parent.leftExpression == jdbcParameter) {
                parent.leftExpression = value

                if (parent.rightExpression instanceof CastExpression) {
                    def cast = (CastExpression) parent.rightExpression
                    parent.leftExpression = queryReport.databaseDataType.generateNomadSQLValue(cast.type)
                }
            } else {
                parent.rightExpression = value

                if (parent.leftExpression instanceof CastExpression) {
                    def cast = (CastExpression) parent.leftExpression
                    parent.rightExpression = queryReport.databaseDataType.generateNomadSQLValue(cast.type)
                }
            }
        } else if (parent instanceof Function) {
            ExpressionList expressionList = new ExpressionList()
            expressionList.expressions = new ArrayList<Expression>()

            parent.parameters.expressions.each {
                if (it == jdbcParameter) {
                    def queryNomadValue = queryReport.databaseDataType.generateNomadSQLValue(tableColumnName)
                    expressionList.expressions.add(queryNomadValue)
                } else {
                    expressionList.expressions.add(it)
                }
            }
            parent.parameters = expressionList
        }
    }

    @Override
    void visit(JdbcNamedParameter jdbcNamedParameter) {
        super.visit(jdbcNamedParameter)

        //get proper table and column names
        Column column = (Column) getColumn(jdbcNamedParameter)
        def queryTableName = tableName
        def columnName = null
        if (column != null) {
            columnName = column.columnName
            if (column.table != null && column.table.name != null) {
                def name = queryTableAliasMap.get(column.table.name)
                if (name != null) {
                    queryTableName = name
                } else {
                    //hibernate translation
                    queryTableName = queryReport.getSQLTableName(column.table.name)
                }
            }
        }

        def tableColumnName = queryReport.getSQLTableColumnName(queryTableName, columnName)

        def joinTableValue = null
        def extract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, queryTableName)
        if (extract != null) {
            def possibleDataType = extract.getQueryColumnDataTypeExtractor().columnDataTypeMap.get(columnName)
            boolean missingDataType = possibleDataType == null
            boolean knownDataType = false
            if (!missingDataType) {
                knownDataType = queryReport.databaseDataType.isKnownDataType(possibleDataType)
            }
            if ((missingDataType || !knownDataType) && extract.getQueryColumnJoinExtractor().columnJoinMap.containsValue(columnName)) {
                //column is used for join; use data type of id on join table
                def joinType = null
                extract.getQueryColumnJoinExtractor().columnJoinMap.each {
                    if (it.value == columnName) {
                        joinType = extract.getQueryColumnJoinExtractor().fieldMappedByTypeMap.get(it.key)
                    }
                }

                if (joinType != null) {
                    def joinTableName = queryReport.getSQLTableName(joinType)
                    def joinExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, joinTableName)
                    if (joinExtract != null) {
                        def joinTableColumnName = queryReport.getSQLTableColumnName(joinTableName, joinExtract.getQueryColumnAliasExtractor().idName)
                        joinTableValue = queryReport.databaseDataType.generateNomadSQLValue(joinTableColumnName)
                    }
                }
            }
        }

        def value
        if (joinTableValue != null) {
            value = joinTableValue
        } else {
            value = queryReport.databaseDataType.generateNomadSQLValue(tableColumnName)
        }
        def parent = getParent(jdbcNamedParameter)
        if (parent instanceof BinaryExpression) {
            if (parent.leftExpression == jdbcNamedParameter) {
                parent.leftExpression = value

                if (parent instanceof LikeExpression) {
                    //use text
                    parent.leftExpression = queryReport.databaseDataType.getDefaultSQLStringExpression()
                } else if (parent.rightExpression instanceof CastExpression) {
                    def cast = (CastExpression) parent.rightExpression
                    parent.leftExpression = queryReport.databaseDataType.generateNomadSQLValue(cast.type)
                }
            } else {
                parent.rightExpression = value

                if (parent instanceof LikeExpression) {
                    //use text
                    parent.rightExpression = queryReport.databaseDataType.getDefaultSQLStringExpression()
                } else if (parent.leftExpression instanceof CastExpression) {
                    def cast = (CastExpression) parent.leftExpression
                    parent.rightExpression = queryReport.databaseDataType.generateNomadSQLValue(cast.type)
                }
            }
        } else if (parent instanceof Function) {
            ExpressionList expressionList = new ExpressionList()
            expressionList.expressions = new ArrayList<Expression>()

            parent.parameters.expressions.each {
                if (it == jdbcNamedParameter) {
                    def queryNomadValue = queryReport.databaseDataType.generateNomadSQLValue(tableColumnName)
                    expressionList.expressions.add(queryNomadValue)
                } else {
                    expressionList.expressions.add(it)
                }
            }
            parent.parameters = expressionList
        }
    }

    @Override
    void visit(InExpression inExpression) {
        super.visit(inExpression)

        //get proper table and column names
        def columnTableName = tableName
        def columnName = null
        if (inExpression.leftExpression instanceof Column) {
            Column column = (Column) inExpression.leftExpression
            columnName = column.columnName

            if (column.table != null && column.table.name != null) {
                def name = queryTableAliasMap.get(column.table.name)
                if (name != null) columnTableName = name
            }
        }

        ExpressionList expressionList = new ExpressionList()
        expressionList.expressions = new ArrayList<Expression>()

        if (inExpression.rightItemsList instanceof SubSelect) {
            ((SubSelect) inExpression.rightItemsList).selectBody.accept(this)
            return
        }

        def tableColumnName = queryReport.getSQLTableColumnName(columnTableName, columnName)
        ((ExpressionList) inExpression.rightItemsList).expressions.each {
            def value = queryReport.databaseDataType.generateNomadSQLValue(tableColumnName)
            if (it instanceof JdbcNamedParameter) {
                expressionList.expressions.add(value)
            } else if (it instanceof JdbcParameter) {
                expressionList.expressions.add(value)
            } else {
                expressionList.expressions.add(it)
            }
        }
        inExpression.rightItemsList = expressionList
    }

}
