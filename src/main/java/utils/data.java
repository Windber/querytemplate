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
    public  static List<String> data = new ArrayList<>(
            Arrays.asList(
                    "select b,a from c where a='33' and b='ddd' and a in ('33','332','334')",
                    "select b,a from x where x.a>33 and b='ddd' and a in ('33','332','334')",
                    "select rownum,organ_level4,organ_name4,user_no,user_name,pzl_num,xf_ccn from (select t.organ_level4,t.organ_name4,t.user_no,t.user_name,sum(t.pzl_num) pzl_num,sum(t.xf_ccn) xf_ccn from rt_smtj_tb t,sm_user_organ_tb a,sm_organ_tb b where 2>1 and a.organ_no=b.organ_no and b.organ_no=t.organ_level4 and b.organ_level=4 and a.user_no='#DEAL_USERNO#' group by t.organ_level4,t.organ_name4,t.user_no,t.user_name order by sum(t.pzl_num) desc,user_no)",
                    "select rownum,\n" +
                            "organ_level4,\n" +
                            "organ_name4,\n" +
                            "user_no,\n" +
                            "user_name,\n" +
                            "pzl_num,\n" +
                            "xf_ccn\n" +
                            "from (select t.organ_level4,\n" +
                            "t.organ_name4,\n" +
                            "t.user_no,\n" +
                            "t.user_name,\n" +
                            "sum(t.pzl_num) pzl_num,\n" +
                            "sum(t.xf_ccn) xf_ccn\n" +
                            "from rt_smtj_tb t, sm_user_organ_tb a, sm_organ_tb b\n" +
                            "where 2 > 1\n" +
                            "and a.organ_no = b.organ_no\n" +
                            "and b.organ_no = t.organ_level4\n" +
                            "and b.organ_level = 4\n" +
                            "and a.user_no = '#DEAL_USERNO#'\n" +
                            "group by t.organ_level4, t.organ_name4, t.user_no, t.user_name\n" +
                            "order by sum(t.pzl_num) desc, user_no)"

            )
    );
}
