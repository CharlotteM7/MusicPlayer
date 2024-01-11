/*
 * JavaFX Music Player. The MIT License (MIT).
 * Copyright (c) Almas Baim.
 * Copyright (c) Gerardo Prada, Michael Martin.
 * See LICENSE for details.
 */

package app.musicplayer.model;

import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.List;

/**
 * An artist has a collection of albums.
 */
public record Artist(
        String title,
        Image image,
        List<Album> albums
) implements Comparable<Artist> {

    @Override
    public List<Album> albums() {
        return new ArrayList<>(albums);
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int compareTo(Artist other) {
        return title.compareTo(other.title);
    }

    @Override
    public String toString() {
        return getTitle();
    }
}