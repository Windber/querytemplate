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
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * @Author: winder
 * @Date: 12/28/21 3:37 PM
 */
public class C1SQLASTOutputVisitor extends SQLASTOutputVisitor {
    Stack<FrameInfo> inwhere = new Stack<>();
    public List<String> extractedFuncs = new ArrayList<>();
    @Override
    protected void printFunctionName(String name) {
        extractedFuncs.add(name);
        super.printFunctionName(name);
    }
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

    public C1SQLASTOutputVisitor(Appendable appender) {
        super(appender);
    }

    public C1SQLASTOutputVisitor(Appendable appender, DbType dbType) {
        super(appender, dbType);
    }

    public C1SQLASTOutputVisitor(Appendable appender, boolean parameterized) {
        super(appender, parameterized);
    }
    private static final Integer ONE = Integer.valueOf(1);







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


//    最好结合语义分析开发
    public boolean visit(SQLPropertyExpr x) {
//        String tname = x.setOwner("");
        x.setOwner(parameterize_tablename(x.getOwnerName()));
        return super.visit(x);
    }
}
