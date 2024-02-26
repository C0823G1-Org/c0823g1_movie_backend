package com.example.c0823g1_movie_backend.repository;

import com.example.c0823g1_movie_backend.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
}
