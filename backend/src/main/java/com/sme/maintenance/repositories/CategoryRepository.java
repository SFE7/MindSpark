package com.sme.maintenance.repositories;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sme.maintenance.entities.Category;
import com.sme.maintenance.room.Room;

public interface CategoryRepository extends JpaRepository<Category,Long>{
    // find methods for a single category
    Category findByRoom(Room room);
    Category findByTitleAndRoom(String title,Room room);

    //find methods for ALL categories
    List<Category> findAllByTitle(String title);

    List<Category> findAllByRoom(Room room);
}
