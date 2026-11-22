# South America Travel Guide

[![CI](https://github.com/bulava03/south-america-travel-guide/actions/workflows/ci.yml/badge.svg)](https://github.com/bulava03/south-america-travel-guide/actions/workflows/ci.yml)

A travel guide to the most famous places of the twelve South American countries: waterfalls, glaciers,
salt flats, volcanoes and old cities. Places, photos and coordinates come from Wikidata and
Wikimedia Commons, descriptions from Wikipedia. An admin panel keeps the catalog up to date.

![Home page](docs/screenshots/home.png)

| Places | Attraction page |
|---|---|
| ![Places](docs/screenshots/places.png) | ![Attraction page](docs/screenshots/attraction.png) |

| Admin panel | Page not found |
|---|---|
| ![Admin panel](docs/screenshots/admin.png) | ![Page not found](docs/screenshots/not-found.png) |

## Tech stack

Java 21, Spring Boot 3.2 (Web, Data JPA, Security, Thymeleaf), MySQL 8.4, Flyway, Apache Jena (SPARQL),
springdoc-openapi, Docker Compose. Tests: JUnit 5, Mockito, Spring MockMvc, Playwright. CI: GitHub Actions.

## Features

- **The most popular places of every country.** Popularity is the number of Wikipedia language
  editions; at most four places of one type per country keep the list varied.
- **Only places with a real photo.** Every photo is checked on Commons: maps, flags and drawings are
  dropped, and a place left without a photo is not added at all. Each card shows the photo in the way
  that suits its size: cropped, whole over a blurred copy, or as a print when it is too small to be
  stretched. If a photo link breaks later, the card shows the place name on green instead.
- **Places in several countries** (Iguazu Falls: Argentina and Brazil) are shown once, in both countries.
- **Fast pages.** Place data and descriptions are saved in the database during the sync, so pages
  never wait for Wikidata or Wikipedia.
- **Admin panel** with a background sync and its progress for every country, retry of a failed
  country, and hiding of places that should not be on the site; the next popular place takes their slot.
- **Public REST API** with interactive documentation (Swagger UI).

## Running

Requires Docker.

```bash
docker compose up -d --build
```

- Site: http://localhost:8080
- Admin panel: http://localhost:8080/admin (`admin` / `admin` by default)
- API documentation: http://localhost:8080/swagger-ui.html

On the first start the database is empty, so the app starts a sync with Wikidata in the background.
It takes about a minute; the progress is shown in the admin panel.

Without `.env` Compose uses dev defaults for the passwords. To set your own (and before any real
deployment), copy `.env.example` to `.env` and change them; the database password is applied only
when the MySQL volume is created, so for an existing one run `docker compose down -v` first.

To run the app from an IDE, start only the database (`docker compose up -d mysql`) and copy
`.env.example` to `.env`: the app reads it, and `DB_URL` there points to the container on port 3307.

### Configuration

| Variable | Description |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | MySQL user, created by the container on the first start; dev defaults in Compose |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Admin panel login, `admin` / `admin` in Compose by default; outside Compose the app does not start without the password |
| `DB_URL` | JDBC URL, only needed outside Docker |
| `SYNC_PER_COUNTRY_LIMIT` | Places selected per country, 25 by default; places without a real photo are dropped after that |
| `SYNC_MAX_PER_TYPE` | Places of one type per country, 4 by default |

Countries, place types and their priority are in [`wikidata.yml`](src/main/resources/wikidata.yml);
words that mark a file as not a photo are in [`commons.yml`](src/main/resources/commons.yml).

## How the sync works

One SPARQL query by type and country is too slow for the 60-second limit of the Wikidata Query Service,
so every country is fetched from Wikidata in three fast steps:

1. **Search:** the Wikidata search API finds candidates of the configured types with a photo and coordinates.
2. **Candidates:** a SPARQL query ([`candidates.rq`](src/main/resources/sparql/candidates.rq)) gets their types
   and popularity; the most popular are selected, with a limit per type. Hidden places are skipped here.
3. **Details:** a second query ([`details.rq`](src/main/resources/sparql/details.rq)) gets names, coordinates,
   photos and Wikipedia links of the selected places.

Then the places of all fetched countries go through three more steps:

4. **Photos:** every photo is checked on Wikimedia Commons (file type and size, 50 files per request).
   Places left without a real photo are dropped.
5. **Descriptions:** the first paragraph of every place's English Wikipedia article (20 articles per request).
   If Wikipedia does not answer, the place keeps its previous description.
6. **Saving.**

A failed country is retried once and then skipped: its places stay as they were. A place that is no longer
in the result (Wikidata stopped returning it, or its photo was removed) is deleted only if all its countries
were fetched. The latest result of every country is stored, so the admin panel shows when each country
was synced and which ones failed.

## REST API

| Request | Description |
|---|---|
| `GET /api/attractions?country=&search=` | Places, most popular first; search by name takes precedence over the country |
| `GET /api/attractions/{id}` | Place with its Wikipedia description |
| `GET /api/countries` | Countries that have places |

Every request is answered from the database, without calls to Wikimedia. If a request fails, the answer has
the matching HTTP status (404 for an unknown place, 400 for a wrong request) and a JSON body whose `detail`
says what went wrong:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Attraction not found with id: 999",
  "instance": "/api/attractions/999"
}
```

The admin API (`/api/admin/**`) requires the admin login and is not part of the public documentation.

## Tests

```bash
mvn test
```

Unit and MockMvc tests of the services, controllers and templates; Wikidata responses are saved fixtures,
so no network is needed.

Browser tests (Playwright) run against a separate stack with its own in-memory MySQL and test data
from [`e2e/seed`](e2e/seed), on port 8081:

```bash
docker compose -f docker-compose.e2e.yml up -d --build --wait
cd e2e
npm ci
npx playwright install chromium
npx playwright test
cd ..
docker compose -f docker-compose.e2e.yml down
```

They cover every page, the photo treatments, hover colors, phone width, sign-in and the admin panel.
Photos from Commons are replaced in the browser and the sync API is mocked, so the tests make no requests
to Wikimedia. CI runs both test suites on every push.

## Project structure

```
src/main/java/com/project/guide/
  config/       security, Wikimedia HTTP client, settings from wikidata.yml and commons.yml, startup sync
  controller/   pages, REST API, admin API, error pages
  service/      Wikidata, Commons and Wikipedia clients, sync, attractions
  dto/          data for pages and the API, photo layout
  model/        JPA entities
src/main/resources/
  db/migration/ Flyway migrations
  sparql/       Wikidata queries
  templates/    Thymeleaf pages
e2e/            Playwright tests and their test data
```

## Data and licenses

The code is under the [MIT License](LICENSE). The data shown on the site comes from Wikimedia projects:

- Place data: [Wikidata](https://www.wikidata.org), CC0.
- Descriptions: [Wikipedia](https://en.wikipedia.org), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
  Every attraction page links to its article.
- Photos: [Wikimedia Commons](https://commons.wikimedia.org), under the licenses of their authors.
  Every photo links to its file page with the author and license.
- Home page photo: [Aerial view of Iguazu Falls](https://commons.wikimedia.org/wiki/File:Aerial_Foz_de_Igua%C3%A7u_26_Nov_2005.jpg);
  error pages: [Salar de Uyuni](https://commons.wikimedia.org/wiki/File:Salar_de_Uyuni_-_panoramio_(10).jpg) and
  [Torres del Paine](https://commons.wikimedia.org/wiki/File:Torres_del_Paine,_Laguna_Azul_09.jpg), Wikimedia Commons.
- Fonts: [Bricolage Grotesque](https://github.com/ateliertriay/bricolage) and
  [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans), [SIL Open Font License 1.1](https://openfontlicense.org),
  served by the app from [`static/fonts`](src/main/resources/static/fonts) with their license files.
