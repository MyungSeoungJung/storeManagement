package com.example.storeManagement.order

import OrderDetailsResponse
import OrderStateAndInfo
import ProductInfoAndFile
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductFiles
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import java.sql.Connection


@RestController
@RequestMapping("/order")
class OrderController (private val orderService: OrderService) {

    @Auth
    @GetMapping("/orderDetail")  //판매자 id에 해당하는 주문 정보만 띄우기 위해서
    fun getOrderDetail(@RequestAttribute authProfile: AuthProfile,
                       @RequestParam state : String,
                       @RequestParam(required = false) keyword: Long?,
                       @RequestParam size: Int,
                       @RequestParam page: Int
    )
    = transaction(
        Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true
    ) {
        val o = OrderTable
//        로그인한 id랑 일치하는 제품들만 추리기
        val findProductBrand = Product.select { Product.brand_id eq authProfile.id }.map { it[Product.id] }
        println("findProductBrand$findProductBrand")

        // state 맞춰서 필터링
        val stateQuery = when (state) {
            "true" -> o.select { (o.orderStatus eq true) }
            "false" -> o.select { (o.orderStatus eq false) }
            else -> o.selectAll()
        }
        // 주문번호 검색
        val searchOrderId = if (keyword == null) {
            stateQuery
        } else {
            stateQuery.andWhere { OrderTable.orderId eq keyword }
        }
        val totalCount = searchOrderId.count()
        //mapNotNull => null 값 제외
        val orderDetails = findProductBrand.mapNotNull { productId ->
            //주문 정보 합치기
            println("stateQuery$stateQuery")

            val orderStateAndInfo = stateQuery.andWhere { o.productId eq productId }
                .orderBy(OrderTable.id, SortOrder.DESC)
                .limit(size, offset = (size * page).toLong())
                .map { r ->
                    OrderStateAndInfo(
                        orderId = r[o.orderId],
                        orderStatus = r[o.orderStatus],
                        quantity = r[o.quantity],
                        orderDate = r[o.orderDate].toString()
                    )
                }
            // 검색
            // 제품 + 제품 사진 합치기
            val productInfoAndFile = if (orderStateAndInfo.isNotEmpty()) {
                (Product innerJoin ProductFiles).select { (Product.id eq productId) }.map {//로그인한 유저가 등록한 제품만 map
                    ProductInfoAndFile(
                        it[Product.id],
                        it[Product.productName],
                        it[ProductFiles.uuidFileName],
                        it[ProductFiles.originalFileName],
                        it[ProductFiles.contentType],
                    )
                }

            } else {
                null
            }

            //내보낼 응답 필터링
            val filteredResponse: OrderDetailsResponse? = if (orderStateAndInfo.isNotEmpty()) {
                OrderDetailsResponse(
                    orderInfo = orderStateAndInfo,
                    productInfo = productInfoAndFile
                ) //orderStateAndInfo값이 있다면
            } else {
                null
            }
            filteredResponse
        }
        return@transaction PageImpl<OrderDetailsResponse?>(orderDetails, PageRequest.of(page, size), totalCount)
    }
}