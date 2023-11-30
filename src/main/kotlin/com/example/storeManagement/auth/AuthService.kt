package com.example.storeManagement.auth


import com.root.backend.auth.util.HashUtil
import com.root.backend.auth.util.JwtUtil
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service


@Service
class AuthService(private val database: Database,
                  private val rabbitTemplate: RabbitTemplate
) {
    private val logger = LoggerFactory.getLogger(this.javaClass.name)
    private val PROFILE_IMAGE_PATH = "files/profileImage"

    @Auth
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



}