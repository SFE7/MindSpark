package com.mindspark.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mindspark.compose.core.network.UserDto
import com.mindspark.compose.features.rooms.model.Room
import com.mindspark.compose.features.rooms.ui.HomeScreenContent
import com.mindspark.compose.features.rooms.ui.HomeUiState
import com.mindspark.compose.ui.theme.MindSparkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleOwner = UserDto(id = 1L, username = "ilyes", email = null)
    private val sampleMember = UserDto(id = 2L, username = "sarah", email = null)

    private val sampleRooms = listOf(
        Room(
            id = "r1",
            name = "Design Sprint",
            invitationCode = "ABC12345",
            owner = sampleOwner,
            memberCount = 2,
            closed = false,
            offline = false
        ),
        Room(
            id = "r2",
            name = "Architecture Review",
            invitationCode = "XYZ98765",
            owner = sampleMember,
            memberCount = 5,
            closed = true,
            offline = false
        )
    )

    @Test
    fun showsRoomCardDetailsOwnerMemberOpenClosedAndCodes() {
        composeTestRule.setContent {
            MindSparkTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Success(sampleRooms),
                    currentUserId = 1L,
                    username = "ilyes",
                    onRefresh = {},
                    onCreate = {},
                    onJoin = {},
                    onOpenRoom = {},
                    onDeleteRoom = {},
                    onSignOut = {}
                )
            }
        }

        // Room 1 (Owner, Open)
        composeTestRule.onNodeWithText("Design Sprint").assertIsDisplayed()
        composeTestRule.onNodeWithText("ABC12345").assertIsDisplayed()
        composeTestRule.onNodeWithText("Owner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open").assertIsDisplayed()

        // Room 2 (Member, Closed)
        composeTestRule.onNodeWithText("Architecture Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("XYZ98765").assertIsDisplayed()
        composeTestRule.onNodeWithText("Member").assertIsDisplayed()
        composeTestRule.onNodeWithText("Closed").assertIsDisplayed()
    }

    @Test
    fun showsEmptyStateWhenNoRooms() {
        composeTestRule.setContent {
            MindSparkTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Success(emptyList()),
                    currentUserId = 1L,
                    username = "ilyes",
                    onRefresh = {},
                    onCreate = {},
                    onJoin = {},
                    onOpenRoom = {},
                    onDeleteRoom = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No rooms yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingCardCallsOpenRoom() {
        var openedRoomId: String? = null

        composeTestRule.setContent {
            MindSparkTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Success(sampleRooms),
                    currentUserId = 1L,
                    username = "ilyes",
                    onRefresh = {},
                    onCreate = {},
                    onJoin = {},
                    onOpenRoom = { openedRoomId = it.id },
                    onDeleteRoom = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Design Sprint").performClick()
        assertEquals("r1", openedRoomId)
    }

    @Test
    fun fabOpensChoiceDialogAndCreateCallsCallback() {
        var createdName: String? = null

        composeTestRule.setContent {
            MindSparkTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Success(sampleRooms),
                    currentUserId = 1L,
                    username = "ilyes",
                    onRefresh = {},
                    onCreate = { createdName = it },
                    onJoin = {},
                    onOpenRoom = {},
                    onDeleteRoom = {},
                    onSignOut = {}
                )
            }
        }

        // Click FAB
        composeTestRule.onNodeWithContentDescription("Add room").performClick()

        // Click "Create room" choice
        composeTestRule.onNodeWithText("Create room").performClick()

        // Type room name
        composeTestRule.onNodeWithText("Room name").performTextInput("Sprint Planning")

        // Confirm create
        composeTestRule.onNodeWithText("Create").performClick()

        assertEquals("Sprint Planning", createdName)
    }

    @Test
    fun fabOpensChoiceDialogAndJoinCallsCallback() {
        var joinedCode: String? = null

        composeTestRule.setContent {
            MindSparkTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Success(sampleRooms),
                    currentUserId = 1L,
                    username = "ilyes",
                    onRefresh = {},
                    onCreate = {},
                    onJoin = { joinedCode = it },
                    onOpenRoom = {},
                    onDeleteRoom = {},
                    onSignOut = {}
                )
            }
        }

        // Click FAB
        composeTestRule.onNodeWithContentDescription("Add room").performClick()

        // Click "Join with code" choice
        composeTestRule.onNodeWithText("Join with code").performClick()

        // Type code
        composeTestRule.onNodeWithText("Invitation code").performTextInput("NEWCODE1")

        // Confirm join
        composeTestRule.onNodeWithText("Join").performClick()

        assertEquals("NEWCODE1", joinedCode)
    }

    @Test
    fun refreshIconCallsRefresh() {
        var refreshed = false

        composeTestRule.setContent {
            MindSparkTheme {
                HomeScreenContent(
                    uiState = HomeUiState.Success(sampleRooms),
                    currentUserId = 1L,
                    username = "ilyes",
                    onRefresh = { refreshed = true },
                    onCreate = {},
                    onJoin = {},
                    onOpenRoom = {},
                    onDeleteRoom = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        assertTrue(refreshed)
    }
}
