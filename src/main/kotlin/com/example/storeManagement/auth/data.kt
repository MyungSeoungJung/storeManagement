package com.example.storeManagement.auth

import org.springframework.web.multipart.MultipartFile

data class AuthProfile (
     val id: Long, // 프로필 id
     val username: String, // 로그인 사용자이름
)