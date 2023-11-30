package com.example.storeManagement

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableFeignClients
@ComponentScan
@EnableScheduling
class StoreManagementApplication

fun main(args: Array<String>) {
	runApplication<StoreManagementApplication>(*args)
}

//
//@Configuration
//class RedisConfig(private val redisTemplate: RedisTemplate<String,String>){
//
//	@PostConstruct
//	fun getConnection(){
//		redisTemplate.connectionFactory.connection
//	}
//
//}
