package com.example.storeManagement.order
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object OrderTable : Table("order_table"){
    val id = long("id").autoIncrement()
    val orderId = long("order_id")
    val userId = long("user_id")
    val productId = long("product_id")   //주문 제품id랑 제품테이블에있는 브랜드id가 가지고 있는 제품 id랑 비교해서 같은 객체만 뽑기
    val quantity = integer("quantity")
    val address = varchar("address", 30)
    val orderDate = datetime("order_date")
    override val primaryKey =PrimaryKey(id, name = "pk_order_id")
}

object OrderState : LongIdTable("order_state") {
    val orderId = reference("order_id", OrderTable.id)
    val orderStatus = varchar("orderStatus",10)
}

//object ProductOrderQuantity : LongIdTable("product_order_quantity") {
//    val productId = reference("product_id", Order.productId)
//    val totalQuantity = long("totalQuantity")
//}