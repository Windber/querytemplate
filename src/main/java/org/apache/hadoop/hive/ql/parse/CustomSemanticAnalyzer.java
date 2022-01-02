package org.apache.hadoop.hive.ql.parse;

import org.apache.hadoop.hive.ql.QueryState;

/***
 * @Author: winder
 * @Date: 12/31/21 3:53 PM
 */
public class CustomSemanticAnalyzer extends SemanticAnalyzer {
    public CustomSemanticAnalyzer(QueryState queryState) throws SemanticException {
        super(queryState);
    }

    @Override
    boolean genResolvedParseTree(ASTNode ast, PlannerContext plannerCtx) throws SemanticException {
        if(!super.genResolvedParseTree(ast, plannerCtx)) {
            System.out.println("genResolvedParseTree failed");
        }
        return false;
    }
}
