package com.sme.maintenance.idea;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class IdeaFinderService {

    private final IdeaRepository ideaRepository;

    public IdeaFinderService(IdeaRepository ideaRepository) {
        this.ideaRepository = ideaRepository;
    }

    public Idea find(UUID ideaId) {
        return ideaRepository.findById(ideaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Idea not found"
                ));
    }
}
