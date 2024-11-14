package com.kogo.content.search

import com.kogo.content.lib.PaginationRequest
import com.kogo.content.lib.PaginationSlice
import com.kogo.content.storage.view.PostAggregate
import org.springframework.stereotype.Repository

@Repository
class PostSearchIndex(
    private val atlasSearchQuery: AtlasSearchQueryBuilder
) : SearchIndex<PostAggregate> {

    override fun search(
        searchText: String,
        paginationRequest: PaginationRequest,
        boost: Double?
    ): PaginationSlice<PostAggregate> {
        return atlasSearchQuery.search(
            entityClass = PostAggregate::class,
            searchIndex = getIndexName(),
            paginationRequest = paginationRequest,
            searchText = searchText,
            searchFields = getSearchFields(),
            scoreFields = listOf(
                AtlasSearchQueryBuilder.ScoreField(
                    field = "popularityScore",
                    boost = boost ?: 1.0
                ),
            )
        )
    }

    override fun getSearchFields(): List<String> = listOf(
        "post.title",
        "post.content"
    )

    override fun getIndexName(): String = "post_stat_search"
}