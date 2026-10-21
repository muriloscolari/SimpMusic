package com.maxrave.simpmusic.extension

import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.data.model.simpconnect.TrackSummaryDomain

/**
 * Turns a track summary synced by the controlled device into the normal [Track] model so remote
 * queue rows use exactly the same list items, artwork and sheets as local ones.
 */
fun TrackSummaryDomain.toQueueTrack(): Track =
    Track(
        album = album?.takeIf { it.isNotBlank() }?.let { Album(id = "", name = it) },
        artists = listOf(Artist(id = null, name = artist)),
        duration = null,
        durationSeconds = (durationMs / 1000L).toInt().takeIf { it > 0 },
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails =
            artworkUri?.takeIf { it.isNotBlank() }?.let {
                listOf(Thumbnail(url = it, width = 500, height = 500))
            },
        title = title,
        videoId = videoId,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
    )
