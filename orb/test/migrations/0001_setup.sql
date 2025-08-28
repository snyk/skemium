CREATE TABLE "resource" (
    "id" uuid PRIMARY KEY NOT NULL,
    "created" timestamptz NOT NULL,
    "description" character varying NULL,
    "is_good" boolean NOT NULL,
    "is_better" boolean NULL
);
