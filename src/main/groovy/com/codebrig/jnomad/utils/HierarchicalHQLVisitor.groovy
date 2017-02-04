package com.codebrig.jnomad.utils

import net.sf.jsqlparser.expression.*
import net.sf.jsqlparser.expression.operators.arithmetic.*
import net.sf.jsqlparser.expression.operators.conditional.AndExpression
import net.sf.jsqlparser.expression.operators.conditional.OrExpression
import net.sf.jsqlparser.expression.operators.relational.*
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.SetStatement
import net.sf.jsqlparser.statement.StatementVisitor
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
class HierarchicalHQLVisitor implements StatementVisitor, SelectVisitor, ExpressionVisitor {

    private List<Object> statementParsePath = new ArrayList<>()

    List<Object> getStatementParsePath() {
        return new ArrayList<>(statementParsePath)
    }

    /**
     * todo: switch to return first like/equal/between etc expression rather than not concat not function etc
     * First thing that can have "child" statement/expression is parent
     */
    Object getParent(Object ob) {
        Object rtnValue
        boolean foundSelf = false
        getStatementParsePath().reverseEach {
            if (it == ob) {
                foundSelf = true
            }
            if (foundSelf && it != ob && !(it instanceof Column) && !(it instanceof Concat) && !(it instanceof Function)) {
                if (rtnValue == null) {
                    if (it instanceof CastExpression) {
                        if (it.leftExpression == ob) {
                            rtnValue = it
                        }
                    } else {
                        rtnValue = it
                    }
                } else if (it instanceof CastExpression) {
                    //cast envelope
                    if (it.leftExpression == ob || it.leftExpression == rtnValue) {
                        rtnValue = getParent(it.leftExpression)
                    }
                }
            }
        }
        return rtnValue
    }

    /**
     * First column found is returned
     */
    Object getColumn(Object ob) {
        Object rtnValue
        getStatementParsePath().reverseEach {
            if (it != ob && rtnValue == null && (it instanceof Column)) {
                rtnValue = it
            }
        }
        return rtnValue
    }

    @Override
    void visit(Parenthesis parenthesis) {
        statementParsePath.add(parenthesis)
        parenthesis.expression.accept(this)
    }

    @Override
    void visit(AndExpression andExpression) {
        statementParsePath.add(andExpression)
        andExpression.leftExpression.accept(this)
        andExpression.rightExpression.accept(this)
    }

    @Override
    void visit(OrExpression orExpression) {
        statementParsePath.add(orExpression)
        orExpression.leftExpression.accept(this)
        orExpression.rightExpression.accept(this)
    }

    @Override
    void visit(Between between) {
        statementParsePath.add(between)
        between.leftExpression.accept(this)
        between.betweenExpressionStart.accept(this)
        between.betweenExpressionEnd.accept(this)
    }

    @Override
    void visit(EqualsTo equalsTo) {
        statementParsePath.add(equalsTo)
        equalsTo.leftExpression.accept(this)
        equalsTo.rightExpression.accept(this)
    }

    @Override
    void visit(GreaterThan greaterThan) {
        statementParsePath.add(greaterThan)
        greaterThan.leftExpression.accept(this)
        greaterThan.rightExpression.accept(this)
    }

    @Override
    void visit(GreaterThanEquals greaterThanEquals) {
        statementParsePath.add(greaterThanEquals)
        greaterThanEquals.leftExpression.accept(this)
        greaterThanEquals.rightExpression.accept(this)
    }

    @Override
    void visit(IsNullExpression isNullExpression) {
        statementParsePath.add(isNullExpression)
        isNullExpression.leftExpression.accept(this)
    }

    @Override
    void visit(LikeExpression likeExpression) {
        statementParsePath.add(likeExpression)
        likeExpression.leftExpression.accept(this)
        likeExpression.rightExpression.accept(this)
    }

    @Override
    void visit(MinorThan minorThan) {
        statementParsePath.add(minorThan)
        minorThan.leftExpression.accept(this)
        minorThan.rightExpression.accept(this)
    }

    @Override
    void visit(MinorThanEquals minorThanEquals) {
        statementParsePath.add(minorThanEquals)
        minorThanEquals.leftExpression.accept(this)
        minorThanEquals.rightExpression.accept(this)
    }

    @Override
    void visit(NotEqualsTo notEqualsTo) {
        statementParsePath.add(notEqualsTo)
        notEqualsTo.leftExpression.accept(this)
        notEqualsTo.rightExpression.accept(this)
    }

    @Override
    void visit(NullValue nullValue) {
        statementParsePath.add(nullValue)
    }

    @Override
    void visit(Function function) {
        statementParsePath.add(function)
        if (function.parameters != null && function.parameters.expressions != null) {
            function.parameters.expressions.each {
                it.accept(this)
            }
        }
    }

    @Override
    void visit(SignedExpression signedExpression) {
        statementParsePath.add(signedExpression)
    }

    @Override
    void visit(JdbcParameter jdbcParameter) {
        statementParsePath.add(jdbcParameter)
    }

    @Override
    void visit(JdbcNamedParameter jdbcNamedParameter) {
        statementParsePath.add(jdbcNamedParameter)
    }

    @Override
    void visit(DoubleValue doubleValue) {
        statementParsePath.add(doubleValue)
    }

    @Override
    void visit(LongValue longValue) {
        statementParsePath.add(longValue)
    }

    @Override
    void visit(HexValue hexValue) {
        statementParsePath.add(hexValue)
    }

    @Override
    void visit(DateValue dateValue) {
        statementParsePath.add(dateValue)
    }

    @Override
    void visit(TimeValue timeValue) {
        statementParsePath.add(timeValue)
    }

    @Override
    void visit(TimestampValue timestampValue) {
        statementParsePath.add(timestampValue)
    }

    @Override
    void visit(StringValue stringValue) {
        statementParsePath.add(stringValue)
    }

    @Override
    void visit(Addition addition) {
        statementParsePath.add(addition)
    }

    @Override
    void visit(Division division) {
        statementParsePath.add(division)
    }

    @Override
    void visit(Multiplication multiplication) {
        statementParsePath.add(multiplication)
    }

    @Override
    void visit(Subtraction subtraction) {
        statementParsePath.add(subtraction)
    }

    @Override
    void visit(InExpression inExpression) {
        statementParsePath.add(inExpression)
        inExpression.leftExpression.accept(this)
        if (inExpression.leftItemsList != null) {
            def itemList = inExpression.leftItemsList
            if (itemList instanceof SubSelect) {
                itemList.selectBody.accept(this)
            } else if (itemList instanceof ExpressionList) {
                itemList.expressions.each {
                    it.accept(this)
                }
            } else if (itemList instanceof MultiExpressionList) {
                itemList.exprList.each {
                    it.expressions.each {
                        it.accept(this)
                    }
                }
            }
        }
        if (inExpression.rightItemsList != null) {
            def itemList = inExpression.rightItemsList
            if (itemList instanceof SubSelect) {
                itemList.accept(this)
            } else if (itemList instanceof ExpressionList) {
                itemList.expressions.each {
                    it.accept(this)
                }
            } else if (itemList instanceof MultiExpressionList) {
                itemList.exprList.each {
                    it.expressions.each {
                        it.accept(this)
                    }
                }
            }
        }
    }

    @Override
    void visit(Column tableColumn) {
        statementParsePath.add(tableColumn)
    }

    @Override
    void visit(SubSelect subSelect) {
        statementParsePath.add(subSelect)
    }

    @Override
    void visit(CaseExpression caseExpression) {
        statementParsePath.add(caseExpression)
    }

    @Override
    void visit(WhenClause whenClause) {
        statementParsePath.add(whenClause)
    }

    @Override
    void visit(ExistsExpression existsExpression) {
        statementParsePath.add(existsExpression)
    }

    @Override
    void visit(AllComparisonExpression allComparisonExpression) {
        statementParsePath.add(allComparisonExpression)
    }

    @Override
    void visit(AnyComparisonExpression anyComparisonExpression) {
        statementParsePath.add(anyComparisonExpression)
    }

    @Override
    void visit(Concat concat) {
        statementParsePath.add(concat)
    }

    @Override
    void visit(Matches matches) {
        statementParsePath.add(matches)
    }

    @Override
    void visit(BitwiseAnd bitwiseAnd) {
        statementParsePath.add(bitwiseAnd)
    }

    @Override
    void visit(BitwiseOr bitwiseOr) {
        statementParsePath.add(bitwiseOr)
    }

    @Override
    void visit(BitwiseXor bitwiseXor) {
        statementParsePath.add(bitwiseXor)
    }

    @Override
    void visit(CastExpression cast) {
        statementParsePath.add(cast)
    }

    @Override
    void visit(Modulo modulo) {
        statementParsePath.add(modulo)
    }

    @Override
    void visit(AnalyticExpression aexpr) {
        statementParsePath.add(aexpr)
    }

    @Override
    void visit(WithinGroupExpression wgexpr) {
        statementParsePath.add(wgexpr)
    }

    @Override
    void visit(ExtractExpression eexpr) {
        statementParsePath.add(eexpr)
    }

    @Override
    void visit(IntervalExpression iexpr) {
        statementParsePath.add(iexpr)
    }

    @Override
    void visit(OracleHierarchicalExpression oexpr) {
        statementParsePath.add(oexpr)
    }

    @Override
    void visit(RegExpMatchOperator rexpr) {
        statementParsePath.add(rexpr)
    }

    @Override
    void visit(JsonExpression jsonExpr) {
        statementParsePath.add(jsonExpr)
    }

    @Override
    void visit(RegExpMySQLOperator regExpMySQLOperator) {
        statementParsePath.add(regExpMySQLOperator)
    }

    @Override
    void visit(UserVariable var) {
        statementParsePath.add(var)
    }

    @Override
    void visit(NumericBind bind) {
        statementParsePath.add(bind)
    }

    @Override
    void visit(KeepExpression aexpr) {
        statementParsePath.add(aexpr)
    }

    @Override
    void visit(MySQLGroupConcat groupConcat) {
        statementParsePath.add(groupConcat)
    }

    @Override
    void visit(RowConstructor rowConstructor) {
        statementParsePath.add(rowConstructor)
    }

    @Override
    void visit(OracleHint hint) {
        statementParsePath.add(hint)
    }

    @Override
    void visit(TimeKeyExpression timeKeyExpression) {
        statementParsePath.add(timeKeyExpression)
    }

    @Override
    void visit(DateTimeLiteralExpression literal) {
        statementParsePath.add(literal)
    }

    @Override
    void visit(Select select) {
        statementParsePath.add(select)
    }

    @Override
    void visit(Delete delete) {
        statementParsePath.add(delete)
    }

    @Override
    void visit(Update update) {
        statementParsePath.add(update)
    }

    @Override
    void visit(Insert insert) {
        statementParsePath.add(insert)
    }

    @Override
    void visit(Replace replace) {
        statementParsePath.add(replace)
    }

    @Override
    void visit(Drop drop) {
        statementParsePath.add(drop)
    }

    @Override
    void visit(Truncate truncate) {
        statementParsePath.add(truncate)
    }

    @Override
    void visit(CreateIndex createIndex) {
        statementParsePath.add(createIndex)
    }

    @Override
    void visit(CreateTable createTable) {
        statementParsePath.add(createTable)
    }

    @Override
    void visit(CreateView createView) {
        statementParsePath.add(createView)
    }

    @Override
    void visit(AlterView alterView) {
        statementParsePath.add(alterView)
    }

    @Override
    void visit(Alter alter) {
        statementParsePath.add(alter)
    }

    @Override
    void visit(Statements stmts) {
        statementParsePath.add(stmts)
    }

    @Override
    void visit(Execute execute) {
        statementParsePath.add(execute)
    }

    @Override
    void visit(SetStatement set) {
        statementParsePath.add(set)
    }

    @Override
    void visit(Merge merge) {
        statementParsePath.add(merge)
    }

    @Override
    void visit(PlainSelect plainSelect) {
        statementParsePath.add(plainSelect)
    }

    @Override
    void visit(SetOperationList setOpList) {
        statementParsePath.add(setOpList)
    }

    @Override
    void visit(WithItem withItem) {
        statementParsePath.add(withItem)
    }

}
