package com.example.storeManagement.product

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.annotation.Configuration

object Product : Table("product"){
    val id = long("id").autoIncrement()
    val productBrand = varchar("product_brand", 20)
    val productName = varchar("product_name", 20)
    val productPrice = long("product_price")
    val productCode = varchar("product_code",10)
    val category = varchar("category",10)
    val productDescription = text("product_Description",)
    override val primaryKey = PrimaryKey(id, name = "pk_product_id")
}

object ProductFiles : LongIdTable("product_file"){
    val productId = reference("product_id", Product.id)
    val originalFileName = varchar("original_file_name", 200)
    val uuidFileName = varchar("uuid", 100).uniqueIndex()
    val contentType = varchar("content_type", 100)
}


//
//object Brand : LongIdTable("brand") {
//    val id = long("id").autoIncrement()
//    val email = varchar("email", 200)
//    val img =  varchar("email", 200)
//
//}
@Configuration
class PostTableSetup(private val database: Database) {
    // migrate(이주하다): 코드 -> DB

    // 의존성 객체 생성 및 주입이 완료된 후에 실행할 코드를 작성
    // 스프링 환경구성이 끝난 후에 실행
    @PostConstruct
    fun migrateSchema() {
        // expose 라이버리에서는 모든 SQL 처리는
        // transaction 함수의 statement 람다함수 안에서 처리를 해야함
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Product, ProductFiles)
        }
    }
}