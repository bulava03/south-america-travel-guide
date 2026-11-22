package com.project.guide.service;

import com.project.guide.config.CommonsProperties;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.service.CommonsClient.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Keeps only real photos of attractions and records the size of the main one.
 * Wikidata images are sometimes maps, satellite images or drawings instead of photos.
 */
@Service
@Slf4j
public class PhotoService {

    private final CommonsClient commonsClient;
    private final Pattern nonPhotoWords;

    public PhotoService(CommonsClient commonsClient, CommonsProperties properties) {
        this.commonsClient = commonsClient;
        this.nonPhotoWords = Pattern.compile(
                properties.nonPhotoWords().stream().map(Pattern::quote).collect(Collectors.joining("|", "\\b(", ")\\b")),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Removes images that are not photos and sets the main photo size.
     * If Commons is unavailable, images are kept and sizes stay unknown.
     *
     * @return attractions that have a photo; the others are not needed on the site
     */
    public List<WikidataAttraction> selectPhotos(List<WikidataAttraction> attractions) {
        Map<String, ImageInfo> info;
        try {
            info = commonsClient.imageInfo(attractions.stream()
                    .flatMap(attraction -> attraction.getImages().stream())
                    .map(PhotoService::fileName)
                    .toList());
        } catch (RuntimeException e) {
            log.warn("Commons is unavailable, photos are not checked: {}", e.getMessage());
            return attractions;
        }

        List<WikidataAttraction> withPhoto = new ArrayList<>();
        for (WikidataAttraction attraction : attractions) {
            List<String> photos = attraction.getImages().stream()
                    .filter(url -> isPhoto(fileName(url), info.get(fileName(url))))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (photos.size() < attraction.getImages().size()) {
                log.info("Not photos of {}: {}", attraction.getName(), attraction.getImages().stream()
                        .filter(url -> !photos.contains(url)).map(PhotoService::fileName).toList());
            }
            if (photos.isEmpty()) {
                log.info("{} has no photo and is skipped", attraction.getName());
                continue;
            }
            attraction.setImages(photos);

            ImageInfo main = info.get(fileName(photos.get(0)));
            attraction.setImageWidth(main.width());
            attraction.setImageHeight(main.height());
            withPhoto.add(attraction);
        }
        return withPhoto;
    }

    /**
     * A photo exists on Commons, is not a vector drawing and has no "map", "flag" etc. in its name.
     */
    boolean isPhoto(String fileName, ImageInfo info) {
        if (info == null || "image/svg+xml".equals(info.mime())) {
            return false;
        }
        // "Lago_Uru_Uru" -> "Lago Uru Uru", "JuncalNASA" -> "Juncal NASA", so whole words match
        String words = fileName.replace('_', ' ').replaceAll("(?<=\\p{Ll})(?=\\p{Lu})", " ");
        return !nonPhotoWords.matcher(words).find();
    }

    /**
     * "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu.jpg" -> "Machu Picchu.jpg"
     */
    static String fileName(String imageUrl) {
        return URLDecoder.decode(imageUrl.substring(imageUrl.lastIndexOf('/') + 1), UTF_8);
    }
}
