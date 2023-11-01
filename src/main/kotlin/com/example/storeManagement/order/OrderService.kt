package com.example.storeManagement.order

import OrderRequest
import OrderResultResponse
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductInventory
import com.example.storeManagement.product.ProductTotalOrder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.relational.core.sql.Update
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.LocalDateTime

@Service
class OrderService(private val rabbitTemplate: RabbitTemplate) {
    private val mapper = jacksonObjectMapper()

    private val emitters = mutableListOf<SseEmitter>()
    @Auth
    @RabbitListener(queues = ["product-payment"]) // queue 읽고 주문내역 테이블에 저장
    fun receiveOrder(message : String){
        val orderRequest : OrderRequest = mapper.readValue(message)

        val deadEmitters : MutableList<SseEmitter> = ArrayList()

        for (emitter in emitters) {
            try {
            emitter.send(message)
            }catch (e: IOException){
                deadEmitters.add(emitter)
            }
        }
        emitters.removeAll(deadEmitters)
        println("주문 ------------$orderRequest-------------------------")
        val o = OrderTable
        val p = Product

         transaction {
              o.insert {
                   it[this.orderId] = orderRequest.orderId
                   it[this.userId] = orderRequest.userId
                   it[this.productId] = orderRequest.productId
                   it[this.quantity] = orderRequest.quantity
                   it[this.address] = orderRequest.address
                   it[this.orderDate] = LocalDateTime.now()
                   it[this.orderStatus] = false
               }

// ProductTotalOrder 구문 -------------------
             val findProductId = p.select { ProductTotalOrder.productId eq orderRequest.productId }
             val findProductCategory = findProductId.singleOrNull()?.get(p.category)
             val findProductTotalOrder = findProductId.singleOrNull()
             val currentTotalOrder = findProductTotalOrder?.get(ProductTotalOrder.totalOrder) ?: 0  //현재 총 주문 없으면 0으로 초기값 설정

             val updatedTotalOrder = currentTotalOrder + orderRequest.quantity  // quantity +=

             if (findProductTotalOrder != null) {
                 // 이미 주문 통계가 있는 경우 업데이트
                 ProductTotalOrder.update({ ProductTotalOrder.productId eq orderRequest.productId }) {
                     it[this.totalOrder] = updatedTotalOrder
                     it[this.category] = findProductCategory.toString()
                 }
             } else {
                 // 주문 통계가 없는 경우 새 레코드 생성
                 ProductTotalOrder.insert {
                     it[this.productId] = orderRequest.productId
                     it[this.category] = findProductCategory.toString()
                     it[this.totalOrder] = updatedTotalOrder
                 }
             }
// --------------------------------

               println(orderRequest)
               val pi = ProductInventory
               // 주문제품 id랑 재고 제품 id 비교
               val findProduct = pi.select { pi.productId eq orderRequest.productId}

               val currentQuantity = findProduct.singleOrNull()?.get(pi.quantity)
               println(currentQuantity)
               if (currentQuantity != null) {
                   if (!findProduct.empty() && currentQuantity >= orderRequest.quantity) { //레코드 반환이라 null이 아니라 empty로 체크
                       // 재고수량 - 주문 수량 빼기 DB업데이트문
                       val newQuantity = currentQuantity - orderRequest.quantity  // 현재 수량 - 주문 수량

                       pi.update ({pi.productId eq orderRequest.productId}) {
                           it[quantity] = newQuantity
                       }
                       o.update({ o.orderId eq orderRequest.orderId }) {
                           it[o.orderStatus] = true
                       }
                       // 성공 메세지 보내기
                       val successOrderRequest = OrderResultResponse(
                           orderId = orderRequest.orderId,
                           isPermission = "true"
                       )
                       sendResultMessage(successOrderRequest)

                   } else if (findProduct.empty() || currentQuantity == 0 || orderRequest.quantity == 0 ||  orderRequest.quantity > currentQuantity){  //추가
                       val falseOrderRequest = OrderResultResponse (
                           orderId = orderRequest.orderId,
                           isPermission = "false"
                       )
                       o.update({ o.orderId eq orderRequest.orderId }) {
                           it[o.orderStatus] = false
                       }
                       sendResultMessage(falseOrderRequest)

                   }
               }

           }

    }

    fun sendResultMessage(orderResultResponse: OrderResultResponse){
        rabbitTemplate.convertAndSend("product-payment-result",mapper.writeValueAsString(orderResultResponse))
    }
}