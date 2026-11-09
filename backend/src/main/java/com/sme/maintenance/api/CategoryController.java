package com.sme.maintenance.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sme.maintenance.api.ApiModels.CategoryRequest;
import com.sme.maintenance.api.ApiModels.CategoryResponse;
import com.sme.maintenance.api.ApiModels.IdeaRequest;
import com.sme.maintenance.api.ApiModels.IdeaResponse;
import com.sme.maintenance.entities.Category;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.services.AuthenticatedUserService;
import com.sme.maintenance.services.CategoryService;

@RestController 
@RequestMapping ("/api")
public class CategoryController {
    private final AuthenticatedUserService authenticatedUserService;
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService,AuthenticatedUserService authenticatedUserService){
        this.authenticatedUserService = authenticatedUserService;
        this.categoryService = categoryService;
    }

    
    @GetMapping("/rooms/{roomId}/categories")
    public ResponseEntity<List<CategoryResponse>> list(@PathVariable UUID roomId, @AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        List<Category> categories = categoryService.list(roomId,userId);

        List<CategoryResponse> responseData = new ArrayList<CategoryResponse>();// transform the list of categories
        // into a response list object where each response only has the necessary data being the id and title

        for (Category category : categories) {
            responseData.add(new CategoryResponse(category.getId(),category.getTitle())); //loop through each category and create a 
            //category response from it
        }

        return ResponseEntity.ok(responseData);
    }

    @PostMapping("/rooms/{roomId}/categories")
    public ResponseEntity<CategoryResponse> create(@PathVariable UUID roomId, @AuthenticationPrincipal Jwt jwt,@RequestBody CategoryRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        String categoryTitle = request.title(); //get the title from the request by using the category request record

        Category category = categoryService.create(roomId, categoryTitle, userId);

        CategoryResponse responseData = new CategoryResponse(category.getId(), category.getTitle());

        return ResponseEntity.status(HttpStatus.CREATED).body(responseData); //we set the status to created (201) and reponse with the responde record
    }

    @PutMapping ("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> rename(@PathVariable Long categoryId,
         @AuthenticationPrincipal Jwt jwt,@RequestBody CategoryRequest request) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();

        String categoryTitle = request.title(); //get the title from the request by using the category request record

        Category category = categoryService.rename(categoryId, categoryTitle,userId); //update the category title

        CategoryResponse responseData = new CategoryResponse(category.getId(), category.getTitle());

        return ResponseEntity.ok(responseData); //we send back the updated category so that we can change the rename the title immediately without
        // having to send a new list all api request
    }

    @DeleteMapping  ("/categories/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Long categoryId,@AuthenticationPrincipal Jwt jwt) {

        User user = authenticatedUserService.getCurrentUser(jwt);
        Long userId = user.getId();


        categoryService.delete(categoryId, userId); //delete the category with no return

        return ResponseEntity.noContent().build(); // the delete response returns with no body
    }
}
