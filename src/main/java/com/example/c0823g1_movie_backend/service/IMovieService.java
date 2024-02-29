package com.example.c0823g1_movie_backend.service;

import com.example.c0823g1_movie_backend.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;

public interface IMovieService extends IGeneralService {
Page<Movie> searchMovieByNameAndPublisher(String name, String publisher, Pageable pageable);
Page<Movie> searchMovieByStartDate(Date startDate, Pageable pageable);
void deleteMovieById(long id);
}
