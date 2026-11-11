
package com.sme.maintenance.idea;

import com.sme.maintenance.vote.VoteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sme.maintenance.api.ApiModels.IdeaFilters;
import com.sme.maintenance.entities.Category;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.room.RoomRepository;
import com.sme.maintenance.room.RoomService;

@Service
@Transactional
public class IdeaService {

    private final VoteRepository voteRepository;
    private final IdeaRepository ideaRepository;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final IdeaFinderService ideaFinderService;

    public IdeaService(
            IdeaRepository ideaRepository,
            RoomService roomService,
            RoomRepository roomRepository,
            UserRepository userRepository,
            VoteRepository voteRepository,
            IdeaFinderService ideaFinderService) {
        this.ideaRepository = ideaRepository;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
        this.ideaFinderService = ideaFinderService;
    }

    @Transactional(readOnly = true)
    public List<Idea> list(UUID roomId, Long userId,IdeaFilters filters) {
        Room room = roomService.memberRoom(roomId, userId);

        // first we apply the sort, either by vote count, created at or no sort at all
        // we get the filters variables from the idea filters records
        String sortBy = filters.sortBy();
        Long categoryId = filters.categoryId();
        String search = filters.search();

        List<Idea> sortedIdeas = sortIdeas(sortBy, room);
        List<Idea> filteredIdeas = sortedIdeas; 

        if (search!=null && !search.isBlank()){
            filteredIdeas = filterBySearch(filteredIdeas, search);
        }
        if (categoryId!=null){
            filteredIdeas = filterByCategory(filteredIdeas, categoryId);
        }
        
        return filteredIdeas;
    }
// I can create an idea in a room I belong to
    public Idea create(UUID roomId, String text, Long userId) {
        return create(
                roomId, text, text, null,
                null, null, null, userId,null
        );
    }

    public Idea create(
            UUID roomId,
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Long userId,
            Category category
        ) {

        Room room = roomService.memberRoom(roomId, userId);
        roomService.requireOpen(room);
        User author = getUser(userId);

        String effectiveTitle =
                title != null && !title.isBlank()
                        ? title.trim()
                        : (text != null ? text.trim() : "");

        if (effectiveTitle.isEmpty() || effectiveTitle.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea title or text is required"
            );
        }

        if (text != null && text.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea text is too long"
            );
        }

        if (description != null && description.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea description is too long"
            );
        }

        Idea idea = new Idea(
                room,
                author,
                text,
                effectiveTitle,
                description,
                x,
                y,
                color,
                category
        );

        return ideaRepository.save(idea);
    }

    // i can update my idea 
    public Idea update(UUID ideaId, String text, Long userId,Category category) {
        return update(
                ideaId, text, text, null,
                null, null, null, userId,category
        );
    }

    public Idea update(
            UUID ideaId,
            String text,
            String title,
            String description,
            Long userId,
            Category category
        ) {

        return update(
                ideaId, text, title, description,
                null, null, null, userId,category
        );
    }

    public Idea update(
            UUID ideaId,
            String text,
            String title,
            String description,
            Double x,
            Double y,
            String color,
            Long userId,
            Category category
        ) {

        Idea idea = find(ideaId);

        roomService.memberRoom(idea.getRoom().getId(), userId);
        roomService.requireOpen(idea.getRoom());

        String currentTitle = idea.getTitle() != null
                ? idea.getTitle()
                : (idea.getText() != null ? idea.getText() : "");

        String currentDescription = idea.getDescription() != null
                ? idea.getDescription()
                : "";

        String currentColor = idea.getColor() != null
                ? idea.getColor()
                : "";

        boolean titleChanged =
                title != null
                && !title.isBlank()
                && !title.trim().equals(currentTitle.trim());

        boolean textChanged =
                text != null
                && !text.isBlank()
                && !text.trim().equals(
                        idea.getText() != null
                                ? idea.getText().trim()
                                : ""
                );

        boolean descriptionChanged =
                description != null
                && !description.trim().equals(currentDescription.trim());

        boolean colorChanged =
                color != null
                && !color.isBlank()
                && !color.equalsIgnoreCase(currentColor);

        if (titleChanged || textChanged || descriptionChanged || colorChanged) {
            requireAuthor(idea, userId);
        }

        String effectiveTitle =
                title != null && !title.isBlank()
                        ? title.trim()
                        : (text != null && !text.isBlank()
                                ? text.trim()
                                : currentTitle);

        String effectiveText =
                text != null && !text.isBlank()
                        ? text.trim()
                        : (idea.getText() != null
                                ? idea.getText()
                                : effectiveTitle);

        String effectiveDescription =
                description != null
                        ? description.trim()
                        : currentDescription;

        if (effectiveTitle.isBlank() || effectiveTitle.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea title is required and must not exceed 200 characters"
            );
        }

        if (effectiveText.isBlank() || effectiveText.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea text is required and must not exceed 2000 characters"
            );
        }

        if (effectiveDescription.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idea description is too long"
            );
        }

        Double newX = x != null ? x : idea.getX();
        Double newY = y != null ? y : idea.getY();

        String newColor =
                color != null && !color.isBlank()
                        ? color
                        : idea.getColor();

        idea.update(
                effectiveText,
                effectiveTitle,
                effectiveDescription,
                newX,
                newY,
                newColor,
                category
        );

        return ideaRepository.save(idea);
    }
// I can delete my own ideas
    public void delete(UUID ideaId, Long userId) {
        Idea idea = find(ideaId);

        roomService.memberRoom(idea.getRoom().getId(), userId);
        requireAuthor(idea, userId);
        roomService.requireOpen(idea.getRoom());

        ideaRepository.delete(idea);
    }

    @Transactional(readOnly = true)
    public Idea find(UUID ideaId) {
        return ideaFinderService.find(ideaId);
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                ));
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required"
            );
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    private void requireAuthor(Idea idea, Long userId) {
        if (userId == null
                || idea.getAuthor() == null
                || !idea.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the author can change this idea"
            );
        }
    }

    // helper methods

    //gets the total vote count of an idea
    public int getVoteCount(Idea idea){
        return (int) voteRepository.countByIdea(idea);
    }

    // filtering and sorting methods


    //sort methods

    //there are currently 3 ways to sort which are:
    // 1. sort by createdAt date, available in the repository
    // 2. sort by vote count, requires its own method since we need to use the vote service to get each idea count
    // 3. no sort just default find all by room funcotin in the repository
    public List<Idea> sortIdeas(String sortBy,Room room){
        if (sortBy.equals("createdAt")){
             return ideaRepository.findByRoomOrderByCreatedAtAsc(room); 
        }
        else if(sortBy.equals("voteCount")){
             return sortIdeasByVoteCount(room);
        }
        else{
             return ideaRepository.findByRoom(room);
        }
    }

    public List<Idea> sortIdeasByVoteCount(Room room){
        List<Idea> unsortedIdeas = ideaRepository.findByRoom(room);
        List<Idea> sortedIdea = unsortedIdeas.stream()
        .sorted(
            (idea1,idea2)->Integer.compare(getVoteCount(idea2),getVoteCount(idea1))  // we compare idea 2 before idea 1
            // so that the result list is from the highest vote count to the lowest
        ).toList();

        return sortedIdea;
    }

    //filter methods

    //filteres by the category id
    public List<Idea> filterByCategory(List<Idea> originalIdeasList,Long categoryId){
        List<Idea> filteredIdeasList = new ArrayList<Idea>();

        //iterates through each idea and compares the category id
        for (Idea idea : originalIdeasList) {
             if (idea.getCategory()!=null){ // if the category if the idea is not null then we can access its id
             Long ideaCategoryId = idea.getCategory().getId();
             if (ideaCategoryId.equals(categoryId)){
                filteredIdeasList.add(idea);
             }
        }
        }

        return filteredIdeasList;
    }

    //filteres by the search text
    public List<Idea> filterBySearch(List<Idea> originalIdeasList,String searchText){
        List<Idea> filteredIdeasList = new ArrayList<Idea>();
        String trimmedSearchText = searchText.trim(); //we trim the serach text so we dont get bad results with white spaces

        //iterates through each idea and checks if the idea title contains the search text
        for (Idea idea : originalIdeasList) {
             String ideaTitle = idea.getTitle().trim();
             if (ideaTitle.contains(trimmedSearchText)){
                filteredIdeasList.add(idea);
             }
        }

        return filteredIdeasList;
    }

    

}
