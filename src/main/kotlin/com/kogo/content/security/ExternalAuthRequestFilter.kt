package com.kogo.content.security

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.kogo.content.logging.Logger
import com.kogo.content.service.UserContextService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.util.StringUtils
import org.springframework.web.client.RestTemplate
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Component
class ExternalAuthRequestFilter (
    val userContextService: UserContextService
) : OncePerRequestFilter() {

    @Value("\${external.auth.url}")
    lateinit var authApiUrl: String

    @Value("\${compileVersionKey}")
    lateinit var compileVersionKey: String

    private val restTemplate = RestTemplate()
    private val pathMatcher = AntPathMatcher()

    companion object : Logger() {
        const val AUTH_ENDPOINT = "/authenticate"
        const val USERDATA = "userdata"
        const val USERNAME = "username"
        const val EMAIL = "email"
        const val SCHOOLINFO = "schoolInfo"
        const val SCHOOL_NAME = "name"
        const val SCHOOL_SHORTENED_NAME = "shortenedName"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (isRequestURINotWhitelisted(request.requestURI)) {
            val accessToken = getAccessTokenFromRequestHeader(request)

            // Call external authentication API
            val userInfoJson = authenticateUserWithApi(accessToken).get(USERDATA)

            val username = userInfoJson.get(USERNAME).toString().removeSurrounding("\"")

            if (userContextService.findUserProfileByUsername(username) == null) {
                val email = userInfoJson.get(EMAIL).toString().removeSurrounding("\"")
                val schoolInfoJson = userInfoJson.get(SCHOOLINFO)

                val schoolName = schoolInfoJson.get(SCHOOL_NAME).toString().removeSurrounding("\"")
                val schoolShortenedName = schoolInfoJson.get(SCHOOL_SHORTENED_NAME).toString().removeSurrounding("\"")

                userContextService.createUserProfile(
                    username = username,
                    email = email,
                    schoolName = schoolName,
                    schoolShortenedName = schoolShortenedName,
                )
            }

            val authorities = listOf(SimpleGrantedAuthority("ROLE_USER")) // You can assign roles or authorities
            val authentication = UsernamePasswordAuthenticationToken(username, null, authorities)

            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    private fun authenticateUserWithApi(accessToken: String): JsonNode {
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $accessToken")
            set("APP-VERSION-KEY", compileVersionKey)
        }
        val entity = HttpEntity<Any?>(headers)
        val response = restTemplate.exchange(
            "$authApiUrl$AUTH_ENDPOINT?grant_type=access_token",
            HttpMethod.GET, entity, String::class.java
        )
        if (response.statusCode != HttpStatus.OK) {
            throw RuntimeException("External authentication failed with status code: ${response.statusCode.value()}")
        }
        val objectMapper = ObjectMapper()
        return objectMapper.readTree(response.body)
    }

    private fun getAccessTokenFromRequestHeader(request: HttpServletRequest): String {
        val bearerToken = request.getHeader("Authorization")
        return bearerToken.takeIf { StringUtils.hasText(it) && bearerToken.startsWith("Bearer") }
            ?.substring(7)
            ?: throw RuntimeException("Cannot read access token.")
    }

    private fun isRequestURINotWhitelisted(requestURI: String): Boolean {
        return !SecurityConfig.WHITELIST_PATHS.any { pathMatcher.match(it, requestURI) }
    }
}