package com.sme.maintenance.entities;

import java.sql.Date;
import java.util.List;

import org.hibernate.annotations.SoftDelete;
import org.springframework.data.annotation.CreatedDate;

import com.sme.maintenance.room.Room;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity 
@Table (name = "users")
@SoftDelete
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    private String keycloakId;


    @Column(nullable = false)
    private String username;

    @Column(nullable = false,unique = true)
    private String email;

    @CreatedDate 
    @Column(name = "created_at",nullable = false,updatable = false)
    private Date createdAt;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms;
}
