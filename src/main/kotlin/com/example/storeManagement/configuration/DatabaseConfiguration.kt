package com.example.storeManagement.configuration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabaseConfiguration (val dataSource: DataSource) {
    @Bean
    fun databaseConfig() : DatabaseConfig {
        return DatabaseConfig { useNestedTransactions = true }
    }
    @Bean
    fun database(): Database {
        return Database.connect(dataSource)
    }
}