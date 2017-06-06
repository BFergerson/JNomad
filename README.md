Java Source Code Query Scanner / Index Recommendations
==================================
[![Build Status](https://travis-ci.org/BFergerson/JNomad.svg?branch=master)](https://travis-ci.org/BFergerson/JNomad)

JNomad is a utility for scanning Java source code bases for HQL/SQL queries which are then ran as an explain statement against a PostgreSQL database to determine the most inefficent queries and indexes that would make them more efficent.

### Download
```sh
http://www.codebrig.com/public_files/JNomad/JNomadCLI.zip
```


### Usage
```
-analyze_explain           Execute query explain with analyze (will actually run query) (default: true)
-cache_scan_results        Cache scan results to jnomad.cache file (default: true)
-db_database               Database name
-db_host                   Database host (Use : to specify host (ex. localhost:5432) [default = 5432])
-db_password               Database password
-db_username               Database username
-f, -log_file              Log console output to specified file
-help, --help              Displays help information (default: false)
-index_priority_threshold  Threshold index priority for recommendation (default: 50)
-offender_report_percent   Report percentage of top offenders (default: 10)
-scan_directory            Directory/directories of Java source code to be scanned for queries
-scan_file                 File(s) of Java source code to be scanned for queries
-scan_file_limit           Java source code file scan limit [-1 = disabled] (default: -1)
-scan_recursive            Scan source directory/directories recursively (default: true)
-scan_thread_count         Number of processing threads to use (default: 5)
-source_directory          Directory/directories of Java source code to be used for type solving
-source_directory_prescan  Pre-scan scan directory for available source directories (default: true)
-version, --version        Displays version information (default: false)
```

### Examples

##### Example 1
- Scan "C:\MyWorkspace\MyJavaProject"
- Run explains against database "postgresql://localhost:5432/postgres"
```sh
java -jar JNomadCLI.jar -scan_directory C:\MyWorkspace\MyJavaProject -db_host localhost -db_username postgres -db_password postgres -db_database postgres
```
##### Example 2
- Scan "C:\MyWorkspace\MyJavaProject"
- Run explains against databases "postgresql://localhost:5432/postgres" & "postgresql://localhost:5432/postgres2"
```sh
java -jar JNomadCLI.jar -scan_directory C:\MyWorkspace\MyJavaProject -db_host localhost -db_username postgres -db_password postgres -db_database postgres -db_host localhost -db_username postgres -db_password postgres -db_database postgres2
```

### Output
```
****************************************************************************************************
JNomad {1.5/Alpha}: Index Recommendations
****************************************************************************************************

Index: CREATE INDEX idx_column_c ON table_b (column_c);
	Index Priority: 1202.0
	Index Table: table_b
	Index Condition: column_c
	Index Affects: 
		File: src\test\resources\TestSingleFile.java - Location: (line 19,col 23)-(line 19,col 88)

```
