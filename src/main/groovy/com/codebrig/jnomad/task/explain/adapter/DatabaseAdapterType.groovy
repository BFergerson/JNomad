package com.codebrig.jnomad.task.explain.adapter

enum DatabaseAdapterType {
    POSTGRES,
    MYSQL

    static DatabaseAdapterType fromString(String databaseTypeStr) {
        databaseTypeStr = Objects.requireNonNull(databaseTypeStr).toUpperCase()
        switch (databaseTypeStr) {
            case "POSTGRES":
                return POSTGRES
            case "MYSQL":
                return MYSQL
            default:
                throw new RuntimeException("Unknown database type: " + databaseTypeStr)
        }
    }
}
