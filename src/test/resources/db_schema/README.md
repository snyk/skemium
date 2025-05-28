# About the database schema

We took a free licensed database schema from https://github.com/lerocha/chinook-database,
so to have a realistic (and populated) database for testing.

# Table without Primary Key

For testing purposes, an additional table has been added: `playlist_track_no_pkey`.
This table is defined to be identical to `playlist_track`, except is missing a `PRIMARY KEY`:

```
CREATE TABLE playlist_track
(
    playlist_id INT NOT NULL,
    track_id    INT NOT NULL,
    CONSTRAINT playlist_track_pkey PRIMARY KEY (playlist_id, track_id)
);

CREATE TABLE playlist_track_no_pkey
(
    playlist_id INT NOT NULL,
    track_id    INT NOT NULL
);
```

This has been done so we can test the behavior of Avro Schema generated from a Table that has no `PRIMARY KEY`.
