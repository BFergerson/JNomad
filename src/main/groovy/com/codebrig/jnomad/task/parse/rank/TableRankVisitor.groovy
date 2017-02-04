package com.codebrig.jnomad.task.parse.rank

import com.codebrig.jnomad.utils.ColumnExistsVisitor
import net.sf.jsqlparser.expression.*
import net.sf.jsqlparser.expression.operators.arithmetic.*
import net.sf.jsqlparser.expression.operators.conditional.AndExpression
import net.sf.jsqlparser.expression.operators.conditional.OrExpression
import net.sf.jsqlparser.expression.operators.relational.*
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.select.*

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class TableRankVisitor implements SelectVisitor, ExpressionVisitor {

    private TableRank tableRank
    private int queryMultiplier

    TableRankVisitor(TableRank tableRank, int queryMultiplier) {
        this.tableRank = tableRank
        this.queryMultiplier = queryMultiplier
    }

    @Override
    void visit(PlainSelect plainSelect) {
        if (plainSelect.where != null) {
            plainSelect.where.accept(this)
        }
    }

    @Override
    void visit(SetOperationList setOpList) {
        for (SelectBody select : setOpList.selects) {
            select.accept(this)
        }
    }

    @Override
    void visit(WithItem withItem) {
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
        andExpression.leftExpression.accept(this)
        andExpression.rightExpression.accept(this)
    }

    @Override
    void visit(OrExpression orExpression) {
        orExpression.leftExpression.accept(this)
        orExpression.rightExpression.accept(this)
    }

    @Override
    void visit(Between between) {
    }

    @Override
    void visit(EqualsTo equalsTo) {
        if (equalsTo.leftExpression instanceof Function) {
            def params = ((Function) equalsTo.leftExpression).parameters
            if (params != null) {
                def columnVisitor = new ColumnExistsVisitor()
                if (columnVisitor.columnExists) {
                    for (String columnName : columnVisitor.columnList) {
                        for (int i = 0; i < queryMultiplier; i++) {
                            tableRank.incrementColumnHitCount(columnName.toLowerCase(), equalsTo.leftExpression)
                        }
                    }
                }
            }
        } else {
            equalsTo.leftExpression.accept(this)
        }
        equalsTo.rightExpression.accept(this)
    }

    @Override
    void visit(GreaterThan greaterThan) {
        greaterThan.leftExpression.accept(this)
        greaterThan.rightExpression.accept(this)
    }

    @Override
    void visit(GreaterThanEquals greaterThanEquals) {
        greaterThanEquals.leftExpression.accept(this)
        greaterThanEquals.rightExpression.accept(this)
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
        for (int i = 0; i < queryMultiplier; i++) {
            tableRank.incrementColumnHitCount(tableColumn.columnName.toLowerCase(), tableColumn)
        }
    }

    @Override
    void visit(SubSelect subSelect) {
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
        cast.leftExpression.accept(this)
        //todo: probably more with the data type its cast to
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