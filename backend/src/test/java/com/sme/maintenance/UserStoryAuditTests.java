package com.sme.maintenance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserStoryAuditTests.TestJwtConfiguration.class)
public class UserStoryAuditTests {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("JWT decoding is not used in MockMvc tests");
            };
        }
    }

    private RequestPostProcessor authenticatedAs(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("preferred_username", subject));
    }

    private String createRoom(String ownerSub, String name) throws Exception {
        String res = mockMvc.perform(post("/api/rooms")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id").toString();
    }

    private Long createCategory(String ownerSub, String roomId, String title) throws Exception {
        String res = mockMvc.perform(post("/api/rooms/" + roomId + "/categories")
                        .with(authenticatedAs(ownerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(res, "$.id");
        return id.longValue();
    }

    // =========================================================================
    // Story #27: Organise ideas into categories
    // =========================================================================
    @Nested
    @DisplayName("Story #27: Organise ideas into categories")
    class Story27Tests {

        @Test
        @DisplayName("27.1 Assign an idea to a category upon creation")
        void testAssignIdeaToCategoryOnCreate() throws Exception {
            String owner = "s27-user-1";
            String roomId = createRoom(owner, "Room 27.1");
            Long categoryId = createCategory(owner, roomId, "Backend");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Use Spring\",\"text\":\"Spring boot\",\"categoryId\":" + categoryId + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category.id").value(categoryId))
                    .andExpect(jsonPath("$.category.title").value("Backend"));
        }

        @Test
        @DisplayName("27.2 Move an idea to another category")
        void testMoveIdeaToAnotherCategory() throws Exception {
            String owner = "s27-user-2";
            String roomId = createRoom(owner, "Room 27.2");
            Long cat1 = createCategory(owner, roomId, "Category 1");
            Long cat2 = createCategory(owner, roomId, "Category 2");

            String ideaResp = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Movable Idea\",\"text\":\"Desc\",\"categoryId\":" + cat1 + "}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String ideaId = JsonPath.read(ideaResp, "$.id").toString();

            // Update to cat2
            mockMvc.perform(put("/api/ideas/" + ideaId)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Movable Idea\",\"text\":\"Desc\",\"categoryId\":" + cat2 + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category.id").value(cat2))
                    .andExpect(jsonPath("$.category.title").value("Category 2"));
        }

        @Test
        @DisplayName("27.3 Remove an idea from a category (set categoryId to null)")
        void testRemoveIdeaFromCategory() throws Exception {
            String owner = "s27-user-3";
            String roomId = createRoom(owner, "Room 27.3");
            Long cat = createCategory(owner, roomId, "Temporary Category");

            String ideaResp = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Categorized Idea\",\"text\":\"Desc\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String ideaId = JsonPath.read(ideaResp, "$.id").toString();

            // Remove category by updating with null categoryId
            mockMvc.perform(put("/api/ideas/" + ideaId)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Categorized Idea\",\"text\":\"Desc\",\"categoryId\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category").value(nullValue()));
        }

        @Test
        @DisplayName("27.4 Create an idea without a category (unassigned / null categoryId)")
        void testCreateIdeaWithoutCategory() throws Exception {
            String owner = "s27-user-4";
            String roomId = createRoom(owner, "Room 27.4");

            // Idea created with no categoryId
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Unassigned Idea\",\"text\":\"Desc\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.category").value(nullValue()));
        }

        @Test
        @DisplayName("27.5 List ideas of a category")
        void testListIdeasOfCategory() throws Exception {
            String owner = "s27-user-5";
            String roomId = createRoom(owner, "Room 27.5");
            Long catA = createCategory(owner, roomId, "Cat A");
            Long catB = createCategory(owner, roomId, "Cat B");

            // Post idea in catA
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea A\",\"text\":\"Desc A\",\"categoryId\":" + catA + "}"))
                    .andExpect(status().isOk());

            // Post idea in catB
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea B\",\"text\":\"Desc B\",\"categoryId\":" + catB + "}"))
                    .andExpect(status().isOk());

            // List ideas filtering by catA
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?categoryId=" + catA + "&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Idea A"))
                    .andExpect(jsonPath("$[0].category.id").value(catA));
        }

        @Test
        @DisplayName("27.6 List categories of a room")
        void testListCategoriesOfRoom() throws Exception {
            String owner = "s27-user-6";
            String roomId = createRoom(owner, "Room 27.6");
            createCategory(owner, roomId, "Alpha");
            createCategory(owner, roomId, "Beta");

            mockMvc.perform(get("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("Alpha"))
                    .andExpect(jsonPath("$[1].title").value("Beta"));
        }
    }

    // =========================================================================
    // Story #28: Create, rename and delete categories
    // =========================================================================
    @Nested
    @DisplayName("Story #28: Create, rename and delete categories")
    class Story28Tests {

        @Test
        @DisplayName("28.1 Create category successfully")
        void testCreateCategorySuccess() throws Exception {
            String owner = "s28-user-1";
            String roomId = createRoom(owner, "Room 28.1");

            mockMvc.perform(post("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"DevOps\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.title").value("DevOps"));
        }

        @Test
        @DisplayName("28.2 Rename category successfully")
        void testRenameCategorySuccess() throws Exception {
            String owner = "s28-user-2";
            String roomId = createRoom(owner, "Room 28.2");
            Long catId = createCategory(owner, roomId, "Old Name");

            mockMvc.perform(put("/api/categories/" + catId)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"New Name\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(catId))
                    .andExpect(jsonPath("$.title").value("New Name"));
        }

        @Test
        @DisplayName("28.3 Delete category successfully")
        void testDeleteCategorySuccess() throws Exception {
            String owner = "s28-user-3";
            String roomId = createRoom(owner, "Room 28.3");
            Long catId = createCategory(owner, roomId, "To Delete");

            mockMvc.perform(delete("/api/categories/" + catId)
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isNoContent());

            // Confirm category no longer listed
            mockMvc.perform(get("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("28.4 Create category with empty or blank name returns 400")
        void testCreateCategoryEmptyOrBlankName() throws Exception {
            String owner = "s28-user-4";
            String roomId = createRoom(owner, "Room 28.4");

            // Empty string
            mockMvc.perform(post("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"\"}"))
                    .andExpect(status().isBadRequest());

            // Blank string with whitespace
            mockMvc.perform(post("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("28.5 Create category with duplicate name in same room returns 409 Conflict")
        void testCreateCategoryDuplicateName() throws Exception {
            String owner = "s28-user-5";
            String roomId = createRoom(owner, "Room 28.5");
            createCategory(owner, roomId, "Marketing");

            mockMvc.perform(post("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Marketing\"}"))
                    .andExpect(status().isConflict());

            // Also test trimmed duplicate
            mockMvc.perform(post("/api/rooms/" + roomId + "/categories")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\" Marketing \"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("28.6 Rename category to empty or blank name returns 400")
        void testRenameCategoryEmptyOrBlankName() throws Exception {
            String owner = "s28-user-6";
            String roomId = createRoom(owner, "Room 28.6");
            Long catId = createCategory(owner, roomId, "Original");

            mockMvc.perform(put("/api/categories/" + catId)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(put("/api/categories/" + catId)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("28.7 Rename category to an existing duplicate name in same room returns 409 Conflict")
        void testRenameCategoryDuplicateName() throws Exception {
            String owner = "s28-user-7";
            String roomId = createRoom(owner, "Room 28.7");
            createCategory(owner, roomId, "Sales");
            Long cat2 = createCategory(owner, roomId, "Support");

            mockMvc.perform(put("/api/categories/" + cat2)
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Sales\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("28.8 What happens to ideas when their category is deleted")
        void testDeleteCategoryWithAssociatedIdeas() throws Exception {
            String owner = "s28-user-8";
            String roomId = createRoom(owner, "Room 28.8");
            Long catId = createCategory(owner, roomId, "Has Ideas");

            // Create idea belonging to this category
            String ideaResp = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Bound Idea\",\"text\":\"Some text\",\"categoryId\":" + catId + "}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String ideaId = JsonPath.read(ideaResp, "$.id").toString();

            // Attempt to delete category
            mockMvc.perform(delete("/api/categories/" + catId)
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isNoContent());

            // Check if idea still exists and its category is cleared (or whatever is expected)
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].category").value(nullValue()));
        }

        @Test
        @DisplayName("28.9 404 for missing category")
        void testMissingCategoryReturns404() throws Exception {
            String owner = "s28-user-9";
            createRoom(owner, "Room 28.9");

            mockMvc.perform(put("/api/categories/99999999")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Non-existent\"}"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(delete("/api/categories/99999999")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("28.10 404 for missing room when accessing categories")
        void testMissingRoomReturns404() throws Exception {
            String owner = "s28-user-10";
            UUID missingRoomId = UUID.randomUUID();

            mockMvc.perform(get("/api/rooms/" + missingRoomId + "/categories")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post("/api/rooms/" + missingRoomId + "/categories")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Any\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // Story #29: Search ideas by keyword
    // =========================================================================
    @Nested
    @DisplayName("Story #29: Search ideas by keyword")
    class Story29Tests {

        @Test
        @DisplayName("29.1 Keyword match in title")
        void testKeywordMatchInTitle() throws Exception {
            String owner = "s29-user-1";
            String roomId = createRoom(owner, "Room 29.1");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Implement Authentication\",\"text\":\"Details\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Design Database Schema\",\"text\":\"Details\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=Authentication&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Implement Authentication"));
        }

        @Test
        @DisplayName("29.2 Case-insensitivity in keyword search")
        void testSearchCaseInsensitivity() throws Exception {
            String owner = "s29-user-2";
            String roomId = createRoom(owner, "Room 29.2");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Docker Deployment Pipeline\",\"text\":\"Details\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            // Search with lower-case "docker"
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=docker&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Docker Deployment Pipeline"));

            // Search with mixed-case "dOcKeR"
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=dOcKeR&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("29.3 Search with no results returns empty list")
        void testSearchNoResults() throws Exception {
            String owner = "s29-user-3";
            String roomId = createRoom(owner, "Room 29.3");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea One\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=NonExistentKeyword&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("29.4 Search with empty or blank keyword returns all ideas")
        void testSearchEmptyKeyword() throws Exception {
            String owner = "s29-user-4";
            String roomId = createRoom(owner, "Room 29.4");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea A\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea B\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=   &sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("29.5 Search with special characters")
        void testSearchSpecialCharacters() throws Exception {
            String owner = "s29-user-5";
            String roomId = createRoom(owner, "Room 29.5");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Fix [BUG-101] in parser\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=[BUG-101]&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Fix [BUG-101] in parser"));
        }

        @Test
        @DisplayName("29.6 Search matches in description as well as title")
        void testSearchTitleVersusDescription() throws Exception {
            String owner = "s29-user-6";
            String roomId = createRoom(owner, "Room 29.6");
            Long cat = createCategory(owner, roomId, "Default");

            // Idea where keyword is ONLY in description, NOT in title
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Feature Alpha\",\"description\":\"Contains the secretKeyword here\",\"text\":\"Details\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=secretKeyword&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Feature Alpha"));
        }
    }

    // =========================================================================
    // Story #30: Filter and sort ideas
    // =========================================================================
    @Nested
    @DisplayName("Story #30: Filter and sort ideas")
    class Story30Tests {

        @Test
        @DisplayName("30.1 Filter by category alone")
        void testFilterByCategoryAlone() throws Exception {
            String owner = "s30-user-1";
            String roomId = createRoom(owner, "Room 30.1");
            Long catA = createCategory(owner, roomId, "Category A");
            Long catB = createCategory(owner, roomId, "Category B");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea A\",\"text\":\"Text\",\"categoryId\":" + catA + "}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Idea B\",\"text\":\"Text\",\"categoryId\":" + catB + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?categoryId=" + catA + "&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Idea A"));
        }

        @Test
        @DisplayName("30.2 Filter by search alone")
        void testFilterBySearchAlone() throws Exception {
            String owner = "s30-user-2";
            String roomId = createRoom(owner, "Room 30.2");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Apple\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Banana\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?search=Apple&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Apple"));
        }

        @Test
        @DisplayName("30.3 Combined filters: category and search")
        void testCombinedFiltersCategoryAndSearch() throws Exception {
            String owner = "s30-user-3";
            String roomId = createRoom(owner, "Room 30.3");
            Long catA = createCategory(owner, roomId, "Category A");
            Long catB = createCategory(owner, roomId, "Category B");

            // Idea with Apple in Cat A
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Apple Pie\",\"text\":\"Text\",\"categoryId\":" + catA + "}"))
                    .andExpect(status().isOk());

            // Idea with Apple in Cat B
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Apple Cider\",\"text\":\"Text\",\"categoryId\":" + catB + "}"))
                    .andExpect(status().isOk());

            // Filter for "Apple" AND Cat A
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?categoryId=" + catA + "&search=Apple&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Apple Pie"));
        }

        @Test
        @DisplayName("30.4 Sort by createdAt descending (newest first)")
        void testSortByCreatedAt() throws Exception {
            String owner = "s30-user-4";
            String roomId = createRoom(owner, "Room 30.4");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"First Idea\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            Thread.sleep(20);

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Second Idea\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("Second Idea"))
                    .andExpect(jsonPath("$[1].title").value("First Idea"));
        }

        @Test
        @DisplayName("30.5 Sort by voteCount descending")
        void testSortByVoteCount() throws Exception {
            String owner = "s30-user-5";
            String member = "s30-member-5";
            String roomId = createRoom(owner, "Room 30.5");
            Long cat = createCategory(owner, roomId, "Default");

            // Member joins room
            String roomInfo = mockMvc.perform(get("/api/rooms/" + roomId).with(authenticatedAs(owner)))
                    .andReturn().getResponse().getContentAsString();
            String code = JsonPath.read(roomInfo, "$.invitationCode").toString();
            mockMvc.perform(post("/api/rooms/join")
                    .with(authenticatedAs(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"invitationCode\":\"" + code + "\"}"))
                    .andExpect(status().isOk());

            // Idea 1 (0 votes)
            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Zero Votes\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            // Idea 2 (will have 1 vote)
            String idea2Resp = mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"One Vote\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String idea2Id = JsonPath.read(idea2Resp, "$.id").toString();

            // Vote for Idea 2
            mockMvc.perform(post("/api/ideas/" + idea2Id + "/vote").with(authenticatedAs(member)))
                    .andExpect(status().isOk());

            // Sort by voteCount -> Idea 2 first
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?sortBy=voteCount")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("One Vote"))
                    .andExpect(jsonPath("$[1].title").value("Zero Votes"));
        }

        @Test
        @DisplayName("30.6 Sort direction support (descending createdAt)")
        void testSortDirectionSupport() throws Exception {
            String owner = "s30-user-6";
            String roomId = createRoom(owner, "Room 30.6");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Earlier Idea\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Later Idea\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            // Request createdAt descending
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?sortBy=createdAt&sortDir=desc")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("Later Idea"))
                    .andExpect(jsonPath("$[1].title").value("Earlier Idea"));
        }

        @Test
        @DisplayName("30.7 Omitted sortBy parameter (should default gracefully without NullPointerException)")
        void testOmittedSortBy() throws Exception {
            String owner = "s30-user-7";
            String roomId = createRoom(owner, "Room 30.7");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Some Idea\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            // GET without ?sortBy= parameter
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Some Idea"));
        }

        @Test
        @DisplayName("30.8 Invalid sortBy parameter")
        void testInvalidSortParameter() throws Exception {
            String owner = "s30-user-8";
            String roomId = createRoom(owner, "Room 30.8");
            Long cat = createCategory(owner, roomId, "Default");

            mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Test Idea\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                    .andExpect(status().isOk());

            // Invalid sortBy string should return 200 with default ordering or 400 Bad Request, NOT a 500 error
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?sortBy=unknownField")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @Disabled("Pagination not implemented - removed upstream in 2c6a690 without implementation")
        @DisplayName("30.9 Pagination support if it exists")
        void testPaginationSupport() throws Exception {
            String owner = "s30-user-9";
            String roomId = createRoom(owner, "Room 30.9");
            Long cat = createCategory(owner, roomId, "Default");

            for (int i = 1; i <= 5; i++) {
                mockMvc.perform(post("/api/rooms/" + roomId + "/ideas")
                                .with(authenticatedAs(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Idea " + i + "\",\"text\":\"Text\",\"categoryId\":" + cat + "}"))
                        .andExpect(status().isOk());
            }

            // Test page=0&size=2
            mockMvc.perform(get("/api/rooms/" + roomId + "/ideas?page=0&size=2&sortBy=createdAt")
                            .with(authenticatedAs(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }
}
