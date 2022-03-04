package gtemplate;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.util.JdbcConstants;
import utils.C1ParameterizedOutputVisitorUtils;
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
    public static final String NULL = "NULL";
    public static void main(String[] args) throws SQLException, FileNotFoundException, ClassNotFoundException {

        Iterator<String> iter;
        if (args.length > 0) {
            Scanner scanner = new Scanner(new FileInputStream(args[0])).useDelimiter("\\n");
            iter = scanner;
        }else {
            iter = data.fordruid.iterator();
        }

        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:sample.db");
        Statement statement = connection.createStatement();
        statement.executeUpdate("drop table if exists sqls");
        String create_sql = "create table sqls (\n" +
                "  id integer primary key ,-- 唯一序号\n" +
                "  origin_sql text, -- 原始sql\n" +
                "  parameterized_template  text, --只参数化而没有去掉日期后缀\n" +
                "  extracted_template  text,--参数化并且去掉日期后缀\n" +
                "  extracted_template_for_generate_testsql  text, --按照生成测试sql要求参数化去掉日期后缀\n" +
                "  extracted_funcs text --提取的函数名\n" +
                ");";
        statement.executeUpdate(create_sql
        );
//        System.out.println(create_sql);

        int id = 0, template0_null_cnt=0, template1_null_cnt=0, template2_null_cnt=0;

        PreparedStatement pstatement = connection.prepareStatement("insert into sqls values(?, ?, ?, ?, ?, ?);");


        for (Iterator<String> it = iter; it.hasNext(); ) {
            String sql = it.next();

            //指定数据库类型
            DbType dbtype = JdbcConstants.HIVE;
            String template0, template1, template2, extractedFuncs;



            try {
                template0 = ParameterizedOutputVisitorUtils.parameterize(sql, dbtype);
            }catch (Exception e) {
                template0 = NULL;
                template0_null_cnt++;
            }
            try {
                template1 = C1ParameterizedOutputVisitorUtils.parameterize(sql, dbtype);
                extractedFuncs = "";
                for(int i=0; i<C1ParameterizedOutputVisitorUtils.visitor.extractedFuncs.size(); i++) {
                    extractedFuncs += C1ParameterizedOutputVisitorUtils.visitor.extractedFuncs.get(i);
                    extractedFuncs += ", ";
                }
                extractedFuncs = C1ParameterizedOutputVisitorUtils.visitor.extractedFuncs.size() == 0? extractedFuncs: extractedFuncs.substring(0, extractedFuncs.length()-2);

            }catch (Exception e) {
                template1 = NULL;
                extractedFuncs = NULL;
                template1_null_cnt++;
            }
            try {
                template2 = CustomizedParameterizedOutputVisitorUtils.parameterize(sql, dbtype);
            }catch (Exception e) {
                template2 = NULL;
                template2_null_cnt++;
            }

            sql = sql.replaceAll("[ \\t\\n]+"," ");
            template0 = template0.replaceAll("[ \\t\\n]+"," ");
            template1 = template1.replaceAll("[ \\t\\n]+"," ");
            template2 = template2.replaceAll("[ \\t\\n]+"," ");

            System.out.println("origin:\n" + sql);
            System.out.println("template0:\n" + template0);
            System.out.println("template1:\n" + template1);
            System.out.println("template2:\n" + template2);
            System.out.println("extractedFuncs:\n" + extractedFuncs);

//            String sql_insert;
            pstatement.setInt(1, id);
            pstatement.setString(2, sql);
            if (template0 != NULL) {
                pstatement.setString(3, template0);
            }else {
                pstatement.setNull(3, Types.VARCHAR);
            }
            if (template1 != NULL) {
                pstatement.setString(4, template1);
            }else {
                pstatement.setNull(4, Types.VARCHAR);
            }
            if (template2 != NULL) {
                pstatement.setString(5, template2);
            }else {
                pstatement.setNull(5, Types.VARCHAR);
            }
            if (extractedFuncs != NULL) {
                pstatement.setString(6, extractedFuncs);
            }else {
                pstatement.setNull(6, Types.VARCHAR);
            }

            pstatement.executeUpdate();
//            sql_insert = pstatement.toString();
//            System.out.println(String.format("execute dml in sqlite: \n%s", sql_insert));
//            statement.executeUpdate(sql_insert);
            id++;
        }
        System.out.println(String.format("count of origin sql: %d\n", id));
        System.out.println(String.format("count of null template0: %d\n", template0_null_cnt));
        System.out.println(String.format("count of null template1: %d\n", template1_null_cnt));
        System.out.println(String.format("count of null template2: %d\n", template2_null_cnt));

//        输出template1
        PrintWriter writer = new PrintWriter(new FileOutputStream("template_allparamed.sql"));
        String gettemplate_sql = "select distinct extracted_template from sqls where extracted_template is not null;";
//        System.out.println(String.format("get template from sqlite: \n%s", gettemplate_sql));
        ResultSet results = statement.executeQuery(gettemplate_sql);
//        System.out.println(String.format("template: \n"));
        int template_cnt = 0;
        while(results.next()) {
            String template_sql = results.getString("extracted_template") + ";";
//            System.out.println(String.format("%s", template_sql));
            writer.println(template_sql);
            template_cnt++;
        }
        System.out.println(String.format("count of distinct extracted_template: %d\n", template_cnt));

        writer.close();

        //        输出template2
        writer = new PrintWriter(new FileOutputStream("template_for_generate_testsql.sql"));
        gettemplate_sql = "select distinct extracted_template_for_generate_testsql from (select extracted_template_for_generate_testsql, row_number() over(partition by extracted_template) as r from sqls where extracted_template is not null) t where r=1;";
//        System.out.println(String.format("get template from sqlite: \n%s", gettemplate_sql));
        results = statement.executeQuery(gettemplate_sql);
//        System.out.println(String.format("template: \n"));
        template_cnt = 0;
        while(results.next()) {
            String template_sql = results.getString("extracted_template_for_generate_testsql") + ";";
//            System.out.println(String.format("%s", template_sql));
            writer.println(template_sql);
            template_cnt++;
        }
        System.out.println(String.format("count of distinct extracted_template_for_generate_testsql: %d\n", template_cnt));

        writer.close();

        statement.close();
        connection.close();
    }

}
