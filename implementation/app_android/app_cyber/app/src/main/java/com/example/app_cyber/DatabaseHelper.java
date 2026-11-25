package com.example.app_cyber;



import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
/*
 *@brief DatabaseHelper class for managing the SQLite database.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "telemetry_sensors.db";
    private static final int DATABASE_VERSION = 4; // update to the 4 version for the new tables

    // table 1: Sensore Temperatura DS18B20
    public static final String TABLE_DS18B20 = "sensor_ds18b20";
    public static final String COL_DS_ID = "id";
    public static final String COL_DS_TIMESTAMP = "timestamp_ms";
    public static final String COL_DS_TEMP = "temperature";
    public static final String COL_DS_CREATED_AT = "created_at";

    // Table 2: Sensore Inerziale GY-521 (MPU6050)
    public static final String TABLE_GY521 = "sensor_gy521";
    public static final String COL_GY_ID = "id";
    public static final String COL_GY_TIMESTAMP = "timestamp_ms";
    public static final String COL_GY_ACCEL_X = "accel_x";
    public static final String COL_GY_ACCEL_Y = "accel_y";
    public static final String COL_GY_ACCEL_Z = "accel_z";
    public static final String COL_GY_CREATED_AT = "created_at";
    /*
     *@brief SQLiteDatabase instance for writing data to the database.
     */
    private SQLiteDatabase writableDb;
    /*
     *@brief Constructor for the DatabaseHelper class.
     */
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    /*
     *@brief Called when the database is created for the first time.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Creazione Tabella DS18B20
        String CREATE_DS_TABLE = "CREATE TABLE " + TABLE_DS18B20 + " (" +
                COL_DS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DS_TIMESTAMP + " INTEGER, " +
                COL_DS_TEMP + " REAL, " +
                COL_DS_CREATED_AT + " TEXT" +
                ")";
        db.execSQL(CREATE_DS_TABLE);
        db.execSQL("CREATE INDEX idx_ds_created_at ON " + TABLE_DS18B20 + " (" + COL_DS_CREATED_AT + ")");

        // Create Table GY-521
        String CREATE_GY_TABLE = "CREATE TABLE " + TABLE_GY521 + " (" +
                COL_GY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_GY_TIMESTAMP + " INTEGER, " +
                COL_GY_ACCEL_X + " REAL, " +
                COL_GY_ACCEL_Y + " REAL, " +
                COL_GY_ACCEL_Z + " REAL, " +
                COL_GY_CREATED_AT + " TEXT" +
                ")";
        db.execSQL(CREATE_GY_TABLE);
        db.execSQL("CREATE INDEX idx_gy_created_at ON " + TABLE_GY521 + " (" + COL_GY_CREATED_AT + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DS18B20);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_GY521);
        db.execSQL("DROP TABLE IF EXISTS telemetry"); // Cancel out the old telemetry table if another one exist
        onCreate(db);
    }

    /*
     *@brief Inserts a new record into the DS18B20 table.
     */
    public synchronized void insertDs18b20(long timestampMs, float temp, String createdAt) {
        if (writableDb == null || !writableDb.isOpen()) {
            writableDb = this.getWritableDatabase();
        }

        ContentValues values = new ContentValues();
        values.put(COL_DS_TIMESTAMP, timestampMs);
        values.put(COL_DS_TEMP, temp);
        values.put(COL_DS_CREATED_AT, createdAt);

        writableDb.insert(TABLE_DS18B20, null, values);
    }

    /*
     *@brief Inserts a new record into the GY521 table.
     */
    public synchronized void insertGy521(long timestampMs, float ax, float ay, float az, String createdAt) {
        if (writableDb == null || !writableDb.isOpen()) {
            writableDb = this.getWritableDatabase();
        }

        ContentValues values = new ContentValues();
        values.put(COL_GY_TIMESTAMP, timestampMs);
        values.put(COL_GY_ACCEL_X, ax);
        values.put(COL_GY_ACCEL_Y, ay);
        values.put(COL_GY_ACCEL_Z, az);
        values.put(COL_GY_CREATED_AT, createdAt);

        writableDb.insert(TABLE_GY521, null, values);
    }
    // History search methods for DS18B20
    public List<Ds18b20Record> getDs18b20ByDate(String dateYMD) {
        List<Ds18b20Record> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_DS18B20 + " WHERE " + COL_DS_CREATED_AT + " LIKE ? ORDER BY " + COL_DS_ID + " ASC";
        Cursor cursor = db.rawQuery(query, new String[]{dateYMD + "%"});

        if (cursor.moveToFirst()) {
            do {
                Ds18b20Record record = new Ds18b20Record(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_DS_TIMESTAMP)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(COL_DS_TEMP)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_DS_CREATED_AT))
                );
                records.add(record);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return records;
    }

    // History search methods for GY521
    public List<Gy521Record> getGy521ByDate(String dateYMD) {
        List<Gy521Record> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_GY521 + " WHERE " + COL_GY_CREATED_AT + " LIKE ? ORDER BY " + COL_GY_ID + " ASC";
        Cursor cursor = db.rawQuery(query, new String[]{dateYMD + "%"});

        if (cursor.moveToFirst()) {
            do {
                Gy521Record record = new Gy521Record(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_GY_TIMESTAMP)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(COL_GY_ACCEL_X)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(COL_GY_ACCEL_Y)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(COL_GY_ACCEL_Z)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_GY_CREATED_AT))
                );
                records.add(record);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return records;
    }
    /*
     *@brief Closes the database connection.
     */
    @Override
    public synchronized void close() {
        if (writableDb != null && writableDb.isOpen()) {
            writableDb.close();
            writableDb = null;
        }
        super.close();
    }
}