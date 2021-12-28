package expr.druid.sqlparser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.JdbcConstants;
import utils.sqls;
/***
 * 参考 https://www.cnblogs.com/lihaiming93/p/7519315.html
 */
public class test1 {

    public static void main(String[] args) {
        //指定数据库类型
        DbType dbtype = JdbcConstants.HIVE;
        for(String sql: sqls.data) {
            String fs = ParameterizedOutputVisitorUtils.parameterize(sql, dbtype);
            System.out.println("sql before extraction:\n" + sql);
            System.out.println("sql after extraction:\n" + fs);
        }
    }
}
