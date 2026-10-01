package com.loomz.cantonese.lyrics.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LyricsDatabase_Impl extends LyricsDatabase {
  private volatile LyricsDao _lyricsDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `songs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `source` TEXT NOT NULL, `trackId` TEXT, `serverVersion` INTEGER NOT NULL, `hasLocalEdit` INTEGER NOT NULL, `preferLocal` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `serverDoc` TEXT, `localDoc` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_title` ON `songs` (`title`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist` ON `songs` (`artist`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_lastOpenedAt` ON `songs` (`lastOpenedAt`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_trackId` ON `songs` (`trackId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bf53578463180a9ddc3b05c48144f224')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `songs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSongs = new HashMap<String, TableInfo.Column>(12);
        _columnsSongs.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("artist", new TableInfo.Column("artist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("trackId", new TableInfo.Column("trackId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("serverVersion", new TableInfo.Column("serverVersion", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("hasLocalEdit", new TableInfo.Column("hasLocalEdit", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("preferLocal", new TableInfo.Column("preferLocal", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("lastOpenedAt", new TableInfo.Column("lastOpenedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("serverDoc", new TableInfo.Column("serverDoc", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSongs.put("localDoc", new TableInfo.Column("localDoc", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSongs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSongs = new HashSet<TableInfo.Index>(4);
        _indicesSongs.add(new TableInfo.Index("index_songs_title", false, Arrays.asList("title"), Arrays.asList("ASC")));
        _indicesSongs.add(new TableInfo.Index("index_songs_artist", false, Arrays.asList("artist"), Arrays.asList("ASC")));
        _indicesSongs.add(new TableInfo.Index("index_songs_lastOpenedAt", false, Arrays.asList("lastOpenedAt"), Arrays.asList("ASC")));
        _indicesSongs.add(new TableInfo.Index("index_songs_trackId", false, Arrays.asList("trackId"), Arrays.asList("ASC")));
        final TableInfo _infoSongs = new TableInfo("songs", _columnsSongs, _foreignKeysSongs, _indicesSongs);
        final TableInfo _existingSongs = TableInfo.read(db, "songs");
        if (!_infoSongs.equals(_existingSongs)) {
          return new RoomOpenHelper.ValidationResult(false, "songs(com.loomz.cantonese.lyrics.data.SongEntity).\n"
                  + " Expected:\n" + _infoSongs + "\n"
                  + " Found:\n" + _existingSongs);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "bf53578463180a9ddc3b05c48144f224", "173da1f0b4b8fb0b5ee1e7678f482430");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "songs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `songs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(LyricsDao.class, LyricsDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public LyricsDao dao() {
    if (_lyricsDao != null) {
      return _lyricsDao;
    } else {
      synchronized(this) {
        if(_lyricsDao == null) {
          _lyricsDao = new LyricsDao_Impl(this);
        }
        return _lyricsDao;
      }
    }
  }
}
