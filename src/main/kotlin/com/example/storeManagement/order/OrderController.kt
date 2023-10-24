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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
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
//        로그인한 id랑 일치하는 제품들만 추리기
        val findProductBrand = (Product innerJoin OrderTable).select { (Product.brand_id eq authProfile.id) and (Product.id eq OrderTable.productId) }.map { it[Product.id] }
        //mapNotNull => null 값 제외
        val orderDetails = findProductBrand.mapNotNull { productId ->

            val orderStateAndInfoJoin = (OrderState innerJoin OrderTable)

            val baseQuery = orderStateAndInfoJoin.select { (OrderTable.productId eq productId) and (OrderTable.orderId eq OrderState.orderId) }
            // state 맞춰서 필터링
            val stateQuery = when (state) {
                "true" -> baseQuery.andWhere { (OrderState.orderStatus eq true) }
                "false" -> baseQuery.andWhere { (OrderState.orderStatus eq false) }
                else -> baseQuery
            }

            // 주문번호 검색
            val searchOrderId = if(keyword == null){
                stateQuery
            }else{
                stateQuery.andWhere { OrderTable.orderId eq keyword }
            }
            val totalCount = searchOrderId.count()

            //주문 정보 합치기
            val orderStateAndInfo = stateQuery
                    .orderBy(OrderTable.id,SortOrder.DESC)
                    .limit(size, offset= (size * page).toLong())
                    .map { r ->
                        OrderStateAndInfo(
                                orderId = r[OrderState.orderId],
                                orderStatus = r[OrderState.orderStatus],
                                quantity = r[OrderTable.quantity],
                                orderDate = r[OrderTable.orderDate].toString()
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
                OrderDetailsResponse(orderInfo = orderStateAndInfo, productInfo = productInfoAndFile) //orderStateAndInfo값이 있다면
            } else {
                null
            }
            filteredResponse
        }
//        val response = PageImpl(orderDetails, PageRequest.of(page, size), totalCount)
        return@transaction orderDetails
        }
}