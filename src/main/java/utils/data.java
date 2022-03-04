package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/***
 * @Author: winder
 * @Date: 12/21/21 3:38 PM
 */
public class data {
    public static String single_select = "select b,a from c where a='33' and b='ddd' and a in ('33','332','334')";
    public static String single_expr = "a='33'";
    public  static List<String> forhive_failed = new ArrayList<>(
            Arrays.asList(
                    "select b3 from o3 order by b3"
                    , "select a1 from o1 where b1 in (select inline(array(struct('A'),struct('B'))))"
//                    , "select a1 from o1 where b1 = 'a' or b1 = 'b' or b1 = 'c'"
//                    "select a1 from o1 where b1 in ('a', 'b', 'c')"
//                    "insert into o1 values (1, '1', 1), (1, '1', 1)"
                    , "select a1 from o1 where b1 in (select b2 from o2 limit 3)"
                    , "select a1 from o1 where b1 in ('a')"
                    , "select a1 from o1 where b1 in ('a', 'b', 'c')"
            , "select b3 from o3 order by b3"
            , "select b3, c3, sum(a3) from (select a1 + 3 as a, b2 as b from o1 as t1 join o2 as t2 on b1 = b2 where c1 > 0 and b2 = 'b') t3 join o3 on b = b3 where b = 'a' and a3 > 5 group by b3, c3 having c3> 0 order by b3 limit 10"
            , "select a1 from o1 where b1 in ('a', 'b', 'c')"
            )
    );
    public  static List<String> forhive_wrong = new ArrayList<>(
    );
    public  static List<String> forhive_succeed = new ArrayList<>(
            Arrays.asList(
                    "select b3, c3, sum(a3) from (select a1 + 3 as a, b2 as b from o1 as t1 join o2 as t2 on b1 = b2 where c1 > 0 and b2 = 'b') t3 join o3 on b = b3 where b = 'a' and a3 > 5 group by b3, c3 having c3> 0 limit 10"
                    , "select a1 from o1 join o2 on o1.a1 = o2.a2"
                    //                    alias会被消除
                    , "Select a1 from (select a1, c1 from o1 as t1 where t1.a1 > 0) t2 where a1 = 1 and c1 != 3.0"
                    , "Select a from (select a from  as xx where xx.a > 0) y where a = 1"
                    , "select b3, c3, sum(a3) from (select a1 + 3 as a, b2 as b from o1 as t1 join o2 as t2 on b1 = b2 where c1 > 0 and b2 = 'b') t3 join o3 on b = b3 where b = 'a' and a3 > 5 group by b3, c3 having c3> 0 limit 10"
                    , "select a1 from (select a1, c1 from o1 where a1 > 0) x1 where a1 < 3"
                    , "select a, b, a3, c3 from (select a1 + 3 as a, b2 as b from o1 as t1 join o2 as t2 on b1 = b2 where c1 > 0 and b2 = 'b') t3 join o3 on b = b3 where b = 'a' and a3 > 5 limit 10"
                    , "select a1 from o1 where limit 10"
                    , "select a1 from (select a1 from o1 where a1 > 0) t1 where a1 < 3"
            )
    );
    public  static List<String> fordruid = new ArrayList<>(
            Arrays.asList(
                    "select substr(a, 1, 4) from t1 where custom(a, 1, 4) = 'abc'",
                    "select substr1(a, 1, 4) from t1 where custom1(a, 1, 4) = 'abc'",
                    "select a from t1 where a like '%abc'"
                    ,
                    "select a from t1 where b ilike '%abc'"
                    ,
//                    test1
                    "select tableprefix_211205.* from mada.tableprefix_211205 as tableprefix_211205",
                    "select * from mada.tableprefix_month_2112",
                    "select * from ma.tableprefix211210",

                    "select start_time, a from mada.tn_211226 as t where t.start_time > 12342 and start_time <= 12345",
                    "select start_time, a from mada.tn_211226 as t where t.start_time > 12342 and start_time < 12345",

                    "select a, b from (select c + 3 as a, b from o1 join o2 on o1.c = o2.c where o1.c > 0 and o1.b in (1, 20)) t1 join t2 where t1.b > 10 or t2.a > 5 limit 100",
                    "select a from t1 where a = 'hello' and substr(t1.b, 25, 4) >= '123'",

                    "select a from t1 where b in ('a', 'b')",
                    "select a from t1 where a = 'hello' and b in ('a', 'b')",

                    "select cast(pre_221212.msisdn as varchar) as _msisdn from mada.pre_221212 pre_221212 where pre_221212.msisdn in ('123456', '123456') and pre_221212.msisdn in ('123456', '123456') limit 1000",
                    "select tn211206.start_time, a from ma.tn211206 as tn211206 where tn211206.start_time > 12342 and start_time <= 12345",

                    "select a from t1 where a = 'hello' and b in ('a', 'b')",
                    "select a from t1 where a = 'ss' and b in ('a', 'd', 'b')"
            )
    );


}
