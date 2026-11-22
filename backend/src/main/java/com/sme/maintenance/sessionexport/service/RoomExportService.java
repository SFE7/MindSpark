package com.sme.maintenance.sessionexport.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sme.maintenance.comment.Comment;
import com.sme.maintenance.comment.CommentRepository;
import com.sme.maintenance.entities.Category;
import com.sme.maintenance.entities.User;
import com.sme.maintenance.idea.Idea;
import com.sme.maintenance.idea.IdeaRepository;
import com.sme.maintenance.repositories.CategoryRepository;
import com.sme.maintenance.repositories.UserRepository;
import com.sme.maintenance.room.Room;
import com.sme.maintenance.sessionexport.dto.CategoryExportDto;
import com.sme.maintenance.sessionexport.dto.CommentExportDto;
import com.sme.maintenance.sessionexport.dto.IdeaExportDto;
import com.sme.maintenance.sessionexport.dto.ParticipantExportDto;
import com.sme.maintenance.sessionexport.dto.RoomExportDataResponse;
import com.sme.maintenance.vote.VoteRepository;

@Service
@Transactional(readOnly = true)
public class RoomExportService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;

    public RoomExportService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            IdeaRepository ideaRepository,
            VoteRepository voteRepository,
            CommentRepository commentRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.ideaRepository = ideaRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
    }

    public RoomExportDataResponse exportRoomData(Room room) {
        User owner = room.getOwner();
        String ownerKeycloakId = owner != null ? owner.getKeycloakId() : "";
        String ownerName = owner != null ? owner.getUsername() : "";

        // Participants: owner + members
        Set<Long> memberIds = new LinkedHashSet<>();
        if (owner != null && owner.getId() != null) {
            memberIds.add(owner.getId());
        }
        if (room.getMemberIds() != null) {
            memberIds.addAll(room.getMemberIds());
        }

        List<User> members = userRepository.findAllById(memberIds);
        Map<Long, User> userMap = members.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (u1, u2) -> u1, LinkedHashMap::new));

        List<ParticipantExportDto> participants = new ArrayList<>();
        // Owner first
        if (owner != null) {
            participants.add(new ParticipantExportDto(
                    owner.getKeycloakId(),
                    owner.getUsername(),
                    "OWNER"
            ));
        }

        // Other members
        for (User user : userMap.values()) {
            if (owner != null && owner.getId().equals(user.getId())) {
                continue;
            }
            participants.add(new ParticipantExportDto(
                    user.getKeycloakId(),
                    user.getUsername(),
                    "MEMBER"
            ));
        }

        // Categories
        List<Category> categoryEntities = categoryRepository.findAllByRoom(room);
        List<CategoryExportDto> categories = categoryEntities.stream()
                .map(c -> new CategoryExportDto(c.getId(), c.getTitle(), null))
                .toList();

        // Ideas
        List<Idea> ideaEntities = ideaRepository.findByRoom(room);
        List<IdeaExportDto> ideas = new ArrayList<>();

        for (Idea idea : ideaEntities) {
            int voteCount = (int) voteRepository.countByIdea(idea);
            List<Comment> ideaComments = commentRepository.findByIdeaOrderByCreatedAtAsc(idea);
            List<CommentExportDto> commentDtos = ideaComments.stream()
                    .map(c -> new CommentExportDto(
                            c.getId(),
                            c.getAuthor() != null ? c.getAuthor().getUsername() : "Anonymous",
                            c.getContent(),
                            c.getParentComment() != null ? c.getParentComment().getId() : null,
                            c.getCreatedAt()
                    ))
                    .toList();

            ideas.add(new IdeaExportDto(
                    idea.getId(),
                    idea.getTitle(),
                    idea.getText(),
                    idea.getCategory() != null ? idea.getCategory().getId() : null,
                    idea.getCategory() != null ? idea.getCategory().getTitle() : null,
                    idea.getAuthor() != null ? idea.getAuthor().getUsername() : "Unknown",
                    idea.getAuthor() != null ? idea.getAuthor().getKeycloakId() : null,
                    voteCount,
                    idea.getCreatedAt(),
                    commentDtos
            ));
        }

        // Sort ideas: voteCount DESC, createdAt ASC
        ideas.sort(Comparator.comparingInt(IdeaExportDto::voteCount).reversed()
                .thenComparing(IdeaExportDto::createdAt));

        return new RoomExportDataResponse(
                room.getId(),
                room.getName(),
                room.getInvitationCode(),
                room.isClosed(),
                room.getCreatedAt(),
                ownerKeycloakId,
                ownerName,
                participants,
                categories,
                ideas
        );
    }
}
