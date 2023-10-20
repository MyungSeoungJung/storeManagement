package com.example.storeManagement.order

import OrderCondition
import OrderDetails
import OrderInfo
import OrderStateAndTable
import ProductInfo
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Connection

@RestController
@RequestMapping("/order")
class OrderController (private val orderService: OrderService) {

    @Auth
    @GetMapping("/orderDetail")  //판매자 id에 해당하는 주문 정보만 띄우기 위해서
    fun getOrderDetail(@RequestAttribute authProfile: AuthProfile)
    = transaction(
        Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true
    ) {
//        로그인한 id랑 일치하는 제품들만 추리기
        val findProductBrand = Product.select { Product.brand_id eq authProfile.id }.map { it[Product.id] }
//        제품정보
        println(authProfile.id)
        val orderDetails = findProductBrand.map {
            val orderDetails = findProductBrand.map { productId ->
                // OrderState와 OrderTable을 쿼리하고 orderId를 기반으로 조인합니다.
                val orderStateAndTable = (OrderState innerJoin OrderTable)
                    .select { (OrderState.orderId eq productId) and (OrderTable.orderId eq productId) }
                    .map {
                        OrderStateAndTable(
                            it[OrderState.orderId],
                            it[OrderState.orderStatus],
                            it[OrderTable.quantity],
                            it[OrderTable.orderDate].toString()
                        )
                    }

                val products = Product.select { Product.id inList findProductBrand }.map { r ->
                    ProductInfo(
                        r[Product.id],
                        r[Product.productName],
                    )
                }
//        주문 정보
                val orderInfo = orderStateAndTable.map {
                    OrderInfo(
                        it.orderId,
                        it.quantity,
                        it.orderDate
                    )
                }
                val orderState = orderStateAndTable.map {
                    OrderCondition(
                        it.orderStatus
                    )
                }
                OrderDetails(
                    productInfo = products,
                    orderInfo = orderInfo,
                    orderState = orderState
                )
            }
            return@transaction orderDetails
        }

    }
}