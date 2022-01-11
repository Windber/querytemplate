package expr.druid.sqlparser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.alibaba.druid.util.JdbcConstants;
import utils.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***
 * https://github.com/alibaba/druid/wiki/SQL_Parser_Demo_visitor
 */
public class usevisitor {
    public static class ExportTableAliasVisitor extends MySqlASTVisitorAdapter {
        private Map<String, SQLTableSource> aliasMap = new HashMap<String, SQLTableSource>();
        public boolean visit(SQLExprTableSource x) {
            String alias = x.getAlias();
            aliasMap.put(alias, x);
            return true;
        }

        public Map<String, SQLTableSource> getAliasMap() {
            return aliasMap;
        }
    }
    public static void main(String[] args) {
        final DbType dbType = JdbcConstants.ORACLE; // JdbcConstants.MYSQL或者JdbcConstants.POSTGRESQL
//        String sql = "select * from mytable a where a.id = 3";
        for(String sql: data.forhive_succeed) {
            List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, dbType);

            ExportTableAliasVisitor visitor = new ExportTableAliasVisitor();
            for (SQLStatement stmt : stmtList) {
                stmt.accept(visitor);
            }

            SQLTableSource tableSource = visitor.getAliasMap().get("a");
            System.out.println(tableSource);
        }
    }
}
