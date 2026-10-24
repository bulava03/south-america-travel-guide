CREATE TABLE attractions
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    wikidata_uri  VARCHAR(500)  NOT NULL,
    name          VARCHAR(500)  NOT NULL,
    type          VARCHAR(200),
    country       VARCHAR(100),
    latitude      DOUBLE,
    longitude     DOUBLE,
    main_image    VARCHAR(1000),
    description   TEXT,
    wikipedia_url VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_attractions_wikidata_uri UNIQUE (wikidata_uri)
);

CREATE INDEX idx_attractions_country ON attractions (country);

CREATE TABLE attraction_images
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    image_url     VARCHAR(1000) NOT NULL,
    attraction_id BIGINT        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attraction_images_attraction
        FOREIGN KEY (attraction_id) REFERENCES attractions (id) ON DELETE CASCADE
);
