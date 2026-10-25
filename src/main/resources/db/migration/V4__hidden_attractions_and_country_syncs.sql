-- Attractions hidden by the admin: not shown on the site and skipped by the sync,
-- so the next popular attractions of the country take their places
ALTER TABLE attractions ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

-- Result of the latest sync of every country, shown in the admin panel
CREATE TABLE country_syncs
(
    country     VARCHAR(100) NOT NULL,
    synced_at   DATETIME(6)  NOT NULL,
    succeeded   BOOLEAN      NOT NULL,
    place_count INT          NOT NULL,
    message     VARCHAR(500),
    PRIMARY KEY (country)
);
