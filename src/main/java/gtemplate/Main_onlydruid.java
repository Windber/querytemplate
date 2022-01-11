package gtemplate;

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
 * @Author: winder
 * @Date: 1/6/22 10:44 AM
 */
public class Main_onlydruid {
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
            iter = data.fordruid.iterator();
        }

        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:sample.db");
        Statement statement = connection.createStatement();
        statement.executeUpdate("drop table if exists sqls");
        String create_sql = "create table sqls (\n" +
                "  id integer primary key,\n" +
                "  origin_sql text,\n" +
                "  extracted_template  text\n" +
                ");";
        statement.executeUpdate(create_sql
        );
        System.out.println(create_sql);

        int id = 0;



        for (Iterator<String> it = iter; it.hasNext(); ) {
            String sql = it.next();

            //指定数据库类型
            DbType dbtype = JdbcConstants.HIVE;
            String template;
            try {
                if (customized) {
                    template = CustomizedParameterizedOutputVisitorUtils.parameterize(sql, dbtype);
                }else {
                    template = ParameterizedOutputVisitorUtils.parameterize(sql, dbtype);
                }
            }catch (Exception e) {
                System.out.println(sql);
                System.out.println(e.getMessage());
                template = NULL;
            }
            System.out.println("sql after alidruid:\n" + template);
            String sql_insert;
            if (template != NULL) {
                sql_insert = String.format("insert into sqls values(%d, \"%s\", \"%s\")", id, sql, template);
            }else {
                sql_insert = String.format("insert into sqls values(%d, \"%s\", %s)", id, sql, template);
            }
            System.out.println(String.format("execute dml in sqlite: \n%s", sql_insert));
            statement.executeUpdate(sql_insert);
            id++;
        }
        System.out.println(String.format("count of origin sql: %d\n", id));

//        输出template
        PrintWriter writer = new PrintWriter(new FileOutputStream("template.sql"));
        String gettemplate_sql = "select distinct extracted_template from sqls where extracted_template is not null;";
        System.out.println(String.format("get template from sqlite: \n%s", gettemplate_sql));
        ResultSet results = statement.executeQuery(gettemplate_sql);
        System.out.println(String.format("template: \n"));
        int template_cnt = 0;
        while(results.next()) {
            String template_sql = results.getString("extracted_template").replaceAll("\\n"," ") + ";";
            System.out.println(String.format("%s", template_sql));
            writer.println(template_sql);
            template_cnt++;
        }
        System.out.println(String.format("count of template: %d\n", template_cnt));

        writer.close();

        statement.close();
        connection.close();
    }

}
