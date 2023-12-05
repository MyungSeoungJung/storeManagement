package com.example.storeManagement.auth

import com.root.backend.auth.util.JwtUtil
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.*
@RestController
@RequestMapping("/api/auth")
class AuthController(private val service: AuthService) {
    @PostMapping(value = ["/login"])
    fun login(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        @RequestHeader (value = "referer", required = false) referer: String,
        res: HttpServletResponse,
    ): ResponseEntity<*> {

        val (result, message) = service.authenticate(username, password)
        println(username)
        println(password)
        println(referer)
        if (result) {
            val generatedToken  = message
            println("Token : $generatedToken")

            val cookie = Cookie("token", generatedToken)
            cookie.path = "/"
            cookie.maxAge = (JwtUtil.TOKEN_TIMEOUT / 1000L).toInt()
            cookie.domain = referer.split("/")[2].split(":")[0]

            res.addCookie(cookie)

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(
                            ServletUriComponentsBuilder
                                    .fromHttpUrl("${referer.split("/")[0]}//${referer.split("/")[2]}")
                                    .path("/home").build().toUri()
                    )
                    .build<Any>()
        } else {
            println("Login failed: $message")
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED).body(mapOf("status" to "error", "message" to message))

        }
    }
}