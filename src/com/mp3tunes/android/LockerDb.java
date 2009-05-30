/***************************************************************************
 *   Copyright (C) 2009  Casey Link <unnamedrambler@gmail.com>             *
 *   Copyright (C) 2007-2008 sibyl project http://code.google.com/p/sibyl/ *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
 ***************************************************************************/

package com.mp3tunes.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.binaryelysium.mp3tunes.api.Track;

/**
 * This class is essentially a wrapper for storing MP3tunes locker data in an
 * sqlite databse. It acts as a local cache of the metadata in a user's locker.
 * 
 * It is also used to handle the current playlist.
 * 
 */
public class LockerDb
{

    private LockerCache mCache;
    private Context mContext;
    private Locker mLocker;
    private SQLiteDatabase mDb;

    private static final String DB_NAME = "locker.dat";
    private static final int DB_VERSION = 1;

    public LockerDb( Context context, Locker locker )
    {
        // Open the database
        mDb = ( new LockerDbHelper( context, DB_NAME, null, DB_VERSION ) ).getWritableDatabase();
        if ( mDb == null )
        {
            throw new SQLiteDiskIOException( "Error creating database" );
        }

        mLocker = locker;
        mContext = context;
        long lastupdate = MP3tunesApplication.getInstance().getLastUpdate();
        mCache = new LockerCache( lastupdate, 8640000, false ); // TODO handle
        // cache timeout
        // properly
    }

    public void close()
    {
        if ( mDb != null )
            mDb.close();
    }

    public void clearDB()
    {
        mDb.delete( "track", null, null );
        mDb.delete( "album", null, null );
        mDb.delete( "artist", null, null );
        // mDb.execSQL("DELETE FROM current_playlist");
    }

    /**
     * Inserts a track into the database cache
     * @param track
     * @throws IOException
     * @throws SQLiteException
     */
    public void insertTrack( Track track ) throws IOException, SQLiteException
    {

        if ( track == null )
        {
            System.out.println( "OMG TRACK NULL" );
            return;
        }
        int artist = 0, album = 0; // = 0 -> last value, !=0 -> null or select
        // , album = false, genre = false;
        mDb.execSQL( "BEGIN TRANSACTION" );
        try
        {
            if ( track.getArtistName().length() > 0 )
            {
                ContentValues cv = new ContentValues( 2 );
                cv.put( "_id", track.getArtistId() );
                cv.put( "artist_name", track.getArtistName() );

                Cursor c = mDb.query( "artist", new String[] { "_id" }, "_id='"
                        + track.getArtistId() + "'", null, null, null, null );
                if ( c.moveToNext() )
                    artist = c.getInt( 0 );
                else
                    mDb.insert( "artist", "Unknown", cv );
                c.close();
            }
            else
            {
                artist = 1;
            }
            /*
             * TODO determine whether the fancy ContentValues means of
             * performing queries is faster than the regular rawQuery + string a
             * concatentation method.
             */
            if ( track.getAlbumTitle().length() > 0 )
            {
                ContentValues cv = new ContentValues( 2 );
                cv.put( "_id", track.getAlbumId() );
                cv.put( "album_name", track.getAlbumTitle() );
                cv.put( "artist_id", track.getArtistId() );
                Cursor c = mDb.query( "album", new String[] { "_id" }, "_id='" + track.getAlbumId()
                        + "'", null, null, null, null );
                // Cursor c =
                // mDb.rawQuery("SELECT _id FROM album WHERE _id='"+track.getAlbumId()+"'"
                // ,null);
                if ( c.moveToNext() )
                    artist = c.getInt( 0 );
                else
                    mDb.insert( "album", "Unknown", cv );
                // mDb.execSQL("INSERT INTO album(_id, album_name) VALUES("+track.getAlbumId()+", '"+track.getAlbumTitle()+"')");
                c.close();
            }
            else
            {
                album = 1;
            }

            Cursor c = mDb.query( "track", new String[] { "_id" }, "_id='" + track.getId() + "'",
                    null, null, null, null );
            if ( !c.moveToNext() )
            {
                ContentValues cv = new ContentValues( 7 );
                cv.put( "_id", track.getId() );
                cv.put( "play_url", track.getPlayUrl() );
                cv.put( "download_url", track.getDownloadUrl() );
                cv.put( "title", track.getTitle() );
                cv.put( "track", track.getNumber() );
                cv.put( "artist_name", track.getArtistName() );
                cv.put( "album_name", track.getAlbumTitle() );
                cv.put( "artist_id", track.getArtistId() );
                cv.put( "album_id", track.getAlbumId() );
                cv.put( "cover_url", track.getAlbumArt() );
                mDb.insert( "track", "Unknown", cv );
            }
            c.close();

            mDb.execSQL( "COMMIT TRANSACTION" );

        }
        catch ( SQLiteException e )
        {
            mDb.execSQL( "ROLLBACK" );
            throw e;
        }
    }
    
    /**
     * 
     * @param artist
     * @throws IOException
     * @throws SQLiteException
     */
    public void insertArtist( Artist artist) throws IOException, SQLiteException
    {
        if ( artist == null )
        {
            System.out.println( "OMG Artist NULL" );
            return;
        }
        try
        {
            if ( artist.getName().length() > 0 )
            {
                ContentValues cv = new ContentValues( 2 );
                cv.put( "_id", artist.getId() );
                cv.put( "artist_name", artist.getName() );
                cv.put( "album_count", artist.getAlbumCount() );
                cv.put( "track_count", artist.getTrackCount() );

                Cursor c = mDb.query( "artist", new String[] { "_id" }, "_id='"
                        + artist.getId() + "'", null, null, null, null );
                if ( !c.moveToNext() ) // artist doesn't exist
                    mDb.insert( "artist", "Unknown", cv );
                else // artist exists, so lets update with new data
                {
                    cv.remove( "_id" );
                    mDb.update( "artist", cv, "_id='" + artist.getId() + "'", null );
                }
                c.close();
            }
        }
        catch ( SQLiteException e )
        {
            throw e;
        }
    }
    
    public void insertAlbum( Album album) throws IOException, SQLiteException
    {
        if ( album == null )
        {
            System.out.println( "OMG Album NULL" );
            return;
        }
        try
        {
            if ( album.getName().length() > 0 )
            {
                ContentValues cv = new ContentValues( 2 );
                cv.put( "_id", album.getId() );
                cv.put( "album_name", album.getName() );
                cv.put( "artist_name", album.getArtistName() );
                cv.put( "artist_id", album.getArtistId() );
                cv.put( "year", album.getYear() );
                cv.put( "track_count", album.getTrackCount() );

                Cursor c = mDb.query( "album", new String[] { "_id" }, "_id='"
                        + album.getId() + "'", null, null, null, null );

                if ( !c.moveToNext() ) // album doesn't exist
                    mDb.insert( "album", "Unknown", cv );
                else // album exists, so lets update with new data
                {
                    cv.remove( "_id" );
                    mDb.update( "album", cv, "_id='" + album.getId() + "'", null );
                }

                c.close();
            }
        }
        catch ( SQLiteException e )
        {
            throw e;
        }
    }
    
    public void insertPlaylist( Playlist playlist) throws IOException, SQLiteException
    {
        if ( playlist == null )
        {
            System.out.println( "OMG Playlist NULL" );
            return;
        }
        try
        {
            if ( playlist.getName().length() > 0 )
            {
                ContentValues cv = new ContentValues( 2 );
                cv.put( "_id", playlist.getId() );
                cv.put( "playlist_name", playlist.getName() );
                cv.put( "file_count", playlist.getCount() );
                cv.put( "file_name", playlist.getFileName() );

                Cursor c = mDb.query( "playlist", new String[] { "_id" }, "_id='"
                        + playlist.getId() + "'", null, null, null, null );

                if ( !c.moveToNext() ) // album doesn't exist
                    mDb.insert( "playlist", "Unknown", cv );
                else // album exists, so lets update with new data
                {
                    cv.remove( "_id" );
                    mDb.update( "playlist", cv, "_id='" + playlist.getId() + "'", null );
                }

                c.close();
            }
        }
        catch ( SQLiteException e )
        {
            throw e;
        }
    }

    public Cursor getTableList( Music.Meta type )
    {
        try
        {
            switch ( type )
            {
            case TRACK:
                if ( !mCache.isCacheValid( LockerCache.TRACK ) )
                    refreshTracks();
                return queryTracks();
            case ALBUM:
                if ( !mCache.isCacheValid( LockerCache.ALBUM ) )
                    refreshAlbums();
                return queryAlbums();
            case ARTIST:
                if ( !mCache.isCacheValid( LockerCache.ARTIST ) )
                    refreshArtists();
                return queryArtists();
            case PLAYLIST:
                if ( !mCache.isCacheValid( LockerCache.PLAYLIST ) )
                    refreshPlaylists();
                return queryPlaylists();
            default:
                return null;
            }
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return null;
    }
    
    private Cursor queryPlaylists()
    {
        return mDb.query( "playlist", Music.PLAYLIST, null, null, null, null, Music.PLAYLIST[1] );   
    }
    
    private Cursor queryPlaylists( int playlist_id )
    {
        String selection = "track._id, title, artist_name, artist_id, album_name, album_id, track, play_url, download_url, cover_url";
        return mDb.rawQuery( 
                "SELECT DISTINCT " + selection + " FROM playlist " +
                "JOIN playlist_tracks ON playlist._id = playlist_tracks.playlist_id " +
                "JOIN track ON playlist_tracks.track_id = track._id " +
                "WHERE playlist_id="+playlist_id, null );
   
    }
    
    private Cursor queryArtists()
    {
        return mDb.query( "artist", Music.ARTIST, null, null,
                null, null, Music.ARTIST[1] );   
    }
    
    private Cursor queryAlbums()
    {
        return mDb.query( "album", Music.ALBUM, null, null, null, null, Music.ALBUM[Music.ALBUM_MAPPING.ALBUM_NAME] );
    }
    
    private Cursor queryAlbums( int artist_id )
    {
        return  mDb.query( "album", Music.ALBUM, "artist_id=" + artist_id,
                null, null, null, Music.ALBUM[Music.ALBUM_MAPPING.ALBUM_NAME] );  
    }
    
    private Cursor queryTracks()
    {
        return mDb.query( "track", Music.TRACK, null, null, null, null, Music.TRACK[Music.TRACK_MAPPING.TITLE] );
    }

    private Cursor queryTracks( int album_id )
    {
        return mDb.query( "track", Music.TRACK, "album_id=" + album_id, null, null, null, Music.TRACK[Music.TRACK_MAPPING.TRACKNUM] );
    }

    /**
     * 
     * @param albumId
     * @return 0 : album name, 1 : artist name, 2 : artist id. track count
     */
    public Cursor getAlbumInfo( int albumId )
    {
        // because 1 is for unknown songs
        if ( albumId == 1 )
            return null;

        return mDb.rawQuery( "SELECT album_name, artist_name, artist_id, track_count "
                + "FROM  album " + "WHERE album._id=" + albumId, null );
    }

    /**
     * 
     * @param artist_id
     * @return 0: _id 1: album_name
     */
    public Cursor getAlbumsForArtist( int artist_id )
    {
        System.out.println( "querying for albums by: " + artist_id );
        Cursor c = mDb.rawQuery( "SELECT artist_name FROM artist WHERE _id=" + artist_id, null );
        if ( !c.moveToNext() ) {
            //TODO fetch the artist?
            Log.e( "Mp3tunes", "Error artist doesnt exist" );
            return null;
        }
        c.close();
        
        c = queryAlbums( artist_id ); 
        
        if( c.getCount() > 0 )
            return c;
        else
            c.close();
        try
        {
            refreshAlbumsForArtist( artist_id );
            return queryAlbums( artist_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 
     * @param albumName
     * @return 0 : artist_name
     */
    public Cursor getArtistFromAlbum( String albumName )
    {
        return mDb.rawQuery( "SELECT DISTINCT artist_name" + " FROM artist, track, album"
                + " WHERE album_name='" + albumName + "'" + " AND album._id=track.album_id"
                + " AND track.artist_id=artist._id" + " ORDER BY artist_name", null );
    }

    /**
     * 
     * @param album_id
     * @return 0: _id 1: title 2: artist_name 3:artist_id 4:album_name
     *         5:album_id 6:track 7:play_url 8:download_url 9:cover_url
     */
    public Cursor getTracksForAlbum( int album_id )
    {
        System.out.println( "querying for tracks on album: " + album_id );
        Cursor c = mDb.rawQuery( "SELECT album_name FROM album WHERE _id=" + album_id, null );
        if ( !c.moveToNext() ) {
            //TODO fetch the album?
            Log.e( "Mp3tunes", "Error album doesnt exist" );
            return null;
        }
        c.close();
        
        c = queryTracks( album_id );

        if( c.getCount() > 0 )
            return c;
        else
            c.close();

        try
        {
            refreshTracksforAlbum( album_id );
            return queryTracks( album_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    public Cursor getTracksForPlaylist( int playlist_id )
    {
        Cursor c = mDb.rawQuery( "SELECT playlist_name FROM playlist WHERE _id=" + playlist_id, null );
        if ( !c.moveToNext() ) {
            //TODO fetch the playlist?
            Log.e( "Mp3tunes", "Error playlist doesnt exist" );
            return null;
        }
        c.close();
        c = queryPlaylists( playlist_id );

        if( c.getCount() > 0 )
            return c;
        else
            c.close();
        try
        {
            refreshTracksforPlaylist( playlist_id );
            return queryPlaylists( playlist_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    

    /**
     * return the song at pos in the playlist
     * 
     * @param pos NOTE: Positions are 1 indexed i.e., the first song is @ pos = 1
     * @return a complete Track obj of the song
     */
    public Track getTrackPlaylist( int pos )
    {
        Cursor c = mDb.query( "track, current_playlist", Music.TRACK, "current_playlist.pos=" + pos, null, null, null, null );
//        Cursor c = mDb.rawQuery("SELECT play_url FROM song, current_playlist WHERE pos="+pos+" AND song._id=current_playlist.id", null);
        if ( !c.moveToFirst() )
        {
            c.close();
            return null;
        }
        
        Track t = new Track(
                c.getInt( Music.TRACK_MAPPING.ID ),
                c.getString( Music.TRACK_MAPPING.PLAY_URL ),
                c.getString( Music.TRACK_MAPPING.DOWNLOAD_URL ),
                c.getString( Music.TRACK_MAPPING.TITLE ),
                c.getInt( Music.TRACK_MAPPING.TRACKNUM ),
                c.getInt( Music.TRACK_MAPPING.ARTIST_ID ),
                c.getString( Music.TRACK_MAPPING.ARTIST_NAME ),
                c.getInt( Music.TRACK_MAPPING.ALBUM_ID ),
                c.getString( Music.TRACK_MAPPING.ALBUM_NAME ),
                c.getString( Music.TRACK_MAPPING.COVER_URL ) );
        c.close();
        return t;
    }
    
    /**
     * insert several tracks into the playlist
     * Note: the song ids are not verified!
     * @param ids the songs ids
     */
    public void insertTracksPlaylist( int[] ids )
    {
        for( int id : ids )
        {
            insertTrackPlaylist( id );
        }
    }
    
    /**
     * insert one track into the playlist
     * Note: the song ids are not verified!
     * @param ids the song id
     */
    public void insertTrackPlaylist( int id )
    {
        mDb.execSQL("INSERT INTO current_playlist(track_id) VALUES("+id+")");
    }
    
    /**
     * Insert an entire artist into the playlist.
     * @param id the artist id
     */
    public void insertArtistPlaylist( int id )
    {
        mDb.execSQL("INSERT INTO current_playlist(track_id) " +
                "SELECT track._id FROM track " +
                "WHERE track.artist_id = " + id);
    }
    
    /**
     * Insert an entire album into the playlist.
     * @param id album id
     */
    public void insertAlbumPlaylist( int id )
    {
        mDb.execSQL("INSERT INTO current_playlist(track_id) " +
                "SELECT track._id FROM track " +
                "WHERE track.album_id = " + id);
    }
    
    /**
     * Returns the size of the current playlist
     *
     * @return  size of the playlist or -1 if an error occurs
     */
    public int getPlaylistSize()
    {
        int size = -1;
        Cursor c = mDb.rawQuery("SELECT COUNT(track_id) FROM current_playlist" ,null);
        if(c.moveToFirst())
        {
            size = c.getInt(0);
        }
        c.close();
        return size;
    }
    
    /**
     * Clear the current playlist
     */
    public void clearPlaylist()
    {
        mDb.execSQL("DELETE FROM current_playlist");
    }
    
    public void refreshTracks() throws SQLiteException, IOException
    {
        ArrayList<Track> tracks = new ArrayList<Track>( mLocker.getTracks() );
        int lim = tracks.size();
        System.out.println( "beginning insertion of " + lim + " tracks" );
        for ( int i = 0; i < lim; i++ )
        {
            insertTrack( tracks.get( i ) );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.TRACK );
    }
    
    private void refreshPlaylists()  throws SQLiteException, IOException
    {
        ArrayList<Playlist> playlists = new ArrayList<Playlist>( mLocker.getPlaylists() );
        int lim = playlists.size();
        System.out.println( "beginning insertion of " + lim + " playlists" );
        for ( int i = 0; i < lim; i++ )
        {
            insertPlaylist( playlists.get( i ) );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.PLAYLIST );
    }
    
    private void refreshArtists()  throws SQLiteException, IOException
    {
        ArrayList<Artist> artists = new ArrayList<Artist>( mLocker.getArtists() );
        int lim = artists.size();
        System.out.println( "beginning insertion of " + lim + " artists" );
        for ( int i = 0; i < lim; i++ )
        {
            insertArtist( artists.get( i ) );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.ARTIST );
    }
    
    private void refreshAlbums()  throws SQLiteException, IOException
    {
        ArrayList<Album> albums = new ArrayList<Album>( mLocker.getAlbums() );
        int lim = albums.size();
        System.out.println( "beginning insertion of " + lim + " albums" );
        for ( int i = 0; i < lim; i++ )
        {
            insertAlbum( albums.get( i ) );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.ALBUM );
    }
    
    private void refreshAlbumsForArtist(int artist_id)  throws SQLiteException, IOException
    {
        ArrayList<Album> albums = new ArrayList<Album>( mLocker.getAlbumsForArtist( artist_id ) );
        int lim = albums.size();
        System.out.println( "beginning insertion of " + lim + " albums for artist id " +artist_id );
        for ( int i = 0; i < lim; i++ )
        {
            insertAlbum( albums.get( i ) );
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshTracksforAlbum(int album_id)  throws SQLiteException, IOException
    {
        ArrayList<Track> tracks = new ArrayList<Track>( mLocker.getTracksForAlbum( album_id ));
        int lim = tracks.size();
        System.out.println( "beginning insertion of " + lim + " tracks for album id " +album_id );
        for ( int i = 0; i < lim; i++ )
        {
            insertTrack( tracks.get( i ) );
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshTracksforPlaylist(int playlist_id)  throws SQLiteException, IOException
    {
        ArrayList<Track> tracks = new ArrayList<Track>( mLocker.getTracksForPlaylist( playlist_id ));
        int lim = tracks.size();
        System.out.println( "beginning insertion of " + lim + " tracks for playlist id " +playlist_id );
        
        mDb.delete( "playlist_tracks", "playlist_id=" + playlist_id, null );
        for ( int i = 0; i < lim; i++ )
        {
            ContentValues cv = new ContentValues(); // TODO move this outside the loop?
            insertTrack( tracks.get( i ) );
            cv.put("playlist_id", playlist_id);
            cv.put( "track_id", tracks.get( i ).getId() );
            mDb.insert( "playlist_tracks", "Unknown", cv );
        }
        System.out.println( "insertion complete" );
    }

    /**
     * Manages connecting, creating, and updating the database
     */
    private class LockerDbHelper extends SQLiteOpenHelper
    {

        private Context mC;

        public LockerDbHelper( Context context, String name, CursorFactory factory, int version )
        {
            super( context, name, factory, version );
            mC = context;
        }

        @Override
        public void onCreate( SQLiteDatabase db )
        {
            db.execSQL( "CREATE TABLE track(" + "_id INTEGER PRIMARY KEY," + "play_url VARCHAR,"
                    + "download_url VARCHAR," + "title VARCHAR," + "track NUMBER(2) DEFAULT 0,"
                    + "artist_id INTEGER," + "artist_name VARCHAR," + "album_id INTEGER,"
                    + "album_name VARCHAR," + "cover_url VARCHAR DEFAULT NULL" + ")" );
            db.execSQL( "CREATE TABLE artist(" + "_id INTEGER PRIMARY KEY,"
                    + "artist_name VARCHAR," + "album_count INTEGER," + "track_count INTEGER"
                    + " )" );
            db.execSQL( "CREATE TABLE album(" + "_id INTEGER PRIMARY KEY," + "album_name VARCHAR, "
                    + "artist_id INTEGER," + "artist_name VARCHAR," + "track_count INTEGER,"
                    + "year INTEGER," + "cover_url VARCHAR DEFAULT NULL" + ")" );
            db.execSQL( "CREATE TABLE playlist(" + "_id INTEGER PRIMARY KEY," + "playlist_name VARCHAR, "
                    + "file_count INTEGER," + "file_name VARCHAR" + ")" );
            db.execSQL( "CREATE TABLE playlist_tracks(" + "playlist_id INTEGER," + "track_id INTEGER" + ")" );
            db.execSQL( "CREATE TABLE current_playlist(" + "pos INTEGER PRIMARY KEY,"
                    + "track_id INTEGER" + ")" );

        }

        @Override
        public void onUpgrade( SQLiteDatabase db, int oldV, int newV )
        {

            db.execSQL( "DROP TABLE IF EXISTS current_playlist" );
            db.execSQL( "DROP TABLE IF EXISTS album" );
            db.execSQL( "DROP TABLE IF EXISTS artist" );
            db.execSQL( "DROP TABLE IF EXISTS track" );
            db.execSQL( "DROP TABLE IF EXISTS directory" );
            onCreate( db );
        }

        public SQLiteDatabase getWritableDatabase()
        {
            return super.getWritableDatabase();

        }

    }

}
