package com.example.storeManagement.product

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.annotation.Configuration

object Product : Table("product"){
    val id = long("id").autoIncrement()
    val brand_id = long("brand_id")
    val productBrand = varchar("product_brand", 20)
    val productName = varchar("product_name", 20)
    val productPrice = long("product_price")
    val category = varchar("category",10)  //tent, table, accessory, tableware, other
    val isActive = bool("is_Active") //활성화/비활성화
    val productDescription = text("product_Description",)   //제품 설명
    override val primaryKey = PrimaryKey(id, name = "pk_product_id")
}

object ProductFiles : LongIdTable("product_file"){
    val productId = reference("product_id", Product.id)
    val originalFileName = varchar("original_file_name", 200)
    val uuidFileName = varchar("uuid", 100).uniqueIndex()
    val contentType = varchar("content_type", 100)
}



//상품 재고 테이블을 따로 분리 이유 -> 실제 재고와의 싱크 유지,유연성 및 확장성
object ProductInventory : IntIdTable("product_inventory") {
    val productId = reference("product_id", Product.id, ) // onDelete = ReferenceOption.CASCADE 테이블 행이 삭제될때 같이 삭제
    val quantity = integer("quantity")
    val lastUpdated = datetime("last_updated")
}

//주문 테이블
object OrderTable : IntIdTable("order_Table"){
    val productId = reference("product_id",Product.id)
    val quantity = integer("quantity")
    val orderDate = datetime("order_date")

}

//판매 취합 테이블
object ProductSales : IntIdTable() {
    val productId = reference("product_id",Product.id)
    val quantity = integer("quantity")
    val saleDate = datetime("sale_date")
}


@Configuration
class TableSetup(private val database: Database) {
    // migrate(이주하다): 코드 -> DB

    // 의존성 객체 생성 및 주입이 완료된 후에 실행할 코드를 작성
    // 스프링 환경구성이 끝난 후에 실행
    @PostConstruct
    fun migrateSchema() {
        // expose 라이버리에서는 모든 SQL 처리는
        // transaction 함수의 statement 람다함수 안에서 처리를 해야함
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Product, ProductFiles,ProductInventory,OrderTable)
        }
    }
}