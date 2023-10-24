package com.example.storeManagement.order

import OrderRequest
import OrderResultResponse
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductInventory
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

               println(orderRequest)
               val pi = ProductInventory
               // 주문제품 id랑 재고 제품 id 비교
               val findProduct = pi.select { pi.productId eq orderRequest.productId}.singleOrNull()

               println(findProduct)
               if (findProduct != null) {
                   val currentQuantity = findProduct[pi.quantity]
                   if (currentQuantity >= orderRequest.quantity) {
                       // 재고수량 - 주문 수량 빼기 DB업데이트문
                       val updatedQuantity = currentQuantity - orderRequest.quantity  // 현재 수량 - 주문 수량

                       pi.update ({pi.productId eq orderRequest.productId}) {
                           it[quantity] = updatedQuantity
                       }
                       o.update {
                           it[o.orderStatus] = true
                       }

                       // 성공 메세지 보내기
                       val successOrderRequest = OrderResultResponse(
                           orderId = orderRequest.orderId,
                           isPermission = "true"
                       )
                       sendResultMessage(successOrderRequest)

                   } else if (currentQuantity == 0 || orderRequest.quantity == 0 ||  orderRequest.quantity > currentQuantity){  //추가
                       val falseOrderRequest = OrderResultResponse (
                           orderId = orderRequest.orderId,
                           isPermission = "false"
                       )
                       sendResultMessage(falseOrderRequest)

                   }
               }

           }

    }

    fun sendResultMessage(orderResultResponse: OrderResultResponse){
        rabbitTemplate.convertAndSend("product-payment-result",mapper.writeValueAsString(orderResultResponse))
    }
}