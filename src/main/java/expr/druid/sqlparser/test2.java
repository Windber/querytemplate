package expr.druid.sqlparser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.JdbcConstants;
import utils.data;

import static com.alibaba.druid.sql.visitor.VisitorFeature.OutputSkipSelectListCacheString;

/***
 * 参考 https://www.cnblogs.com/lihaiming93/p/7519315.html
 */
public class test2 {

    public static void main(String[] args) {
        //指定数据库类型
        DbType dbtype = JdbcConstants.MYSQL;
        for(String sql: data.forhive_succeed) {
            String fs = ParameterizedOutputVisitorUtils.parameterize(sql, dbtype, OutputSkipSelectListCacheString);
            System.out.println("sql before extraction:\n" + sql);
            System.out.println("sql after extraction:\n" + fs);
        }
    }
}
