package com.example.storeManagement.order

import OrderCondition
import OrderDetailsResponse
import OrderInfo
import OrderStateAndInfo
import ProductFile
import ProductInfo
import ProductInfoAndFile
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductFiles
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
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
        val findProductBrand = (Product innerJoin OrderTable).select { (Product.brand_id eq authProfile.id) and (Product.id eq OrderTable.productId) }.map { it[Product.id] }

        println(authProfile.id)
        findProductBrand.map {
            val orderDetails = findProductBrand.map { //로그인한 id랑 일치하는 제품 map

                val orderStateAndInfo = (OrderState innerJoin OrderTable)   // OrderState와 OrderTable 조인
                .select { (OrderTable.productId inList findProductBrand) }
                    .map {
                        OrderStateAndInfo(
                            it[OrderState.orderId],
                            it[OrderState.orderStatus],
                            it[OrderTable.quantity],
                            it[OrderTable.orderDate].toString()
                        )
                    }           // 제품 + 제품 사진 합치기
                val productInfoAndFile = (Product innerJoin ProductFiles).select { (Product.id inList findProductBrand)}.map {//로그인한 유저가 등록한 제품만 map
                    ProductInfoAndFile(
                        it[Product.id],
                        it[Product.productName],
                        it[ProductFiles.uuidFileName],
                        it[ProductFiles.originalFileName],
                        it[ProductFiles.contentType],
                    )
                }   // 제품 정보
                val products = Product.select { Product.id inList findProductBrand }.map { r ->
                    ProductInfo(
                        r[Product.id],
                        r[Product.productName],
                    )
                }  // 제품 이미지
                val productFile = productInfoAndFile.map {
                    ProductFile(
                        it.uuidFileName,
                        it.originalFileName,
                        it.contentType
                    )
                }
//              주문 정보
                val orderInfo = orderStateAndInfo.map {
                    OrderInfo(
                        it.orderId,
                        it.quantity,
                        it.orderDate
                    )
                } // 주문 처리 상태
                val orderState = orderStateAndInfo.map {
                    OrderCondition(
                        it.orderStatus
                    )
                } // 내보낼 응답
                OrderDetailsResponse(
                    productInfo = products,
                    orderInfo = orderInfo,
                    orderState = orderState,
                    productFile = productFile
                )
            }
            return@transaction orderDetails
        }

    }
}