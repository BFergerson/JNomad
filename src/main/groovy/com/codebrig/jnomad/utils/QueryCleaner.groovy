package com.codebrig.jnomad.utils

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryCleaner {

    static String cleanQueryParse(String query) {
        def cleanSQL = query.toLowerCase().replace("\"", "").replace("\\'", "'")

        //take long hibernate links and quote so they can be parsed later on
        def arrayFinderRegex = "(\\S*\\.\\S*\\.\\S*\\.\\S*\\.\\S*)"
        for (def longColumnIdent : cleanSQL.findAll(arrayFinderRegex)) {
            cleanSQL = cleanSQL.replace(longColumnIdent, "\"" + longColumnIdent + "\"")
        }

        arrayFinderRegex = "([^ \\)\\(\\[]+)\\[[0-9 ]+]"
        for (def attrString : cleanSQL.findAll(arrayFinderRegex)) {
            def arrayName = attrString.find("\\s*([^\\[]*)")
            def arrayNumber = attrString.replace(arrayName, "").replace("[", "").replace("]", "").find("\\s*(.+)\\s*")
            cleanSQL = cleanSQL.replace(attrString, "jnomad_array_" + arrayName + "_" + arrayNumber)
        }

        if (cleanSQL.startsWith("from ")) {
            cleanSQL = "select * " + cleanSQL
        }
        cleanSQL = cleanSQL.replace(" join fetch ", " join ")
        return cleanSQL
    }

    static String cleanQueryExecute(String query) {
        def cleanSQL = query.toLowerCase()
        def arrayFinderRegex = "jnomad_array_(\\S+)_([0-9]+)"
        Pattern pattern = Pattern.compile(arrayFinderRegex)
        Matcher matcher = pattern.matcher(cleanSQL)
        while (matcher.find()) {
            def arrayName = matcher.group(1)
            def arrayNumber = matcher.group(2)

            cleanSQL = cleanSQL.replace("jnomad_array_" + arrayName + "_" + arrayNumber, arrayName + "[" + arrayNumber + "]")
        }
        return cleanSQL
    }

}
