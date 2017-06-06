package com.codebrig.jnomad.task.explain.adapter

enum DatabaseAdapterType {
    POSTGRESQL,
    MYSQL

    static DatabaseAdapterType fromString(String databaseTypeStr) {
        databaseTypeStr = Objects.requireNonNull(databaseTypeStr).toUpperCase()
        switch (databaseTypeStr) {
            case "POSTGRESQL":
                return POSTGRESQL
            case "MYSQL":
                return MYSQL
            default:
                throw new RuntimeException("Unknown database type: " + databaseTypeStr)
        }
    }
}
