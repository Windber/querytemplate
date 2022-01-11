package gtemplate;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.JdbcConstants;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.rel2sql.CustomRelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.HiveSqlDialect;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.CustomDriver;
import org.apache.hadoop.hive.ql.Driver;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.BaseSemanticAnalyzer;
import org.apache.hadoop.hive.ql.session.SessionState;
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
public class Main {
    public static ASTNode ast = null;
    public static RelNode rel = null;
    public static BaseSemanticAnalyzer sem = null;

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
            iter = data.forhive_succeed.iterator();
        }

        String metastore_addr = "thrift://localhost:9083";
        if (args.length > 2) {
            metastore_addr = args[2];
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

//        配置Hive
        HiveConf conf = new HiveConf();
        conf.set("hive.metastore.uris", metastore_addr);
//        conf.setBoolVar(HiveConf.ConfVars.HIVE_CBO_ENABLED, false);
        conf.setVar(HiveConf.ConfVars.SEMANTIC_ANALYZER_HOOK, "utils.GetAST");
        SessionState ss = SessionState.start(conf);

        org.apache.hadoop.hive.ql.Driver driver_ddl = new Driver(conf);
        CustomDriver driver = new CustomDriver(conf);

        if (driver_ddl.run("create table if not exists o1 (a1 int, b1 string, c1 float)").failed()) {
            System.out.println("ddl failed");
        }
        if (driver_ddl.run("create table if not exists o2 (a2 int, b2 string, c2 float)").failed()) {
            System.out.println("ddl failed");
        }
        if (driver_ddl.run("create table if not exists o3 (a3 int, b3 string, c3 float)").failed()) {
            System.out.println("ddl failed");
        }


        for (Iterator<String> it = iter; it.hasNext(); ) {
            String command = it.next();

//            hive
            System.out.println(String.format("origin sql: \n%s", command));
            if (driver.compile(command) != 0){
                System.out.println("failed");
            }

            System.out.println(String.format("relnode after hive: \n%s", RelOptUtil.toString(rel)));
            SqlDialect sqlDialect = HiveSqlDialect.DEFAULT;
            CustomRelToSqlConverter converter = new CustomRelToSqlConverter(sqlDialect);
            SqlNode sqlNode = null;
            sqlNode = converter.visitChild(0, rel).asStatement();
            String sql = sqlNode.toSqlString(sqlDialect).getSql();
            System.out.println("sql after calcite:\n" + sql);

            //指定数据库类型
            DbType dbtype = JdbcConstants.HIVE;
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


//        输出template
        PrintWriter writer = new PrintWriter(new FileOutputStream("template.sql"));
        String gettemplate_sql = "select distinct extracted_template from sqls where extracted_template is not null;";
        System.out.println(String.format("get template from sqlite: \n%s", gettemplate_sql));
        ResultSet results = statement.executeQuery(gettemplate_sql);
        System.out.println(String.format("template: \n"));
        while(results.next()) {
            String template_sql = results.getString("extracted_template").replaceAll("\\n"," ") + ";";
            System.out.println(String.format("%s", template_sql));
            writer.println(template_sql);
        }

        writer.close();

        statement.close();
        connection.close();
    }

}
