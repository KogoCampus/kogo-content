package com.kogo.content.endpoint

import com.kogo.content.service.UserContextService
import com.kogo.content.service.PostService
import com.kogo.content.service.TopicService
import com.kogo.content.storage.entity.UserDetailsEntity
import com.kogo.content.storage.entity.TopicEntity
import com.kogo.content.util.fixture
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable Spring Security filter chain
class TopicControllerTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    @MockkBean
    lateinit var topicService: TopicService

    @MockkBean
    lateinit var postService: PostService

    @MockkBean
    lateinit var userService: UserContextService

    companion object {
        private const val TOPIC_API_BASE_URL = "/media/topics"
    }

    @Test
    fun `should return a topic by id`() {
        val topic = createTopicFixture()
        val topicId = topic.id!!
        every { topicService.find(topicId) } returns topic
        mockMvc.get(buildTopicApiUrl(topicId))
            .andExpect { status { isOk() } }
            .andExpect { content { contentType(MediaType.APPLICATION_JSON) } }
            .andExpect { jsonPath("$.data.id") { value(topicId) } }
            .andExpect { jsonPath("$.data.topicName") { value(topic.topicName) } }
    }

    @Test
    fun `should return 404 when topic is not found`() {
        val topicId = "invalid-id"
        every { topicService.find(topicId) } returns null
        mockMvc.get(buildTopicApiUrl(topicId))
            .andExpect { status { isNotFound() } }
            .andExpect { content { contentType(MediaType.APPLICATION_JSON) } }
    }

    @Test
    fun `should create a new topic`() {
        val topicOwner = createUserFixture()
        val topic = createTopicFixture()
        topic.owner = topicOwner
        every { topicService.findByTopicName(any()) } returns null
        every { userService.getCurrentUserContext() } returns topicOwner
        every { topicService.create(any(), topicOwner) } returns topic
        mockMvc.perform(
            multipart(buildTopicApiUrl())
                .part(MockPart("topicName", topic.topicName.toByteArray()))
                .part(MockPart("description", topic.description.toByteArray()))
                .part(MockPart("tags", "awefawef##$@#$@#".toByteArray()))
                .file(MockMultipartFile("profileImage", "image.jpeg", "image/jpeg", "some image".toByteArray()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with { it.method = "POST"; it })
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data.topicName").value(topic.topicName))
            .andExpect(jsonPath("$.data.description").value(topic.description))
    }

    @Test
    fun `should return 400 when topic name is not unique`() {
        val topicName = "existing-topic"
        val existingTopic = createTopicFixture()
        existingTopic.topicName = topicName
        every { topicService.findByTopicName(topicName) } returns existingTopic
        mockMvc.perform(
            multipart(buildTopicApiUrl())
                .part(MockPart("topicName", topicName.toByteArray()))
                .part(MockPart("description", "some description".toByteArray()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with { it.method = "POST"; it })
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `should update an existing topic`() {
        val topic = createTopicFixture()
        val topicId = topic.id!!
        val updatedTopic = TopicEntity(
            id = topicId,
            topicName = "updated topic name",
            description = "updated description"
        )
        every { topicService.find(topicId) } returns topic
        every { topicService.update(topic, any()) } returns updatedTopic
        mockMvc.perform(
            multipart(buildTopicApiUrl(topicId))
                .part(MockPart("topicName", updatedTopic.topicName.toByteArray()))
                .part(MockPart("description", updatedTopic.description.toByteArray()))
                .file(MockMultipartFile("profileImage", "image.jpeg", "image/jpeg", "some image".toByteArray()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with { it.method = "PUT"; it })
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data.topicName").value(updatedTopic.topicName))
            .andExpect(jsonPath("$.data.description").value(updatedTopic.description))
    }

    @Test
    fun `should return 404 when updating non-existing topic`() {
        val topicId = "invalid-id"
        every { topicService.find(topicId) } returns null
        mockMvc.perform(
            multipart(buildTopicApiUrl(topicId))
                .part(MockPart("topicName", "some topic name".toByteArray()))
                .part(MockPart("description", "some description".toByteArray()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with { it.method = "PUT"; it })
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `should delete a topic`() {
        val topic = createTopicFixture()
        val topicId = topic.id!!
        every { topicService.find(topicId) } returns topic
        every { topicService.delete(topic) } returns Unit
        mockMvc.delete(buildTopicApiUrl(topicId))
            .andExpect { status { isOk() } }
    }

    @Test
    fun `should return 404 when deleting non-existing topic`() {
        val topicId = "invalid-id"
        every { topicService.find(topicId) } returns null
        mockMvc.delete(buildTopicApiUrl(topicId))
            .andExpect { status { isNotFound() } }
    }

    private fun buildTopicApiUrl(vararg paths: String) =
        if (paths.isNotEmpty()) "$TOPIC_API_BASE_URL/" + paths.joinToString("/")
        else TOPIC_API_BASE_URL

    private fun createTopicFixture() = fixture<TopicEntity>()

    private fun createUserFixture() = fixture<UserDetailsEntity>()
}
