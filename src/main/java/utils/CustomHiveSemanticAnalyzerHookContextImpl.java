package utils;

import gtemplate.Main;
import org.apache.hadoop.hive.ql.parse.BaseSemanticAnalyzer;
import org.apache.hadoop.hive.ql.parse.HiveSemanticAnalyzerHookContextImpl;

/***
 * @Author: winder
 * @Date: 12/31/21 2:03 PM
 */
public class CustomHiveSemanticAnalyzerHookContextImpl extends HiveSemanticAnalyzerHookContextImpl {
    @Override
    public void update(BaseSemanticAnalyzer sem) {
        super.update(sem);
        Main.sem = sem;
    }
}
