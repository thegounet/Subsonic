/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.daneren2005.dsub.service;

import java.io.File;
import java.io.Reader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import org.apache.http.HttpResponse;

import github.daneren2005.dsub.domain.Artist;
import github.daneren2005.dsub.domain.ArtistInfo;
import github.daneren2005.dsub.domain.ChatMessage;
import github.daneren2005.dsub.domain.Genre;
import github.daneren2005.dsub.domain.Indexes;
import github.daneren2005.dsub.domain.PodcastEpisode;
import github.daneren2005.dsub.domain.RemoteStatus;
import github.daneren2005.dsub.domain.Lyrics;
import github.daneren2005.dsub.domain.MusicDirectory;
import github.daneren2005.dsub.domain.MusicFolder;
import github.daneren2005.dsub.domain.Playlist;
import github.daneren2005.dsub.domain.PodcastChannel;
import github.daneren2005.dsub.domain.SearchCritera;
import github.daneren2005.dsub.domain.SearchResult;
import github.daneren2005.dsub.domain.Share;
import github.daneren2005.dsub.domain.User;
import github.daneren2005.dsub.util.Constants;
import github.daneren2005.dsub.util.FileUtil;
import github.daneren2005.dsub.util.ProgressListener;
import github.daneren2005.dsub.util.SilentBackgroundTask;
import github.daneren2005.dsub.util.Util;
import java.io.*;
import java.util.Comparator;
import java.util.SortedSet;

/**
 * @author Sindre Mehus
 */
public class OfflineMusicService implements MusicService {
	private static final String TAG = OfflineMusicService.class.getSimpleName();
	private static final String ERRORMSG = "Not available in offline mode";
	private static final Random random = new Random();

	@Override
	public void ping(Context context, ProgressListener progressListener) throws Exception {

	}

	@Override
    public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception {
        return true;
    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        List<Artist> artists = new ArrayList<Artist>();
        File root = FileUtil.getMusicDirectory(context);
        for (File file : FileUtil.listFiles(root)) {
            if (file.isDirectory()) {
                Artist artist = new Artist();
                artist.setId(file.getPath());
                artist.setIndex(file.getName().substring(0, 1));
                artist.setName(file.getName());
                artists.add(artist);
            }
        }
		
        Indexes indexes = new Indexes(0L, Collections.<Artist>emptyList(), artists);
		indexes.sortChildren(context);
		return indexes;
    }

    @Override
    public MusicDirectory getMusicDirectory(String id, String artistName, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        return getMusicDirectory(id, artistName, refresh, context, progressListener, false);
    }
	private MusicDirectory getMusicDirectory(String id, String artistName, boolean refresh, Context context, ProgressListener progressListener, boolean isPodcast) throws Exception {
		File dir = new File(id);
		MusicDirectory result = new MusicDirectory();
		result.setName(dir.getName());

		Set<String> names = new HashSet<String>();

		for (File file : FileUtil.listMediaFiles(dir)) {
			String name = getName(file);
			if (name != null & !names.contains(name)) {
				names.add(name);
				result.addChild(createEntry(context, file, name, true, isPodcast));
			}
		}
		result.sortChildren(Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_CUSTOM_SORT_ENABLED, true));
		return result;
	}

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	private String getName(File file) {
        String name = file.getName();
        if (file.isDirectory()) {
            return name;
        }

        if (name.endsWith(".partial") || name.contains(".partial.") || name.equals(Constants.ALBUM_ART_FILE)) {
            return null;
        }

        name = name.replace(".complete", "");
        return FileUtil.getBaseName(name);
    }

	private MusicDirectory.Entry createEntry(Context context, File file, String name) {
		return createEntry(context, file, name, true);
	}
    private MusicDirectory.Entry createEntry(Context context, File file, String name, boolean load) {
        return createEntry(context, file, name, load, false);
    }
	private MusicDirectory.Entry createEntry(Context context, File file, String name, boolean load, boolean isPodcast) {
		MusicDirectory.Entry entry;
		if(isPodcast) {
			PodcastEpisode episode = new PodcastEpisode();
			episode.setStatus("completed");
			entry = episode;
		} else {
			entry = new MusicDirectory.Entry();
		}
		entry.setDirectory(file.isDirectory());
		entry.setId(file.getPath());
		entry.setParent(file.getParent());
		entry.setSize(file.length());
		String root = FileUtil.getMusicDirectory(context).getPath();
		if(!file.getParentFile().getParentFile().getPath().equals(root)) {
			entry.setGrandParent(file.getParentFile().getParent());
		}
		entry.setPath(file.getPath().replaceFirst("^" + root + "/" , ""));
		String title = name;
		if (file.isFile()) {
			File artistFolder = file.getParentFile().getParentFile();
			File albumFolder = file.getParentFile();
			if(artistFolder.getPath().equals(root)) {
				entry.setArtist(albumFolder.getName());
			} else {
				entry.setArtist(artistFolder.getName());
			}
			entry.setAlbum(albumFolder.getName());

			int index = name.indexOf('-');
			if(index != -1) {
				try {
					entry.setTrack(Integer.parseInt(name.substring(0, index)));
					title = title.substring(index + 1);
				} catch(Exception e) {
					// Failed parseInt, just means track filled out
				}
			}

			if(load) {
				entry.loadMetadata(file);
			}
		}

		entry.setTitle(title);
		entry.setSuffix(FileUtil.getExtension(file.getName().replace(".complete", "")));

		File albumArt = FileUtil.getAlbumArtFile(context, entry);
		if (albumArt.exists()) {
			entry.setCoverArt(albumArt.getPath());
		}
		if(FileUtil.isVideoFile(file)) {
			entry.setVideo(true);
		}
		return entry;
	}

    @Override
    public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		try {
			return FileUtil.getAlbumArtBitmap(context, entry, size);
		} catch(Exception e) {
			return null;
		}
    }

	@Override
	public HttpResponse getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public String getMusicUrl(Context context, MusicDirectory.Entry song, int maxBitrate) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
    public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

	@Override
	public void startRescan(Context context, ProgressListener listener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
    public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception {
		List<Artist> artists = new ArrayList<Artist>();
		List<MusicDirectory.Entry> albums = new ArrayList<MusicDirectory.Entry>();
		List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>();
        File root = FileUtil.getMusicDirectory(context);
		int closeness = 0;
        for (File artistFile : FileUtil.listFiles(root)) {
			String artistName = artistFile.getName();
            if (artistFile.isDirectory()) {
				if((closeness = matchCriteria(criteria, artistName)) > 0) {
					Artist artist = new Artist();
					artist.setId(artistFile.getPath());
					artist.setIndex(artistFile.getName().substring(0, 1));
					artist.setName(artistName);
					artist.setCloseness(closeness);
					artists.add(artist);
				}
				
				recursiveAlbumSearch(artistName, artistFile, criteria, context, albums, songs);
            }
        }
		
		Collections.sort(artists, new Comparator<Artist>() {
			public int compare(Artist lhs, Artist rhs) {
				if(lhs.getCloseness() == rhs.getCloseness()) {
					return 0;
				}
				else if(lhs.getCloseness() > rhs.getCloseness()) {
					return -1;
				}
				else {
					return 1;
				}
			}
		});
		Collections.sort(albums, new Comparator<MusicDirectory.Entry>() {
			public int compare(MusicDirectory.Entry lhs, MusicDirectory.Entry rhs) {
				if(lhs.getCloseness() == rhs.getCloseness()) {
					return 0;
				}
				else if(lhs.getCloseness() > rhs.getCloseness()) {
					return -1;
				}
				else {
					return 1;
				}
			}
		});
		Collections.sort(songs, new Comparator<MusicDirectory.Entry>() {
			public int compare(MusicDirectory.Entry lhs, MusicDirectory.Entry rhs) {
				if(lhs.getCloseness() == rhs.getCloseness()) {
					return 0;
				}
				else if(lhs.getCloseness() > rhs.getCloseness()) {
					return -1;
				}
				else {
					return 1;
				}
			}
		});
		
		return new SearchResult(artists, albums, songs);
    }

	@Override
	public MusicDirectory getStarredList(Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	private void recursiveAlbumSearch(String artistName, File file, SearchCritera criteria, Context context, List<MusicDirectory.Entry> albums, List<MusicDirectory.Entry> songs) {
		int closeness;
		for(File albumFile : FileUtil.listMediaFiles(file)) {
			if(albumFile.isDirectory()) {
				String albumName = getName(albumFile);
				if((closeness = matchCriteria(criteria, albumName)) > 0) {
					MusicDirectory.Entry album = createEntry(context, albumFile, albumName);
					album.setArtist(artistName);
					album.setCloseness(closeness);
					albums.add(album);
				}

				for(File songFile : FileUtil.listMediaFiles(albumFile)) {
					String songName = getName(songFile);
					if(songName == null) {
						continue;
					}

					if(songFile.isDirectory()) {
						recursiveAlbumSearch(artistName, songFile, criteria, context, albums, songs);
					}
					else if((closeness = matchCriteria(criteria, songName)) > 0){
						MusicDirectory.Entry song = createEntry(context, albumFile, songName);
						song.setArtist(artistName);
						song.setAlbum(albumName);
						song.setCloseness(closeness);
						songs.add(song);
					}
				}
			}
			else {
				String songName = getName(albumFile);
				if((closeness = matchCriteria(criteria, songName)) > 0) {
					MusicDirectory.Entry song = createEntry(context, albumFile, songName);
					song.setArtist(artistName);
					song.setAlbum(songName);
					song.setCloseness(closeness);
					songs.add(song);
				}
			}
		}
	}
	private int matchCriteria(SearchCritera criteria, String name) {
		String query = criteria.getQuery().toLowerCase();
		String[] queryParts = query.split(" ");
		String[] nameParts = name.toLowerCase().split(" ");
		
		int closeness = 0;
		for(String queryPart : queryParts) {
			for(String namePart : nameParts) {
				if(namePart.equals(queryPart)) {
					closeness++;
				}
			}
		}
		
		return closeness;
	}

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        List<Playlist> playlists = new ArrayList<Playlist>();
        File root = FileUtil.getPlaylistDirectory(context);
		String lastServer = null;
		boolean removeServer = true;
        for (File folder : FileUtil.listFiles(root)) {
			if(folder.isDirectory()) {
				String server = folder.getName();
				SortedSet<File> fileList = FileUtil.listFiles(folder);
				for(File file: fileList) {
					if(FileUtil.isPlaylistFile(file)) {
						String id = file.getName();
						String filename = server + ": " + FileUtil.getBaseName(id);
						Playlist playlist = new Playlist(server, filename);
						playlists.add(playlist);
					}
				}
				
				if(!server.equals(lastServer) && fileList.size() > 0) {
					if(lastServer != null) {
						removeServer = false;
					}
					lastServer = server;
				}
			} else {
				// Delete legacy playlist files
				try {
					folder.delete();
				} catch(Exception e) {
					Log.w(TAG, "Failed to delete old playlist file: " + folder.getName());
				}
			}
        }
		
		if(removeServer) {
			for(Playlist playlist: playlists) {
				playlist.setName(playlist.getName().substring(playlist.getId().length() + 2));
			}
		}
        return playlists;
    }

    @Override
    public MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception {
		DownloadService downloadService = DownloadService.getInstance();
        if (downloadService == null) {
            return new MusicDirectory();
        }
		
        Reader reader = null;
		BufferedReader buffer = null;
		try {
			int firstIndex = name.indexOf(id);
			if(firstIndex != -1) {
				name = name.substring(id.length() + 2);
			}
			
			File playlistFile = FileUtil.getPlaylistFile(context, id, name);
			reader = new FileReader(playlistFile);
			buffer = new BufferedReader(reader);
			
			MusicDirectory playlist = new MusicDirectory();
			String line = buffer.readLine();
	    	if(!"#EXTM3U".equals(line)) return playlist;
			
			while( (line = buffer.readLine()) != null ){
				// No matter what, end file can't have .complete in it
				line = line.replace(".complete", "");
				File entryFile = new File(line);
				
				// Don't add file to playlist if it doesn't exist as cached or pinned!
				File checkFile = entryFile;
				if(!checkFile.exists()) {
					// If normal file doens't exist, check if .complete version does
					checkFile = new File(entryFile.getParent(), FileUtil.getBaseName(entryFile.getName())
						+ ".complete." + FileUtil.getExtension(entryFile.getName()));
				}
				
				String entryName = getName(entryFile);
				if(checkFile.exists() && entryName != null){
					playlist.addChild(createEntry(context, entryFile, entryName, false));
				}
			}
			
			return playlist;
		} finally {
			Util.close(buffer);
			Util.close(reader);
		}
    }

    @Override
    public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }
	
	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void addToPlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void overwritePlaylist(String id, String name, int toRemove, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

    @Override
    public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

    @Override
    public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception {
		if(!submission) {
			return;
		}

		SharedPreferences prefs = Util.getPreferences(context);
		String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);

		SharedPreferences offline = Util.getOfflineSync(context);
		int scrobbles = offline.getInt(Constants.OFFLINE_SCROBBLE_COUNT, 0);
		scrobbles++;
		SharedPreferences.Editor offlineEditor = offline.edit();
		
		if(id.indexOf(cacheLocn) != -1) {
			String scrobbleSearchCriteria = Util.parseOfflineIDSearch(context, id, cacheLocn);
			offlineEditor.putString(Constants.OFFLINE_SCROBBLE_SEARCH + scrobbles, scrobbleSearchCriteria);
			offlineEditor.remove(Constants.OFFLINE_SCROBBLE_ID + scrobbles);
		} else {
			offlineEditor.putString(Constants.OFFLINE_SCROBBLE_ID + scrobbles, id);
			offlineEditor.remove(Constants.OFFLINE_SCROBBLE_SEARCH + scrobbles);
		}
		
		offlineEditor.putLong(Constants.OFFLINE_SCROBBLE_TIME + scrobbles, System.currentTimeMillis());
		offlineEditor.putInt(Constants.OFFLINE_SCROBBLE_COUNT, scrobbles);
		offlineEditor.commit();
    }

    @Override
    public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

	@Override
	public MusicDirectory getAlbumList(String type, String extra, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public MusicDirectory getRandomSongs(int size, String artistId, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
    public String getVideoUrl(int maxBitrate, Context context, String id) {
        return null;
    }
	
	@Override
    public String getVideoStreamUrl(String format, int maxBitrate, Context context, String id) throws Exception {
		throw new OfflineException(ERRORMSG);
    }
	
	@Override
	public String getHlsUrl(String id, int bitRate, Context context) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

    @Override
    public RemoteStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

    @Override
    public RemoteStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

    @Override
    public RemoteStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

    @Override
    public RemoteStatus startJukebox(Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

    @Override
    public RemoteStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }

    @Override
    public RemoteStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
    }
	
	@Override
	public void setStarred(List<MusicDirectory.Entry> entries, List<MusicDirectory.Entry> artists, List<MusicDirectory.Entry> albums, boolean starred, ProgressListener progressListener, Context context) throws Exception {
		SharedPreferences prefs = Util.getPreferences(context);
		String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);

		SharedPreferences offline = Util.getOfflineSync(context);
		int stars = offline.getInt(Constants.OFFLINE_STAR_COUNT, 0);
		stars++;
		SharedPreferences.Editor offlineEditor = offline.edit();

		String id = entries.get(0).getId();
		if(id.indexOf(cacheLocn) != -1) {
			String searchCriteria = Util.parseOfflineIDSearch(context, id, cacheLocn);
			offlineEditor.putString(Constants.OFFLINE_STAR_SEARCH + stars, searchCriteria);
			offlineEditor.remove(Constants.OFFLINE_STAR_ID + stars);
		} else {
			offlineEditor.putString(Constants.OFFLINE_STAR_ID + stars, id);
			offlineEditor.remove(Constants.OFFLINE_STAR_SEARCH + stars);
		}
		
		offlineEditor.putBoolean(Constants.OFFLINE_STAR_SETTING + stars, starred);
		offlineEditor.putInt(Constants.OFFLINE_STAR_COUNT, stars);
		offlineEditor.commit();
	}

	@Override
	public List<Share> getShares(Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public MusicDirectory getTopTrackSongs(String artist, int size, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
    public MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
        File root = FileUtil.getMusicDirectory(context);
        List<File> children = new LinkedList<File>();
        listFilesRecursively(root, children);
        MusicDirectory result = new MusicDirectory();

        if (children.isEmpty()) {
            return result;
        }
        for (int i = 0; i < size; i++) {
            File file = children.get(random.nextInt(children.size()));
            result.addChild(createEntry(context, file, getName(file)));
        }

        return result;
    }

	@Override
	public String getCoverArtUrl(Context context, MusicDirectory.Entry entry) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public List<PodcastChannel> getPodcastChannels(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		List<PodcastChannel> channels = new ArrayList<PodcastChannel>();
		
		File dir = FileUtil.getPodcastDirectory(context);
		String line;
		for(File file: dir.listFiles()) {
			BufferedReader br = new BufferedReader(new FileReader(file));
			while ((line = br.readLine()) != null && !"".equals(line)) {
				PodcastChannel channel = new PodcastChannel();
				channel.setId(line);
				channel.setName(line);
				channel.setStatus("completed");
				
				if(FileUtil.getPodcastDirectory(context, channel).exists() && !channels.contains(channel)) {
					channels.add(channel);
				}
			}
			br.close();
		}
		
		return channels;
	}
	
	@Override
	public MusicDirectory getPodcastEpisodes(boolean refresh, String id, Context context, ProgressListener progressListener) throws Exception {
		return getMusicDirectory(FileUtil.getPodcastDirectory(context, id).getPath(), null, false, context, progressListener, true);
	}
	
	@Override
	public void refreshPodcasts(Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void createPodcastChannel(String url, Context context, ProgressListener progressListener) throws Exception{
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void deletePodcastChannel(String id, Context context, ProgressListener progressListener) throws Exception{
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void downloadPodcastEpisode(String id, Context context, ProgressListener progressListener) throws Exception{
		throw new OfflineException(ERRORMSG);
	}
	
	@Override
	public void deletePodcastEpisode(String id, String parent, ProgressListener progressListener, Context context) throws Exception{
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void setRating(MusicDirectory.Entry entry, int rating, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public MusicDirectory getBookmarks(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void createBookmark(MusicDirectory.Entry entry, int position, String comment, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void deleteBookmark(MusicDirectory.Entry entry, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public User getUser(boolean refresh, String username, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public List<User> getUsers(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void createUser(User user, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void updateUser(User user, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void deleteUser(String username, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void changeEmail(String username, String email, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public void changePassword(String username, String password, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public Bitmap getAvatar(String username, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public ArtistInfo getArtistInfo(String id, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
	public Bitmap getBitmap(String url, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		throw new OfflineException(ERRORMSG);
	}

	@Override
    public int processOfflineSyncs(final Context context, final ProgressListener progressListener) throws Exception{
		throw new OfflineException(ERRORMSG);
    }
    
    @Override
    public void setInstance(Integer instance) throws Exception{
		throw new OfflineException(ERRORMSG);
    }

    private void listFilesRecursively(File parent, List<File> children) {
        for (File file : FileUtil.listMediaFiles(parent)) {
            if (file.isFile()) {
                children.add(file);
            } else {
                listFilesRecursively(file, children);
            }
        }
    }
}
