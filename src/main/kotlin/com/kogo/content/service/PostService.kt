package com.kogo.content.service

import com.kogo.content.endpoint.model.PostDto
import com.kogo.content.endpoint.model.PostUpdate
import com.kogo.content.filehandler.FileHandler
import com.kogo.content.storage.entity.Attachment
import com.kogo.content.storage.entity.Post
import com.kogo.content.storage.entity.Topic
import com.kogo.content.storage.repository.*
import com.kogo.content.storage.entity.UserDetails
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService (
    private val repository: PostRepository,
    private val attachmentRepository: AttachmentRepository,
    private val fileHandler: FileHandler,
) {
    fun find(postId: String): Post? = repository.findByIdOrNull(postId)

    fun listPostsByTopicId(topicId: String): List<Post> = repository.findByTopicId(topicId)

    fun listPostsByAuthorId(authorId: String): List<Post> = repository.findByAuthorId(authorId)

    @Transactional
    fun create(topic: Topic, author: UserDetails, dto: PostDto): Post {
        val post = Post(
            title = dto.title,
            content = dto.content,
            topic = topic,
            author = author,
            attachments = (dto.images!! + dto.videos!!).map {
                attachmentRepository.saveFileAndReturnAttachment(it, fileHandler, attachmentRepository)
            },
            comments = emptyList()
        )
        return repository.save(post)
    }

    @Transactional
    fun update(post: Post, postUpdate: PostUpdate) : Post {
        postUpdate.title?.let { post.title = it }
        postUpdate.content?.let { post.content = it }

        val attachmentMaintainedAfterDeletion: List<Attachment> = post.attachments.filter { attachment -> attachment.id !in postUpdate.attachmentDelete!! }
        val attachmentAdded = (postUpdate.images!! + postUpdate.videos!!).map {
            attachmentRepository.saveFileAndReturnAttachment(it, fileHandler, attachmentRepository) }
        post.attachments = attachmentMaintainedAfterDeletion + attachmentAdded
        return repository.save(post)
    }

    @Transactional
    fun delete(post: Post) {
        post.attachments.forEach { attachmentRepository.delete(it) }
        repository.deleteById(post.id!!)
    }
}
