# 方案
1. 
    - 采用hive-calcite-alidruid的工具组合
        - 因为hiveql最好使用hive的SQL解析器
        - 因为hive没有将`ASTNode`转换回SQL的工具，而且hive利用calcite进行优化，所有有将`ASTNode`转换成`RelNode`的工具，所以使用calcite的转换工具将`RelNode`转换回SQL
        - 因为alidruid自带了个现成工具类能将SQL转换成PreparedStatement，所以多了这一步
    - 输出的模板以及原始SQL以及其他提取的信息先存储在数据库中(可以辅助分析)，然后再从数据库中获取用于生成测试用例的SQL模板

2. 步骤
    - 对于每一条SQL，经过Hive(访问metastore)进行语法分析，语义分析，转化成`RelNode`
    - (此处可以进行一些与语义分析相关如转换过滤等工作)
    - 将`RelNode`输入Calcite，利用其带的转换工具将经过语义分析转回SQL
    - 基于alidruid已有的一个工具类`SQLASTOutputVisitor`二次开发，将SQL转换成需要的SQL模板
    - 

3. 表设计
```
        create table sqls (
    id integer primary key, -- 唯一序号
    origin_sql text,    -- 原始sql
    extracted_template  text    -- 模板
    );    
```

```
        create table element_from (
    id integer, -- 关联sqls
    tablesource text,    -- from中的真实数据源
    );    
```

```
        create table element_where (
    id integer, -- 关联sqls
    tablesource text,    -- 被参数化的字段的数据源
    cols text,    -- where中被参数化的字段
    oper_type text, 
    );    
```

```
        create table element_groupby (
    id integer, -- 关联sqls
    tablesource text,    -- 被参数化的字段的数据源
    cols text,    -- where中被参数化的字段
    );    
```

# 示例
* origin sql: 

```
select b3, c3, sum(a3) from (select a1 + 3 as a, b2 as b from o1 as t1 join o2 as t2 on b1 = b2 where c1 > 0 and b2 = 'b') t3 join o3 on b = b3 where b = 'a' and a3 > 5 group by b3, c3 having c3> 0 limit 10
```

* relnode after hive: 

```
HiveSortLimit(offset=[0], fetch=[10])
  HiveProject(b3=[$0], c3=[$1], _o__c2=[$2])
    HiveFilter(condition=[>($1, 0E-1)])
      HiveAggregate(group=[{0, 1}], agg#0=[sum($2)])
        HiveProject($f0=[$3], $f1=[$4], $f2=[$2])
          HiveFilter(condition=[AND(=($1, _UTF-16LE'a'), >($2, 5))])
            HiveJoin(condition=[=($1, $3)], joinType=[inner], algorithm=[none], cost=[not available])
              HiveProject(a=[+($0, 3)], b=[$7])
                HiveFilter(condition=[AND(>($2, 0E-1), =($7, _UTF-16LE'b'))])
                  HiveJoin(condition=[=($1, $7)], joinType=[inner], algorithm=[none], cost=[not available])
                    HiveTableScan(table=[[default.o1]], table:alias=[t1])
                    HiveTableScan(table=[[default.o2]], table:alias=[t2])
              HiveTableScan(table=[[default.o3]], table:alias=[o3])
```

* sql after calcite:
```
SELECT o3.b3, o3.c3, SUM(o3.a3) _o__c2
FROM (SELECT o1.a1 + 3 a, o2.b2 b
FROM default.o1
INNER JOIN default.o2 ON o1.b1 = o2.b2
WHERE o1.c1 > 0.0 AND o2.b2 = 'b') t0
INNER JOIN default.o3 ON t0.b = o3.b3
WHERE t0.b = 'a' AND o3.a3 > 5
GROUP BY o3.b3, o3.c3
HAVING o3.c3 > 0.0
LIMIT 10
OFFSET 0
```

* sql after alidruid:
```
SELECT o3.b3, o3.c3, SUM(o3.a3) AS _o__c2
FROM (
	SELECT o1.a1 + ${o1.a1} AS a, o2.b2 AS b
	FROM DEFAULT.o1
		INNER JOIN DEFAULT.o2 ON o1.b1 = o2.b2
	WHERE o1.c1 > ${o1.c1}
		AND o2.b2 = ${o2.b2}
) t0
	INNER JOIN DEFAULT.o3 ON t0.b = o3.b3
WHERE t0.b = ${t0.b}
	AND o3.a3 > ${o3.a3}
GROUP BY o3.b3, o3.c3
HAVING o3.c3 > ${o3.c3}
LIMIT 0, 10
```

* execute dml in sqlite: 
```
insert into sqls values(0, "SELECT o3.b3, o3.c3, SUM(o3.a3) _o__c2
FROM (SELECT o1.a1 + 3 a, o2.b2 b
FROM default.o1
INNER JOIN default.o2 ON o1.b1 = o2.b2
WHERE o1.c1 > 0.0 AND o2.b2 = 'b') t0
INNER JOIN default.o3 ON t0.b = o3.b3
WHERE t0.b = 'a' AND o3.a3 > 5
GROUP BY o3.b3, o3.c3
HAVING o3.c3 > 0.0
LIMIT 10
OFFSET 0", "SELECT o3.b3, o3.c3, SUM(o3.a3) AS _o__c2
FROM (
	SELECT o1.a1 + ${o1.a1} AS a, o2.b2 AS b
	FROM DEFAULT.o1
		INNER JOIN DEFAULT.o2 ON o1.b1 = o2.b2
	WHERE o1.c1 > ?
		AND o2.b2 = ${o2.b2}
) t0
	INNER JOIN DEFAULT.o3 ON t0.b = o3.b3
WHERE t0.b = ${t0.b}
	AND o3.a3 > ${o3.a3}
GROUP BY o3.b3, o3.c3
HAVING o3.c3 > ?
LIMIT 0, 10")
```

* get template from sqlite: 
```
select distinct extracted_template from sqls where extracted_template is not null;
```
* template: 
```
SELECT o3.b3, o3.c3, SUM(o3.a3) AS _o__c2 FROM ( 	SELECT o1.a1 + ${o1.a1} AS a, o2.b2 AS b 	FROM DEFAULT.o1 		INNER JOIN DEFAULT.o2 ON o1.b1 = o2.b2 	WHERE o1.c1 > ? 		AND o2.b2 = ${o2.b2} ) t0 	INNER JOIN DEFAULT.o3 ON t0.b = o3.b3 WHERE t0.b = ${t0.b} 	AND o3.a3 > ${o3.a3} GROUP BY o3.b3, o3.c3 HAVING o3.c3 > ? LIMIT 0, 10;
SELECT o1.a1 FROM default.o1 INNER JOIN default.o2 ON o1.a1 = o2.a2;
SELECT t0.a1 FROM ( 	SELECT o1.a1, o1.c1 	FROM DEFAULT.o1 	WHERE o1.a1 > ${o1.a1} ) t0 WHERE t0.a1 = 1 	AND t0.c1 <> CAST(3 AS FLOAT);
SELECT t0.a1 FROM ( 	SELECT o1.a1, o1.c1 	FROM DEFAULT.o1 	WHERE o1.a1 > ${o1.a1} ) t0 WHERE t0.a1 < 3;
SELECT t0.a, t0.b, o3.a3, o3.c3 FROM ( 	SELECT o1.a1 + ${o1.a1} AS a, o2.b2 AS b 	FROM DEFAULT.o1 		INNER JOIN DEFAULT.o2 ON o1.b1 = o2.b2 	WHERE o1.c1 > ? 		AND o2.b2 = ${o2.b2} ) t0 	INNER JOIN DEFAULT.o3 ON t0.b = o3.b3 WHERE t0.b = ${t0.b} 	AND o3.a3 > ${o3.a3} LIMIT 0, 10;
SELECT * FROM ( 	SELECT o1.a1 	FROM DEFAULT.o1 	WHERE o1.a1 > ${o1.a1} ) t0 WHERE t0.a1 < 3;
```

# 使用方式
1. 打成fat jar
```
mvn clean package -Pinner
```
2. 在服务器上传位置执行命令
```
java -cp ./querytemplate-1.0-SNAPSHOT-jar-with-dependencies.jar gtemplate.Main  custom /usr/lib/impala-shell/gen-py/result.txt thrift://192.168.31.92:9083 
```

3. 查看结果
    * 打包程序`querytemplate-1.0-SNAPSHOT-jar-with-dependencies.jar`，上传至服务器`root@192.168.80.151:/usr/lib/impala-shell/gen-py/extract/`
    * `test.sql`为输入sql，执行程序`java -cp querytemplate-1.0-SNAPSHOT-jar-with-dependencies.jar gtemplate.Main test.sql`
    * 查看生成的数据库`sqlite3 ./sample.db`，执行`select * from sqls;`
      ```
                create table sqls (\n" +
                "  id integer primary key ,-- 唯一序号\n" +
                "  origin_sql text, -- 原始sql\n" +
                "  parameterized_template  text, --只参数化而没有去掉日期后缀\n" +
                "  extracted_template  text,--参数化并且去掉日期后缀\n" +
                "  extracted_template_for_generate_testsql  text --按照生成测试sql要求参数化去掉日期后缀\n" +
                ");
      ```
    * 查看从数据库生成的文件`less ./template_allparamed.sql`，用于查看模板
      - (通过该sql生成`select distinct extracted_template from sqls where extracted_template is not null;`)
    * 查看从数据库生成的文件`less ./template_for_generate_testsql.sql`，用于生成测试sql 
      - (通过该sql生成`select distinct extracted_template_for_generate_testsql from (select extracted_template_for_generate_testsql, row_number() over(partition by extracted_template) as r from sqls where extracted_template is not null) t where r=1;`)

