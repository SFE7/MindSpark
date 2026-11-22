package com.mindspark.compose.features.whiteboard

import com.mindspark.compose.TestFixtures
import com.mindspark.compose.features.whiteboard.data.CategoryApi
import com.mindspark.compose.features.whiteboard.data.CategoryRepository
import com.mindspark.compose.features.whiteboard.data.CommentApi
import com.mindspark.compose.features.whiteboard.data.CommentRepository
import com.mindspark.compose.features.whiteboard.data.DrawingApi
import com.mindspark.compose.features.whiteboard.data.DrawingRepository
import com.mindspark.compose.features.whiteboard.data.DrawingRequest
import com.mindspark.compose.features.whiteboard.data.IdeaApi
import com.mindspark.compose.features.whiteboard.data.IdeaPayload
import com.mindspark.compose.features.whiteboard.data.IdeaRepository
import com.mindspark.compose.features.whiteboard.data.NetworkCategoryRepository
import com.mindspark.compose.features.whiteboard.data.NetworkCommentRepository
import com.mindspark.compose.features.whiteboard.data.NetworkDrawingRepository
import com.mindspark.compose.features.whiteboard.data.NetworkIdeaRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RepositoriesTest {

    private val server = MockWebServer()
    private lateinit var ideaRepo: IdeaRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var commentRepo: CommentRepository
    private lateinit var drawingRepo: DrawingRepository

    @Before
    fun setUp() {
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        ideaRepo = NetworkIdeaRepository(retrofit.create(IdeaApi::class.java))
        categoryRepo = NetworkCategoryRepository(retrofit.create(CategoryApi::class.java))
        commentRepo = NetworkCommentRepository(retrofit.create(CommentApi::class.java))
        drawingRepo = NetworkDrawingRepository(retrofit.create(DrawingApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun ideaListSendsQueryParameters() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        ideaRepo.list(roomId = "r1", categoryId = 10L, search = "sprint", sortBy = "voteCount")
        val req = server.takeRequest()

        assertEquals("GET", req.method)
        assertEquals("/api/rooms/r1/ideas?categoryId=10&search=sprint&sortBy=voteCount", req.path)
    }

    @Test
    fun ideaCreateUpdateDeleteVote() = runTest {
        val ideaJson = TestFixtures.fixture("idea.json")

        // Create
        server.enqueue(MockResponse().setResponseCode(201).setBody(ideaJson))
        val payload = IdeaPayload(title = "New Idea", description = "Desc", x = 100.0, y = 200.0)
        ideaRepo.create("r1", payload)
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        assertEquals("/api/rooms/r1/ideas", createReq.path)
        assertTrue(createReq.body.readUtf8().contains("\"title\":\"New Idea\""))

        // Update
        server.enqueue(MockResponse().setResponseCode(200).setBody(ideaJson))
        ideaRepo.update("i1", payload)
        val updateReq = server.takeRequest()
        assertEquals("PUT", updateReq.method)
        assertEquals("/api/ideas/i1", updateReq.path)

        // Delete
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        ideaRepo.delete("i1")
        val deleteReq = server.takeRequest()
        assertEquals("DELETE", deleteReq.method)
        assertEquals("/api/ideas/i1", deleteReq.path)

        // Vote
        server.enqueue(MockResponse().setResponseCode(200).setBody(ideaJson))
        ideaRepo.vote("i1")
        val voteReq = server.takeRequest()
        assertEquals("POST", voteReq.method)
        assertEquals("/api/ideas/i1/vote", voteReq.path)

        // Remove Vote
        server.enqueue(MockResponse().setResponseCode(200).setBody(ideaJson))
        ideaRepo.removeVote("i1")
        val removeVoteReq = server.takeRequest()
        assertEquals("DELETE", removeVoteReq.method)
        assertEquals("/api/ideas/i1/vote", removeVoteReq.path)
    }

    @Test
    fun categoryEndpoints() = runTest {
        val catJson = TestFixtures.fixture("category.json")

        // List
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$catJson]"))
        categoryRepo.list("r1")
        val listReq = server.takeRequest()
        assertEquals("GET", listReq.method)
        assertEquals("/api/rooms/r1/categories", listReq.path)

        // Create
        server.enqueue(MockResponse().setResponseCode(201).setBody(catJson))
        categoryRepo.create("r1", "Architecture")
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        assertEquals("/api/rooms/r1/categories", createReq.path)
        assertTrue(createReq.body.readUtf8().contains("\"title\":\"Architecture\""))

        // Rename
        server.enqueue(MockResponse().setResponseCode(200).setBody(catJson))
        categoryRepo.rename(10L, "Infra")
        val renameReq = server.takeRequest()
        assertEquals("PUT", renameReq.method)
        assertEquals("/api/categories/10", renameReq.path)
        assertTrue(renameReq.body.readUtf8().contains("\"title\":\"Infra\""))

        // Delete
        server.enqueue(MockResponse().setResponseCode(204).setBody(""))
        categoryRepo.delete(10L)
        val deleteReq = server.takeRequest()
        assertEquals("DELETE", deleteReq.method)
        assertEquals("/api/categories/10", deleteReq.path)
    }

    @Test
    fun commentEndpoints() = runTest {
        val commentJson = TestFixtures.fixture("comment_with_replies.json")

        // List
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$commentJson]"))
        commentRepo.list("i1")
        val listReq = server.takeRequest()
        assertEquals("GET", listReq.method)
        assertEquals("/api/ideas/i1/comments", listReq.path)

        // Add
        server.enqueue(MockResponse().setResponseCode(200).setBody(commentJson))
        commentRepo.add("i1", "New Comment")
        val addReq = server.takeRequest()
        assertEquals("POST", addReq.method)
        assertEquals("/api/ideas/i1/comments", addReq.path)
        val addBody = addReq.body.readUtf8()
        assertTrue(addBody.contains("\"content\":\"New Comment\""))
        assertTrue(addBody.contains("\"text\":\"New Comment\""))

        // Reply
        server.enqueue(MockResponse().setResponseCode(200).setBody(commentJson))
        commentRepo.reply("c1", "Reply Comment")
        val replyReq = server.takeRequest()
        assertEquals("POST", replyReq.method)
        assertEquals("/api/comments/c1/replies", replyReq.path)

        // Edit
        server.enqueue(MockResponse().setResponseCode(200).setBody(commentJson))
        commentRepo.edit("c1", "Edited Comment")
        val editReq = server.takeRequest()
        assertEquals("PUT", editReq.method)
        assertEquals("/api/comments/c1", editReq.path)

        // Delete
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        commentRepo.delete("c1")
        val deleteReq = server.takeRequest()
        assertEquals("DELETE", deleteReq.method)
        assertEquals("/api/comments/c1", deleteReq.path)
    }

    @Test
    fun drawingEndpoints() = runTest {
        val drawJson = TestFixtures.fixture("drawing_pen.json")

        // List
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$drawJson]"))
        drawingRepo.list("r1")
        val listReq = server.takeRequest()
        assertEquals("GET", listReq.method)
        assertEquals("/api/rooms/r1/drawings", listReq.path)

        // Create
        server.enqueue(MockResponse().setResponseCode(201).setBody(drawJson))
        val drawReq = DrawingRequest(
            id = "d1",
            kind = "pen",
            color = "#273b3b",
            width = 5.0f,
            opacity = 1.0f,
            payload = "[]",
            offsetX = 0.0f,
            offsetY = 0.0f
        )
        drawingRepo.create("r1", drawReq)
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        assertEquals("/api/rooms/r1/drawings", createReq.path)

        // Move
        server.enqueue(MockResponse().setResponseCode(200).setBody(drawJson))
        drawingRepo.move("r1", "d1", 10.0f, 5.0f)
        val moveReq = server.takeRequest()
        assertEquals("PUT", moveReq.method)
        assertEquals("/api/rooms/r1/drawings/d1", moveReq.path)
        val moveBody = moveReq.body.readUtf8()
        assertTrue(moveBody.contains("\"offsetX\":10.0"))
        assertTrue(moveBody.contains("\"offsetY\":5.0"))

        // Delete
        server.enqueue(MockResponse().setResponseCode(204).setBody(""))
        drawingRepo.delete("r1", "d1")
        val deleteReq = server.takeRequest()
        assertEquals("DELETE", deleteReq.method)
        assertEquals("/api/rooms/r1/drawings/d1", deleteReq.path)
    }
}
