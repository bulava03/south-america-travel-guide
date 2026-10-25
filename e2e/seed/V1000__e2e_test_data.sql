-- Test data for the Playwright tests, loaded only by docker-compose.e2e.yml.
-- Covers every photo treatment (cover, whole photo, print, no photo), a place in two countries,
-- a hidden place and saved sync results of three countries. No Wikipedia links: pages make no requests.

INSERT INTO attractions (id, wikidata_uri, name, type, latitude, longitude, main_image, main_image_width,
                         main_image_height, description, wikipedia_url, popularity, hidden)
VALUES (1, 'http://www.wikidata.org/entity/Q676203', 'Machu Picchu', 'archaeological site', -13.1631, -72.545,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Machu%20Picchu.jpg', 5184, 3456,
        'Machu Picchu is a 15th-century Inca citadel.', NULL, 141, FALSE),
       (2, 'http://www.wikidata.org/entity/Q36332', 'Iguazu Falls', 'waterfall', -25.695, -54.4367,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Iguazu.jpg', 3008, 1854, NULL, NULL, 99, FALSE),
       (3, 'http://www.wikidata.org/entity/Q79961', 'Christ the Redeemer', 'monument', -22.9519, -43.2105,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Christ.jpg', 2667, 4000, NULL, NULL, 92, FALSE),
       (4, 'http://www.wikidata.org/entity/Q35342', 'Titicaca', 'lake', -15.825, -69.325,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Titicaca.jpg', 4288, 2848, NULL, NULL, 88, FALSE),
       (5, 'http://www.wikidata.org/entity/Q272347', 'Llullaillaco', 'volcano', -24.72, -68.5372,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Llullaillaco.jpg', 350, 247, NULL, NULL, 47, FALSE),
       (6, 'http://www.wikidata.org/entity/Q721708', 'Colón Theater', 'opera house', -34.601, -58.3833,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Colon.jpg', 3264, 2331, NULL, NULL, 44, TRUE),
       (7, 'http://www.wikidata.org/entity/Q1544582', 'Laguna Colorada', 'lake', -22.2, -67.7833,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Laguna.jpg', 9000, 1900, NULL, NULL, 39, FALSE),
       (8, 'http://www.wikidata.org/entity/Q1140612', 'La Tortuga Island', 'island', 10.9333, -65.3167,
        NULL, NULL, NULL, NULL, NULL, 31, FALSE),
       (9, 'http://www.wikidata.org/entity/Q193372', 'Sugarloaf Mountain', 'mountain', -22.9492, -43.1545,
        'http://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Sugarloaf.jpg', 4000, 2667, NULL, NULL, 30, FALSE);

INSERT INTO attraction_countries (attraction_id, country)
VALUES (1, 'Peru'),
       (2, 'Argentina'),
       (2, 'Brazil'),
       (3, 'Brazil'),
       (4, 'Bolivia'),
       (4, 'Peru'),
       (5, 'Argentina'),
       (5, 'Chile'),
       (6, 'Argentina'),
       (7, 'Bolivia'),
       (8, 'Venezuela'),
       (9, 'Brazil');

INSERT INTO attraction_images (image_url, attraction_id)
SELECT main_image, id
FROM attractions
WHERE main_image IS NOT NULL;

INSERT INTO country_syncs (country, synced_at, succeeded, place_count, message)
VALUES ('Brazil', NOW(6) - INTERVAL 2 DAY, TRUE, 25, NULL),
       ('Peru', NOW(6) - INTERVAL 2 DAY, TRUE, 24, NULL),
       ('Venezuela', NOW(6) - INTERVAL 2 DAY, FALSE, 0, 'HttpTimeoutException: request timed out');
