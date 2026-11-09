package com.sme.maintenance.services;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.entities.Category;
import com.sme.maintenance.repositories.CategoryRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.room.RoomService;

@Service 
public class CategoryService {


    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final CategoryRepository categoryRepository;

    public CategoryService(RoomService roomService,RoomRepository roomRepository,CategoryRepository categoryRepository){
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.categoryRepository = categoryRepository;
    }


    //this function checks for:
    // 1. the user is a part of the room
    // 2. the room is open for modification and not closed
    // 3. the user is the owner of the room
    // if any of those methods dont match it will throw in an exception otherwise it will return the room from the roomId
    public Room validateUserAndRoom(UUID roomId,Long userId){
        Room room = roomService.memberRoom(roomId, userId);
        roomService.requireOpen(room);
        roomService.requireOwner(room, userId);

        return room;
    }


    //finds all categories within a single room
    public List<Category> list(UUID roomId,Long userId){
        roomService.memberRoom(roomId, userId); //first we check if the user is a part of the room (only users of the room can get)
        // the list of rooms


        Room room = roomRepository.findById(roomId).get(); //finds the room by id otherwise the .get() function will throw an exception
        return categoryRepository.findAllByRoom(room);
    }

    // before we rename a category we need to do the following checks:
    // 1. the category exists by id
    // 2. user is eligable for using CRUD operations on the category by calling or validation function
    // 3. the string is not blank or null
    // 4. the new title dosent already exists inside the room
    public Category rename(Long categoryId,String newTitle,Long userId){
        Category category = getCategoryById(categoryId);

        Room room = validateUserAndRoom(category.getRoom().getId(), userId);

        if (newTitle== null || newTitle.isBlank()){
            throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, 
               "Cannot rename the category to an empty string"
            );
        }

        String trimmedNewTitle = newTitle.trim(); //after we verify that the string is not null then we can trim it

        if (!trimmedNewTitle.equalsIgnoreCase(category.getTitle().trim())){ //we add this validation so that if both the names match the if below doesnt
            //cause an error
        if (isDuplicateByTitleAndRoom(trimmedNewTitle, room)){
            throw new ResponseStatusException(
            HttpStatus.CONFLICT, 
               "a category with the title: "+trimmedNewTitle+" already exists in this room"
            );
        }
    }

        category.setTitle(trimmedNewTitle);
        return category;

    }

    //to create a room we need to validate 2 checks which are:
    // 1. we check if the string is not blank or null
    // 2. user is eligable for using CRUD operations on the category by calling or validation function
    // 3. the category title doesnt already exists
    public Category create(UUID roomId, String title, Long userId){
        if (title== null || title.isBlank()){
            throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, 
               "Cannot create the category with an empty string/null"
            );
        }
        // we trim the title so what white space dont create duplicate categories, for example
        // "business" and " business" would be the same if we didnt trim it
        String trimmedTitle = title.trim(); 

        Room room = validateUserAndRoom(roomId, userId);
        

        if (isDuplicateByTitleAndRoom(trimmedTitle, room)){
            throw new ResponseStatusException(
            HttpStatus.CONFLICT, 
               "a category with the title: "+trimmedTitle+" already exists in this room"
            );
        }

        Category category = new Category(trimmedTitle, room);
        return categoryRepository.save(category);
    }


    //checks if a category already exists in the same room with the same title to avoid duplicates, if it already exists it returns true
    // otherwise it returns false
    public boolean isDuplicateByTitleAndRoom(String title,Room room){
        List<Category> categories = categoryRepository.findAllByTitle(title); //find the list of all categories with the same title

        // goes through each category and check if it exists in the same room
        for (Category category : categories) {
            if (category.getRoom().getId().equals(room.getId())){
                return true;
            }
        }

        return false;
    }


    //find the category by id and returns it
    // if the category doesnt exists it throws a status exception
    public Category getCategoryById(Long categoryId){
        Category category = categoryRepository.findById(categoryId) //we first check if the category exists by id
        .orElseThrow(() -> new ResponseStatusException(  //if not we return an NOT FOUND http error message
                        HttpStatus.NOT_FOUND,
                        "Couldnt find category with this id: "+categoryId
                ));
        return category;
    }

    //for a category to be deleted, theere are 1 checks that needs to be confirmed which are:
    // 1. the category exists by id
    // 2. user is eligable for using CRUD operations on the category by calling or validation function
    public void delete(Long categoryId,Long userId){
        Category category = getCategoryById(categoryId);

        Room room = category.getRoom(); //we get the room this category belongs to
        
        validateUserAndRoom(room.getId(), userId);

        categoryRepository.delete(category);
    }
}
