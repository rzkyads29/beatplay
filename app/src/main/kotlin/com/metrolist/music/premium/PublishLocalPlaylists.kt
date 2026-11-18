/**
 * Publishes the account's local playlists to the BeatPlay server.
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Local playlists are private to the device/account database. Publishing uploads a snapshot
 * (name, cover, songs) so other BeatPlay users can see the playlists of this account and like
 * or play them. Re-publishing replaces the previous snapshot for the account on the server.
 */

package com.metrolist.music.premium

import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.DatabaseDao
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Uploads every local playlist of the active account and returns how many were published.
 * Only playlists the user can edit are uploaded; downloaded/auto playlists are skipped.
 */
suspend fun publishLocalPlaylists(
    databaseDao: DatabaseDao,
    api: PremiumApi,
    serverUrl: String,
    token: String,
): Int {
    val playlists = databaseDao.playlists(PlaylistSortType.CREATE_DATE, descending = true).first()
    val editable = playlists.filter { it.playlist.isEditable }

    val payload =
        buildJsonArray {
            editable.forEach { playlist ->
                val songs = databaseDao.playlistSongs(playlist.playlist.id).first()
                addJsonObject {
                    put("id", playlist.playlist.id)
                    put("name", playlist.playlist.name)
                    put("cover", playlist.thumbnails.firstOrNull().orEmpty())
                    putJsonArray("songs") {
                        songs.forEach { entry ->
                            val song = entry.song.song
                            addJsonObject {
                                put("id", song.id)
                                put("title", song.title)
                                put("artist", entry.song.artists.joinToString(", ") { it.name })
                                put("thumbnail", song.thumbnailUrl.orEmpty())
                                put("duration", song.duration)
                            }
                        }
                    }
                }
            }
        }

    return api.syncPlaylists(serverUrl, token, payload.toString())
}
