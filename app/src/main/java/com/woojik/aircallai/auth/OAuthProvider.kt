package com.woojik.aircallai.auth

/**
 * PRD-09 OAuth Provider 계약. GitHub/Google 구현체가 이 인터페이스를 따른다.
 * Tool 계층은 OAuth 세부 구현을 직접 다루지 않고 CredentialManager만 읽는다.
 * 구현은 state/PKCE 등 provider가 지원하는 보안 메커니즘을 적용해야 한다.
 */
interface OAuthProvider {
    /** 서비스 식별자. CredentialManager의 service 키와 일치한다(github, gmail). */
    val provider: String

    /** 연결 시작. 시스템 브라우저·권장 인증 흐름을 여는 것은 구현의 책임이다. */
    suspend fun startConnect(): OAuthCallbackResult.Rejected?

    /**
     * 인증 callback 검증·토큰 교환.
     * 예상하지 않은 provider/state/code는 null이 아닌 Rejected를 반환해야 한다.
     * 성공 시 토큰을 반환하고, 저장은 호출자(OAuthCredentialStore)가 수행한다.
     */
    suspend fun handleCallback(provider: String, code: String, state: String?): OAuthTokens?

    /** refresh token으로 access token을 갱신. 갱신 불가 시 null. */
    suspend fun refresh(tokens: OAuthTokens): OAuthTokens?
}
