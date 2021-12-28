package expr.druid.sqlparser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.JdbcConstants;
import utils.sqls;

import java.util.List;

/***
 * 参考 https://www.cnblogs.com/lihaiming93/p/7519315.html
 */
public class test3 {

    public static void main(String[] args) {
        //指定数据库类型
        DbType dbType = JdbcConstants.MYSQL;
        String sql = sqls.single_select;
        List<SQLStatement> statementList = SQLUtils.parseStatements(sql, dbType);

        System.out.println(SQLUtils.toSQLString(statementList, dbType));
    }
}
