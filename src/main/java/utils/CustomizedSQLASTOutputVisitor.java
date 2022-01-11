package utils;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSubqueryTableSource;
import com.alibaba.druid.sql.visitor.ExportParameterVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.sql.visitor.VisitorFeature;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/***
 * @Author: winder
 * @Date: 12/28/21 3:37 PM
 */
public class CustomizedSQLASTOutputVisitor extends SQLASTOutputVisitor {
    public static class ColInfo {
        String name;
        public ColInfo(String name) {
            this.name = name;
        }
    }

    public CustomizedSQLASTOutputVisitor(Appendable appender) {
        super(appender);
    }

    public CustomizedSQLASTOutputVisitor(Appendable appender, DbType dbType) {
        super(appender, dbType);
    }

    public CustomizedSQLASTOutputVisitor(Appendable appender, boolean parameterized) {
        super(appender, parameterized);
    }
    private static final Integer ONE = Integer.valueOf(1);
//    public boolean iswhere = false;
    @Override
    protected void printInteger(SQLIntegerExpr x, boolean parameterized) {
        Number number = x.getNumber();

        if (number.equals(ONE)) {
            if (DbType.oracle.equals(dbType)) {
                SQLObject parent = x.getParent();
                if (parent instanceof SQLBinaryOpExpr) {
                    SQLBinaryOpExpr binaryOpExpr = (SQLBinaryOpExpr) parent;
                    SQLExpr left = binaryOpExpr.getLeft();
                    SQLBinaryOperator op = binaryOpExpr.getOperator();
                    if (left instanceof SQLIdentifierExpr
                            && op == SQLBinaryOperator.Equality) {
                        String name = ((SQLIdentifierExpr) left).getName();
                        if ("rownum".equals(name)) {
                            print(1);
                            return;
                        }
                    }
                }
            }
        }

//        判断subquery
//        boolean issubquery = false;
//        SQLObject obj = x;
//        while(true) {
//            obj = obj.getParent();
//            if (obj instanceof SQLSelectQueryBlock) {
//                break;
//            }
//        }
//        SQLSelectQueryBlock qb = (SQLSelectQueryBlock)obj;
//        if (qb.getFrom() instanceof SQLSubqueryTableSource) {
//            issubquery = true;
//        }

//        if (parameterized && !issubquery && iswhere) {
        if (parameterized) {

            if (this.parameterized) {

                ColInfo ci;
                ci = get_colname(x);
                if (ci != null) {
                    print(ci.name);
                } else {
                    if (number instanceof BigDecimal || number instanceof BigInteger) {
                        print(number.toString());
                    } else {
                        print(number.longValue());
                    }
                }
//            else {
////                没有覆盖的情况不进行替换
//                if (number instanceof BigDecimal || number instanceof BigInteger) {
//                    print(number.toString());
//                } else {
//                    print(number.longValue());
//                }
//            }
                incrementReplaceCunt();

                if (this.parameters != null) {
                    ExportParameterVisitorUtils.exportParameter(this.parameters, x);
                }
                return;
            }

            if (number instanceof BigDecimal || number instanceof BigInteger) {
                print(number.toString());
            } else {
                print(number.longValue());
            }
        }
    }
    public boolean visit(SQLNCharExpr x) {
        if (this.parameterized) {
            ColInfo ci;
            ci = get_colname(x);
            if (ci!=null) {
                print(ci.name);
            }
            else {
                printChars(x.getText());
            }
            incrementReplaceCunt();

            if(this.parameters != null){
                ExportParameterVisitorUtils.exportParameter(this.parameters, x);
            }
            return false;
        }

        if ((x.getText() == null) || (x.getText().length() == 0)) {
            print0(ucase ? "NULL" : "null");
        } else {
            print0(ucase ? "N'" : "n'");
            print0(x.getText().replace("'", "''"));
            print('\'');
        }
        return false;
    }

    @Override
    public boolean visit(SQLCharExpr x, boolean parameterized) {
        if (parameterized) {

            ColInfo ci;
            ci = get_colname(x);
            if (ci!=null) {
                print(ci.name);
            }
            else {
                printChars(x.getText());
            }

            incrementReplaceCunt();
            if (this.parameters != null) {
                ExportParameterVisitorUtils.exportParameter(this.parameters, x);
            }
            return false;
        }

        printChars(x.getText());

        return false;
    }

    public ColInfo get_colname(SQLObject x) {
        String name = "";
        SQLObject parent = x.getParent();

//          考虑SQLBinaryOpExpr的情况
        if (parent.getClass() == SQLBinaryOpExpr.class) {
            SQLBinaryOpExpr biparent = (SQLBinaryOpExpr)parent;
            if (biparent.getLeft().getClass() == SQLIdentifierExpr.class) {
                SQLIdentifierExpr id = (SQLIdentifierExpr)biparent.getLeft();
                name += "$" + "{" + id.getName() + "}";
            }else if(biparent.getLeft().getClass() == SQLPropertyExpr.class) {
                SQLPropertyExpr prop = (SQLPropertyExpr)biparent.getLeft();
//                name += "$" + "{" + prop.getOwnerName() + "." + prop.getName() + "}";
                name += "$" + "{" + prop.getName() + "}";
            }
        }else if(parent.getClass() == SQLInListExpr.class) {
            SQLInListExpr inlistparent = (SQLInListExpr) parent;
            if (inlistparent.getExpr().getClass() == SQLIdentifierExpr.class) {
                SQLIdentifierExpr id = (SQLIdentifierExpr) inlistparent.getExpr();
                name += "$" + "{" + id.getName() + "}";
            } else if (inlistparent.getExpr().getClass() == SQLPropertyExpr.class) {
                SQLPropertyExpr prop = (SQLPropertyExpr) inlistparent.getExpr();
//                name += "$" + "{" + prop.getOwnerName() + "." + prop.getName() + "}";
                name += "$" + "{" + prop.getName() + "}";
            }
        }
        return name.equals("")? null: new ColInfo(name);
    }
    public boolean visit(SQLNumberExpr x) {
        if (this.parameterized) {

            ColInfo ci;
            ci = get_colname(x);
            if (ci!=null) {
                print(ci.name);
            }
            else {
                if (appender instanceof StringBuilder) {
                    x.output((StringBuilder) appender);
                } else if (appender instanceof StringBuilder) {
                    x.output((StringBuilder) appender);
                } else {
                    print0(x.getNumber().toString());
                }
            }

            incrementReplaceCunt();

            if(this.parameters != null){
                ExportParameterVisitorUtils.exportParameter((this).getParameters(), x);
            }
            return false;
        }

        if (appender instanceof StringBuilder) {
            x.output((StringBuilder) appender);
        } else if (appender instanceof StringBuilder) {
            x.output((StringBuilder) appender);
        } else {
            print0(x.getNumber().toString());
        }
        return false;
    }

    public boolean visit(SQLInListExpr x) {
        final SQLExpr expr = x.getExpr();

        boolean quote = false;
        if (expr instanceof SQLBinaryOpExpr) {
            SQLBinaryOperator operator = ((SQLBinaryOpExpr) expr).getOperator();
            switch (operator) {
                case BooleanAnd:
                case BooleanOr:
                case BooleanXor:
                case Assignment:
                    quote = true;
                    break;
                default:
                    quote = ((SQLBinaryOpExpr) expr).isParenthesized();
                    break;
            }
        } else if (expr instanceof SQLNotExpr
                || expr instanceof SQLBetweenExpr
                || expr instanceof SQLInListExpr
                || expr instanceof SQLUnaryExpr
                || expr instanceof SQLBinaryOpExprGroup){
            quote = true;
        }

        if (this.parameterized) {
            List<SQLExpr> targetList = x.getTargetList();

            boolean allLiteral = true;
            for (SQLExpr item : targetList) {
                if (!(item instanceof SQLLiteralExpr || item instanceof SQLVariantRefExpr)) {
                    if (item instanceof SQLListExpr) {
                        SQLListExpr list = (SQLListExpr) item;
                        for (SQLExpr listItem : list.getItems()) {
                            if (!(listItem instanceof SQLLiteralExpr
                                    || listItem instanceof SQLVariantRefExpr)) {
                                allLiteral = false;
                                break;
                            }
                        }
                        if (allLiteral) {
                            break;
                        }
                        continue;
                    }
                    allLiteral = false;
                    break;
                }
            }

            if (allLiteral) {
                boolean changed = true;
                if (targetList.size() == 1 && targetList.get(0) instanceof SQLVariantRefExpr) {
                    changed = false;
                }

                if (quote) {
                    print('(');
                }
                printExpr(expr, parameterized);
                if (quote) {
                    print(')');
                }

                if (x.isNot()) {
                    print(ucase ? " NOT IN" : " not in");
                } else {
                    print(ucase ? " IN" : " in");
                }

                if((!parameterizedQuesUnMergeInList) || (targetList.size() == 1 && !(targetList.get(0) instanceof SQLListExpr))) {
//                    if (parameters != null) {
//                        print(" (");
//                        for (int i = 0; i < targetList.size(); i++) {
//                            if(i != 0) {
//                                print(", ");
//                            }
//                            SQLExpr item = targetList.get(i);
//                            printExpr(item);
//                        }
//                        print(')');
//                        return false;
//                    } else {
//
//                    }
                    print(" (");
                    SQLExpr item = targetList.get(0);
                    if (item instanceof SQLIntegerExpr) {
                        visit((SQLIntegerExpr)item);
                    } else if (item instanceof SQLCharExpr) {
                        visit((SQLCharExpr)item);
                    } else if (item instanceof SQLNumberExpr) {
                        visit((SQLNumberExpr)item);
                    } else {
                        print("?");
                    }
                    print(")");
                } else {
                    print(" (");
                    for (int i = 0; i < targetList.size(); i++) {
                        if(i != 0) {
                            print(", ");
                        }
                        SQLExpr item = targetList.get(i);
                        if (item instanceof SQLListExpr) {
                            visit((SQLListExpr) item);
                            changed = false;
                        } else if (item instanceof SQLIntegerExpr) {
                            visit((SQLIntegerExpr)item);
                        } else if (item instanceof SQLCharExpr) {
                            visit((SQLCharExpr)item);
                        } else if (item instanceof SQLNumberExpr) {
                            visit((SQLNumberExpr)item);
                        } else {
                            print("?");
                        }
                    }
                    print(")");
                }

                if (changed) {
                    incrementReplaceCunt();
                    if (this.parameters != null) {
                        if (parameterizedMergeInList) {
                            List<Object> subList = new ArrayList<Object>(x.getTargetList().size());
                            for (SQLExpr target : x.getTargetList()) {
                                ExportParameterVisitorUtils.exportParameter(subList, target);
                            }
                            if (subList != null) {
                                parameters.add(subList);
                            }
                        } else {
                            for (SQLExpr target : x.getTargetList()) {
                                ExportParameterVisitorUtils.exportParameter(this.parameters, target);
                            }
                        }
                    }
                }

                if (x.getHint() != null) {
                    x.getHint().accept(this);
                }

                return false;
            }
        }

        if (quote) {
            print('(');
        }
        printExpr(expr, parameterized);
        if (quote) {
            print(')');
        }

        if (x.isNot()) {
            print0(ucase ? " NOT IN (" : " not in (");
        } else {
            print0(ucase ? " IN (" : " in (");
        }

        final List<SQLExpr> list = x.getTargetList();

        boolean printLn = false;
        if (list.size() > 5) {
            printLn = true;
            for (int i = 0, size = list.size(); i < size; ++i) {
                if (!(list.get(i) instanceof SQLCharExpr)) {
                    printLn = false;
                    break;
                }
            }
        }

        if (printLn) {
            this.indentCount++;
            println();
            for (int i = 0, size = list.size(); i < size; ++i) {
                if (i != 0) {
                    print0(", ");
                    println();
                }
                SQLExpr item = list.get(i);
                printExpr(item, parameterized);
            }
            this.indentCount--;
            println();
        } else {
            List<SQLExpr> targetList = x.getTargetList();
            for (int i = 0; i < targetList.size(); i++) {
                if (i != 0) {
                    print0(", ");
                }
                printExpr(targetList.get(i), parameterized);
            }
        }

        print(')');

        List<String> afterComments = x.getAfterCommentsDirect();
        if (afterComments != null && !afterComments.isEmpty() && afterComments.get(0).startsWith("--")) {
            print(' ');
        }
        printlnComment(afterComments);

        if (x.getHint() != null) {
            x.getHint().accept(this);
        }
        return false;
    }
//    public void endVisit(SQLBinaryOpExprGroup x) {
//        SQLObject obj = x;
//        while(true) {
//            obj = obj.getParent();
//            if (obj instanceof SQLSelectQueryBlock) {
//                break;
//            }
//        }
//        SQLSelectQueryBlock qb = (SQLSelectQueryBlock)obj;
//        if (qb.getWhere() == this) {
//            iswhere = false;
//        }
//    }
//    public boolean visit(SQLBinaryOpExprGroup x) {
//
//        SQLObject obj = x;
//        while(true) {
//            obj = obj.getParent();
//            if (obj instanceof SQLSelectQueryBlock) {
//                break;
//            }
//        }
//        SQLSelectQueryBlock qb = (SQLSelectQueryBlock)obj;
//        if (qb.getWhere() == this) {
//            iswhere = true;
//        }
//
//
//        SQLObject parent = x.getParent();
//        SQLBinaryOperator operator = x.getOperator();
//
//        boolean isRoot = parent instanceof SQLSelectQueryBlock || parent instanceof SQLBinaryOpExprGroup;
//
//        List<SQLExpr> items = x.getItems();
//        if (items.size() == 0) {
//            print("true");
//            return false;
//        }
//
//        if (isRoot) {
//            this.indentCount++;
//        }
//
//        if (this.parameterized) {
//            SQLExpr firstLeft = null;
//            SQLBinaryOperator firstOp = null;
//            List<Object> parameters = new ArrayList<Object>(items.size());
//
//            List<SQLBinaryOpExpr> literalItems = null;
//
//            if ((operator != SQLBinaryOperator.BooleanOr || !isEnabled(VisitorFeature.OutputParameterizedQuesUnMergeOr)) &&
//                    (operator != SQLBinaryOperator.BooleanAnd || !isEnabled(VisitorFeature.OutputParameterizedQuesUnMergeAnd))) {
//                for (int i = 0; i < items.size(); i++) {
//                    SQLExpr item = items.get(i);
//                    if (item instanceof SQLBinaryOpExpr) {
//                        SQLBinaryOpExpr binaryItem = (SQLBinaryOpExpr) item;
//                        SQLExpr left = binaryItem.getLeft();
//                        SQLExpr right = binaryItem.getRight();
//
//                        if (right instanceof SQLLiteralExpr && !(right instanceof SQLNullExpr)) {
//                            if (left instanceof SQLLiteralExpr) {
//                                if (literalItems == null) {
//                                    literalItems = new ArrayList<SQLBinaryOpExpr>();
//                                }
//                                literalItems.add(binaryItem);
//                                continue;
//                            }
//
//                            if (this.parameters != null) {
//                                ExportParameterVisitorUtils.exportParameter(parameters, right);
//                            }
//                        } else if (right instanceof SQLVariantRefExpr) {
//                            // skip
//                        } else {
//                            firstLeft = null;
//                            break;
//                        }
//
//
//                        if (firstLeft == null) {
//                            firstLeft = binaryItem.getLeft();
//                            firstOp = binaryItem.getOperator();
//                        } else {
//                            if (firstOp != binaryItem.getOperator() || !SQLExprUtils.equals(firstLeft, left)) {
//                                firstLeft = null;
//                                break;
//                            }
//                        }
//                    } else {
//                        firstLeft = null;
//                        break;
//                    }
//                }
//            }
//
//            if (firstLeft != null) {
//                if (literalItems != null) {
//                    for (SQLBinaryOpExpr literalItem : literalItems) {
//                        visit(literalItem);
//                        println();
//                        printOperator(operator);
//                        print(' ');
//
//                    }
//                }
//                printExpr(firstLeft, parameterized);
//                print(' ');
//                printOperator(firstOp);
//                print0(" ?");
//
//                if (this.parameters != null) {
//                    if (parameters.size() > 0) {
//                        this.parameters.addAll(parameters);
//                    }
//                }
//
//                incrementReplaceCunt();
//                if (isRoot) {
//                    this.indentCount--;
//                }
//                return false;
//            }
//        }
//
//        for (int i = 0; i < items.size(); i++) {
//            SQLExpr item = items.get(i);
//
//            if (i != 0) {
//                println();
//                printOperator(operator);
//                print(' ');
//            }
//
//            if (item.hasBeforeComment()) {
//                printlnComments(item.getBeforeCommentsDirect());
//            }
//
//            if (item instanceof SQLBinaryOpExpr) {
//                SQLBinaryOpExpr binaryOpExpr = (SQLBinaryOpExpr) item;
//                SQLExpr binaryOpExprRight = binaryOpExpr.getRight();
//                SQLBinaryOperator itemOp = binaryOpExpr.getOperator();
//
//                boolean isLogic = itemOp.isLogical();
//                if (isLogic) {
//                    indentCount++;
//                }
//
//                boolean bracket;
//                if (itemOp.priority > operator.priority) {
//                    bracket = true;
//                } else {
//                    bracket = binaryOpExpr.isParenthesized() & !parameterized;
//                }
//                if (bracket) {
//                    print('(');
//                    visit(binaryOpExpr);
//                    print(')');
//                } else {
//                    visit(binaryOpExpr);
//                }
//
//                if (item.hasAfterComment() && !parameterized) {
//                    print(' ');
//                    printlnComment(item.getAfterCommentsDirect());
//                }
//
//                if (isLogic) {
//                    indentCount--;
//                }
//            } else if (item instanceof SQLBinaryOpExprGroup) {
//                print('(');
//                visit((SQLBinaryOpExprGroup) item);
//                print(')');
//            } else {
//                printExpr(item, parameterized);
//            }
//        }
//        if (isRoot) {
//            this.indentCount--;
//        }
//        return false;
//    }

}
