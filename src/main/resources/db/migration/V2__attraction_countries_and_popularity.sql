-- An attraction can be located in several countries (e.g. Iguazu Falls: Argentina and Brazil)
CREATE TABLE attraction_countries
(
    attraction_id BIGINT       NOT NULL,
    country       VARCHAR(100) NOT NULL,
    PRIMARY KEY (attraction_id, country),
    CONSTRAINT fk_attraction_countries_attraction
        FOREIGN KEY (attraction_id) REFERENCES attractions (id) ON DELETE CASCADE
);

CREATE INDEX idx_attraction_countries_country ON attraction_countries (country);

INSERT INTO attraction_countries (attraction_id, country)
SELECT id, country
FROM attractions
WHERE country IS NOT NULL;

DROP INDEX idx_attractions_country ON attractions;
ALTER TABLE attractions DROP COLUMN country;

-- Number of Wikipedia language editions, used to rank attractions
ALTER TABLE attractions ADD COLUMN popularity INT NOT NULL DEFAULT 0;
