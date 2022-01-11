package expr.druid.sqlparser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.parser.ParserException;
import com.alibaba.druid.util.JdbcConstants;
import utils.CustomizedParameterizedOutputVisitorUtils;
import utils.data;

import static com.alibaba.druid.sql.visitor.VisitorFeature.OutputParameterizedQuesUnMergeInList;

/***
 * 参考 https://www.cnblogs.com/lihaiming93/p/7519315.html
 */
public class testcustmonparam {

    public static void main(String[] args) {
        //指定数据库类型
        DbType dbtype = JdbcConstants.HIVE;
        for(String sql: data.fordruid) {
            String fs = null;
            try {
                fs = CustomizedParameterizedOutputVisitorUtils.parameterize(sql, dbtype, OutputParameterizedQuesUnMergeInList);
            }catch (ParserException pe) {
                pe.printStackTrace();
            }
            System.out.println("sql before extraction:\n" + sql);
            System.out.println("sql after extraction:\n" + fs);
        }
    }
}
