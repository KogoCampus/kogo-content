package com.kogo.content.endpoint.model

import java.time.Instant

data class TopicResponse(
    var id: String,
    var ownerUserId: String? = null,
    var topicName: String,
    var description: String,
    var tags: List<String> = emptyList(),
    var profileImage: TopicProfileImage? = null,
    var createdAt: Instant
) {
    data class TopicProfileImage (
        val attachmentId: String,
        val name: String,
        val size: Long,
        val contentType: String,
        val url: String
    )
}
