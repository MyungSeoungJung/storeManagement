package com.root.backend.auth

import com.root.backend.auth.Profiles.brandIntro
import com.root.backend.auth.Profiles.brandName
import com.root.backend.auth.Profiles.businessNumber
import com.root.backend.auth.Profiles.representativeName
import com.root.backend.auth.util.HashUtil
import com.root.backend.auth.util.JwtUtil
import com.root.backend.auth.util.JwtUtil.extractToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class AuthService(private val database: Database) {
    private val logger = LoggerFactory.getLogger(this.javaClass.name)
    private val PROFILE_IMAGE_PATH = "files/profileImage"

    fun authenticate(username: String, password: String): Pair<Boolean, String> {

        // readOnly를 하게되면 transaction id를 생성하지 않음
        // MySQL 기본 격리수준, repeatable_read
        // 다른 SQL DBMS는 기본 격리수준, read_commited

        // read_commited (병렬처리지만, 커밋된 것만 조회되게 함)
        // txn = 1, select all - 오래걸림
        // txn = 2, insert - 빠르게됨
        // txn(2)의 insert 결과가 txn(2)의 select 결과에 반영이됨.

        // repeatable_read (병렬처리지만, 요청한 순서대로 조회되게 함)
        // txn = 1, select all - 오래걸림
        // txn = 2, insert - 빠르게됨
        // txn(2)의 insert 결과가 txn(2)의 select 결과에 반영이됨.

        val dummyUser = users.find { it.username == username }

        if (dummyUser != null) {
            if (dummyUser.password == password) {
                val token = JwtUtil.createToken(dummyUser.id, dummyUser.username)
                return Pair(true, token)
            } else {
                return Pair(false, "Unauthorized")
            }
        } else {

            val (result, payload) = transaction(
                    database.transactionManager.defaultIsolationLevel, readOnly = true) {
                val i = Identities;
                val p = Profiles;

                // 인증정보 조회
                val identityRecord = i.select(i.username eq username).singleOrNull()
                        ?: run {
                            println("User with username $username not found.")
                            return@transaction Pair(false, mapOf("message" to "Unauthorized"))
                        }

                // 프로필정보 조회
                val profileRecord = p.select(p.identityId eq identityRecord[i.id].value).singleOrNull()
                        ?: return@transaction Pair(false, mapOf("message" to "Conflict")) // 에러나면 삭제

                return@transaction Pair(true, mapOf(
                        "id" to profileRecord[p.id],
                        "username" to identityRecord[i.username],
                        "secret" to identityRecord[i.secret]
                ))
            }

            if (!result) {
                return Pair(false, payload["message"].toString());
            }

            //   password+salt -> 해시 -> secret 일치여부 확인
            val isVerified = HashUtil.verifyHash(password, payload["secret"].toString())
            if (!isVerified) {
                return Pair(false, "Unauthorized")
            }

            val token = JwtUtil.createToken(
                    payload["id"].toString().toLong(),
                    payload["username"].toString(),
            )

            return Pair(true, token)
        }
    }

    fun registerProfile(
            @RequestParam token: String,
            @RequestPart profileData: Profile,
            @RequestPart("profileImage") files: List<MultipartFile>

            ): Boolean {
        logger.info("Attempting to register profile with token: $token")
        println("ProfileData: $profileData")

        val actualToken = extractToken(token) ?: return false
        val authProfile = JwtUtil.validateToken(actualToken)
        if (authProfile == null) {
            println("토큰 검증 실패")
            return false
        } else {
            println("토큰이 검증됨. AuthProfile: $authProfile")
        }

        val userId = EntityID(authProfile.id, Identities)

        val dirPath = Paths.get(PROFILE_IMAGE_PATH)
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath)
        }

        val filesMetaList = mutableListOf<Map<String, String>>()

        runBlocking {
            profileData.profileImage.forEach {
                launch {
                    val originalFileName = it.originalFilename
                            ?: throw IllegalArgumentException("Original filename is missing")
                    val uuidFileName = buildString {
                        append(UUID.randomUUID().toString())
                        append(".")
                        append(originalFileName!!.split(".").last())
                    }
                    val filePath = dirPath.resolve(uuidFileName)
                    it.inputStream.use {
                        Files.copy(it, filePath, StandardCopyOption.REPLACE_EXISTING)
                    }
                    filesMetaList.add(mapOf(
                            "originalFileName" to originalFileName,
                            "uuidFileName" to uuidFileName,
                            "contentType" to it.contentType!!
                    ))
                }
            }
        }


        transaction {
            try {
                val result = Profiles.insert {
                    it[this.identityId] = userId
                    it[this.brandName] = brandName
                    it[this.businessNumber] = businessNumber
                    it[this.representativeName] = representativeName
                    it[this.brandIntro] = brandIntro
                }

                // 로그 추가
                logger.info("brandName: $brandName")
                logger.info("businessNumber: $businessNumber")
                logger.info("representativeName: $representativeName")
                logger.info("brandIntro: $brandIntro")

                // `resultedValues`가 null인지 체크하고, null이 아니라면 첫 번째 값을 가져옵니다.
                val profile = result.resultedValues?.firstOrNull()
                        ?: throw Exception("Failed to insert profile into the database.")

                ProfilesMeta.batchInsert(filesMetaList) {
                    this[ProfilesMeta.profileID] = profile[Profiles.id]
                    this[ProfilesMeta.originalFileName] = it["originalFileName"] as String
                    this[ProfilesMeta.uuidFileName] = it["uuidFileName"] as String
                    this[ProfilesMeta.contentType] = it["contentType"] as String
                }

                println("프로필이 데이터베이스에 성공적으로 삽입되었습니다.")
            } catch (e: Exception) {
                logger.error("데이터베이스에 프로필을 삽입하는중 오류 발생. 예외: $e")
                return@transaction false
            }
        }
        logger.info("프로필 등록 성공 token: $token")
        return true
    }
}