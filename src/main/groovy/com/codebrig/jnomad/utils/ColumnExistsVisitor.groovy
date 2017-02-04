package com.codebrig.jnomad.utils

import net.sf.jsqlparser.expression.*
import net.sf.jsqlparser.expression.operators.arithmetic.*
import net.sf.jsqlparser.expression.operators.conditional.AndExpression
import net.sf.jsqlparser.expression.operators.conditional.OrExpression
import net.sf.jsqlparser.expression.operators.relational.*
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.select.SubSelect

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class ColumnExistsVisitor implements ItemsListVisitor, ExpressionVisitor {

    private List<String> columnList = new ArrayList<>()
    private boolean columnExists

    boolean getColumnExists() {
        return columnExists
    }

    List<String> getColumnList() {
        return columnList
    }

    @Override
    void visit(NullValue nullValue) {
    }

    @Override
    void visit(Function function) {
    }

    @Override
    void visit(SignedExpression signedExpression) {
    }

    @Override
    void visit(JdbcParameter jdbcParameter) {
    }

    @Override
    void visit(JdbcNamedParameter jdbcNamedParameter) {
    }

    @Override
    void visit(DoubleValue doubleValue) {
    }

    @Override
    void visit(LongValue longValue) {
    }

    @Override
    void visit(HexValue hexValue) {
    }

    @Override
    void visit(DateValue dateValue) {
    }

    @Override
    void visit(TimeValue timeValue) {
    }

    @Override
    void visit(TimestampValue timestampValue) {
    }

    @Override
    void visit(Parenthesis parenthesis) {
    }

    @Override
    void visit(StringValue stringValue) {
    }

    @Override
    void visit(Addition addition) {
    }

    @Override
    void visit(Division division) {
    }

    @Override
    void visit(Multiplication multiplication) {
    }

    @Override
    void visit(Subtraction subtraction) {
    }

    @Override
    void visit(AndExpression andExpression) {
    }

    @Override
    void visit(OrExpression orExpression) {
    }

    @Override
    void visit(Between between) {
    }

    @Override
    void visit(EqualsTo equalsTo) {
        equalsTo.leftExpression.accept(this)
        equalsTo.rightExpression.accept(this)
    }

    @Override
    void visit(GreaterThan greaterThan) {
    }

    @Override
    void visit(GreaterThanEquals greaterThanEquals) {
    }

    @Override
    void visit(InExpression inExpression) {
    }

    @Override
    void visit(IsNullExpression isNullExpression) {
    }

    @Override
    void visit(LikeExpression likeExpression) {
    }

    @Override
    void visit(MinorThan minorThan) {
        minorThan.leftExpression.accept(this)
        minorThan.rightExpression.accept(this)
    }

    @Override
    void visit(MinorThanEquals minorThanEquals) {
        minorThanEquals.leftExpression.accept(this)
        minorThanEquals.rightExpression.accept(this)
    }

    @Override
    void visit(NotEqualsTo notEqualsTo) {
        notEqualsTo.leftExpression.accept(this)
        notEqualsTo.rightExpression.accept(this)
    }

    @Override
    void visit(Column tableColumn) {
        columnExists = true
        columnList.add(tableColumn.columnName)
    }

    @Override
    void visit(SubSelect subSelect) {
    }

    @Override
    void visit(ExpressionList expressionList) {
        for (Expression expression : expressionList.expressions) {
            expression.accept(this)
        }
    }

    @Override
    void visit(MultiExpressionList multiExprList) {
        for (ExpressionList expressionList : multiExprList.exprList) {
            visit(expressionList)
        }
    }

    @Override
    void visit(CaseExpression caseExpression) {
    }

    @Override
    void visit(WhenClause whenClause) {
    }

    @Override
    void visit(ExistsExpression existsExpression) {
    }

    @Override
    void visit(AllComparisonExpression allComparisonExpression) {
    }

    @Override
    void visit(AnyComparisonExpression anyComparisonExpression) {
    }

    @Override
    void visit(Concat concat) {
    }

    @Override
    void visit(Matches matches) {
    }

    @Override
    void visit(BitwiseAnd bitwiseAnd) {
    }

    @Override
    void visit(BitwiseOr bitwiseOr) {
    }

    @Override
    void visit(BitwiseXor bitwiseXor) {
    }

    @Override
    void visit(CastExpression cast) {
    }

    @Override
    void visit(Modulo modulo) {
    }

    @Override
    void visit(AnalyticExpression aexpr) {
    }

    @Override
    void visit(WithinGroupExpression wgexpr) {
    }

    @Override
    void visit(ExtractExpression eexpr) {
    }

    @Override
    void visit(IntervalExpression iexpr) {
    }

    @Override
    void visit(OracleHierarchicalExpression oexpr) {
    }

    @Override
    void visit(RegExpMatchOperator rexpr) {
    }

    @Override
    void visit(JsonExpression jsonExpr) {
    }

    @Override
    void visit(RegExpMySQLOperator regExpMySQLOperator) {
    }

    @Override
    void visit(UserVariable var) {
    }

    @Override
    void visit(NumericBind bind) {
    }

    @Override
    void visit(KeepExpression aexpr) {
    }

    @Override
    void visit(MySQLGroupConcat groupConcat) {
    }

    @Override
    void visit(RowConstructor rowConstructor) {
    }

    @Override
    void visit(OracleHint hint) {
    }

    @Override
    void visit(TimeKeyExpression timeKeyExpression) {
    }

    @Override
    void visit(DateTimeLiteralExpression literal) {
    }

}
