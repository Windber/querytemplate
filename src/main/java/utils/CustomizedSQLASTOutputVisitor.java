package utils;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.visitor.ExportParameterVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.sql.visitor.VisitorFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * @Author: winder
 * @Date: 12/28/21 3:37 PM
 */
public class CustomizedSQLASTOutputVisitor extends SQLASTOutputVisitor {
    Stack<FrameInfo> inwhere = new Stack<>();

    public static enum Frame {
        QB,
        TARGET,
        FROM,
        WHERE,
        GROUPBY,
        HAVING,
        ORDERBY
    }
    public static class FrameInfo {
        public Frame type;
        public SQLObject obj;
        public FrameInfo(Frame type_, SQLObject obj_) {
            type = type_;
            obj = obj_;
        }
    }
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



    public boolean isnotSubQuery(SQLTableSource x) {
        if (x instanceof SQLExprTableSource) {
            return true;
        }else if (x instanceof SQLSubqueryTableSource) {
            return false;
        }else if (x instanceof SQLJoinTableSource) {
            return isnotSubQuery(((SQLJoinTableSource) x).getLeft()) && isnotSubQuery(((SQLJoinTableSource) x).getRight());
        }else {
            try {
                throw new Exception("unkown SQLTableSource");
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

//    @Override
//    protected void printFunctionName(String name) {
//        print0("{{{" + name + "}}}");
//    }

    //    利用短路与性质，与isInwhere结合使用
    public boolean isnotFromSubQuery() {
        SQLTableSource source = ((SQLSelectQueryBlock)
                (inwhere.peek().obj.getParent())).getFrom();
        return isnotSubQuery(source);
    }
    public boolean isInwhere() {
        if (inwhere.peek().type == Frame.WHERE) {
            return true;
        }else {
            return false;
        }
    }

    @Override
    public void printAlias(String alias) {
        if (alias == null || alias.length() == 0) {
            return;
        }

        print(' ');

        try {
            this.appender.append(parameterize_tablename(alias));
        } catch (IOException e) {
            throw new RuntimeException("println error", e);
        }
    }

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


            if (this.parameterized && isInwhere() && isnotFromSubQuery()) {

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

    public String parameterize_tablename(String tname) {
        Pattern p = Pattern.compile("_?[0-9]{4,6}$");
        Matcher m = p.matcher(tname);
        boolean match = m.find();
        String pname = tname;
        if (match) {
            int suffixLen = m.group().length();
            pname = tname.substring(0, tname.length()-suffixLen);
            return pname;
        }

        return pname;
    }

    @Override
    public boolean visit(SQLExprTableSource x) {
        printTableSourceExpr(x.getExpr());

        final SQLTableSampling sampling = x.getSampling();
        if (sampling != null) {
            print(' ');
            sampling.accept(this);
        }

        String alias = x.getAlias();
        List<SQLName> columns = x.getColumnsDirect();
        if (alias != null) {
            print(' ');
            if (columns != null && columns.size() > 0) {
                print0(ucase ? " AS " : " as ");
            }
            print0(parameterize_tablename(alias));
        }

        if (columns != null && columns.size() > 0) {
            print(" (");
            printAndAccept(columns, ", ");
            print(')');
        }

        if (isPrettyFormat() && x.hasAfterComment()) {
            print(' ');
            printlnComment(x.getAfterCommentsDirect());
        }

        return false;
    }


    @Override
    protected void printTableSourceExpr(SQLExpr expr) {
        if (exportTables) {
            addTable(expr.toString());
        }

        if (isEnabled(VisitorFeature.OutputDesensitize)) {
            String ident = null;
            if (expr instanceof SQLIdentifierExpr) {
                ident = ((SQLIdentifierExpr) expr).getName();
            } else if (expr instanceof SQLPropertyExpr) {
                SQLPropertyExpr propertyExpr = (SQLPropertyExpr) expr;
                propertyExpr.getOwner().accept(this);
                print('.');

                ident = propertyExpr.getName();
            }

            if (ident != null) {
                String desensitizeTable = SQLUtils.desensitizeTable(ident);
                print0(desensitizeTable);
                return;
            }
        }

        if (tableMapping != null && expr instanceof SQLName) {
            String tableName;
            if (expr instanceof SQLIdentifierExpr) {
                tableName = ((SQLIdentifierExpr) expr).normalizedName();
            } else if (expr instanceof SQLPropertyExpr) {
                tableName = ((SQLPropertyExpr) expr).normalizedName();
            } else {
                tableName = expr.toString();
            }

            String destTableName = tableMapping.get(tableName);
            if (destTableName == null) {
                if (expr instanceof SQLPropertyExpr) {
                    SQLPropertyExpr propertyExpr = (SQLPropertyExpr) expr;
                    String propName = propertyExpr.getName();
                    destTableName = tableMapping.get(propName);
                    if (destTableName == null
                            && propName.length() > 2 && propName.charAt(0) == '`' && propName.charAt(propName.length() - 1) == '`') {
                        destTableName = tableMapping.get(propName.substring(1, propName.length() - 1));
                    }

                    if (destTableName != null) {
                        propertyExpr.getOwner().accept(this);
                        print('.');
                        print(destTableName);
                        return;
                    }
                } else if (expr instanceof SQLIdentifierExpr) {
                    boolean quote = tableName.length() > 2 && tableName.charAt(0) == '`' && tableName.charAt(tableName.length() - 1) == '`';
                    if (quote) {
                        destTableName = tableMapping.get(tableName.substring(1, tableName.length() - 1));
                    }
                }
            }
            if (destTableName != null) {
                tableName = destTableName;
                printName0(tableName);
                return;
            }
        }

//        目前只考虑`SQLIdentifierExpr` `SQLPropertyExpr`
        if (expr instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr identifierExpr = (SQLIdentifierExpr) expr;
            String name = identifierExpr.getName();
            name = parameterize_tablename(name);
            identifierExpr.setName(name);
            if (!this.parameterized) {
                printName0(name);
                return;
            }

            boolean shardingSupport = this.shardingSupport
                    && this.parameterized;

            if (shardingSupport) {
                String nameUnwrappe = unwrapShardingTable(name);

                if (!name.equals(nameUnwrappe)) {
                    incrementReplaceCunt();
                }

                printName0(nameUnwrappe);
            } else {
                printName0(name);
            }
        } else if (expr instanceof SQLPropertyExpr) {
            SQLPropertyExpr propertyExpr = (SQLPropertyExpr) expr;
            SQLExpr owner = propertyExpr.getOwner();

            if (owner instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr identOwner = (SQLIdentifierExpr) owner;

                String ownerName = identOwner.getName();
                if (!this.parameterized) {
                    printName0(identOwner.getName());
                } else {
                    if (shardingSupport) {
                        ownerName = unwrapShardingTable(ownerName);
                    }
                    printName0(ownerName);
                }
            } else {
                printExpr(owner);
            }
            print('.');

            String name = propertyExpr.getName();
            name = parameterize_tablename(name);
            propertyExpr.setName(name);
            if (!this.parameterized) {
                printName0(propertyExpr.getName());
                return;
            }

            boolean shardingSupport = this.shardingSupport
                    && this.parameterized;

            if (shardingSupport) {
                String nameUnwrappe = unwrapShardingTable(name);

                if (!name.equals(nameUnwrappe)) {
                    incrementReplaceCunt();
                }

                printName0(nameUnwrappe);
            } else {
                printName0(name);
            }
        } else if (expr instanceof SQLMethodInvokeExpr) {
            visit((SQLMethodInvokeExpr) expr);
        } else {
            expr.accept(this);
        }

    }


    public boolean visit(SQLNCharExpr x) {
        if (this.parameterized  && isInwhere() && isnotFromSubQuery()) {
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
        if (parameterized && isInwhere() && isnotFromSubQuery()) {

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
        boolean ltle_op = false;

//          考虑SQLBinaryOpExpr的情况
        if (parent.getClass() == SQLBinaryOpExpr.class) {
            SQLBinaryOpExpr biparent = (SQLBinaryOpExpr)parent;
            if (biparent.getOperator() == SQLBinaryOperator.LessThan || biparent.getOperator() == SQLBinaryOperator.LessThanOrEqual) {
                ltle_op = true;
            }
            if (biparent.getLeft().getClass() == SQLIdentifierExpr.class) {
                SQLIdentifierExpr id = (SQLIdentifierExpr)biparent.getLeft();
                name = id.getName();
                if (ltle_op && name.equals("start_time")) {
                    name = "end_time";
                }
                name = "$" + "{" + name + "}";
            }else if(biparent.getLeft().getClass() == SQLPropertyExpr.class) {
                SQLPropertyExpr prop = (SQLPropertyExpr)biparent.getLeft();
//                name += "$" + "{" + prop.getOwnerName() + "." + prop.getName() + "}";
                name = prop.getName();
                if (ltle_op && name.equals("start_time")) {
                    name = "end_time";
                }
                name = "$" + "{" + name + "}";
            }else {
                if(x instanceof SQLCharExpr)
                    name = (String)((SQLCharExpr)x).getValue();
                else if(x instanceof SQLNCharExpr)
                    name = (String)((SQLNCharExpr)x).getText();
//                else if(x instanceof SQLNumberExpr)


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
        if (x instanceof SQLCharExpr || x instanceof SQLNCharExpr) {
            name = "\"" + name + "\"";
        }
        return name.equals("")? null: new ColInfo(name);
    }

    public boolean visit(SQLSelectQueryBlock x) {
        inwhere.push(new FrameInfo(Frame.QB, x));
        if (isPrettyFormat() && x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }

        print0(ucase ? "SELECT " : "select ");

        if (x.getHintsSize() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        final boolean informix =DbType.informix == dbType;
        if (informix) {
            printFetchFirst(x);
        }

        final int distinctOption = x.getDistionOption();
        if (SQLSetQuantifier.ALL == distinctOption) {
            print0(ucase ? "ALL " : "all ");
        } else if (SQLSetQuantifier.DISTINCT == distinctOption) {
            print0(ucase ? "DISTINCT " : "distinct ");
        } else if (SQLSetQuantifier.UNIQUE == distinctOption) {
            print0(ucase ? "UNIQUE " : "unique ");
        }

        printSelectList(
                x.getSelectList());

        SQLExprTableSource into = x.getInto();
        if (into != null) {
            println();
            print0(ucase ? "INTO " : "into ");
            into.accept(this);
        }

        SQLTableSource from = x.getFrom();
        if (from != null) {
            println();

            boolean printFrom = from instanceof SQLLateralViewTableSource
                    && ((SQLLateralViewTableSource) from).getTableSource() == null;
            if (!printFrom) {
                print0(ucase ? "FROM " : "from ");
            }
            printTableSource(from);
        }

        SQLExpr where = x.getWhere();

        inwhere.push(new FrameInfo(Frame.WHERE, where));

        if (where != null) {
            println();
            print0(ucase ? "WHERE " : "where ");
            printExpr(where, parameterized);

            if (where.hasAfterComment() && isPrettyFormat()) {
                print(' ');
                printlnComment(x.getWhere().getAfterCommentsDirect());
            }
        }
        inwhere.pop();

        printHierarchical(x);

        SQLSelectGroupByClause groupBy = x.getGroupBy();
        if (groupBy != null) {
            println();
            visit(groupBy);
        }

        List<SQLWindow> windows = x.getWindows();
        if (windows != null && windows.size() > 0) {
            println();
            print0(ucase ? "WINDOW " : "window ");
            printAndAccept(windows, ", ");
        }

        SQLOrderBy orderBy = x.getOrderBy();
        if (orderBy != null) {
            println();
            orderBy.accept(this);
        }

        final List<SQLSelectOrderByItem> distributeBy = x.getDistributeByDirect();
        if (distributeBy != null && distributeBy.size() > 0) {
            println();
            print0(ucase ? "DISTRIBUTE BY " : "distribute by ");
            printAndAccept(distributeBy, ", ");
        }

        List<SQLSelectOrderByItem> sortBy = x.getSortByDirect();
        if (sortBy != null && sortBy.size() > 0) {
            println();
            print0(ucase ? "SORT BY " : "sort by ");
            printAndAccept(sortBy, ", ");
        }

        final List<SQLSelectOrderByItem> clusterBy = x.getClusterByDirect();
        if (clusterBy != null && clusterBy.size() > 0) {
            println();
            print0(ucase ? "CLUSTER BY " : "cluster by ");
            printAndAccept(clusterBy, ", ");
        }

        if (!informix) {
            printFetchFirst(x);
        }

        if (x.isForUpdate()) {
            println();
            print0(ucase ? "FOR UPDATE" : "for update");
        }

        inwhere.pop();
        return false;
    }

    public boolean visit(SQLNumberExpr x) {
        if (this.parameterized && isInwhere() && isnotFromSubQuery()) {

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

        if (this.parameterized  && isInwhere() && isnotFromSubQuery()) {
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

//    最好结合语义分析开发
    public boolean visit(SQLPropertyExpr x) {
//        String tname = x.setOwner("");
        x.setOwner(parameterize_tablename(x.getOwnerName()));
        return super.visit(x);
    }
}
