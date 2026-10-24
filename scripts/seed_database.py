#!/usr/bin/env python3

import sys
import sqlite3
import os

def create_db(db_path, images_dir):
    if os.path.exists(db_path):
        os.remove(db_path)

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    cur.execute("""
    CREATE TABLE IF NOT EXISTS room_master_table (
        id INTEGER PRIMARY KEY,
        identity_hash TEXT
    );
    """)
    cur.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '07f8a8f63d475eeedf1ccc4a4359a3f8');")

    cur.execute("""
    CREATE TABLE IF NOT EXISTS upload_providers (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        targetUrl TEXT NOT NULL,
        fileParam TEXT NOT NULL,
        authUser TEXT,
        authPass TEXT,
        responseRegex TEXT,
        isDefault INTEGER NOT NULL
    );
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS history_entries (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        thumbnailBytes BLOB NOT NULL,
        link TEXT NOT NULL,
        timestamp INTEGER NOT NULL
    );
    """)

    cur.execute("""
    INSERT INTO upload_providers (id, name, targetUrl, fileParam, authUser, authPass, responseRegex, isDefault)
    VALUES (1, 'example.org', 'https://example.org/upload', 'file', NULL, NULL, NULL, 1);
    """)

    cur.execute("""
    INSERT INTO upload_providers (id, name, targetUrl, fileParam, authUser, authPass, responseRegex, isDefault)
    VALUES (2, 'other-example.net', 'https://other-example.net/api/upload', 'image', NULL, NULL, NULL, 0);
    """)

    def read_thumb(filename):
        path = os.path.join(images_dir, filename)
        if os.path.exists(path):
            with open(path, "rb") as f:
                return f.read()
        return b""

    thumb1 = read_thumb("thumb1.jpg")
    thumb2 = read_thumb("thumb2.jpg")
    thumb3 = read_thumb("thumb3.jpg")
    thumb4 = read_thumb("thumb4.jpg")
    thumb5 = read_thumb("thumb5.jpg")

    # 5 history entries, with entries 2, 3, 4 sharing the exact same timestamp (group-upload)
    t1 = 1718000000000
    t_group = 1718010000000
    t5 = 1718020000000

    cur.execute("INSERT INTO history_entries (id, thumbnailBytes, link, timestamp) VALUES (?, ?, ?, ?)",
                (1, thumb1, "https://example.org/photo-sunset.jpg", t1))
    cur.execute("INSERT INTO history_entries (id, thumbnailBytes, link, timestamp) VALUES (?, ?, ?, ?)",
                (2, thumb2, "https://example.org/vacation-beach1.jpg", t_group))
    cur.execute("INSERT INTO history_entries (id, thumbnailBytes, link, timestamp) VALUES (?, ?, ?, ?)",
                (3, thumb3, "https://example.org/vacation-beach2.jpg", t_group))
    cur.execute("INSERT INTO history_entries (id, thumbnailBytes, link, timestamp) VALUES (?, ?, ?, ?)",
                (4, thumb4, "https://example.org/vacation-beach3.jpg", t_group))
    cur.execute("INSERT INTO history_entries (id, thumbnailBytes, link, timestamp) VALUES (?, ?, ?, ?)",
                (5, thumb5, "https://other-example.net/document-scan.png", t5))

    conn.commit()
    cur.close()
    conn.execute("PRAGMA journal_mode = DELETE;")
    conn.close()
    print(f"Seeded database created at {db_path}")

if __name__ == "__main__":
    create_db(sys.argv[1], sys.argv[2])
