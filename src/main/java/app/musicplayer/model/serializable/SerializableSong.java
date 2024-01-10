/*
 * JavaFX Music Player. The MIT License (MIT).
 * Copyright (c) Almas Baim.
 * Copyright (c) Gerardo Prada, Michael Martin.
 * See LICENSE for details.
 */

package app.musicplayer.model.serializable;

import java.time.LocalDateTime;

/**
 * @author Almas Baim (https://github.com/AlmasB)
 */
public record SerializableSong(
        int id,
        String title,
        String artistTitle,
        String albumTitle,
        int lengthInSeconds,
        int trackNumber,
        int discNumber,
        int playCount,
        LocalDateTime playDate,
        String filePath
) { }
