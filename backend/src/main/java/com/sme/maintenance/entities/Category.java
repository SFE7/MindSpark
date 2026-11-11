package com.sme.maintenance.entities;

import org.hibernate.annotations.SoftDelete;
import org.springframework.data.annotation.CreatedDate;

import com.sme.maintenance.room.Room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor
@Entity 
@Table (name = "categories")
public class Category {
    //construtor only accepts the title of teh category and the room that it was created within
    public Category(String title,Room room){
        this.title = title;
        this.room = room;
    }


    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //for now the category has only a name, i was thinking about adding a description for categories but decided it was not necessary
    @Column(nullable = false)
    private String title;

    // each category MUST belong to room which is why optional and nullable are both set to false
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

}
