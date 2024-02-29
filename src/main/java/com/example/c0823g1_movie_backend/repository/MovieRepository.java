package com.example.c0823g1_movie_backend.repository;

import com.example.c0823g1_movie_backend.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Repository
@Transactional
public interface MovieRepository extends JpaRepository<Movie, Long> {
    @Query(value = "select id, actor, country, description, director, duration, is_deleted, name,poster, publisher, start_date, ticket_price,trailer from movie where name like :name or publisher like :publisher ;", nativeQuery = true)
    Page<Movie> searchMovieByNameAndPublisher(@Param("name") String name, @Param("publisher") String publisher, Pageable pageable);

    @Query(value = "select id, actor, country, description, director, duration, is_deleted, name,poster, publisher, start_date, ticket_price,trailer from movie where start_date  =:startDate;", nativeQuery = true)
    Page<Movie> searchMovieByStartDate(@Param("startDate") Date startDate, Pageable pageable);

    @Query(value = "delete from movie where id :=id;", nativeQuery = true)
    void deleteMovieById(@Param("id") long id);

    @Modifying
    @Query(value = "insert into movie(director,actor)" +
                   "values (:#{#movie.director},:#{#movie.actor})", nativeQuery = true)
    void createMovie(@Param("movie") Movie movie);
}
