package com.kogo.content.storage.view

import com.kogo.content.storage.entity.Post
import com.kogo.content.storage.MongoPaginationQueryBuilder
import com.kogo.content.lib.PaginationRequest
import com.kogo.content.lib.PaginationSlice
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

@Component
class PostAggregateView(
    mongoTemplate: MongoTemplate,
    private val mongoPaginationQueryBuilder: MongoPaginationQueryBuilder
) : MongoView<PostAggregate>(mongoTemplate, PostAggregate::class) {

    companion object {
        private const val POPULARITY_LIKE_WEIGHT = 0.8
        private const val POPULARITY_COMMENT_WEIGHT = 0.4
        private const val POPULARITY_VIEW_WEIGHT = 0.1

        private val PAGINATION_FIELD_MAPPINGS = mapOf(
            "id" to "postId",
            "author" to "post.author.id",
            "topic" to "post.topic.id",
            "title" to "post.title",
            "content" to "post.content",
            "createdAt" to "post.createdAt",
            "updatedAt" to "post.updatedAt",
        )
    }

    override fun buildAggregation(id: String) = newAggregation(
        match(Criteria.where("_id").`is`(id)),

        lookup()
            .from("post")
            .localField("_id")
            .foreignField("_id")
            .`as`("post"),
        unwind("post"),

        // Lookup likes
        lookup()
            .from("like")
            .localField("_id")
            .foreignField("likableId")
            .`as`("likes"),

        // Lookup viewers
        lookup()
            .from("viewer")
            .localField("_id")
            .foreignField("viewableId")
            .`as`("viewers"),

        // Lookup comments
        lookup()
            .from("comment")
            .localField("_id")
            .foreignField("post")
            .`as`("comments"),

        // Project final structure with popularity score
        project()
            .and("_id").`as`("postId")
            .and("post").`as`("post")
            .and("likes.userId").`as`("likedUserIds")
            .and("viewers.userId").`as`("viewerIds")
            .and("likes").size().`as`("likeCount")
            .and("viewers").size().`as`("viewCount")
            .and("comments").size().`as`("commentCount")
            .andExpression("\$\$NOW").`as`("lastUpdated"),

        // compute popularity score
        addFields()
            .addField("popularityScore")
            .withValue(
                Document("\$add", listOf(
                    Document("\$multiply", listOf("\$likeCount", POPULARITY_LIKE_WEIGHT)),
                    Document("\$multiply", listOf("\$commentCount", POPULARITY_COMMENT_WEIGHT)),
                    Document("\$multiply", listOf("\$viewCount", POPULARITY_VIEW_WEIGHT))
                ))
            ).build(),
    )

    fun findAll(paginationRequest: PaginationRequest): PaginationSlice<PostAggregate> {
        return mongoPaginationQueryBuilder.getPage(
            PostAggregate::class,
            PAGINATION_FIELD_MAPPINGS,
            paginationRequest = paginationRequest
        )
    }

    override fun getSourceCollection() = Post::class.java
}
