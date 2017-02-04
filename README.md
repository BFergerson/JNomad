## JNomad - Java Code Query Scanner / Index Recommender

JNomad is a utility for scanning Java source code bases for HQL/SQL queries which are then ran as an explain statement against a PostgreSQL database to determine the most inefficent queries and indexes that would make them more efficent.

### Usage

##### View Help
```bat
java -jar JNomadCLI.jar -help
```

##### Example Usage
```bat
java -jar JNomadCLI.jar -db_host localhost -db_username postgres -db_password postgres -db_database postgres -scan_directory C:\MyWorkspace\MyJavaProject
```

### Output
```bat
****************************************************************************************************
JNomad {1.0/Alpha}: Index Recommendations
****************************************************************************************************

Index Priority: 1202.0
Index Condition: create_date
Index Table: my_date_table
Index: CREATE INDEX ON my_date_table (create_date);

Index Priority: 66.0
Index Condition: item_id
Index Table: my_item_table
Index: CREATE INDEX ON my_item_table (item_id);

Index Priority: 64.0
Index Condition: account_id
Index Table: my_account_table
Index: CREATE INDEX ON my_account_table (account_id);
```
