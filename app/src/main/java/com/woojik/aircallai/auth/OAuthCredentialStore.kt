package com.woojik.aircallai.auth

import com.woojik.aircallai.core.storage.CredentialManager
import java.util.Base64

/**
 * PRD-09 OAuth 자격증명 저장소.
 * PRD-02 CredentialManager(Keystore 암호화) 위에 서비스별 연결 데이터를 저장한다.
 * 토큰 직렬화는 Base64 필드 조합을 사용한다(단위 테스트 JVM에서도 동작).
 * 토큰은 어떤 경로(로그/예외 메시지)로도 노출하지 않는다.
 *
 * 연결/갱신 시 기존 Tool 계층(GitHubTool/GmailTool)이 읽는 서비스 키(github/gmail)로
 * access token을 함께 동기화한다. Tool은 OAuth 구현을 몰라도 토큰을 쓸 수 있다.
 */
class OAuthCredentialStore(
    private val credentials: CredentialManager,
) {
    /** provider 연결 데이터를 저장한다. 연결 식별 표시명은 토큰과 별도 키로 저장한다. */
    suspend fun save(provider: String, tokens: OAuthTokens, displayName: String?) {
        credentials.save(serviceKey(provider), encode(tokens))
        if (displayName != null) {
            credentials.save(accountKey(provider), displayName.toByteArray(Charsets.UTF_8))
        } else {
            credentials.delete(accountKey(provider))
        }
        // 기존 Tool 계층 호환: 서비스 키에 access token을 평문이 아닌 CredentialManager
        // 암호화 저장소에 동기화한다(CredentialManager가 저장 시 암호화한다).
        credentials.save(provider, tokens.accessToken.toByteArray(Charsets.UTF_8))
    }

    suspend fun load(provider: String): OAuthTokens? =
        credentials.load(serviceKey(provider))?.let(::decode)

    suspend fun loadDisplayName(provider: String): String? =
        credentials.load(accountKey(provider))?.toString(Charsets.UTF_8)

    /** PRD-09 연결 해제: 해당 서비스의 토큰·계정 식별자를 모두 삭제한다. 다른 서비스는 건드리지 않는다. */
    suspend fun disconnect(provider: String) {
        credentials.delete(serviceKey(provider))
        credentials.delete(accountKey(provider))
        credentials.delete(provider)
    }

    /**
     * 유효한 access token을 반환한다. 만료 시 refresh 토큰으로 갱신을 시도한다.
     * 갱신 성공 시 저장소를 갱신한 토큰으로 교체한다. 갱신 불가 시 null(재인증 필요).
     */
    suspend fun validAccessToken(provider: String, refresher: OAuthProvider, nowEpochMs: Long): String? {
        val tokens = load(provider) ?: return null
        if (!tokens.isExpired(nowEpochMs)) return tokens.accessToken
        val refreshToken = tokens.refreshToken ?: return null
        val refreshed = refresher.refresh(tokens) ?: return null
        save(provider, refreshed, loadDisplayName(provider))
        return refreshed.accessToken
    }

    internal fun encode(tokens: OAuthTokens): ByteArray {
        val b64 = { v: String -> Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8)) }
        val fields = mutableListOf(
            VERSION.toString(),
            b64(tokens.accessToken),
            tokens.refreshToken?.let(b64) ?: "-",
            tokens.expiresAtEpochMs?.toString() ?: "-",
        )
        return fields.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    internal fun decode(bytes: ByteArray): OAuthTokens? = runCatching {
        val parts = bytes.toString(Charsets.UTF_8).split("\n")
        if (parts.size != 4 || parts[0] != VERSION.toString()) return null
        val decoder = Base64.getDecoder()
        OAuthTokens(
            accessToken = String(decoder.decode(parts[1]), Charsets.UTF_8),
            refreshToken = if (parts[2] == "-") null else String(decoder.decode(parts[2]), Charsets.UTF_8),
            expiresAtEpochMs = parts[3].toLongOrNull(),
        )
    }.getOrNull()

    companion object {
        private const val VERSION = 1
        fun serviceKey(provider: String) = "oauth." + provider
        fun accountKey(provider: String) = "oauth." + provider + ".account"
    }
}
