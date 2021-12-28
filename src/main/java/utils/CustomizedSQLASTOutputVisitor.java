package utils;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.visitor.ExportParameterVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;

import java.math.BigDecimal;
import java.math.BigInteger;

/***
 * @Author: winder
 * @Date: 12/28/21 3:37 PM
 */
public class CustomizedSQLASTOutputVisitor extends SQLASTOutputVisitor {
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

        if (parameterized) {
            SQLObject parent = x.getParent();
            if (parent.getClass() == SQLBinaryOpExpr.class) {
                SQLBinaryOpExpr biparent = (SQLBinaryOpExpr)parent;
                if (biparent.getLeft().getClass() == SQLIdentifierExpr.class) {
                    SQLIdentifierExpr id = (SQLIdentifierExpr)biparent.getLeft();
                    print("$" + "{" + id.getName() + "}");
                }else if(biparent.getLeft().getClass() == SQLPropertyExpr.class) {
                    SQLPropertyExpr prop = (SQLPropertyExpr)biparent.getLeft();
                    print("$" + "{" + prop.getOwnerName() + "." + prop.getName() + "}");
                }
            }
            incrementReplaceCunt();

            if(this.parameters != null){
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

    @Override
    public boolean visit(SQLCharExpr x, boolean parameterized) {
        if (parameterized) {
            print('*');
            incrementReplaceCunt();
            if (this.parameters != null) {
                ExportParameterVisitorUtils.exportParameter(this.parameters, x);
            }
            return false;
        }

        printChars(x.getText());

        return false;
    }

}
