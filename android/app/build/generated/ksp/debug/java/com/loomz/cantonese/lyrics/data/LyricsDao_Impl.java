package com.loomz.cantonese.lyrics.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LyricsDao_Impl implements LyricsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SongEntity> __insertionAdapterOfSongEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSong;

  private final SharedSQLiteStatement __preparedStmtOfTouch;

  private final SharedSQLiteStatement __preparedStmtOfSetEditFlags;

  private final SharedSQLiteStatement __preparedStmtOfSetServerDoc;

  private final SharedSQLiteStatement __preparedStmtOfSetLocalDoc;

  private final SharedSQLiteStatement __preparedStmtOfSetServerVersion;

  public LyricsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSongEntity = new EntityInsertionAdapter<SongEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `songs` (`id`,`title`,`artist`,`source`,`trackId`,`serverVersion`,`hasLocalEdit`,`preferLocal`,`createdAt`,`lastOpenedAt`,`serverDoc`,`localDoc`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SongEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getArtist());
        statement.bindString(4, entity.getSource());
        if (entity.getTrackId() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getTrackId());
        }
        statement.bindLong(6, entity.getServerVersion());
        final int _tmp = entity.getHasLocalEdit() ? 1 : 0;
        statement.bindLong(7, _tmp);
        final int _tmp_1 = entity.getPreferLocal() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        statement.bindLong(9, entity.getCreatedAt());
        statement.bindLong(10, entity.getLastOpenedAt());
        if (entity.getServerDoc() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getServerDoc());
        }
        if (entity.getLocalDoc() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getLocalDoc());
        }
      }
    };
    this.__preparedStmtOfDeleteSong = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM songs WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfTouch = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET lastOpenedAt = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetEditFlags = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET hasLocalEdit = ?, preferLocal = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetServerDoc = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET serverDoc = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetLocalDoc = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET localDoc = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetServerVersion = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET serverVersion = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public void upsertSong(final SongEntity song) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfSongEntity.insert(song);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteSong(final String id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSong.acquire();
    int _argIndex = 1;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteSong.release(_stmt);
    }
  }

  @Override
  public void touch(final String id, final long ts) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfTouch.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, ts);
    _argIndex = 2;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfTouch.release(_stmt);
    }
  }

  @Override
  public void setEditFlags(final String id, final boolean hasLocalEdit, final boolean preferLocal) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetEditFlags.acquire();
    int _argIndex = 1;
    final int _tmp = hasLocalEdit ? 1 : 0;
    _stmt.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    final int _tmp_1 = preferLocal ? 1 : 0;
    _stmt.bindLong(_argIndex, _tmp_1);
    _argIndex = 3;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetEditFlags.release(_stmt);
    }
  }

  @Override
  public void setServerDoc(final String id, final String doc) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetServerDoc.acquire();
    int _argIndex = 1;
    if (doc == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, doc);
    }
    _argIndex = 2;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetServerDoc.release(_stmt);
    }
  }

  @Override
  public void setLocalDoc(final String id, final String doc) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetLocalDoc.acquire();
    int _argIndex = 1;
    if (doc == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, doc);
    }
    _argIndex = 2;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetLocalDoc.release(_stmt);
    }
  }

  @Override
  public void setServerVersion(final String id, final int v) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetServerVersion.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, v);
    _argIndex = 2;
    _stmt.bindString(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetServerVersion.release(_stmt);
    }
  }

  @Override
  public List<SongMeta> allSongs() {
    final String _sql = "SELECT id, title, artist, source, trackId, serverVersion, hasLocalEdit, preferLocal, createdAt, lastOpenedAt FROM songs ORDER BY createdAt, title";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfTitle = 1;
      final int _cursorIndexOfArtist = 2;
      final int _cursorIndexOfSource = 3;
      final int _cursorIndexOfTrackId = 4;
      final int _cursorIndexOfServerVersion = 5;
      final int _cursorIndexOfHasLocalEdit = 6;
      final int _cursorIndexOfPreferLocal = 7;
      final int _cursorIndexOfCreatedAt = 8;
      final int _cursorIndexOfLastOpenedAt = 9;
      final List<SongMeta> _result = new ArrayList<SongMeta>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SongMeta _item;
        final String _tmpId;
        _tmpId = _cursor.getString(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpArtist;
        _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpTrackId;
        if (_cursor.isNull(_cursorIndexOfTrackId)) {
          _tmpTrackId = null;
        } else {
          _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
        }
        final int _tmpServerVersion;
        _tmpServerVersion = _cursor.getInt(_cursorIndexOfServerVersion);
        final boolean _tmpHasLocalEdit;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfHasLocalEdit);
        _tmpHasLocalEdit = _tmp != 0;
        final boolean _tmpPreferLocal;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfPreferLocal);
        _tmpPreferLocal = _tmp_1 != 0;
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpLastOpenedAt;
        _tmpLastOpenedAt = _cursor.getLong(_cursorIndexOfLastOpenedAt);
        _item = new SongMeta(_tmpId,_tmpTitle,_tmpArtist,_tmpSource,_tmpTrackId,_tmpServerVersion,_tmpHasLocalEdit,_tmpPreferLocal,_tmpCreatedAt,_tmpLastOpenedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<SongMeta> search(final String q) {
    final String _sql = "SELECT id, title, artist, source, trackId, serverVersion, hasLocalEdit, preferLocal, createdAt, lastOpenedAt FROM songs WHERE lower(title) LIKE ? OR lower(artist) LIKE ? ORDER BY lastOpenedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, q);
    _argIndex = 2;
    _statement.bindString(_argIndex, q);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfTitle = 1;
      final int _cursorIndexOfArtist = 2;
      final int _cursorIndexOfSource = 3;
      final int _cursorIndexOfTrackId = 4;
      final int _cursorIndexOfServerVersion = 5;
      final int _cursorIndexOfHasLocalEdit = 6;
      final int _cursorIndexOfPreferLocal = 7;
      final int _cursorIndexOfCreatedAt = 8;
      final int _cursorIndexOfLastOpenedAt = 9;
      final List<SongMeta> _result = new ArrayList<SongMeta>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SongMeta _item;
        final String _tmpId;
        _tmpId = _cursor.getString(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpArtist;
        _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpTrackId;
        if (_cursor.isNull(_cursorIndexOfTrackId)) {
          _tmpTrackId = null;
        } else {
          _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
        }
        final int _tmpServerVersion;
        _tmpServerVersion = _cursor.getInt(_cursorIndexOfServerVersion);
        final boolean _tmpHasLocalEdit;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfHasLocalEdit);
        _tmpHasLocalEdit = _tmp != 0;
        final boolean _tmpPreferLocal;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfPreferLocal);
        _tmpPreferLocal = _tmp_1 != 0;
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpLastOpenedAt;
        _tmpLastOpenedAt = _cursor.getLong(_cursorIndexOfLastOpenedAt);
        _item = new SongMeta(_tmpId,_tmpTitle,_tmpArtist,_tmpSource,_tmpTrackId,_tmpServerVersion,_tmpHasLocalEdit,_tmpPreferLocal,_tmpCreatedAt,_tmpLastOpenedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<SongMeta> recent(final int n) {
    final String _sql = "SELECT id, title, artist, source, trackId, serverVersion, hasLocalEdit, preferLocal, createdAt, lastOpenedAt FROM songs ORDER BY lastOpenedAt DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, n);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfTitle = 1;
      final int _cursorIndexOfArtist = 2;
      final int _cursorIndexOfSource = 3;
      final int _cursorIndexOfTrackId = 4;
      final int _cursorIndexOfServerVersion = 5;
      final int _cursorIndexOfHasLocalEdit = 6;
      final int _cursorIndexOfPreferLocal = 7;
      final int _cursorIndexOfCreatedAt = 8;
      final int _cursorIndexOfLastOpenedAt = 9;
      final List<SongMeta> _result = new ArrayList<SongMeta>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SongMeta _item;
        final String _tmpId;
        _tmpId = _cursor.getString(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpArtist;
        _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpTrackId;
        if (_cursor.isNull(_cursorIndexOfTrackId)) {
          _tmpTrackId = null;
        } else {
          _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
        }
        final int _tmpServerVersion;
        _tmpServerVersion = _cursor.getInt(_cursorIndexOfServerVersion);
        final boolean _tmpHasLocalEdit;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfHasLocalEdit);
        _tmpHasLocalEdit = _tmp != 0;
        final boolean _tmpPreferLocal;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfPreferLocal);
        _tmpPreferLocal = _tmp_1 != 0;
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpLastOpenedAt;
        _tmpLastOpenedAt = _cursor.getLong(_cursorIndexOfLastOpenedAt);
        _item = new SongMeta(_tmpId,_tmpTitle,_tmpArtist,_tmpSource,_tmpTrackId,_tmpServerVersion,_tmpHasLocalEdit,_tmpPreferLocal,_tmpCreatedAt,_tmpLastOpenedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public SongEntity byId(final String id) {
    final String _sql = "SELECT * FROM songs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
      final int _cursorIndexOfServerVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "serverVersion");
      final int _cursorIndexOfHasLocalEdit = CursorUtil.getColumnIndexOrThrow(_cursor, "hasLocalEdit");
      final int _cursorIndexOfPreferLocal = CursorUtil.getColumnIndexOrThrow(_cursor, "preferLocal");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfLastOpenedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastOpenedAt");
      final int _cursorIndexOfServerDoc = CursorUtil.getColumnIndexOrThrow(_cursor, "serverDoc");
      final int _cursorIndexOfLocalDoc = CursorUtil.getColumnIndexOrThrow(_cursor, "localDoc");
      final SongEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpId;
        _tmpId = _cursor.getString(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpArtist;
        _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
        final String _tmpSource;
        _tmpSource = _cursor.getString(_cursorIndexOfSource);
        final String _tmpTrackId;
        if (_cursor.isNull(_cursorIndexOfTrackId)) {
          _tmpTrackId = null;
        } else {
          _tmpTrackId = _cursor.getString(_cursorIndexOfTrackId);
        }
        final int _tmpServerVersion;
        _tmpServerVersion = _cursor.getInt(_cursorIndexOfServerVersion);
        final boolean _tmpHasLocalEdit;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfHasLocalEdit);
        _tmpHasLocalEdit = _tmp != 0;
        final boolean _tmpPreferLocal;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfPreferLocal);
        _tmpPreferLocal = _tmp_1 != 0;
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpLastOpenedAt;
        _tmpLastOpenedAt = _cursor.getLong(_cursorIndexOfLastOpenedAt);
        final String _tmpServerDoc;
        if (_cursor.isNull(_cursorIndexOfServerDoc)) {
          _tmpServerDoc = null;
        } else {
          _tmpServerDoc = _cursor.getString(_cursorIndexOfServerDoc);
        }
        final String _tmpLocalDoc;
        if (_cursor.isNull(_cursorIndexOfLocalDoc)) {
          _tmpLocalDoc = null;
        } else {
          _tmpLocalDoc = _cursor.getString(_cursorIndexOfLocalDoc);
        }
        _result = new SongEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpSource,_tmpTrackId,_tmpServerVersion,_tmpHasLocalEdit,_tmpPreferLocal,_tmpCreatedAt,_tmpLastOpenedAt,_tmpServerDoc,_tmpLocalDoc);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public String byTrackId(final String trackId) {
    final String _sql = "SELECT id FROM songs WHERE trackId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, trackId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final String _result;
      if (_cursor.moveToFirst()) {
        if (_cursor.isNull(0)) {
          _result = null;
        } else {
          _result = _cursor.getString(0);
        }
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public String byTitleArtist(final String title, final String artist) {
    final String _sql = "SELECT id FROM songs WHERE title = ? AND artist = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, title);
    _argIndex = 2;
    _statement.bindString(_argIndex, artist);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final String _result;
      if (_cursor.moveToFirst()) {
        if (_cursor.isNull(0)) {
          _result = null;
        } else {
          _result = _cursor.getString(0);
        }
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
