package com.codebrig.jnomad.task.explain.transform.hql

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.extractor.query.QueryColumnJoinExtractor
import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.HierarchicalHQLVisitor
import net.sf.jsqlparser.expression.operators.relational.EqualsTo
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.select.Join
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.SelectExpressionItem
import net.sf.jsqlparser.statement.update.Update
import org.apache.commons.lang3.ObjectUtils

import java.util.regex.Pattern

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class HQLTableJoinTransformer extends HierarchicalHQLVisitor {

    private QueryIndexReport queryReport
    private String tableName
    private PlainSelect plainSelect
    private Delete plainDelete
    private Update plainUpdate
    private Map<String, String> queryTableAliasMap = new HashMap<>()
    private Set<String> joinTableAliasSet = new HashSet<>()

    HQLTableJoinTransformer(QueryIndexReport queryReport) {
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

        this.plainDelete = delete
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

        this.plainUpdate = update
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
            //wrap plainSelect.joins in new list as joins may be added dynamically
            for (Join join : new ArrayList<Join>(plainSelect.joins)) {
                if (join.rightItem instanceof Table) {
                    def table = (Table) join.rightItem
                    if (join.rightItem.alias != null) {
                        queryTableAliasMap.put(table.alias.name, table.name)
                        joinTableAliasSet.add(table.alias.name)
                    }

                    addTableJoin(join, table)
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
    void visit(Column column) {
        super.visit(column)

        if (column.columnName.contains("\"")) {
            column.columnName = column.columnName.replace("\"", "")
        } else if (column.table == null || column.table.name == null || column.table.name == tableName) {
            return //no type of join here
        } else {
            def name = queryTableAliasMap.get(column.table.name)
            if (name != null) {
                if (name == tableName) {
                    //check for direct field reference
                    def tableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, name)
                    if (tableExtract != null) {
                        if (tableExtract.getQueryColumnJoinExtractor().columnJoinMap.containsKey(column.columnName)) {
                            column.table = new Table(column.table.name + "." + column.columnName)
                            column.columnName = tableExtract.getQueryColumnJoinExtractor().columnJoinMap.get(column.columnName)
                            visit(column)
                        }
                    }
                }
                return //no type of join here
            } else if (name != null) {
                def tableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, name)
                if (tableExtract != null) {
                    if ((queryTableAliasMap.containsKey(name) || queryTableAliasMap.containsValue(name)) &&
                            (tableExtract.queryColumnAliasExtractor.columnNameAliasMap.containsKey(column.columnName) ||
                                    tableExtract.queryColumnAliasExtractor.columnNameAliasMap.containsValue(column.columnName))) {
                        return //no type of join here
                    }
                }
            }
        }

        //skip arrays (which may contain periods making it looking like a join)
        if (column.table != null && column.table.name != null) {
            if (column.table.name.startsWith("jnomad_array")) {
                return //no type of join here
            }
        }

        String finalJoinColumn = null
        String finalJoinTable = null
        def strParts = column.toString().split(Pattern.quote("."))
        for (int i = 1; i < strParts.length; i++) {
            def leftTableName = strParts[i - 1]
            if (queryTableAliasMap.containsKey(leftTableName)) {
                leftTableName = queryTableAliasMap.get(leftTableName)
            }
            leftTableName = queryReport.getSQLTableName(leftTableName)
            strParts[i - 1] = leftTableName

            def rightTableName = strParts[i]
            def leftTableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, leftTableName)
            if (leftTableExtract != null) {
                def typeName = leftTableExtract.queryColumnJoinExtractor.fieldMappedByTypeMap.get(rightTableName)
                if (typeName != null) {
                    rightTableName = typeName
                }
            }
            if (queryTableAliasMap.containsKey(rightTableName)) {
                rightTableName = queryTableAliasMap.get(rightTableName)
            }
            rightTableName = queryReport.getSQLTableName(rightTableName)
            strParts[i] = rightTableName

            def rightTableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, rightTableName)
            if (rightTableExtract == null) {
                if (leftTableExtract != null && leftTableExtract.queryColumnAliasExtractor.columnNameAliasMap.containsKey(rightTableName)) {
                    rightTableName = leftTableName
                    leftTableName = tableName
                    rightTableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, rightTableName)
                }

                if (rightTableExtract == null && leftTableName != null) {
                    //make sure isn't embeddable of main table
                    if (tableName == leftTableName && (i + 1) < strParts.length) {
                        leftTableName = rightTableName
                        rightTableName = strParts[++i]
                    }
                    def mainExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, tableName)
                    def embedExtract = null
                    if (mainExtract != null) {
                        embedExtract = CodeLocator.getJPAEmbeddableSourceCodeExtract(
                                queryReport.jNomad, mainExtract.queryColumnJoinExtractor.embeddedJoinTableTypeMap.get(leftTableName))
                    }

                    if (embedExtract != null) {
                        finalJoinTable = tableName
                        finalJoinColumn = embedExtract.queryColumnAliasExtractor.columnNameAliasMap.get(rightTableName)
                        break
                    } else if (mainExtract != null) {
                        def joinType = mainExtract.queryColumnJoinExtractor.fieldMappedByTypeMap.get(leftTableName)
                        if (joinType != null) {
                            def joinTableName = queryReport.getSQLTableName(joinType)
                            def tableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, joinTableName)
                            if (tableExtract != null && tableExtract.queryColumnAliasExtractor.columnNameAliasMap.containsKey(rightTableName)) {
                                leftTableName = tableName
                                rightTableName = joinTableName
                                rightTableExtract = tableExtract
                            } else {
                                throw new HQLTransformException("Failed HQL join translation on query: "
                                        + ObjectUtils.firstNonNull(plainSelect, plainUpdate, plainDelete))
                            }
                        } else if (leftTableExtract != null && leftTableExtract.queryColumnJoinExtractor.embeddedJoinTableTypeMap.containsKey(rightTableName)) {
                            finalJoinTable = leftTableName
                            finalJoinColumn = strParts[++i]
                            continue
                        } else {
                            throw new HQLTransformException("Failed HQL join translation on query: "
                                    + ObjectUtils.firstNonNull(plainSelect, plainUpdate, plainDelete))
                        }
                    } else {
                        throw new HQLTransformException("Failed HQL join translation on query: "
                                + ObjectUtils.firstNonNull(plainSelect, plainUpdate, plainDelete))
                    }
                } else if (rightTableExtract == null) {
                    throw new HQLTransformException("Failed HQL join translation on query: "
                            + ObjectUtils.firstNonNull(plainSelect, plainUpdate, plainDelete))
                }
            }

            //handles embedded tables
            SourceCodeExtract possibleEmbeddableExtract
            def embeddedFound = false
            while ((i + 1) < strParts.length) {
                possibleEmbeddableExtract = CodeLocator.getJPAEmbeddableSourceCodeExtract(
                        queryReport.jNomad, rightTableExtract.queryColumnJoinExtractor.embeddedJoinTableTypeMap.get(strParts[i + 1]))

                if (possibleEmbeddableExtract != null) {
                    embeddedFound = true
                    i++
                    rightTableExtract = possibleEmbeddableExtract
                } else {
                    if (embeddedFound) possibleEmbeddableExtract = rightTableExtract
                    break
                }
            }

            if (possibleEmbeddableExtract != null) {
                //follow embedded objects till column is found
                addTableJoin(new Table(leftTableName), new Table(rightTableName))
                finalJoinColumn = possibleEmbeddableExtract.queryColumnAliasExtractor.columnNameAliasMap.get(strParts[++i++])
                finalJoinTable = rightTableName
            } else {
                finalJoinColumn = addTableJoin(new Table(leftTableName), new Table(rightTableName))
                finalJoinTable = rightTableName
            }

            if ((i + 1) == strParts.length - 1) {
                finalJoinColumn = strParts[++i]
            }
        }

        if (finalJoinTable != null) {
            column.table = new Table(finalJoinTable)
            if (queryTableAliasMap.containsValue(finalJoinTable)) {
                queryTableAliasMap.each {
                    if (it.value == finalJoinTable) {
                        column.table = new Table(it.key)
                    }
                }
            }
            column.columnName = queryReport.getSQLColumnName(finalJoinTable, finalJoinColumn)
        }
    }

    private String addTableJoin(Table leftTable, Table rightTable) {
        def joinSourceExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, leftTable.name)
        def extractor = joinSourceExtract.getQueryColumnJoinExtractor()

        String linkColumnName = extractor.columnJoinMap.get(rightTable.name)
        if (linkColumnName == null) {
            def linkColumnMap = extractor.fieldMappedByTypeMap.find {
                queryReport.getSQLTableName(it.value) == rightTable.name
            }
            linkColumnName = extractor.columnJoinMap.get(linkColumnMap?.key)
        }

        if (linkColumnName == null) {
            //failsafe; maybe another table with same name?
            def excludeList = new ArrayList<>()
            excludeList.add(joinSourceExtract)

            while ((joinSourceExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, leftTable.name, excludeList)) != null) {
                extractor = joinSourceExtract.getQueryColumnJoinExtractor()

                linkColumnName = extractor.columnJoinMap.get(rightTable.name)
                if (linkColumnName == null) {
                    def linkColumnMap = extractor.fieldMappedByTypeMap.find {
                        queryReport.getSQLTableName(it.value) == rightTable.name
                    }
                    linkColumnName = extractor.columnJoinMap.get(linkColumnMap?.key)
                }
                if (linkColumnName != null) {
                    break
                }
            }
        }

        if (linkColumnName != null) {
            def join = new Join()
            join.rightItem = rightTable

            def equalsTo = new EqualsTo()
            if (queryTableAliasMap.containsValue(leftTable.name)) {
                queryTableAliasMap.each {
                    if (it.value == leftTable.name) {
                        equalsTo.leftExpression = new Column(new Table(it.key), linkColumnName)
                    }
                }
            } else {
                equalsTo.leftExpression = new Column(leftTable, linkColumnName)
            }

            def rightTableExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, rightTable.name)
            if (rightTableExtract != null && rightTableExtract.queryColumnAliasExtractor.idName != null) {
                linkColumnName = rightTableExtract.queryColumnAliasExtractor.idName
            }

            if (queryTableAliasMap.containsValue(rightTable.name)) {
                queryTableAliasMap.each {
                    if (it.value == rightTable.name) {
                        equalsTo.rightExpression = new Column(new Table(it.key), linkColumnName)
                    }
                }
            } else {
                equalsTo.rightExpression = new Column(rightTable, linkColumnName)
            }
            join.onExpression = equalsTo

            List<Join> joins = null
            if (plainSelect != null) {
                if (plainSelect.joins == null) {
                    plainSelect.joins = joins = new ArrayList<>()
                } else {
                    joins = plainSelect.joins
                }
            } else if (plainUpdate != null) {
                if (plainUpdate.joins == null) {
                    plainUpdate.joins = joins = new ArrayList<>()
                } else {
                    joins = plainUpdate.joins
                }
            } else if (plainDelete != null) {
                if (plainDelete.joins == null) {
                    plainDelete.joins = joins = new ArrayList<>()
                } else {
                    joins = plainDelete.joins
                }
            }

            //ensure join doesn't already exist
            boolean duplicateJoin = false
            joins.each {
                if (it.toString() == join.toString()) {
                    duplicateJoin = true
                }
            }
            if (!duplicateJoin) joins.add(join)
        }

        return linkColumnName
    }

    private void addTableJoin(Join join, Table table) {
        def extractTableName = tableName
        def joinTableName = null
        boolean hasAlias = false

        if (table.database != null) {
            def name = queryTableAliasMap.get(table.database)
            if (name == tableName) {
                hasAlias = true
                joinTableName = table.schemaName
            } else if (joinTableAliasSet.contains(table.schemaName)) {
                extractTableName = queryTableAliasMap.get(table.schemaName)
                hasAlias = true
                joinTableName = table.name
            }
        }
        if (table.schemaName != null) {
            def name = queryTableAliasMap.get(table.schemaName)
            if (name == tableName) {
                hasAlias = true
                joinTableName = table.name
            }
        }

        if (!hasAlias && (joinTableName == null || joinTableName == tableName)) {
            return //no join needed
        }

        def joinSourceExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, extractTableName)
        if (joinSourceExtract == null) {
            return //todo: remove after fixing (if scanning one file, won't use source files for table name translations)
        }
        def extractor = joinSourceExtract.getQueryColumnJoinExtractor()
        boolean madeJoin = makeJoin(extractor, joinTableName, join, table, extractTableName)

        if (!madeJoin) {
            //failsafe; maybe another table with same name?
            def excludeList = new ArrayList<>()
            excludeList.add(joinSourceExtract)

            while ((joinSourceExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, tableName, excludeList)) != null) {
                extractor = joinSourceExtract.getQueryColumnJoinExtractor()
                madeJoin = makeJoin(extractor, joinTableName, join, table, extractTableName)
                excludeList.add(joinSourceExtract)

                if (madeJoin) break
            }
        }
    }

    private boolean makeJoin(QueryColumnJoinExtractor extractor, String joinTableName, Join join, Table table, String tableName) {
        if (extractor.fieldMappedByMap.containsKey(joinTableName)) {
            def joinType = extractor.fieldMappedByTypeMap.get(joinTableName)
            def joinSQLTableName = queryReport.getSQLTableName(joinType)
            def joinExtract = CodeLocator.getJPATableSourceCodeExtract(queryReport.jNomad, joinSQLTableName)
            def columnName = joinExtract.queryColumnJoinExtractor.columnJoinMap.get(extractor.fieldMappedByMap.get(joinTableName))

            join.rightItem = new Table(joinSQLTableName)
            if (table.alias != null) {
                join.rightItem.alias = table.alias
            }

            def equalsTo = new EqualsTo()
            if (queryTableAliasMap.containsValue(tableName)) {
                queryTableAliasMap.each {
                    if (it.value == tableName) {
                        equalsTo.leftExpression = new Column(new Table(it.key), columnName)
                    }
                }
            } else {
                equalsTo.leftExpression = new Column(new Table(tableName), columnName)
            }

            def sqlTableName
            if (table.alias != null) {
                sqlTableName = table.alias.name
            } else {
                sqlTableName = joinSQLTableName
            }
            equalsTo.rightExpression = new Column(new Table(sqlTableName), columnName)
            join.onExpression = equalsTo

            queryTableAliasMap.put(sqlTableName, joinSQLTableName)
            return true
        } else if (extractor.fieldMappedByTypeMap.containsKey(joinTableName) && extractor.joinTableTypeMap.get(joinTableName) == null) {
            def joinType = extractor.fieldMappedByTypeMap.get(joinTableName)
            def joinSQLTableName = queryReport.getSQLTableName(joinType)
            def columnName = extractor.columnJoinMap.get(joinTableName)

            join.rightItem = new Table(joinSQLTableName)
            if (table.alias != null) {
                join.rightItem.alias = table.alias
            }

            def equalsTo = new EqualsTo()
            if (queryTableAliasMap.containsValue(tableName)) {
                queryTableAliasMap.each {
                    if (it.value == tableName) {
                        equalsTo.leftExpression = new Column(new Table(it.key), columnName)
                    }
                }
            } else {
                equalsTo.leftExpression = new Column(new Table(tableName), columnName)
            }

            def sqlTableName
            if (table.alias != null) {
                sqlTableName = table.alias.name
            } else {
                sqlTableName = joinSQLTableName
            }
            equalsTo.rightExpression = new Column(new Table(sqlTableName), columnName)
            join.onExpression = equalsTo

            queryTableAliasMap.put(sqlTableName, joinSQLTableName)
            return true
        } else if (extractor.fieldMappedByTypeMap.containsKey(joinTableName) && extractor.joinTableTypeMap.get(joinTableName) != null) {
            //double join
            def firstJoinTable = extractor.joinTableTypeMap.get(joinTableName)
            def joinColumnName = extractor.joinTableMap.get(firstJoinTable).get(0)

            def equalsTo = new EqualsTo()
            equalsTo.leftExpression = new Column(new Table(firstJoinTable), joinColumnName)
            if (queryTableAliasMap.containsValue(tableName)) {
                queryTableAliasMap.each {
                    if (it.value == tableName) {
                        equalsTo.rightExpression = new Column(new Table(it.key), joinColumnName)
                    }
                }
            } else {
                equalsTo.rightExpression = new Column(new Table(tableName), joinColumnName)
            }
            join.rightItem = new Table(firstJoinTable)
            join.onExpression = equalsTo

            queryTableAliasMap.put(firstJoinTable, firstJoinTable)

            def secondTableSQLTableName = queryReport.getSQLTableName(extractor.fieldMappedByTypeMap.get(joinTableName))
            def secondJoin = new Join()
            secondJoin.left = true
            secondJoin.rightItem = new Table(secondTableSQLTableName)
            if (table.alias != null) {
                secondJoin.rightItem.alias = table.alias
            }

            def sqlTableName
            if (table.alias != null) {
                sqlTableName = table.alias.name
            } else {
                sqlTableName = queryReport.getSQLTableName(extractor.fieldMappedByTypeMap.get(joinTableName))
            }

            def columnName = extractor.inverseJoinTableMap.get(firstJoinTable).get(0)
            equalsTo = new EqualsTo()
            equalsTo.leftExpression = new Column(new Table(sqlTableName), columnName)
            equalsTo.rightExpression = new Column(new Table(firstJoinTable), columnName)
            secondJoin.onExpression = equalsTo
            plainSelect.joins.add(secondJoin)

            //update alias
            if (queryTableAliasMap.containsKey(sqlTableName)) {
                queryTableAliasMap.put(sqlTableName, secondTableSQLTableName)
            }
            return true
        } else if (extractor.joinTableTypeMap.containsKey(joinTableName)) {
            //double join (join table)
            def firstJoinTable = extractor.joinTableTypeMap.get(joinTableName)
            def joinColumnName = extractor.joinTableMap.get(firstJoinTable).get(0)

            def equalsTo = new EqualsTo()
            equalsTo.leftExpression = new Column(new Table(firstJoinTable), joinColumnName)
            if (queryTableAliasMap.containsValue(tableName)) {
                queryTableAliasMap.each {
                    if (it.value == tableName) {
                        equalsTo.rightExpression = new Column(new Table(it.key), joinColumnName)
                    }
                }
            } else {
                equalsTo.rightExpression = new Column(new Table(tableName), joinColumnName)
            }
            join.rightItem = new Table(firstJoinTable)
            join.onExpression = equalsTo

            queryTableAliasMap.put(firstJoinTable, firstJoinTable)

            def secondTableSQLTableName = queryReport.getSQLTableName(extractor.joinTableMappedByTypeMap.get(joinTableName))
            def secondJoin = new Join()
            secondJoin.left = true
            secondJoin.rightItem = new Table(secondTableSQLTableName)
            if (table.alias != null) {
                secondJoin.rightItem.alias = table.alias
            }

            def sqlTableName
            if (table.alias != null) {
                sqlTableName = table.alias.name
            } else {
                sqlTableName = queryReport.getSQLTableName(extractor.fieldMappedByTypeMap.get(joinTableName))
            }

            def joinTable = extractor.inverseJoinTableMap.get(firstJoinTable)
            if (joinTable == null) {
                HQLColumnNameTransformer nameTransformer = new HQLColumnNameTransformer(queryReport)
                if (queryTableAliasMap.containsKey(sqlTableName)) {
                    nameTransformer.queryTableTranslationMap.put(sqlTableName, tableName)
                }
                nameTransformer.visit(plainSelect)
            } else {
                def columnName = joinTable.get(0)
                equalsTo = new EqualsTo()
                equalsTo.leftExpression = new Column(new Table(sqlTableName), columnName)
                equalsTo.rightExpression = new Column(new Table(firstJoinTable), columnName)
                secondJoin.onExpression = equalsTo
                plainSelect.joins.add(secondJoin)
            }

            //update alias
            if (queryTableAliasMap.containsKey(sqlTableName)) {
                queryTableAliasMap.put(sqlTableName, secondTableSQLTableName)
            }
            return true
        }

        return false
    }

}
