package expr.druid.sqlparser;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.JdbcConstants;
import utils.CustomizedParameterizedOutputVisitorUtils;
import utils.data;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Iterator;
import java.util.Scanner;

import static com.alibaba.druid.sql.visitor.VisitorFeature.OutputParameterizedQuesUnMergeInList;
import static java.lang.System.exit;

/***
 * 参考 https://www.cnblogs.com/lihaiming93/p/7519315.html
 */
public class customextract {
    public static final String NULL = "NULL";
    public static void main(String[] args) throws SQLException, FileNotFoundException, ClassNotFoundException {
        boolean customized = false;
        if (args[0].equals("custom")) {
            customized = true;
        }else if (args[0].equals("param")) {
            customized = false;
        }else {
            System.out.println(String.format("error mode: %s", args[0]));
            exit(1);
        }
        Iterator<String> iter;
        if (args.length > 1) {
            Scanner scanner = new Scanner(new FileInputStream(args[1])).useDelimiter("\\n");
            iter = scanner;
        }else {
            iter = data.data.iterator();
        }

        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:sample.db");
        Statement statement = connection.createStatement();
        statement.executeUpdate("drop table if exists sqls");
        statement.executeUpdate("create table sqls (\n" +
                "  id integer primary key,\n" +
                "  origin_sql text,\n" +
                "  extracted_template  text\n" +
                ");"
        );

        int id = 0;

        //指定数据库类型
        DbType dbtype = JdbcConstants.HIVE;
        for (Iterator<String> it = iter; it.hasNext(); ) {
            String sql = it.next();
//            System.out.println(sql);
            String template;
            try {
                if (customized) {
                    template = CustomizedParameterizedOutputVisitorUtils.parameterize(sql, dbtype, OutputParameterizedQuesUnMergeInList);
                }else {
                    template = ParameterizedOutputVisitorUtils.parameterize(sql, dbtype, OutputParameterizedQuesUnMergeInList);
                }
            }catch (Exception e) {
                System.out.println(sql);
                System.out.println(e.getMessage());
                template = NULL;
            }
//            System.out.println("sql before extraction:\n" + sql);
//            System.out.println("sql after extraction:\n" + fs);
            String sql_insert;
            if (template != NULL) {
                sql_insert = String.format("insert into sqls values(%d, \"%s\", \"%s\")", id, sql, template);
            }else {
                sql_insert = String.format("insert into sqls values(%d, \"%s\", %s)", id, sql, template);
            }
            statement.executeUpdate(sql_insert);
            id++;
        }


//        输出template
        PrintWriter writer = new PrintWriter(new FileOutputStream("template.sql"));
        ResultSet results = statement.executeQuery("select distinct extracted_template from sqls where extracted_template is not null;");
        while(results.next()) {
            writer.println(results.getString("extracted_template").replaceAll("\\n"," ") + ";");
        }

        writer.close();

        statement.close();
        connection.close();
    }
}
