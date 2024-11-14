package com.kogo.content.storage.view

import com.kogo.content.endpoint.`test-util`.Fixture
import com.kogo.content.exception.InvalidFieldException
import com.kogo.content.lib.PageToken
import com.kogo.content.lib.PaginationRequest
import com.kogo.content.lib.SortDirection
import com.kogo.content.storage.entity.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class PostAggregateViewTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val postAggregateView: PostAggregateView
) {
    private lateinit var user: User
    private lateinit var topic: Topic
    private lateinit var posts: List<Post>

    @BeforeEach
    fun setup() {
        // Clear collections
        mongoTemplate.dropCollection(Post::class.java)
        mongoTemplate.dropCollection(Like::class.java)
        mongoTemplate.dropCollection(Comment::class.java)
        mongoTemplate.dropCollection(Viewer::class.java)
        mongoTemplate.dropCollection("post_stats")
        mongoTemplate.dropCollection(User::class.java)
        mongoTemplate.dropCollection(Topic::class.java)

        // Create base test data
        user = Fixture.createUserFixture()
        mongoTemplate.save(user)

        topic = Fixture.createTopicFixture(owner = user)
        mongoTemplate.save(topic)

        // Create posts with timestamps in reverse order
        val now = Instant.now()
        posts = (1..5).map { i ->
            val post = Fixture.createPostFixture(topic = topic, author = user).copy(
                title = "Post Title $i",
                content = "Post Content $i",
                createdAt = now.plusSeconds(i * 100L)
            )
            mongoTemplate.save(post)

            // Add likes
            repeat(i) { j ->
                mongoTemplate.save(Like(likableId = post.id!!, userId = "user-$j"))
            }

            // Add comments
            repeat(i) {
                mongoTemplate.save(Fixture.createCommentFixture(post = post, author = user))
            }

            // Add views
            repeat(i * 2) { j ->
                mongoTemplate.save(Viewer(viewableId = post.id!!, userId = "viewer-$j"))
            }

            post
        }

        // Refresh views for all posts
        posts.forEach {
            postAggregateView.refreshView(it.id!!)
        }
    }

    @Test
    fun `should get all posts with pagination and sorting`() {
        val request = PaginationRequest(limit = 2)
            .withSort("createdAt", SortDirection.DESC)

        // First page - should get newest posts first
        val firstPage = postAggregateView.findAll(request)
        assertThat(firstPage.items).hasSize(2)
        assertThat(firstPage.nextPageToken).isNotNull
        assertThat(firstPage.items.map { it.post.title })
            .containsExactly("Post Title 5", "Post Title 4")

        // Second page
        val secondPage = postAggregateView.findAll(
            PaginationRequest(
                limit = 2,
                pageToken = firstPage.nextPageToken!!
            )
        )
        assertThat(secondPage.items).hasSize(2)
        assertThat(secondPage.items.map { it.post.title })
            .containsExactly("Post Title 3", "Post Title 2")

        // Last page
        val lastPage = postAggregateView.findAll(
            PaginationRequest(
                limit = 2,
                pageToken = secondPage.nextPageToken!!
            )
        )
        assertThat(lastPage.items).hasSize(1)
        assertThat(lastPage.nextPageToken).isNull()
        assertThat(lastPage.items.map { it.post.title })
            .containsExactly("Post Title 1")
    }

    @Test
    fun `should handle multiple filters and sorts`() {
        val request = PaginationRequest(limit = 10)
            .withFilter("topic", topic.id!!)
            .withFilter("likeCount", 3)
            .withSort("popularityScore", SortDirection.DESC)
            .withSort("createdAt", SortDirection.DESC)

        val result = postAggregateView.findAll(request)

        assertThat(result.items).isNotEmpty
        assertThat(result.items.map { it.popularityScore })
            .isSortedAccordingTo(Comparator.reverseOrder())

        // Verify secondary sort when popularity scores are equal
        val sameScorePosts = result.items.groupBy { it.popularityScore }
            .filter { it.value.size > 1 }
            .values
            .flatten()

        assertThat(sameScorePosts.map { it.post.createdAt })
            .isSortedAccordingTo(Comparator.reverseOrder())
    }

    @Test
    fun `should aggregate post stats correctly`() {
        val post = posts[4] // Last post has 5 likes, 5 comments, and 10 views
        val stats = postAggregateView.find(post.id!!)!!

        assertThat(stats.post.id).isEqualTo(post.id)
        assertThat(stats.post.title).isEqualTo("Post Title 5")
        assertThat(stats.likeCount).isEqualTo(5)
        assertThat(stats.viewCount).isEqualTo(10)
        assertThat(stats.commentCount).isEqualTo(5)
        assertThat(stats.likedUserIds).containsExactly("user-0", "user-1", "user-2", "user-3", "user-4")
        assertThat(stats.viewerIds).hasSize(10)
    }

    @Test
    fun `should throw exception for invalid field`() {
        assertThrows<InvalidFieldException> {
            postAggregateView.findAll(
                PaginationRequest(limit = 3)
                    .withFilter("invalidField", "value")
            )
        }
    }

    @Test
    fun `should handle empty results`() {
        val result = postAggregateView.findAll(
            PaginationRequest(limit = 10)
                .withFilter("topic", "non-existent-topic-id")
        )

        assertThat(result.items).isEmpty()
        assertThat(result.nextPageToken).isNull()
    }

    @Test
    fun `should encode and decode page token correctly`() {
        val initialRequest = PaginationRequest(limit = 2)
            .withFilter("topic", topic.id!!)
            .withSort("createdAt", SortDirection.DESC)

        // First page
        val firstPage = postAggregateView.findAll(initialRequest)
        assertThat(firstPage.items).hasSize(2)
        assertThat(firstPage.nextPageToken).isNotNull
        assertThat(firstPage.items.map { it.post.topic.id }).containsOnly(topic.id)
        assertThat(firstPage.items.map { it.post.title })
            .containsExactly("Post Title 5", "Post Title 4")

        // Encode and decode token
        val encodedToken = firstPage.nextPageToken?.encode()
        assertThat(encodedToken).isNotNull

        val decodedToken = PageToken.fromString(encodedToken!!)
        assertThat(decodedToken.filters).anySatisfy { filter ->
            assertThat(filter.field).isEqualTo("topic")
            assertThat(filter.value).isEqualTo(topic.id)
        }
        assertThat(decodedToken.sortFields).anySatisfy { sortField ->
            assertThat(sortField.field).isEqualTo("createdAt")
            assertThat(sortField.direction).isEqualTo(SortDirection.DESC)
        }

        // Next page using decoded token
        val nextPage = postAggregateView.findAll(
            PaginationRequest(
                limit = 2,
                pageToken = decodedToken
            )
        )

        assertThat(nextPage.items).hasSize(2)
        assertThat(nextPage.items.map { it.post.topic.id }).containsOnly(topic.id)
        assertThat(nextPage.items.map { it.post.title })
            .containsExactly("Post Title 3", "Post Title 2")
    }
}