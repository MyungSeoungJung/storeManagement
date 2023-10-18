package com.example.storeManagement.order

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/order")
class OrderController (private val orderService: OrderService) {

    @GetMapping("/orderDetail")
    fun getOrderDetail(){

    }
}