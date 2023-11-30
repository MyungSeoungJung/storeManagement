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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.transactions.transaction
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
    @RabbitListener(queues = ["product-payment"])
//    product-payment에 있는 queue receiveOrder(message : String) 매개변수로 전달
    fun receiveOrder(message : String){
//      JSON 문자열(message)을 OrderRequest 클래스의 인스턴스로 변환
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
//        val pto = ProductTotalOrder
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

               val pi = ProductInventory
               // 주문제품 id랑 재고 제품 id 비교
               val findProduct = pi.select { pi.productId eq orderRequest.productId}

               val currentQuantity = findProduct.singleOrNull()?.get(pi.quantity)
               println(currentQuantity)
               if (currentQuantity != null) {
                   if (!findProduct.empty() && currentQuantity >= orderRequest.quantity) { //레코드 반환이라 null이 아니라 empty로 체크
                       // 재고수량 - 주문 수량 빼기 DB업데이트문
                       val newQuantity = currentQuantity - orderRequest.quantity  // 현재 수량 - 주문 수량

                       // 주문이 들어온 후에 재고가 0이 되었을 때 상품을 숨김 처리
                       if (newQuantity == 0) {
                           val p = Product
                           p.update({ p.id eq orderRequest.productId }) {
                               it[p.isActive] = false
                           }
                       }

                       pi.update ({pi.productId eq orderRequest.productId}) {
                           it[quantity] = newQuantity
                       }
                       o.update({ o.orderId eq orderRequest.orderId }) {
                           it[o.orderStatus] = true
                       }
                       // 주문 통계 update 구문 ------------------------------------------------------------------------------------------------------------
                       val findProductId = p.select { p.id eq orderRequest.productId }
                       val findProductCategory = findProductId.singleOrNull()?.get(p.category)
                       val currentTotalOrder = OrderTable.select { (OrderTable.productId eq orderRequest.productId) and (OrderTable.orderStatus eq true) }.sumOf{ it[OrderTable.quantity] }
                       val existingRecord = ProductTotalOrder.select { ProductTotalOrder.productId eq orderRequest.productId }.singleOrNull()
                       println("currentTotalOrder ------------$currentTotalOrder-------------------------")
                       val updatedTotalOrder = currentTotalOrder + orderRequest.quantity

                       if (existingRecord != null) {
                           // 이미 주문 통계가 있는 경우 업데이트
                           ProductTotalOrder.update({ ProductTotalOrder.productId eq orderRequest.productId }) {
                               it[this.totalOrder] = updatedTotalOrder.toLong()
                               it[this.category] = findProductCategory.toString()
                           }
                       } else {
                           // 주문 통계가 없는 경우 새 레코드 생성
                           ProductTotalOrder.insert {
                               it[this.productId] = orderRequest.productId
                               it[this.category] = findProductCategory.toString()
                               it[this.totalOrder] = orderRequest.quantity.toLong()
                           }
                       }
                       // 주문 통계 update 구문
                       // 성공 메세지 보내기
                       val successOrderRequest = OrderResultResponse(
                           orderId = orderRequest.orderId,
                           isPermission = "true"
                       )
                       println("successOrderRequest---------------------------$successOrderRequest")
                       sendResultMessage(successOrderRequest)
                   } else if (findProduct.empty() || currentQuantity == 0 || orderRequest.quantity == 0 ||  orderRequest.quantity > currentQuantity){
                       val falseOrderRequest = OrderResultResponse (
                           orderId = orderRequest.orderId,
                           isPermission = "false"
                       )
                       o.update({ o.orderId eq orderRequest.orderId }) {
                           it[o.orderStatus] = false
                       }
                       println("falseOrderRequest----------------------------$falseOrderRequest")
                       sendResultMessage(falseOrderRequest)



                   }

               }
           }

    }
    fun createEmitter(): SseEmitter {
        val emitter = SseEmitter()
        emitters.add(emitter)

        emitter.onTimeout {
            emitters.remove(emitter)
        }

        emitter.onCompletion {
            emitters.remove(emitter)
        }
        emitter.send("connected");  //기본메세지를 안 보내면 pending 처리 됨
        return emitter
    }


    fun sendResultMessage(orderResultResponse: OrderResultResponse){
        rabbitTemplate.convertAndSend("product-payment-result",mapper.writeValueAsString(orderResultResponse))
    }
}
